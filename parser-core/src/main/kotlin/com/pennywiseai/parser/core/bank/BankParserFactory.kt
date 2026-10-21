// Mudal fork: modified from PennyWise AI (AGPL-3.0-only) on 2026-10-21 - see NOTICE.
package com.pennywiseai.parser.core.bank

import com.pennywiseai.parser.core.ParsedTransaction

/**
 * Factory for creating bank-specific parsers based on SMS sender.
 * Exclusively configured for Sri Lankan banks.
 */
object BankParserFactory {

    private val parsers = listOf(
        CommercialBankParser(),       // Commercial Bank of Ceylon (COMBANK)
        SampathBankParser(),          // Sampath Bank (SAMPATHTXN / SAMPCCTXN)
        NationsTrustBankParser(),     // Nations Trust Bank (NationsSMS)
        NationalSavingsBankParser(),  // National Savings Bank / NSB
        NDBBankParser()               // National Development Bank / NDB
    )

    /**
     * Returns the appropriate bank parser for the given sender.
     * Returns null if no specific parser is found.
     */
    fun getParser(sender: String): BankParser? {
        return parsers.firstOrNull { it.canHandle(sender) }
    }

    /**
     * Returns every parser whose canHandle matches the sender.
     * Multiple parsers can share a sender; content-aware dispatch in [parse] picks the right one.
     */
    fun getParsers(sender: String): List<BankParser> = parsers.filter { it.canHandle(sender) }

    /**
     * Content-aware dispatch: tries every canHandle-matching parser and returns the
     * first non-null parse().
     */
    fun parse(smsBody: String, sender: String, timestamp: Long): ParsedTransaction? =
        getParsers(sender).firstNotNullOfOrNull { it.parse(smsBody, sender, timestamp) }

    /**
     * Returns the bank parser for the given bank name.
     * Returns null if no specific parser is found.
     */
    fun getParserByName(bankName: String): BankParser? {
        return parsers.firstOrNull { it.getBankName() == bankName }
    }

    /**
     * Returns all available bank parsers.
     */
    fun getAllParsers(): List<BankParser> = parsers

    /**
     * Checks if the sender belongs to any known bank.
     */
    fun isKnownBankSender(sender: String): Boolean {
        return parsers.any { it.canHandle(sender) }
    }
}
