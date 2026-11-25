package com.pennywiseai.tracker.domain.service

import com.pennywiseai.tracker.data.database.entity.RecurringFrequency
import com.pennywiseai.tracker.data.database.entity.RecurringTransactionEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionDirection
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionState
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Pure domain matcher uniting Subscription and Recurring Template reconciliation
 * against incoming parsed transactions (SMS or notifications).
 */
object RecurringMatcher {

    data class ScheduleWindow(
        val cycleDueDate: LocalDate,
        val start: LocalDate,
        val end: LocalDate
    ) {
        fun contains(date: LocalDate): Boolean = !date.isBefore(start) && !date.isAfter(end)
    }

    data class SubscriptionMatch(
        val subscription: SubscriptionEntity,
        val window: ScheduleWindow,
        val exactNameMatch: Boolean,
        val amountDiffRatio: BigDecimal,
        val daysFromDue: Long
    )

    data class TemplateMatch(
        val template: RecurringTransactionEntity,
        val window: ScheduleWindow,
        val exactNameMatch: Boolean,
        val amountDiffRatio: BigDecimal,
        val daysFromDue: Long
    )

    private val MIN_RATIO = BigDecimal("0.5")
    private val MAX_RATIO = BigDecimal("2.0")
    private const val MAX_HISTORICAL_CYCLES = 24

    private val GENERIC_PLACEHOLDERS = setOf(
        "visa recurring payment",
        "transfer inward",
        "ceft transfer",
        "bank interest",
        "manual entry",
        "unknown",
        "merchant",
        "atm"
    )

    private val GENERIC_BRAND_TOKENS = setOf(
        "google", "microsoft", "apple", "amazon", "meta", "samsung",
        "adobe", "visa", "mastercard", "paypal", "bank", "card",
        "pay", "store", "shop", "online", "payment", "service", "services"
    )

    private val REVERSAL_REGEX = Regex(
        """(?i)(?:\becom/rev\b|\btr\s*rev\b|\breversal\b|\brefund(?:ed)?\s+(?:for|of|from|to)\b|\bamount\s+refunded\b)"""
    )
    private val LEADING_NOISE_REGEX = Regex(
        """^(?:via\s+pos\s+at|via\s+atm\s+at|atm\s*-|tfr\s+owner|tfr|ceft|vsrpmt[-:\s]+)\s+""",
        RegexOption.IGNORE_CASE
    )
    private val ECOM_PREFIX_REGEX = Regex("""^ecom(?:/rev)?\s+""", RegexOption.IGNORE_CASE)
    private val TRAILING_GATEWAY_REGEX = Regex(
        """\s+(?:(?:co|mo|si|vi)\s+(?:mastterm|[a-z]?\d{4,}|[a-z]{2}\s+\d{4,})|(?:co|mo|si|vi)$).*$|\s+mastterm.*$|\s*\(\s*[a-z]{2}\s+\d{5,}.*$|\s+[a-z]{2}\s+\d{6,}.*$|\s+\d{6,}.*$""",
        RegexOption.IGNORE_CASE
    )
    private val NON_ALNUM_REGEX = Regex("""[^a-z0-9]+""")
    private val WHITESPACE_REGEX = Regex("""\s+""")

    fun isReversalSms(smsBody: String?): Boolean =
        !smsBody.isNullOrBlank() && REVERSAL_REGEX.containsMatchIn(smsBody)

    fun normalizeMerchant(raw: String): String {
        var s = raw.trim()
        if (s.isEmpty()) return ""
        var prev: String
        do {
            prev = s
            s = LEADING_NOISE_REGEX.replace(s, "").trim()
            s = ECOM_PREFIX_REGEX.replace(s, "").trim()
        } while (s != prev && s.isNotEmpty())
        s = TRAILING_GATEWAY_REGEX.replace(s, "").trim()
        s = s.lowercase()
        s = NON_ALNUM_REGEX.replace(s, " ")
        return WHITESPACE_REGEX.replace(s, " ").trim()
    }

    fun merchantsMatch(candidateMerchant: String, scheduleMerchant: String): Boolean {
        val normA = normalizeMerchant(candidateMerchant)
        val normB = normalizeMerchant(scheduleMerchant)
        if (normA.isEmpty() || normB.isEmpty()) return false
        if (normA in GENERIC_PLACEHOLDERS || normB in GENERIC_PLACEHOLDERS) return false
        if (normA == normB) return true

        val tokensA = substantiveTokens(normA)
        val tokensB = substantiveTokens(normB)
        if (tokensA.isEmpty() || tokensB.isEmpty()) return false

        val (shorter, longer) = if (tokensA.size <= tokensB.size) tokensA to tokensB else tokensB to tokensA
        if (shorter.size == 1 && longer.size > 1 && shorter[0] in GENERIC_BRAND_TOKENS) {
            return false
        }
        return shorter.all { sTok ->
            longer.any { lTok -> lTok == sTok }
        }
    }

