// Mudal fork: new file in Mudal, a fork of PennyWise AI (AGPL-3.0-only), 2026-10-23 - see NOTICE.
package com.pennywiseai.tracker.domain.service

import com.pennywiseai.tracker.data.merchant.cleanMerchantName
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * One spend row the detector is allowed to consider. Callers drop transfers,
 * income, loan legs, and deleted rows before they get here.
 */
data class SubscriptionCharge(
    val merchantName: String,
    val amount: BigDecimal,
    val currency: String,
    val date: LocalDate,
    val category: String? = null,
    val bankName: String? = null,
    val accountLast4: String? = null,
)

/**
 * A repeating charge the user has not saved as a subscription yet.
 * [key] is the dismiss token: cleaned merchant plus currency.
 */
data class DetectedSubscription(
    val key: String,
    val merchantName: String,
    val amount: BigDecimal,
    val currency: String,
    val billingCycle: String,
    val nextPaymentDate: LocalDate,
    val chargeCount: Int,
    val category: String? = null,
    val bankName: String? = null,
    val accountLast4: String? = null,
)

/**
 * Finds subscriptions in spending history.
 *
 * A merchant qualifies when the same cleaned name and currency show up on at
 * least two different days, the gaps line up with a billing cycle, and the
 * amounts stay in the same band (a price change like 1,000 → 1,500 still
 * counts; a grocery basket that jumps around does not). Everyday shops,
 * fuel, and rides are left out — those repeat without being a bill. Anything
 * already saved, hidden, or dismissed is left out too.
 */
object SubscriptionDetector {

    fun key(merchantName: String, currency: String): String {
        val name = cleanMerchantName(merchantName).trim().lowercase()
        val code = currency.trim().uppercase().ifBlank { "LKR" }
        return "$name|$code"
    }

    fun detect(
        charges: List<SubscriptionCharge>,
        existingKeys: Set<String>,
        dismissedKeys: Set<String>,
        today: LocalDate,
    ): List<DetectedSubscription> {
        return charges
            .asSequence()
            .filter { it.amount.signum() > 0 && it.merchantName.isNotBlank() }
            .groupBy { key(it.merchantName, it.currency) }
            .mapNotNull { (groupKey, group) ->
                val nameKey = groupKey.substringBefore('|')
                if (nameKey.isBlank() || nameKey in NOT_A_SUBSCRIPTION) return@mapNotNull null
                if (groupKey in existingKeys || groupKey in dismissedKeys) return@mapNotNull null
                toSuggestion(group, groupKey, today)
            }
            .sortedWith(compareBy({ it.nextPaymentDate }, { it.merchantName.lowercase() }))
            .toList()
    }

