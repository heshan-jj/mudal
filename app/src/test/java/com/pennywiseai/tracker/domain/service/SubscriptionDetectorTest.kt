package com.pennywiseai.tracker.domain.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class SubscriptionDetectorTest {

    private val today = LocalDate.of(2026, 10, 23)

    @Test
    fun `price change a month apart is still a subscription and the latest amount wins`() {
        val found = SubscriptionDetector.detect(
            charges = listOf(
                charge("NETFLIX.COM", "1000.00", "2026-09-19"),
                charge("Netflix", "1500.00", "2026-10-21"),
            ),
            existingKeys = emptySet(),
            dismissedKeys = emptySet(),
            today = today,
        )

        assertEquals(1, found.size)
        assertEquals("Netflix", found[0].merchantName)
        assertEquals(BigDecimal("1500.00"), found[0].amount)
        assertEquals("Monthly", found[0].billingCycle)
        assertEquals(LocalDate.of(2026, 11, 21), found[0].nextPaymentDate)
        assertEquals("LKR", found[0].currency)
        assertEquals(2, found[0].chargeCount)
    }

    @Test
    fun `three monthly utility charges advance the next date past today`() {
        val found = SubscriptionDetector.detect(
            charges = listOf(
                charge("Dialog Axiata", "2490.00", "2026-07-05", category = "Utilities"),
                charge("DIALOG", "2490.00", "2026-08-05", category = "Utilities"),
                charge("Dialog", "2490.00", "2026-09-05", category = "Utilities"),
            ),
            existingKeys = emptySet(),
            dismissedKeys = emptySet(),
            today = today,
        )

        assertEquals(1, found.size)
        assertEquals("Dialog", found[0].merchantName)
        assertEquals("Monthly", found[0].billingCycle)
        assertEquals(LocalDate.of(2026, 11, 5), found[0].nextPaymentDate)
        assertEquals("Utilities", found[0].category)
        assertEquals(3, found[0].chargeCount)
    }

    @Test
    fun `weekly charges are a weekly subscription`() {
        val found = SubscriptionDetector.detect(
            charges = listOf(
                charge("Spotify", "499.00", "2026-09-25"),
                charge("Spotify", "499.00", "2026-10-02"),
                charge("Spotify", "499.00", "2026-10-09"),
                charge("Spotify", "499.00", "2026-10-16"),
            ),
            existingKeys = emptySet(),
            dismissedKeys = emptySet(),
            today = today,
        )

        assertEquals("Weekly", found.single().billingCycle)
        assertEquals(LocalDate.of(2026, 10, 23), found.single().nextPaymentDate)
    }

    @Test
    fun `same merchant in two currencies stays two subscriptions`() {
        val found = SubscriptionDetector.detect(
            charges = listOf(
                charge("Netflix", "1500.00", "2026-09-19", currency = "LKR"),
                charge("Netflix", "1500.00", "2026-10-21", currency = "LKR"),
                charge("Netflix", "15.99", "2026-09-19", currency = "USD"),
                charge("Netflix", "15.99", "2026-10-21", currency = "USD"),
            ),
            existingKeys = emptySet(),
            dismissedKeys = emptySet(),
            today = today,
        )

        assertEquals(setOf("LKR", "USD"), found.map { it.currency }.toSet())
    }

    @Test
    fun `a shop visit and a bill with the same amount do not merge`() {
        val found = SubscriptionDetector.detect(
            charges = listOf(
                charge("CEB", "4000.00", "2026-09-05"),
                charge("CEB", "4000.00", "2026-10-05"),
                charge("City Hardware", "4000.00", "2026-09-05"),
                charge("City Hardware", "4000.00", "2026-10-05"),
            ),
            existingKeys = emptySet(),
            dismissedKeys = emptySet(),
            today = today,
        )

        assertEquals(setOf("CEB", "City Hardware"), found.map { it.merchantName }.toSet())
    }

    @Test
    fun `known shops and a one-off pair are not subscriptions`() {
        val monthlyKfc = SubscriptionDetector.detect(
            charges = listOf(
                charge("KFC-KURUNEGALA", "2450.00", "2026-08-02"),
                charge("KFC", "2500.00", "2026-09-02"),
                charge("KFC", "2480.00", "2026-10-02"),
            ),
            existingKeys = emptySet(),
            dismissedKeys = emptySet(),
            today = today,
        )
        assertTrue(monthlyKfc.isEmpty())

        val twiceInAWeek = SubscriptionDetector.detect(
            charges = listOf(
                charge("City Hardware", "800.00", "2026-10-01"),
                charge("City Hardware", "800.00", "2026-10-04"),
            ),
            existingKeys = emptySet(),
            dismissedKeys = emptySet(),
            today = today,
        )
        assertTrue(twiceInAWeek.isEmpty())
    }

    @Test
    fun `wildly different amounts are not a subscription`() {
        val found = SubscriptionDetector.detect(
            charges = listOf(
                charge("Corner Shop", "500.00", "2026-08-01"),
                charge("Corner Shop", "8000.00", "2026-09-01"),
                charge("Corner Shop", "1200.00", "2026-10-01"),
            ),
            existingKeys = emptySet(),
            dismissedKeys = emptySet(),
            today = today,
        )
        assertTrue(found.isEmpty())
    }

    @Test
    fun `a saved or dismissed merchant is not suggested again`() {
        val charges = listOf(
            charge("Netflix", "1500.00", "2026-09-19"),
            charge("Netflix", "1500.00", "2026-10-21"),
            charge("Spotify", "499.00", "2026-09-01"),
            charge("Spotify", "499.00", "2026-10-01"),
        )
        val netflixKey = SubscriptionDetector.key("Netflix", "LKR")
        val spotifyKey = SubscriptionDetector.key("Spotify", "LKR")

        val found = SubscriptionDetector.detect(
            charges = charges,
            existingKeys = setOf(netflixKey),
            dismissedKeys = setOf(spotifyKey),
            today = today,
        )
        assertTrue(found.isEmpty())
        assertEquals("netflix|LKR", netflixKey)
    }

    @Test
    fun `two texts on the same day count as one charge`() {
        val found = SubscriptionDetector.detect(
            charges = listOf(
                charge("Netflix", "1500.00", "2026-09-19"),
                charge("Netflix", "1500.00", "2026-09-19"),
                charge("Netflix", "1500.00", "2026-10-21"),
            ),
            existingKeys = emptySet(),
            dismissedKeys = emptySet(),
            today = today,
        )
        assertEquals(2, found.single().chargeCount)
    }

    @Test
    fun `a bill that stopped a year ago is not suggested`() {
        val found = SubscriptionDetector.detect(
            charges = listOf(
                charge("Old Gym", "3000.00", "2024-01-05"),
                charge("Old Gym", "3000.00", "2024-02-05"),
                charge("Old Gym", "3000.00", "2024-03-05"),
            ),
            existingKeys = emptySet(),
            dismissedKeys = emptySet(),
            today = today,
        )
        assertNull(found.firstOrNull())
    }

    private fun charge(
        merchant: String,
        amount: String,
        date: String,
        currency: String = "LKR",
        category: String? = "Bills",
    ) = SubscriptionCharge(
        merchantName = merchant,
        amount = BigDecimal(amount),
        currency = currency,
        date = LocalDate.parse(date),
        category = category,
        bankName = "Sampath Bank",
        accountLast4 = "7700",
    )
}
