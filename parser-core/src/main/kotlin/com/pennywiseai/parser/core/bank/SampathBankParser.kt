// Mudal fork: modified from PennyWise AI (AGPL-3.0-only) on 2026-10-21 - see NOTICE.
package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import java.math.BigDecimal

/**
 * Parser for Sampath Bank (Sri Lanka) SMS messages.
 *
 * Handles account transactions (SAMPATHTXN) and credit-card transactions (SAMPCCTXN).
 *
 * Account channel variants:
 * - ATM cash withdrawal: "LKR 1,005.00 debited from AC **7700 via ATM at Makumbura MMC - CASH SET"
 * - POS debit purchase: "LKR 2,160.00 debited from AC **7700 via POS at KFC-KURUNEGALA KU 80401564"
 * - POS with hyphenated merchant: "LKR 440.00 debited from AC **7700 via POS at PERERA - SONS BAKERS ( MA 51001413"
 * - eCom debit: "LKR 377.75 debited from AC **7700 for eCom PickMe Co 76134439"
 * - eCom refund/reversal: "LKR 346.54 credited to AC **7700 for eCom/REV Microsoft*Store MASTTERM"
 * - Transfer out: "LKR 20,000.00 debited from AC **7700 for TFR Vishwa -000086201821"
 * - Transfer in (CEFT/TRRR): "LKR .56 credited to AC **9786 for TRRR/000000554871/14-10-2026 13:20:43/IF"
 * - Visa recurring payment: "LKR 50.00 debited from AC **7700 for VSRPMT-000018281730"
 * - Bank interest: "LKR 155.05 credited to AC **9786 for Interest: 1xxx9786:Int.Pd:20-08-2026 to 19-09-2026"
 * - Amounts without leading zero: "LKR .56 debited from AC **9786 ..."
 *
 * Credit-card format (SAMPCCTXN):
 * - "Cr Crd no..**0282 Auth Pmt LKR 2,100.00 at SPICE ASIA - DELIVERY Avl Bal LKR 250,000.00 ..."
 */
class SampathBankParser : BankParser() {

    override fun getBankName() = "Sampath Bank"

    override fun getCurrency() = "LKR"

    override fun canHandle(sender: String): Boolean {
        val normalized = sender.uppercase()
        return normalized.contains("SAMPATHTXN") || normalized.contains("SAMPCCTXN")
    }

    override fun detectIsCard(message: String): Boolean {
        val lower = message.lowercase()
        if (lower.contains("crd no") || lower.contains("cr crd") || lower.contains("via pos at")) {
            return true
        }
        return super.detectIsCard(message)
    }

    private fun extractMessageCurrency(message: String): String {
        val pattern = Regex("""\b([A-Z]{3})\s+(?:[0-9,]*\.\d{2}|[0-9,]+)""")
        pattern.find(message)?.let { return it.groupValues[1].uppercase() }
        return getCurrency()
    }

    override fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? {
        val base = super.parse(smsBody, sender, timestamp) ?: return null
        val currency = extractMessageCurrency(smsBody)
        return if (currency == base.currency) base else base.copy(currency = currency)
    }

    override fun extractAmount(message: String): BigDecimal? {
        val patterns = listOf(
            Regex("""Auth\s+Pmt\s+[A-Z]{3}\s+([0-9,]*\.\d{2}|\d+)""", RegexOption.IGNORE_CASE),
            Regex("""[A-Z]{3}\s+([0-9,]*\.\d{2}|\d+)\s+(?:credited|debited)""", RegexOption.IGNORE_CASE)
        )
        for (pattern in patterns) {
            pattern.find(message)?.let { match ->
                val raw = match.groupValues[1].replace(",", "")
                val amountStr = if (raw.startsWith(".")) "0$raw" else raw
                return try {
                    BigDecimal(amountStr)
                } catch (e: NumberFormatException) {
                    null
                }
            }
        }
        return super.extractAmount(message)
    }

    override fun extractTransactionType(message: String): TransactionType? {
        val lower = message.lowercase()
        return when {
            lower.contains("credited to") -> TransactionType.INCOME
            lower.contains("debited from") -> TransactionType.EXPENSE
            lower.contains("auth pmt") -> TransactionType.EXPENSE
            else -> super.extractTransactionType(message)
        }
    }

