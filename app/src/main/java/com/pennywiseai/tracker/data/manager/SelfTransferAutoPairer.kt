package com.pennywiseai.tracker.data.manager

import androidx.room.withTransaction
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.CardRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.domain.service.RecurringMatcher
import java.time.Duration
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Automatically pairs tight-window (±2 min) own-account transfer SMS legs into
 * a single active [TransactionType.TRANSFER] row so internal moves never inflate
 * income or spending and never double-count in balances or discrepancy checks.
 */
@Singleton
class SelfTransferAutoPairer @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val cardRepository: CardRepository,
    private val database: PennyWiseDatabase
) {

    companion object {
        private const val WINDOW_SECONDS = 120L
        private const val MERGED_HASH_PREFIX = "MERGED_TRANSFER_"
        private val TRANSFER_CUE_REGEX = Regex(
            """(?i)\b(?:tfr|transfer|neft|imps|rtgs|ceft|slips|owner)\b"""
        )
        private val RETAIL_OR_ATM_REGEX = Regex(
            """(?i)(?:\bvia\s+pos\s+at\b|\bvia\s+atm\s+at\b|\batm\s*-|\bfor\s+ecom\b|\bpurchase\s+at\b|\bwithdrawal\s+at\b)"""
        )
    }

    private data class ResolvedAccount(
        val bank: String,
        val last4: String
    ) {
        val identity: String get() = "${bank.lowercase()}|$last4"
    }

    private suspend fun resolveAccount(tx: TransactionEntity): ResolvedAccount? {
        val rawLast4 = tx.accountNumber?.takeIf { it.isNotBlank() } ?: return null
        val bank = tx.bankName?.takeIf { it.isNotBlank() }.orEmpty()
        val mappedCardAccount = if (bank.isNotEmpty()) {
            cardRepository.getCard(bank, rawLast4)?.accountLast4?.takeIf { it.isNotBlank() }
        } else null
        return ResolvedAccount(bank = bank, last4 = mappedCardAccount ?: rawLast4)
    }

    private fun hasTransferCue(tx: TransactionEntity): Boolean =
        TRANSFER_CUE_REGEX.containsMatchIn(tx.merchantName) ||
            (!tx.smsBody.isNullOrBlank() && TRANSFER_CUE_REGEX.containsMatchIn(tx.smsBody))

    private fun isRetailOrAtm(tx: TransactionEntity): Boolean =
        !tx.smsBody.isNullOrBlank() && RETAIL_OR_ATM_REGEX.containsMatchIn(tx.smsBody)

    private fun canAutoPair(
        a: TransactionEntity,
        acctA: ResolvedAccount,
        b: TransactionEntity,
        acctB: ResolvedAccount
    ): Boolean {
        if (a.id == b.id || a.isDeleted || b.isDeleted) return false
        if (a.loanId != null || b.loanId != null ||
            a.lentSharesTotal != null || b.lentSharesTotal != null ||
            a.groupId != null || b.groupId != null
        ) return false
        val opposite = (a.transactionType == TransactionType.EXPENSE && b.transactionType == TransactionType.INCOME) ||
            (a.transactionType == TransactionType.INCOME && b.transactionType == TransactionType.EXPENSE)
        if (!opposite) return false
        if (!a.currency.equals(b.currency, ignoreCase = true)) return false
        if (a.amount.compareTo(b.amount) != 0) return false
        if (acctA.identity == acctB.identity) return false
        if (RecurringMatcher.isReversalSms(a.smsBody) || RecurringMatcher.isReversalSms(b.smsBody)) return false

        val refA = a.reference?.trim()?.takeIf { it.isNotEmpty() }
        val refB = b.reference?.trim()?.takeIf { it.isNotEmpty() }
        if (refA != null && refB != null && !refA.equals(refB, ignoreCase = true)) return false

        val gapSeconds = abs(Duration.between(a.dateTime, b.dateTime).seconds)
        if (gapSeconds > WINDOW_SECONDS) return false

        val expense = if (a.transactionType == TransactionType.EXPENSE) a else b
        if (isRetailOrAtm(expense)) return false
        if (!hasTransferCue(a) && !hasTransferCue(b)) return false

        return true
    }

    private fun isEligible(tx: TransactionEntity): Boolean =
        !tx.isDeleted &&
            (tx.transactionType == TransactionType.EXPENSE || tx.transactionType == TransactionType.INCOME) &&
            tx.loanId == null &&
            tx.lentSharesTotal == null &&
            tx.groupId == null &&
            !RecurringMatcher.isReversalSms(tx.smsBody)

    /**
     * Attempts to auto-pair [saved] with its counterpart transfer leg within ±2 minutes.
     * Callers MUST invoke this after [saved]'s own balance update has been recorded/captured
     * so both accounts keep accurate running balances.
     */
    suspend fun tryPairSavedTransaction(saved: TransactionEntity): Boolean {
        if (!isEligible(saved)) return false
        val savedAcct = resolveAccount(saved) ?: return false

        val candidates = transactionRepository.findSelfTransferCandidates(
            amount = saved.amount,
            dateStart = saved.dateTime.minusSeconds(WINDOW_SECONDS),
            dateEnd = saved.dateTime.plusSeconds(WINDOW_SECONDS),
            excludeId = saved.id
        )
        if (candidates.isEmpty()) return false

        var bestMatch: Pair<TransactionEntity, ResolvedAccount>? = null
        var bestGap = Long.MAX_VALUE
        for (cand in candidates) {
            val candAcct = resolveAccount(cand) ?: continue
            if (!canAutoPair(saved, savedAcct, cand, candAcct)) continue
            val gap = abs(Duration.between(saved.dateTime, cand.dateTime).seconds)
            if (gap < bestGap) {
                bestGap = gap
                bestMatch = cand to candAcct
            }
        }
        val (partner, partnerAcct) = bestMatch ?: return false

        val (expense, expenseAcct, income, incomeAcct) =
            if (saved.transactionType == TransactionType.EXPENSE) {
                Tuple4(saved, savedAcct, partner, partnerAcct)
            } else {
                Tuple4(partner, partnerAcct, saved, savedAcct)
            }

        consolidatePair(expense, expenseAcct, income, incomeAcct)
        return true
    }

    private data class CandidateEdge(
        val a: TransactionEntity,
        val acctA: ResolvedAccount,
        val b: TransactionEntity,
        val acctB: ResolvedAccount,
        val gapSeconds: Long,
        val exactRefMatch: Boolean
    )

    /**
     * Sweeps unpaired active SMS INCOME/EXPENSE transactions and consolidates any
     * matching own-account transfer pairs. Idempotent and safe after batch scans or upgrades.
     */
    suspend fun sweepExistingUnpairedTransfers(): Int {
        val rows = transactionRepository.getActiveSmsIncomeExpenseTransactions()
        if (rows.size < 2) return 0

        val acctCache = HashMap<Long, ResolvedAccount?>(rows.size)
        suspend fun acctFor(tx: TransactionEntity): ResolvedAccount? =
            acctCache.getOrPut(tx.id) { resolveAccount(tx) }

        val candidates = ArrayList<CandidateEdge>()
        for (i in rows.indices) {
            val a = rows[i]
            if (!isEligible(a)) continue
            val acctA = acctFor(a) ?: continue

            for (j in (i + 1) until rows.size) {
                val b = rows[j]
                if (Duration.between(a.dateTime, b.dateTime).seconds > WINDOW_SECONDS) break
                if (!isEligible(b)) continue
                val acctB = acctFor(b) ?: continue
                if (!canAutoPair(a, acctA, b, acctB)) continue
                val gap = abs(Duration.between(a.dateTime, b.dateTime).seconds)
                val refA = a.reference?.trim()?.takeIf { it.isNotEmpty() }
                val refB = b.reference?.trim()?.takeIf { it.isNotEmpty() }
                val refMatch = refA != null && refB != null && refA.equals(refB, ignoreCase = true)
                candidates += CandidateEdge(a, acctA, b, acctB, gap, refMatch)
            }
        }

        if (candidates.isEmpty()) return 0
        candidates.sortWith(
            compareBy<CandidateEdge> { it.gapSeconds }
                .thenByDescending { it.exactRefMatch }
                .thenBy { it.a.dateTime }
                .thenBy { it.a.id }
                .thenBy { it.b.id }
        )

        val claimed = HashSet<Long>()
        var pairedCount = 0
        for (edge in candidates) {
            if (edge.a.id in claimed || edge.b.id in claimed) continue
            claimed += edge.a.id
            claimed += edge.b.id
            val (expense, expenseAcct, income, incomeAcct) =
                if (edge.a.transactionType == TransactionType.EXPENSE) {
                    Tuple4(edge.a, edge.acctA, edge.b, edge.acctB)
                } else {
                    Tuple4(edge.b, edge.acctB, edge.a, edge.acctA)
                }
            consolidatePair(expense, expenseAcct, income, incomeAcct)
            pairedCount++
        }
        return pairedCount
    }

    private suspend fun consolidatePair(
        expense: TransactionEntity,
        expenseAcct: ResolvedAccount,
        income: TransactionEntity,
        incomeAcct: ResolvedAccount
    ) {
        val now = LocalDateTime.now()
        database.withTransaction {
            transactionRepository.updateTransaction(
                expense.copy(
                    transactionType = TransactionType.TRANSFER,
                    fromAccount = expenseAcct.last4,
                    toAccount = incomeAcct.last4,
                    fromBankName = expense.bankName,
                    toBankName = income.bankName,
                    updatedAt = now
                )
            )
            transactionRepository.updateTransaction(
                income.copy(
                    isDeleted = true,
                    transactionHash = "$MERGED_HASH_PREFIX${expense.id}_${income.transactionHash}",
                    updatedAt = now
                )
            )
        }
    }

    private data class Tuple4<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
}
