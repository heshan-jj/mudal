package com.pennywiseai.tracker.data.manager

import androidx.room.withTransaction
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.RecurringTransactionRepository
import com.pennywiseai.tracker.data.repository.SubscriptionRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.domain.service.RecurringMatcher
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Shared reconciliation layer for SMS/notification transactions against active
 * subscriptions and recurring templates.
 */
@Singleton
class RecurringReconciliationService @Inject constructor(
    private val subscriptionRepository: SubscriptionRepository,
    private val recurringTransactionRepository: RecurringTransactionRepository,
    private val transactionRepository: TransactionRepository,
    private val accountBalanceRepository: AccountBalanceRepository,
    private val database: PennyWiseDatabase
) {

    sealed class PreparedMatch {
        data object None : PreparedMatch()
        data class Subscription(val match: RecurringMatcher.SubscriptionMatch) : PreparedMatch()
        data class Template(
            val match: RecurringMatcher.TemplateMatch,
            val placeholderToReplace: TransactionEntity? = null
        ) : PreparedMatch()
    }

    suspend fun prepareForIncomingTransaction(
        candidate: TransactionEntity,
        resolvedAccountLast4: String?
    ): PreparedMatch {
        if (RecurringMatcher.isReversalSms(candidate.smsBody)) return PreparedMatch.None

        val txDate = candidate.dateTime.toLocalDate()
        val activeSubs = subscriptionRepository.getActiveSubscriptionsList()
        val candidateSubTxns = mutableMapOf<Long, List<TransactionEntity>>()
        for (sub in activeSubs) {
            if (RecurringMatcher.merchantsMatch(candidate.merchantName, sub.merchantName) &&
                RecurringMatcher.isAmountWithinTolerance(candidate.amount, sub.amount)
            ) {
                candidateSubTxns[sub.id] = transactionRepository.getTransactionsForSubscriptionInRange(
                    subscriptionId = sub.id,
                    start = txDate.minusDays(20).atStartOfDay(),
                    end = txDate.plusDays(20).atTime(LocalTime.MAX)
                )
            }
        }
        val subMatch = RecurringMatcher.findBestSubscriptionMatch(
            tx = candidate,
            activeSubs = activeSubs,
            resolvedAccountLast4 = resolvedAccountLast4,
            hasLinkedTxnInCycle = { subId, window ->
                val linked = candidateSubTxns[subId].orEmpty()
                linked.any { existing ->
                    !existing.smsBody.isNullOrBlank() && window.contains(existing.dateTime.toLocalDate())
                }
            }
        )
        if (subMatch != null) return PreparedMatch.Subscription(subMatch)

        val activeTemplates = recurringTransactionRepository.getActiveList()
        val candidateTemplateTxns = mutableMapOf<Long, List<TransactionEntity>>()
        for (tpl in activeTemplates) {
            if (RecurringMatcher.merchantsMatch(candidate.merchantName, tpl.merchantName) &&
                RecurringMatcher.isAmountWithinTolerance(candidate.amount, tpl.amount)
            ) {
                candidateTemplateTxns[tpl.id] = transactionRepository.getTransactionsForTemplateInRange(
                    templateId = tpl.id,
                    start = txDate.minusDays(20).atStartOfDay(),
                    end = txDate.plusDays(20).atTime(LocalTime.MAX)
                )
            }
        }
        val tplMatch = RecurringMatcher.findBestTemplateMatch(
            tx = candidate,
            activeTemplates = activeTemplates,
            resolvedAccountLast4 = resolvedAccountLast4,
            hasLinkedTxnInCycle = { tplId, window ->
                val linked = candidateTemplateTxns[tplId].orEmpty()
                linked.any { existing ->
                    !existing.smsBody.isNullOrBlank() && window.contains(existing.dateTime.toLocalDate())
                }
            }
        ) ?: return PreparedMatch.None

        val placeholder = candidateTemplateTxns[tplMatch.template.id].orEmpty()
            .firstOrNull { existing ->
                existing.smsBody.isNullOrBlank() &&
                    tplMatch.window.contains(existing.dateTime.toLocalDate())
            }
        return PreparedMatch.Template(tplMatch, placeholder)
    }

    fun enrichEntity(entity: TransactionEntity, prepared: PreparedMatch): TransactionEntity = when (prepared) {
        PreparedMatch.None -> entity
        is PreparedMatch.Subscription -> entity.copy(
            isRecurring = true,
            subscriptionId = prepared.match.subscription.id
        )
        is PreparedMatch.Template -> entity.copy(
            isRecurring = true,
            recurringTemplateId = prepared.match.template.id
        )
    }

    suspend fun commitScheduleUpdate(prepared: PreparedMatch, savedEntity: TransactionEntity) {
        val txDate = savedEntity.dateTime.toLocalDate()
        when (prepared) {
            PreparedMatch.None -> Unit
            is PreparedMatch.Subscription -> {
                val sub = prepared.match.subscription
                val isNewerOrCurrentCycle =
                    (sub.lastPaidAt == null || !txDate.isBefore(sub.lastPaidAt)) &&
                        (sub.nextPaymentDate == null || !prepared.match.window.cycleDueDate.isBefore(sub.nextPaymentDate))
                if (isNewerOrCurrentCycle) {
                    val anchor = prepared.match.window.cycleDueDate
                    var nextDate = subscriptionRepository.advance(anchor, sub.billingCycle)
                    while (!nextDate.isAfter(txDate)) {
                        nextDate = subscriptionRepository.advance(nextDate, sub.billingCycle)
                    }
                    subscriptionRepository.markPaidWithAmount(
                        subscriptionId = sub.id,
                        paidAt = txDate,
                        nextPaymentDate = nextDate,
                        amount = savedEntity.amount
                    )
                }
            }
            is PreparedMatch.Template -> {
                val tpl = prepared.match.template
                if (!prepared.match.window.cycleDueDate.isBefore(tpl.nextDueDate)) {
                    var nextDue = tpl.nextDueAfter(prepared.match.window.cycleDueDate)
                    val floor = maxOf(tpl.nextDueDate, txDate)
                    while (!nextDue.isAfter(floor)) {
                        nextDue = tpl.nextDueAfter(nextDue)
                    }
                    recurringTransactionRepository.updateNextDueDateAndAmount(
                        id = tpl.id,
                        nextDueDate = nextDue,
                        amount = savedEntity.amount
                    )
                } else if (prepared.placeholderToReplace != null &&
                    tpl.amount.compareTo(savedEntity.amount) != 0
                ) {
                    // Worker already advanced nextDueDate for this cycle's placeholder;
                    // still update the template amount so price changes stick.
                    recurringTransactionRepository.updateNextDueDateAndAmount(
                        id = tpl.id,
                        nextDueDate = tpl.nextDueDate,
                        amount = savedEntity.amount
                    )
                }
            }
        }
    }

    /**
     * Replaces a worker-materialized recurring placeholder with the real incoming
     * bank SMS transaction so the bank's charge wins and balance adjustments remain accurate.
     *
     * @return the upgraded row id if replaced, or null if skipped due to a hash conflict.
     */
    suspend fun replaceMaterializedPlaceholder(
        prepared: PreparedMatch.Template,
        smsEntity: TransactionEntity,
        resolvedAccountLast4: String? = null
    ): Long? {
        val placeholder = prepared.placeholderToReplace ?: return null
        if (transactionRepository.getTransactionByHash(smsEntity.transactionHash) != null) {
            return null
        }
        val template = prepared.match.template
        val upgraded = smsEntity.copy(
            id = placeholder.id,
            createdAt = placeholder.createdAt,
            isRecurring = true,
            recurringTemplateId = template.id,
            loanId = placeholder.loanId,
            loanContribution = placeholder.loanContribution,
            lentSharesTotal = placeholder.lentSharesTotal
        )

        database.withTransaction {
            val phBank = placeholder.bankName
            val phAcct = placeholder.accountNumber
            if (phBank != null && phAcct != null) {
                accountBalanceRepository.ensureManualOpening(phBank, phAcct)
            }
            val effectiveAcct = resolvedAccountLast4?.takeIf { it.isNotBlank() } ?: upgraded.accountNumber
            val effectiveForBalance = if (effectiveAcct != null &&
                upgraded.bankName != null &&
                effectiveAcct != upgraded.accountNumber
            ) {
                upgraded.copy(accountNumber = effectiveAcct)
            } else {
                upgraded
            }
            val newBank = effectiveForBalance.bankName
            val newAcct = effectiveForBalance.accountNumber
            if (newBank != null && newAcct != null) {
                accountBalanceRepository.ensureManualOpening(newBank, newAcct)
            }

            transactionRepository.updateTransaction(upgraded)

            if (upgraded.balanceAfter == null) {
                accountBalanceRepository.applyTransactionBalanceShift(placeholder, effectiveForBalance)
            } else {
                // Remove placeholder's derived balance snapshot before caller writes
                // the SMS's authoritative balanceAfter timestamped at entity.dateTime.
                accountBalanceRepository.deleteBalancesForTransaction(placeholder.id)
            }

            if (phBank != null && phAcct != null && accountBalanceRepository.isManualAccount(phBank, phAcct)) {
                accountBalanceRepository.recomputeManualBalance(phBank, phAcct)
            }
            if (newBank != null && newAcct != null &&
                (newBank != phBank || newAcct != phAcct) &&
                accountBalanceRepository.isManualAccount(newBank, newAcct)
            ) {
                accountBalanceRepository.recomputeManualBalance(newBank, newAcct)
            }
            commitScheduleUpdate(prepared, upgraded)
        }
        return upgraded.id
    }
}