    private fun substantiveTokens(normalized: String): List<String> =
        normalized.split(' ')
            .filter { tok -> tok.length >= 3 && !tok.all(Char::isDigit) }

    fun isAmountWithinTolerance(txAmount: BigDecimal, scheduleAmount: BigDecimal): Boolean {
        if (txAmount <= BigDecimal.ZERO || scheduleAmount <= BigDecimal.ZERO) return false
        val ratio = txAmount.divide(scheduleAmount, 6, RoundingMode.HALF_UP)
        return ratio >= MIN_RATIO && ratio <= MAX_RATIO
    }

    fun cycleAdvance(date: LocalDate, billingCycle: String, reverse: Boolean = false): LocalDate {
        val sign = if (reverse) -1L else 1L
        return when (billingCycle.uppercase()) {
            "DAILY" -> date.plusDays(sign)
            "WEEKLY" -> date.plusWeeks(sign)
            "MONTHLY" -> date.plusMonths(sign)
            "QUARTERLY" -> date.plusMonths(3L * sign)
            "SEMI-ANNUAL", "SEMI ANNUAL", "SEMIANNUAL" -> date.plusMonths(6L * sign)
            "ANNUAL", "YEARLY" -> date.plusYears(sign)
            else -> date.plusMonths(sign)
        }
    }

    fun templateStepBack(template: RecurringTransactionEntity, from: LocalDate): LocalDate =
        when (template.frequency) {
            RecurringFrequency.DAILY -> from.minusDays(1)
            RecurringFrequency.WEEKLY -> from.minusWeeks(1)
            RecurringFrequency.MONTHLY -> {
                val ym = YearMonth.from(from).minusMonths(1)
                ym.atDay((template.dayOfMonth ?: from.dayOfMonth).coerceIn(1, ym.lengthOfMonth()))
            }
        }

    fun windowOffsetsForBillingCycle(billingCycle: String): Pair<Long, Long> =
        when (billingCycle.uppercase()) {
            "DAILY" -> 0L to 0L
            "WEEKLY" -> 3L to 3L
            else -> 7L to 10L
        }

    fun windowOffsetsForFrequency(frequency: RecurringFrequency): Pair<Long, Long> =
        when (frequency) {
            RecurringFrequency.DAILY -> 0L to 0L
            RecurringFrequency.WEEKLY -> 3L to 3L
            RecurringFrequency.MONTHLY -> 7L to 10L
        }

    fun findMatchingScheduleWindow(
        txDate: LocalDate,
        anchorDue: LocalDate,
        beforeDays: Long,
        afterDays: Long,
        stepBack: (LocalDate) -> LocalDate,
        stepForward: (LocalDate) -> LocalDate = { it.plusMonths(1) }
    ): ScheduleWindow? {
        var due = anchorDue
        val direct = ScheduleWindow(due, due.minusDays(beforeDays), due.plusDays(afterDays))
        if (direct.contains(txDate)) return direct
        if (txDate.isBefore(direct.start)) {
            repeat(MAX_HISTORICAL_CYCLES) {
                due = stepBack(due)
                val w = ScheduleWindow(due, due.minusDays(beforeDays), due.plusDays(afterDays))
                if (w.contains(txDate)) return w
                if (!txDate.isBefore(w.start)) return null
            }
            return null
        }

        repeat(MAX_HISTORICAL_CYCLES) {
            due = stepForward(due)
            val w = ScheduleWindow(due, due.minusDays(beforeDays), due.plusDays(afterDays))
            if (w.contains(txDate)) return w
            if (w.start.isAfter(txDate)) return null
        }
        return null
    }

    private fun subscriptionDirectionMatches(
        direction: SubscriptionDirection,
        type: TransactionType
    ): Boolean = when (direction) {
        SubscriptionDirection.EXPENSE -> type == TransactionType.EXPENSE || type == TransactionType.CREDIT
        SubscriptionDirection.INCOME -> type == TransactionType.INCOME
    }

    private fun templateTypeMatches(
        templateType: TransactionType,
        txType: TransactionType
    ): Boolean = when (templateType) {
        TransactionType.EXPENSE -> txType == TransactionType.EXPENSE || txType == TransactionType.CREDIT
        else -> txType == templateType
    }

    private fun accountsCompatible(
        scheduleBank: String?,
        scheduleLast4: String?,
        txBank: String?,
        rawTxLast4: String?,
        resolvedLast4: String?
    ): Boolean {
        val schedAcct = scheduleLast4?.takeIf { it.isNotBlank() } ?: return true
        if (scheduleBank.equals("Manual Entry", ignoreCase = true)) return true
        val raw = rawTxLast4?.takeIf { it.isNotBlank() }
        val resolved = resolvedLast4?.takeIf { it.isNotBlank() }
        if (raw == null && resolved == null) return true
        if (!txBank.isNullOrBlank() && !scheduleBank.isNullOrBlank() &&
            !txBank.equals(scheduleBank, ignoreCase = true)
        ) {
            return false
        }
        return schedAcct == resolved || schedAcct == raw
    }

