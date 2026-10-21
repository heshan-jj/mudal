// Mudal fork: modified from PennyWise AI (AGPL-3.0-only) on 2026-10-21 - see NOTICE.
package com.pennywiseai.tracker.receiver

import com.pennywiseai.parser.core.bank.BankParserFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The allowlist maps an app's package to a sender alias, and that alias is what
 * the notification listener hands to BankParserFactory. Nothing else ties the two
 * together, so a renamed parser or a typo'd alias would silently drop every
 * notification for that bank.
 */
class BankNotificationConfigTest {

    private val expectedBankByPackage = mapOf(
        "lk.combank.flash" to "Commercial Bank",
        "com.sampath.vishwa" to "Sampath Bank",
        "com.fri_mi" to "Nations Trust Bank",
        "lk.ndb.neos" to "National Development Bank",
        "lk.nsb.pay" to "National Savings Bank"
    )

    @Test
    fun `every allowlisted package reaches a parser`() {
        // Iterates the production map, so a package added there without a
        // matching parser alias fails here instead of silently dropping notifications.
        BankNotificationConfig.allowedPackages.forEach { (pkg, alias) ->
            assertTrue("$pkg should be allowed", BankNotificationConfig.isAllowed(pkg))
            assertNotNull("$pkg alias '$alias' reaches no parser", BankParserFactory.getParser(alias))
        }
    }

    @Test
    fun `known packages route to the right bank`() {
        expectedBankByPackage.forEach { (pkg, bank) ->
            val parser = BankParserFactory.getParser(BankNotificationConfig.senderAlias(pkg))
            assertEquals("$pkg should route to $bank", bank, parser?.getBankName())
        }
    }

    @Test
    fun `commercial bank notification parses like its text`() {
        val alias = BankNotificationConfig.senderAlias("lk.combank.flash")
        val body = "Purchase at ARPITAN for LKR 2,500.00 with Card #4101 has been authorised."
        val parsed = BankParserFactory.getParser(alias)?.parse(body, alias, 0L)
        assertEquals("2500.00", parsed?.amount?.toPlainString())
        assertEquals("LKR", parsed?.currency)
    }

    @Test
    fun `unlisted packages are not read`() {
        assertFalse(BankNotificationConfig.isAllowed("com.whatsapp"))
    }
}
