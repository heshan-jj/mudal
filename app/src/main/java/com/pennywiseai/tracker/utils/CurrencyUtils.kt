// Mudal fork: modified from PennyWise AI (AGPL-3.0-only) on 2026-10-21 - see NOTICE.
package com.pennywiseai.tracker.utils

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Utility functions for currency formatting.
 * Supported currencies are restricted to LKR (Sri Lankan Rupee), USD (US Dollar), and EUR (Euro).
 */
object CurrencyUtils {

    private val lkLocale = Locale.Builder().setLanguage("en").setRegion("LK").build()
    private val lkCurrencyFormat = NumberFormat.getCurrencyInstance(lkLocale).apply {
        try {
            currency = Currency.getInstance("LKR")
        } catch (_: Exception) {}
        maximumFractionDigits = 0
    }

    /**
     * Formats a BigDecimal amount as Sri Lankan Rupees
     */
    fun formatCurrency(amount: BigDecimal): String {
        return if (amount.stripTrailingZeros().scale() > 0) {
            val formatter = NumberFormat.getCurrencyInstance(lkLocale).apply {
                try {
                    currency = Currency.getInstance("LKR")
                } catch (_: Exception) {}
                maximumFractionDigits = 2
                minimumFractionDigits = 1
            }
            formatter.format(amount)
        } else {
            lkCurrencyFormat.format(amount)
        }
    }

    /**
     * Formats a Double amount as Sri Lankan Rupees
     */
    fun formatCurrency(amount: Double): String {
        return formatCurrency(BigDecimal.valueOf(amount))
    }

    /**
     * Formats an Int amount as Sri Lankan Rupees
     */
    fun formatCurrency(amount: Int): String {
        return formatCurrency(BigDecimal(amount))
    }

    /**
     * Formats an amount with a custom number of decimal places
     */
    fun formatCurrency(amount: BigDecimal, decimalPlaces: Int): String {
        val formatter = NumberFormat.getCurrencyInstance(lkLocale).apply {
            try {
                currency = Currency.getInstance("LKR")
            } catch (_: Exception) {}
            maximumFractionDigits = decimalPlaces
            minimumFractionDigits = decimalPlaces
        }
        return formatter.format(amount)
    }

    /**
     * Sorts a list of currency codes with LKR prioritized first, followed by USD, EUR, then others.
     */
    fun sortCurrencies(currencies: List<String>): List<String> {
        val priority = listOf("LKR", "USD", "EUR")
        return currencies.sortedWith { a, b ->
            val idxA = priority.indexOf(a.uppercase())
            val idxB = priority.indexOf(b.uppercase())
            when {
                idxA != -1 && idxB != -1 -> idxA.compareTo(idxB)
                idxA != -1 -> -1
                idxB != -1 -> 1
                else -> a.compareTo(b)
            }
        }
    }

    /**
     * Returns the list of all supported currencies in Mudal.
     * Restricted to LKR, USD, and EUR.
     */
    fun getAllSupportedCurrencies(): List<String> {
        return listOf("LKR", "USD", "EUR")
    }
}
