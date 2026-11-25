package com.pennywiseai.tracker.domain.service

import com.pennywiseai.tracker.data.database.entity.RecurringFrequency
import com.pennywiseai.tracker.data.database.entity.RecurringTransactionEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionDirection
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionState
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class RecurringMatcherTest {

    private fun sub(
        id: Long = 1L,
        merchant: String,
        amount: String,
        nextPaymentDate: LocalDate?,
        currency: String = "LKR",
        billingCycle: String = "Monthly",
        direction: SubscriptionDirection = SubscriptionDirection.EXPENSE,
        bankName: String? = "Sampath Bank",
        accountLast4: String? = "7700",
        lastPaidAt: LocalDate? = null
    ) = SubscriptionEntity(
        id = id,
        merchantName = merchant,
        amount = BigDecimal(amount),
        nextPaymentDate = nextPaymentDate,
        state = SubscriptionState.ACTIVE,
        bankName = bankName,
        accountLast4 = accountLast4,
        currency = currency,
        direction = direction,
        billingCycle = billingCycle,
        lastPaidAt = lastPaidAt
    )

    private fun template(
        id: Long = 10L,
        merchant: String,
        amount: String,
        nextDueDate: LocalDate,
        currency: String = "LKR",
        frequency: RecurringFrequency = RecurringFrequency.MONTHLY,
        type: TransactionType = TransactionType.EXPENSE
    ) = RecurringTransactionEntity(
        id = id,
        uid = "tpl-$id",
        merchantName = merchant,
        amount = BigDecimal(amount),
        currency = currency,
        transactionType = type,
        frequency = frequency,
        nextDueDate = nextDueDate
    )

    private fun tx(
        merchant: String,
        amount: String,
        dateTime: LocalDateTime,
        type: TransactionType = TransactionType.EXPENSE,
        currency: String = "LKR",
        bankName: String? = "Sampath Bank",
        accountLast4: String? = "7700",
        smsBody: String? = null
    ) = TransactionEntity(
        amount = BigDecimal(amount),
        merchantName = merchant,
        category = "Bills & Utilities",
        transactionType = type,
        dateTime = dateTime,
        bankName = bankName,
        accountNumber = accountLast4,
        smsBody = smsBody,
        transactionHash = "hash-${merchant}-${dateTime}-${amount}",
        currency = currency
    )

    @Test
    fun `normalizes bank noise in Sampath and CBC merchant names`() {
        assertTrue(RecurringMatcher.merchantsMatch("eCom Google Spotify Music Mo MASTTERM", "Spotify"))
        assertTrue(RecurringMatcher.merchantsMatch("eCom Dialog Axiata PLC Co T7064677", "Dialog"))
        assertTrue(RecurringMatcher.merchantsMatch("via POS at KFC-KURUNEGALA KU 80401564", "KFC"))
        assertTrue(RecurringMatcher.merchantsMatch("VSRPMT-CARGILLS FOOD CITY", "Cargills Food City"))
        assertFalse(RecurringMatcher.merchantsMatch("eCom Google Spotify Music Mo MASTTERM", "Google"))
        assertFalse(RecurringMatcher.merchantsMatch("eCom Google Spotify Music Mo MASTTERM", "Google One"))
        assertFalse(RecurringMatcher.merchantsMatch("eCom PickMe Co 76134439", "Dialog"))
        assertEquals("tiffany co store", RecurringMatcher.normalizeMerchant("Tiffany & Co Store"))
        assertFalse(RecurringMatcher.merchantsMatch("Tiffany & Co Store", "Tiffany & Co Cafe"))
        assertFalse(RecurringMatcher.merchantsMatch("Visa Recurring Payment", "Visa Recurring Payment"))
        assertFalse(RecurringMatcher.isReversalSms("Monthly payment for Rev Fitness"))
        assertTrue(RecurringMatcher.isReversalSms("LKR 346.54 credited to AC **7700 for eCom/REV Microsoft*Store"))
    }

    @Test
    fun `netflix sequential price hikes 1000 to 1500 to 2100 match across months`() {
        val subAt1000 = sub(
            id = 1L,
            merchant = "Netflix",
            amount = "1000.00",
            nextPaymentDate = LocalDate.of(2026, 10, 21),
            direction = SubscriptionDirection.INCOME
        )
        val octSms = tx(
            merchant = "Netflix",
            amount = "1500.00",
            dateTime = LocalDateTime.of(2026, 10, 21, 8, 28, 51),
            type = TransactionType.INCOME,
            smsBody = "LKR 1,500.00 credited to AC **7700 for Netflix"
        )
        val octMatch = RecurringMatcher.findBestSubscriptionMatch(
            tx = octSms,
            activeSubs = listOf(subAt1000),
            resolvedAccountLast4 = "7700",
            hasLinkedTxnInCycle = { _, _ -> false }
        )
        assertNotNull(octMatch)

        val subUpdatedTo1500 = subAt1000.copy(
            amount = BigDecimal("1500.00"),
            nextPaymentDate = LocalDate.of(2026, 11, 21),
            lastPaidAt = LocalDate.of(2026, 10, 21)
        )
        val novSms = tx(
            merchant = "Netflix",
            amount = "2100.00",
            dateTime = LocalDateTime.of(2026, 11, 21, 9, 0, 0),
            type = TransactionType.INCOME
        )
        val novMatch = RecurringMatcher.findBestSubscriptionMatch(
            tx = novSms,
            activeSubs = listOf(subUpdatedTo1500),
            resolvedAccountLast4 = "7700",
            hasLinkedTxnInCycle = { _, _ -> false }
        )
        assertNotNull(novMatch)
    }

    @Test
    fun `card-created schedule still matches after debit card is linked to bank account`() {
        val cardSub = sub(
            id = 8L,
            merchant = "Spotify",
            amount = "375.00",
            nextPaymentDate = LocalDate.of(2026, 10, 14),
            bankName = "Commercial Bank",
            accountLast4 = "4101"
        )
        val cbcCardSms = tx(
            merchant = "Spotify",
            amount = "375.00",
            dateTime = LocalDateTime.of(2026, 10, 14, 12, 0),
            bankName = "Commercial Bank",
            accountLast4 = "4101",
            smsBody = "Purchase authorized on card #4101 for LKR 375.00 at Spotify"
        )
        val match = RecurringMatcher.findBestSubscriptionMatch(
            tx = cbcCardSms,
            activeSubs = listOf(cardSub),
            resolvedAccountLast4 = "3415",
            hasLinkedTxnInCycle = { _, _ -> false }
        )
        assertNotNull(match)
        assertEquals(8L, match!!.subscription.id)
    }

    @Test
    fun `overdue schedule advances forward to match subsequent cycle and DAILY windows do not overlap`() {
        val overdueSub = sub(
            id = 9L,
            merchant = "Netflix",
            amount = "1500.00",
            nextPaymentDate = LocalDate.of(2026, 9, 14)
        )
        val octCharge = tx(
            merchant = "Netflix",
            amount = "1500.00",
            dateTime = LocalDateTime.of(2026, 10, 14, 10, 0)
        )
        val forwardMatch = RecurringMatcher.findBestSubscriptionMatch(
            tx = octCharge,
            activeSubs = listOf(overdueSub),
            resolvedAccountLast4 = "7700",
            hasLinkedTxnInCycle = { _, _ -> false }
        )
        assertNotNull(forwardMatch)
        assertEquals(LocalDate.of(2026, 10, 14), forwardMatch!!.window.cycleDueDate)

        val dailyTpl = template(
            id = 12L,
            merchant = "Coffee",
            amount = "250.00",
            nextDueDate = LocalDate.of(2026, 10, 6),
            frequency = RecurringFrequency.DAILY
        )
        val oct6Sms = tx("Coffee", "250.00", LocalDateTime.of(2026, 10, 6, 8, 30))
        val dailyMatch = RecurringMatcher.findBestTemplateMatch(
            tx = oct6Sms,
            activeTemplates = listOf(dailyTpl),
            resolvedAccountLast4 = "7700",
            // Oct 5 linked SMS must NOT block Oct 6 daily cycle.
            hasLinkedTxnInCycle = { _, window -> window.contains(LocalDate.of(2026, 10, 5)) }
        )
        assertNotNull(dailyMatch)
        assertEquals(LocalDate.of(2026, 10, 6), dailyMatch!!.window.cycleDueDate)
    }

    @Test
    fun `netflix price increase from 1000 to 1500 still matches within cycle window`() {
        val netflixIncomeSub = sub(
            id = 1L,
            merchant = "Netflix",
            amount = "1000.00",
            nextPaymentDate = LocalDate.of(2026, 10, 21),
            direction = SubscriptionDirection.INCOME
        )
        val octSms = tx(
            merchant = "Netflix",
            amount = "1500.00",
            dateTime = LocalDateTime.of(2026, 10, 21, 8, 28, 51),
            type = TransactionType.INCOME,
            smsBody = "LKR 1,500.00 credited to AC **7700 for Netflix"
        )

        val match = RecurringMatcher.findBestSubscriptionMatch(
            tx = octSms,
            activeSubs = listOf(netflixIncomeSub),
            resolvedAccountLast4 = "7700",
            hasLinkedTxnInCycle = { _, _ -> false }
        )

        assertNotNull(match)
        assertEquals(1L, match!!.subscription.id)
        assertEquals(LocalDate.of(2026, 10, 21), match.window.cycleDueDate)
    }

    @Test
    fun `two different merchants with identical amount never cross-match`() {
        val spotify = sub(
            id = 2L,
            merchant = "Spotify",
            amount = "375.00",
            nextPaymentDate = LocalDate.of(2026, 10, 14)
        )
        val pickMeSameAmount = tx(
            merchant = "eCom PickMe Co 76134439",
            amount = "375.00",
            dateTime = LocalDateTime.of(2026, 10, 14, 10, 0)
        )

        val match = RecurringMatcher.findBestSubscriptionMatch(
            tx = pickMeSameAmount,
            activeSubs = listOf(spotify),
            resolvedAccountLast4 = "7700",
            hasLinkedTxnInCycle = { _, _ -> false }
        )

        assertNull(match)
    }

    @Test
    fun `micro charge does not match larger subscription of same brand`() {
        val dialogFiber = sub(
            id = 3L,
            merchant = "Dialog",
            amount = "2500.00",
            nextPaymentDate = LocalDate.of(2026, 10, 15)
        )
        val tinyReload = tx(
            merchant = "eCom Dialog Axiata PLC Co T7064677",
            amount = "50.00",
            dateTime = LocalDateTime.of(2026, 10, 15, 9, 47)
        )

        val match = RecurringMatcher.findBestSubscriptionMatch(
            tx = tinyReload,
            activeSubs = listOf(dialogFiber),
            resolvedAccountLast4 = "7700",
            hasLinkedTxnInCycle = { _, _ -> false }
        )

        assertNull(match)
    }

    @Test
    fun `refund reversal sms never matches expense subscription or template`() {
        val msSub = sub(
            id = 4L,
            merchant = "Microsoft Store",
            amount = "346.54",
            nextPaymentDate = LocalDate.of(2026, 10, 18),
            direction = SubscriptionDirection.INCOME
        )
        val reversalSms = tx(
            merchant = "Microsoft*Store",
            amount = "346.54",
            dateTime = LocalDateTime.of(2026, 10, 18, 15, 54, 30),
            type = TransactionType.INCOME,
            smsBody = "LKR 346.54 credited to AC **7700 for eCom/REV Microsoft*Store MASTTERM"
        )

        val match = RecurringMatcher.findBestSubscriptionMatch(
            tx = reversalSms,
            activeSubs = listOf(msSub),
            resolvedAccountLast4 = "7700",
            hasLinkedTxnInCycle = { _, _ -> false }
        )

        assertNull(match)
    }

    @Test
    fun `matches historical cycle during inbox scan without failing forward schedule window`() {
        val spotify = sub(
            id = 5L,
            merchant = "Spotify",
            amount = "375.00",
            nextPaymentDate = LocalDate.of(2026, 11, 14),
            lastPaidAt = LocalDate.of(2026, 10, 14)
        )
        val septCharge = tx(
            merchant = "eCom Google Spotify Music Mo MASTTERM",
            amount = "375.00",
            dateTime = LocalDateTime.of(2026, 9, 14, 23, 34, 58)
        )

        val match = RecurringMatcher.findBestSubscriptionMatch(
            tx = septCharge,
            activeSubs = listOf(spotify),
            resolvedAccountLast4 = "7700",
            hasLinkedTxnInCycle = { _, _ -> false }
        )

        assertNotNull(match)
        assertEquals(LocalDate.of(2026, 9, 14), match!!.window.cycleDueDate)
    }

    @Test
    fun `matches recurring template within cycle window and rejects outside window`() {
        val cebTemplate = template(
            id = 11L,
            merchant = "CEB Electricity",
            amount = "4000.00",
            nextDueDate = LocalDate.of(2026, 10, 5)
        )
        val inWindow = tx(
            merchant = "CEB",
            amount = "4400.00",
            dateTime = LocalDateTime.of(2026, 10, 6, 11, 0)
        )
        val staleWindow = tx(
            merchant = "CEB",
            amount = "4000.00",
            dateTime = LocalDateTime.of(2026, 10, 20, 11, 0)
        )

        assertNotNull(
            RecurringMatcher.findBestTemplateMatch(
                tx = inWindow,
                activeTemplates = listOf(cebTemplate),
                resolvedAccountLast4 = null,
                hasLinkedTxnInCycle = { _, _ -> false }
            )
        )
        assertNull(
            RecurringMatcher.findBestTemplateMatch(
                tx = staleWindow,
                activeTemplates = listOf(cebTemplate),
                resolvedAccountLast4 = null,
                hasLinkedTxnInCycle = { _, _ -> false }
            )
        )
    }
}