    override fun extractMerchant(message: String, sender: String): String? {
        // Card auth: "at SPICE ASIA - DELIVERY Avl Bal LKR ..."
        val cardPattern = Regex("""\bat\s+(.+?)\s+Avl\s+Bal""", RegexOption.IGNORE_CASE)
        cardPattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            if (merchant.isNotEmpty()) return merchant
        }

        // ATM: "via ATM at Makumbura MMC - CASH SET" / "via ATM at ICBS CASH SET"
        val atmPattern = Regex("""\bvia\s+ATM\s+at\s+(.+?)(?:\s*-?\s*CASH\s+SET|\n|$)""", RegexOption.IGNORE_CASE)
        atmPattern.find(message)?.let { match ->
            val loc = match.groupValues[1].trim()
            if (loc.isNotEmpty()) return "ATM - $loc"
        }

        // POS: "via POS at KFC-KURUNEGALA KU 80401564" / "via POS at PERERA - SONS BAKERS ( MA 51001413"
        val posPattern = Regex("""\bvia\s+POS\s+at\s+(.+?)(?:\n|$)""", RegexOption.IGNORE_CASE)
        posPattern.find(message)?.let { match ->
            var merchant = match.groupValues[1].trim()
            // Strip trailing branch/terminal codes: e.g. "( MA 51001413", "KU 80401564", "76131020"
            merchant = merchant.replace(Regex("""\s*(?:\(\s*)?[A-Z]{2}\s+\d{6,}\s*$"""), "")
            merchant = merchant.replace(Regex("""\s+\d{6,}\s*$"""), "").trim()
            if (merchant.isNotEmpty()) return merchant
        }

        // International remittance: "for Remittance ID : [IR26GBP36623] : REALIZE"
        val remittancePattern = Regex(
            """for\s+Remittance\s+ID\s*:\s*\[[^\]]+\]\s*:\s*([^\n]+)""",
            RegexOption.IGNORE_CASE
        )
        remittancePattern.find(message)?.let { match ->
            val merchant = match.groupValues[1].trim()
            if (merchant.isNotEmpty()) return merchant
        }

        // eCom & Reversals: "for eCom/REV Microsoft*Store MASTTERM", "for eCom PickMe Co 76134439"
        val ecomPattern = Regex("""\bfor\s+eCom(?:/REV)?\s+(.+?)(?:\n|$)""", RegexOption.IGNORE_CASE)
        ecomPattern.find(message)?.let { match ->
            var merchant = match.groupValues[1].trim()
            // Strip trailing gateway/card association tokens: "Co 76134439", "Mo MASTTERM", "Si MASTTERM", "MASTTERM", "Vi NMP20489"
            merchant = merchant.replace(Regex("""\s+(?:Co|Mo|Si|Vi)\b.*$""", RegexOption.IGNORE_CASE), "")
            merchant = merchant.replace(Regex("""\s+MASTTERM.*$""", RegexOption.IGNORE_CASE), "").trim()
            if (merchant.isNotEmpty()) return merchant
        }

        // Outward transfer: "for TFR Vishwa -000086201821" / "for TFR Vishwa cash-000086472616"
        val tfrPattern = Regex("""\bfor\s+TFR\s+(.+?)(?:-\d+|\n|$)""", RegexOption.IGNORE_CASE)
        tfrPattern.find(message)?.let { match ->
            val name = match.groupValues[1].trim()
            if (name.isNotEmpty()) return "TFR $name"
        }

        // TRRR inward transfer: "for TRRR/000000554871/14-10-2026 13:20:43/IF"
        if (message.contains("for TRRR/", ignoreCase = true)) {
            return "Transfer Inward"
        }

        // CEFT transfer: "for CEFTLV-01-22312167-..."
        if (message.contains("for CEFTLV-", ignoreCase = true)) {
            return "CEFT Transfer"
        }

        // Visa recurring payment: "for VSRPMT-000018281730"
        if (message.contains("for VSRPMT-", ignoreCase = true)) {
            return "Visa Recurring Payment"
        }

