package com.pennywiseai.tracker.presentation.recurring

import com.pennywiseai.tracker.data.database.entity.RecurringFrequency
import com.pennywiseai.tracker.data.database.entity.RecurringTransactionEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionDirection
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionState
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.presentation.home.pickNextExpectedCharge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class RecurringStatusAndNextChargeTest {

    @Test
    fun `computeRecurringTemplateStatus distinguishes Paused, Paid, Overdue, and Scheduled`() {
        val today = LocalDate.of(2026, 10, 20)
        val tpl = RecurringTransactionEntity(
            id = 1L,
            uid = "tpl-1",
            merchantName = "Rent",
            amount = BigDecimal("25000.00"),
            currency = "LKR",
            frequency = RecurringFrequency.MONTHLY,
            nextDueDate = LocalDate.of(2026, 10, 15),
            isActive = true
        )

        assertEquals(
            RecurringCycleState.OVERDUE,
            computeRecurringTemplateStatus(tpl, latestPaidDate = null, today = today).state
        )
        assertEquals(
            RecurringCycleState.PAID,
            computeRecurringTemplateStatus(tpl, latestPaidDate = LocalDate.of(2026, 10, 15), today = today).state
        )
        assertEquals(
            RecurringCycleState.SCHEDULED,
            computeRecurringTemplateStatus(tpl.copy(nextDueDate = LocalDate.of(2026, 10, 25)), latestPaidDate = null, today = today).state
        )
        assertEquals(
            RecurringCycleState.PAUSED,
            computeRecurringTemplateStatus(tpl.copy(isActive = false), latestPaidDate = null, today = today).state
        )
        // Late payment on Oct 25 advanced nextDueDate to Nov 15; on Nov 16 (> nextDueDate)
        // the Nov 15 cycle is overdue even though Oct 25 + 1 month hasn't elapsed yet.
        assertEquals(
            RecurringCycleState.OVERDUE,
            computeRecurringTemplateStatus(
                tpl.copy(nextDueDate = LocalDate.of(2026, 11, 15)),
                latestPaidDate = LocalDate.of(2026, 10, 25),
                today = LocalDate.of(2026, 11, 16)
            ).state
        )
    }

    @Test
    fun `pickNextExpectedCharge returns earliest active outflow across subscriptions and templates`() {
        val sub = SubscriptionEntity(
            id = 1L,
            merchantName = "Netflix",
            amount = BigDecimal("1500.00"),
            nextPaymentDate = LocalDate.of(2026, 11, 21),
            state = SubscriptionState.ACTIVE,
            currency = "LKR",
            direction = SubscriptionDirection.EXPENSE
        )
        val tpl = RecurringTransactionEntity(
            id = 2L,
            uid = "tpl-2",
            merchantName = "CEB",
            amount = BigDecimal("4000.00"),
            nextDueDate = LocalDate.of(2026, 11, 5),
            currency = "LKR",
            isActive = true,
            transactionType = TransactionType.EXPENSE
        )
        val pausedTpl = tpl.copy(id = 3L, isActive = false, nextDueDate = LocalDate.of(2026, 10, 1))

        val next = pickNextExpectedCharge(listOf(sub), listOf(tpl, pausedTpl))
        assertNotNull(next)
        assertEquals("CEB", next!!.merchantName)
        assertTrue(next.isTemplate)

        val onlySubNext = pickNextExpectedCharge(listOf(sub), listOf(pausedTpl))
        assertEquals("Netflix", onlySubNext!!.merchantName)
        assertFalse(onlySubNext.isTemplate)
    }
}