    fun findBestSubscriptionMatch(
        tx: TransactionEntity,
        activeSubs: List<SubscriptionEntity>,
        resolvedAccountLast4: String?,
        hasLinkedTxnInCycle: (Long, ScheduleWindow) -> Boolean
    ): SubscriptionMatch? {
        if (isReversalSms(tx.smsBody)) return null
        val txDate = tx.dateTime.toLocalDate()
        val normTxMerchant = normalizeMerchant(tx.merchantName)

        val candidates = activeSubs.mapNotNull { sub ->
            if (sub.state != SubscriptionState.ACTIVE) return@mapNotNull null
            if (!subscriptionDirectionMatches(sub.direction, tx.transactionType)) return@mapNotNull null
            if (!tx.currency.equals(sub.currency, ignoreCase = true)) return@mapNotNull null
            if (!accountsCompatible(sub.bankName, sub.accountLast4, tx.bankName, tx.accountNumber, resolvedAccountLast4)) {
                return@mapNotNull null
            }
            if (!merchantsMatch(tx.merchantName, sub.merchantName)) return@mapNotNull null
            if (!isAmountWithinTolerance(tx.amount, sub.amount)) return@mapNotNull null

            val (beforeDays, afterDays) = windowOffsetsForBillingCycle(sub.billingCycle)
            val window = if (sub.nextPaymentDate != null) {
                findMatchingScheduleWindow(
                    txDate = txDate,
                    anchorDue = sub.nextPaymentDate,
                    beforeDays = beforeDays,
                    afterDays = afterDays,
                    stepBack = { d -> cycleAdvance(d, sub.billingCycle, reverse = true) },
                    stepForward = { d -> cycleAdvance(d, sub.billingCycle, reverse = false) }
                ) ?: return@mapNotNull null
            } else {
                ScheduleWindow(
                    cycleDueDate = txDate,
                    start = txDate.minusDays(beforeDays),
                    end = txDate.plusDays(afterDays)
                )
            }
            if (hasLinkedTxnInCycle(sub.id, window)) return@mapNotNull null

            val exactName = normTxMerchant == normalizeMerchant(sub.merchantName)
            val ratioDiff = tx.amount.subtract(sub.amount).abs()
                .divide(sub.amount, 6, RoundingMode.HALF_UP)
            val days = abs(ChronoUnit.DAYS.between(txDate, window.cycleDueDate))
            SubscriptionMatch(sub, window, exactName, ratioDiff, days)
        }

        return candidates.minWithOrNull(
            compareByDescending<SubscriptionMatch> { it.exactNameMatch }
                .thenBy { it.amountDiffRatio }
                .thenBy { it.daysFromDue }
        )
    }

    fun findBestTemplateMatch(
        tx: TransactionEntity,
        activeTemplates: List<RecurringTransactionEntity>,
        resolvedAccountLast4: String?,
        hasLinkedTxnInCycle: (Long, ScheduleWindow) -> Boolean
    ): TemplateMatch? {
        if (isReversalSms(tx.smsBody)) return null
        val txDate = tx.dateTime.toLocalDate()
        val normTxMerchant = normalizeMerchant(tx.merchantName)

        val candidates = activeTemplates.mapNotNull { template ->
            if (!template.isActive) return@mapNotNull null
            if (!templateTypeMatches(template.transactionType, tx.transactionType)) return@mapNotNull null
            if (!tx.currency.equals(template.currency, ignoreCase = true)) return@mapNotNull null
            if (!accountsCompatible(template.bankName, template.accountLast4, tx.bankName, tx.accountNumber, resolvedAccountLast4)) {
                return@mapNotNull null
            }
            if (!merchantsMatch(tx.merchantName, template.merchantName)) return@mapNotNull null
            if (!isAmountWithinTolerance(tx.amount, template.amount)) return@mapNotNull null

            val (beforeDays, afterDays) = windowOffsetsForFrequency(template.frequency)
            val window = findMatchingScheduleWindow(
                txDate = txDate,
                anchorDue = template.nextDueDate,
                beforeDays = beforeDays,
                afterDays = afterDays,
                stepBack = { d -> templateStepBack(template, d) },
                stepForward = { d -> template.nextDueAfter(d) }
            ) ?: return@mapNotNull null
            if (hasLinkedTxnInCycle(template.id, window)) return@mapNotNull null

            val exactName = normTxMerchant == normalizeMerchant(template.merchantName)
            val ratioDiff = tx.amount.subtract(template.amount).abs()
                .divide(template.amount, 6, RoundingMode.HALF_UP)
            val days = abs(ChronoUnit.DAYS.between(txDate, window.cycleDueDate))
            TemplateMatch(template, window, exactName, ratioDiff, days)
        }

        return candidates.minWithOrNull(
            compareByDescending<TemplateMatch> { it.exactNameMatch }
                .thenBy { it.amountDiffRatio }
                .thenBy { it.daysFromDue }
        )
    }
}
