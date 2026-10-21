// Mudal fork: new file in Mudal, a fork of PennyWise AI (AGPL-3.0-only), 2026-10-21 - see NOTICE.
package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Commercial Bank of Ceylon (CBC / COMBANK, Sri Lanka) SMS messages.
 *
 * Currency is LKR (stated as "LKR" on card SMS and "Rs." on branch credits).
 * Sender: COMBANK, CBC, COMBNK, etc.
 *
 * Supported formats:
 * - Card purchase (EXPENSE):
 *   "Dear Cardholder, Purchase at TERRA LOUNGE CAFE & RESKURUNEGALA KGLK for LKR 1,144.00
 *    on 07/03/26 04:25 PM has been authorised on your debit card ending #4101."
 * - ATM withdrawal (EXPENSE):
 *   "Withdrawal at ICBS AKUB3 KU BZ3 LK for LKR 1,500.00 on 17/02/26 09:22 AM
 *    from card ending #4101."
 * - Branch / Digital Banking credit (INCOME):
 *   "Credit for Rs. 400.00 to 8000123415 at 11:17 at DIGITAL BANKING DIVISION"
 */
class CommercialBankParser : BankParser() {

    override fun getBankName() = "Commercial Bank"

    override fun getCurrency() = "LKR"

    override fun canHandle(sender: String): Boolean {
        val upper = sender.uppercase()
        return upper.contains("COMBANK") ||
               upper.contains("COMBNK") ||
               upper == "CBC" ||
               upper.contains("COMMERCIAL")
    }

    override fun isTransactionMessage(message: String): Boolean {
        if (isNonTransactionMessage(message)) return false
        val lower = message.lowercase()
        return lower.contains("purchase at") ||
               lower.contains("authorised") ||
               lower.contains("withdrawal at") ||
               lower.startsWith("credit for") ||
               super.isTransactionMessage(message)
    }

    override fun extractAmount(message: String): BigDecimal? {
        val pattern = Regex(
            """(?:for\s+LKR|for\s+Rs\.?|Credit\s+for\s+Rs\.?)\s+([0-9,]+\.\d{2})""",
            RegexOption.IGNORE_CASE
        )
        pattern.find(message)?.let { match ->
            return match.groupValues[1].replace(",", "").toBigDecimalOrNull()
        }

        val fallback = Regex("""(?:LKR|Rs\.?)\s+([0-9,]+\.\d{2})""", RegexOption.IGNORE_CASE)
        fallback.find(message)?.let { match ->
            return match.groupValues[1].replace(",", "").toBigDecimalOrNull()
        }
        return null
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lower = message.lowercase()
        return when {
            lower.startsWith("credit for") || lower.contains("credited") -> TransactionType.INCOME
            lower.contains("purchase at") || lower.contains("has been authorised") ||
            lower.startsWith("withdrawal at") || lower.contains("debited") -> TransactionType.EXPENSE
            else -> null
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        val purchasePattern = Regex("""Purchase\s+at\s+(.+?)\s+for\s+LKR""", RegexOption.IGNORE_CASE)
        purchasePattern.find(message)?.let { match ->
            var merchant = match.groupValues[1].trim()
            if (merchant.endsWith(" LK", ignoreCase = true)) {
                merchant = merchant.dropLast(3).trim()
            }
            if (merchant.isNotEmpty()) return merchant
        }

        val withdrawalPattern = Regex("""Withdrawal\s+at\s+(.+?)\s+for\s+LKR""", RegexOption.IGNORE_CASE)
        withdrawalPattern.find(message)?.let { match ->
            var loc = match.groupValues[1].trim()
            if (loc.endsWith(" LK", ignoreCase = true)) {
                loc = loc.dropLast(3).trim()
            }
            if (loc.isNotEmpty()) return "ATM - $loc"
        }

        val creditPattern = Regex("""at\s+\d{1,2}:\d{2}\s+at\s+(.+)$""", RegexOption.IGNORE_CASE)
        creditPattern.find(message)?.let { match ->
            val branch = match.groupValues[1].trim()
            if (branch.isNotEmpty()) return branch
        }

        return null
    }

    override fun extractAccountLast4(message: String): String? {
        val cardPattern = Regex("""ending\s+#?(\d{4})""", RegexOption.IGNORE_CASE)
        cardPattern.find(message)?.let { match ->
            return match.groupValues[1]
        }

        val acctPattern = Regex("""to\s+([A-Za-z0-9_]{4,})""", RegexOption.IGNORE_CASE)
        acctPattern.find(message)?.let { match ->
            val raw = match.groupValues[1]
            val digits = raw.filter { it.isDigit() }
            return if (digits.length >= 4) digits.takeLast(4) else if (digits.length >= 3) digits else null
        }

        return null
    }

    override fun detectIsCard(message: String): Boolean {
        val lower = message.lowercase()
        return lower.contains("card ending") || lower.contains("cardholder")
    }
}
