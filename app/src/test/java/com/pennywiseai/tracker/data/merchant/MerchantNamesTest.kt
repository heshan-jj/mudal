// Mudal fork: new file in Mudal, a fork of PennyWise AI (AGPL-3.0-only), 2026-10-23 - see NOTICE.
package com.pennywiseai.tracker.data.merchant

import com.pennywiseai.parser.core.ParsedTransaction
import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.tracker.data.mapper.toEntity
import org.junit.Test
import java.math.BigDecimal
import kotlin.test.assertEquals

class MerchantNamesTest {

    @Test
    fun `known shops show their brand name`() {
        assertEquals("Dialog", cleanMerchantName("Dialog Axiata PLC"))
        assertEquals("Hutch", cleanMerchantName("HUTCHISON TELECOM LANKA COLOMBO 04"))
        assertEquals("Perera & Sons", cleanMerchantName("PERERA - SONS BAKERS"))
        assertEquals("Perera & Sons", cleanMerchantName("PERERA & SONS BAKERS PVT HOMAGAMA"))
        assertEquals("KFC", cleanMerchantName("KFC-KURUNEGALA"))
        assertEquals("Keells", cleanMerchantName("keels"))
        assertEquals("Spotify", cleanMerchantName("Google Spotify Music"))
        assertEquals("Google One", cleanMerchantName("Google One"))
        assertEquals("Mintpay", cleanMerchantName("MINTPAY"))
        assertEquals("Microsoft", cleanMerchantName("Microsoft*Store"))
        assertEquals("PickMe", cleanMerchantName("PickMe"))
        assertEquals("Finagle", cleanMerchantName("Finagle Minimart _ NSBM CHomagama"))
        assertEquals("Cargills Food City", cleanMerchantName("CARGILLS FOOD CITY"))
        assertEquals("Uber Eats", cleanMerchantName("Uber Eats"))
        assertEquals("Uber", cleanMerchantName("Uber"))
        assertEquals("Laugfs Gas", cleanMerchantName("LAUGFS GAS"))
        assertEquals("Laugfs Super", cleanMerchantName("LAUGFS SUPER"))
        assertEquals("Softlogic Life", cleanMerchantName("Softlogic Life"))
        assertEquals("Softlogic", cleanMerchantName("Softlogic"))
        assertEquals("NSBM", cleanMerchantName("NSBM"))
        assertEquals("NSB", cleanMerchantName("NSB"))
    }

    @Test
    fun `unknown shops lose a trailing city or terminal code`() {
        assertEquals("Serenty Inn", cleanMerchantName("SERENTY INN HOMAGAMA"))
        assertEquals("Ransiri Bake And House", cleanMerchantName("Ransiri Bake And House KU"))
        assertEquals("Terra Lounge Cafe", cleanMerchantName("TERRA LOUNGE CAFE & RESKURUNEGALA KGLK"))
        assertEquals("H P H Dulanjaya", cleanMerchantName("H P H DULANJAYA PITIPANA"))
    }

    @Test
    fun `names that are not a sri lankan pos string stay as they are`() {
        assertEquals("Holiday Inn", cleanMerchantName("Holiday Inn"))
        assertEquals("Hilton Garden Inn", cleanMerchantName("Hilton Garden Inn"))
        assertEquals("7-11", cleanMerchantName("7-11"))
        assertEquals("Transfer: 002 → 001", cleanMerchantName("Transfer: 002 → 001"))
        assertEquals("Ransiri Bake And House", cleanMerchantName("Ransiri Bake And House"))
    }

    @Test
    fun `a user alias wins over the brand name`() {
        val aliases = mapOf("Dialog Axiata PLC" to "Office Dialog")
        assertEquals("Office Dialog", displayMerchantName("Dialog Axiata PLC", aliases))
        assertEquals("Dialog", displayMerchantName("Dialog Axiata PLC", mapOf("Dialog Axiata PLC" to "  ")))
        assertEquals(null, displayMerchantName(null, aliases))
    }

    @Test
    fun `saving a transaction stores the brand name and categorises the raw text`() {
        val saved = parsed("KFC-KURUNEGALA").toEntity()
        assertEquals("KFC", saved.merchantName)
        assertEquals("Food & Dining", saved.category)

        val keells = parsed("keels").toEntity()
        assertEquals("Keells", keells.merchantName)
        assertEquals("Groceries", keells.category)
    }

    private fun parsed(merchant: String) = ParsedTransaction(
        amount = BigDecimal("10.00"),
        type = TransactionType.EXPENSE,
        merchant = merchant,
        reference = null,
        accountLast4 = "7700",
        balance = null,
        smsBody = "sms $merchant",
        sender = "SAMPATH",
        timestamp = 1_700_000_000_000L,
        bankName = "Sampath Bank",
        currency = "LKR"
    )
}