        // Bank interest: "for Interest: 1xxx9786:Int.Pd:..."
        if (message.contains("for Interest:", ignoreCase = true)) {
            return "Bank Interest"
        }

        // General account note fallback: "for PICKME - 37419306" / "for cash" / "for Vishwa-"
        val forPattern = Regex("""\bfor\s+(.+?)(?:\n|$)""", RegexOption.IGNORE_CASE)
        forPattern.find(message)?.let { match ->
            var merchant = match.groupValues[1].trim()
            // Strip trailing reference appended after " - " only when followed by digits
            merchant = merchant.replace(Regex("""\s+-\s+\d+\S*$"""), "")
            merchant = merchant.removeSuffix("-").trim()
            if (merchant.isNotEmpty()) return merchant
        }

        return super.extractMerchant(message, sender)
    }

    override fun extractAccountLast4(message: String): String? {
        val pattern = Regex("""(?:AC|Crd\s+no\.*)\s*\*+\s*(\d{3,})""", RegexOption.IGNORE_CASE)
        pattern.find(message)?.let { match ->
            return extractLast4Digits(match.groupValues[1])
        }
        return super.extractAccountLast4(message)
    }

    override fun extractReference(message: String): String? {
        // International remittance: "Remittance ID : [IR26GBP36623]"
        val remittancePattern = Regex("""Remittance\s+ID\s*:\s*\[([^\]]+)\]""", RegexOption.IGNORE_CASE)
        remittancePattern.find(message)?.let { return it.groupValues[1].trim() }

        // TFR ref: "for TFR Vishwa -000086201821" / "cash-000086472616"
        val tfrRefPattern = Regex("""\bfor\s+TFR\s+.*?-(\d+)""", RegexOption.IGNORE_CASE)
        tfrRefPattern.find(message)?.let { return it.groupValues[1].trim() }

        // TRRR ref: "for TRRR/000000554871/14-10-2026 13:20:43/IF"
        val trrrPattern = Regex("""\bfor\s+TRRR/([^/]+)/""", RegexOption.IGNORE_CASE)
        trrrPattern.find(message)?.let { return it.groupValues[1].trim() }

        // CEFT ref: "for CEFTLV-01-22312167-..."
        val ceftPattern = Regex("""\bfor\s+CEFTLV-01-([0-9A-Za-z]+)-""", RegexOption.IGNORE_CASE)
        ceftPattern.find(message)?.let { return it.groupValues[1].trim() }

        // VSRPMT ref: "for VSRPMT-000018281730"
        val vsrPattern = Regex("""\bfor\s+VSRPMT-(\d+)""", RegexOption.IGNORE_CASE)
        vsrPattern.find(message)?.let { return it.groupValues[1].trim() }

        // POS terminal ref: "via POS at KFC-KURUNEGALA KU 80401564"
        val posRefPattern = Regex("""\bvia\s+POS\s+at\s+.*?(?:[A-Z]{2}\s+|\(\s*[A-Z]{2}\s+)?(\d{6,})(?:\n|$)""", RegexOption.IGNORE_CASE)
        posRefPattern.find(message)?.let { return it.groupValues[1].trim() }

        // Account: "for PICKME - 37419306" -> trailing numeric reference
        val dashRefPattern = Regex("""\bfor\s+.+?\s+-\s+(\d+)""", RegexOption.IGNORE_CASE)
        dashRefPattern.find(message)?.let { return it.groupValues[1].trim() }

        return super.extractReference(message)
    }

    override fun extractBalance(message: String): BigDecimal? {
        val pattern = Regex("""Avl\s+Bal\s+[A-Z]{3}\s+([0-9,]+\.\d{2})""", RegexOption.IGNORE_CASE)
        pattern.find(message)?.let { match ->
            val balStr = match.groupValues[1].replace(",", "")
            return try {
                BigDecimal(balStr)
            } catch (e: NumberFormatException) {
                null
            }
        }
        return super.extractBalance(message)
    }

    override fun isTransactionMessage(message: String): Boolean {
        val lower = message.lowercase()
        if (lower.contains("credited to") ||
            lower.contains("debited from") ||
            lower.contains("auth pmt")
        ) {
            return true
        }
        return super.isTransactionMessage(message)
    }
}