    private fun toSuggestion(
        group: List<SubscriptionCharge>,
        groupKey: String,
        today: LocalDate,
    ): DetectedSubscription? {
        val byDay = group
            .sortedWith(compareBy<SubscriptionCharge> { it.date }.thenByDescending { it.amount })
            .distinctBy { it.date }
        val kept = similarAmounts(byDay) ?: return null
        val dates = kept.map { it.date }.sorted()
        val gaps = dates.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b) }.filter { it > 0 }
        val cycle = inferCycle(gaps) ?: return null
        val last = dates.last()
        if (isStale(last, cycle, today)) return null
        val latest = kept.maxBy { it.date }
        return DetectedSubscription(
            key = groupKey,
            merchantName = cleanMerchantName(latest.merchantName).ifBlank { latest.merchantName.trim() },
            amount = latest.amount,
            currency = latest.currency.trim().uppercase().ifBlank { "LKR" },
            billingCycle = cycle,
            nextPaymentDate = nextDate(last, cycle, today),
            chargeCount = kept.size,
            category = kept.sortedByDescending { it.date }
                .firstNotNullOfOrNull { it.category?.trim()?.takeIf { name -> name.isNotEmpty() } },
            bankName = latest.bankName?.takeIf { it.isNotBlank() },
            accountLast4 = latest.accountLast4?.takeIf { it.isNotBlank() },
        )
    }

    /** Keep charges that sit within 50% of the median so a price change survives and a random basket does not. */
    private fun similarAmounts(charges: List<SubscriptionCharge>): List<SubscriptionCharge>? {
        if (charges.size < 2) return null
        val median = median(charges.map { it.amount })
        if (median.signum() <= 0) return null
        val tolerance = median.multiply(AMOUNT_TOLERANCE)
        val kept = charges.filter { it.amount.subtract(median).abs() <= tolerance }
        return kept.takeIf { it.size >= 2 }
    }

    private fun median(amounts: List<BigDecimal>): BigDecimal {
        val sorted = amounts.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[mid]
        } else {
            sorted[mid - 1].add(sorted[mid]).divide(BigDecimal(2), 2, RoundingMode.HALF_UP)
        }
    }

    private fun inferCycle(gaps: List<Long>): String? {
        if (gaps.isEmpty()) return null
        val scored = CYCLES.map { cycle -> cycle to gaps.count { it in cycle.gapDays } }
        val bestHits = scored.maxOf { it.second }
        if (bestHits <= 0) return null
        val medianGap = gaps.sorted()[gaps.size / 2]
        val best = scored
            .filter { it.second == bestHits }
            .minBy { kotlin.math.abs(midpoint(it.first.gapDays) - medianGap) }
            .first
        val needed = (gaps.size + 1) / 2
        return best.name.takeIf { bestHits >= needed }
    }

    private fun midpoint(range: LongRange): Long = (range.first + range.last) / 2

    private fun isStale(last: LocalDate, cycle: String, today: LocalDate): Boolean {
        val horizon = when (cycle) {
            "Weekly" -> today.minusWeeks(3)
            "Quarterly" -> today.minusMonths(8)
            "Semi-Annual" -> today.minusMonths(14)
            "Annual" -> today.minusYears(3)
            else -> today.minusMonths(3)
        }
        return last.isBefore(horizon)
    }

    private fun nextDate(last: LocalDate, cycle: String, today: LocalDate): LocalDate {
        var next = step(last, cycle)
        var guard = 0
        while (next.isBefore(today) && guard < 60) {
            next = step(next, cycle)
            guard++
        }
        return next
    }

    /** Same labels [com.pennywiseai.tracker.data.repository.SubscriptionRepository.advance] understands. */
    private fun step(date: LocalDate, cycle: String): LocalDate = when (cycle) {
        "Weekly" -> date.plusWeeks(1)
        "Quarterly" -> date.plusMonths(3)
        "Semi-Annual" -> date.plusMonths(6)
        "Annual" -> date.plusYears(1)
        else -> date.plusMonths(1)
    }

    private data class Cycle(val name: String, val gapDays: LongRange)

    private val CYCLES = listOf(
        Cycle("Weekly", 6L..9L),
        Cycle("Monthly", 25L..40L),
        Cycle("Quarterly", 75L..110L),
        Cycle("Semi-Annual", 160L..200L),
        Cycle("Annual", 330L..400L),
    )

    private val AMOUNT_TOLERANCE = BigDecimal("0.50")

    /**
     * Shops, fuel, and rides people pay over and over without them being a
     * bill. Matched on the cleaned display name, lowercased.
     */
    private val NOT_A_SUBSCRIPTION = setOf(
        "keells",
        "cargills food city",
        "arpico",
        "glomark",
        "laugfs super",
        "spar",
        "sathosa",
        "finagle",
        "perera & sons",
        "fab",
        "barista",
        "java lounge",
        "sen-saal",
        "sponge",
        "dinemore",
        "kfc",
        "mcdonald's",
        "pizza hut",
        "burger king",
        "subway",
        "domino's",
        "popeyes",
        "breadtalk",
        "uber eats",
        "uber",
        "pickme",
        "kangaroo cabs",
        "ceypetco",
        "lanka ioc",
        "sinopec",
        "shell",
        "daraz",
        "kapruka",
        "odel",
        "nolimit",
        "cool planet",
        "fashion bug",
        "abans",
        "singer mega",
        "singer",
        "damro",
        "singhagiri",
        "takas",
        "wasi",
        "simplytek",
        "idealz",
        "spa ceylon",
        "barefoot",
        "laksala",
        "dsi",
        "bata",
        "sarasavi",
        "vijitha yapa",
        "mydeal",
        "healthguard",
        "osu sala",
        "asiri",
        "nawaloka",
        "durdans",
        "hemas",
        "ninewells",
        "doc990",
        "echannelling",
        "vision care",
        "scope cinemas",
        "mytickets",
    )
}
