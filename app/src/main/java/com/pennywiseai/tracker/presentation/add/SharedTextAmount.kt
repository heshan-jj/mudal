// Mudal fork: modified from PennyWise AI (AGPL-3.0-only) on 2026-10-21 - see NOTICE.
package com.pennywiseai.tracker.presentation.add

import java.math.BigDecimal

/**
 * Extracts a spending amount and optional currency from freeform text shared
 * into the app (e.g. from a UPI receipt, a WhatsApp message, or another tracker).
 *
 * Scans for explicit currency tags first ($120, EUR 45, LKR 500, Rs. 1500).
 * If none match and the note is short (<= 40 chars), falls back to the first
 * bare number on the assumption that a shared string like "450 lunch" is the amount.
 */
object SharedTextAmount {
    data class Guess(val amount: BigDecimal, val currency: String?)

    // An amount ends cleanly: never cut "12.345" down to 12.34.
    private const val END = """(?![\d,]|\.\d)"""
    private const val NUMBER = """(\d{1,3}(?:,\d{2,3})+(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)$END"""
    private const val SMALL_NUMBER = """(\d{1,6}(?:\.\d{1,2})?)$END"""

    // \b keeps "rs" from matching inside words ("users 50").
    private val TAGGED = Regex("""([$€]|\b(?:rs\.?|lkr|usd|eur)(?![a-z]))\s*$NUMBER""", RegexOption.IGNORE_CASE)
    private val BARE = Regex("""(?<![\w/.,-])$SMALL_NUMBER(?![/-])""")
    private const val SHORT_NOTE_CHARS = 40

    private fun currencyOf(tag: String): String? = when (tag.lowercase().trimEnd('.')) {
        "rs", "lkr" -> "LKR"
        "$", "usd" -> "USD"
        "€", "eur" -> "EUR"
        else -> null
    }

    fun extract(text: String): Guess? {
        TAGGED.find(text)?.let { m ->
            val amount = m.groupValues[2].replace(",", "").toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
            return amount?.let { Guess(it, currencyOf(m.groupValues[1])) }
        }
        if (text.length > SHORT_NOTE_CHARS) return null
        val bare = BARE.find(text)?.groupValues?.get(1)?.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }
        return bare?.let { Guess(it, null) }
    }
}
