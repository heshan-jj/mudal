package com.pennywiseai.tracker.ui.components.cards

import org.junit.Assert.assertEquals
import org.junit.Test

class PassbookHeaderLogicTest {
    @Test
    fun `uses TOTAL_BALANCE when accounts exist and NET_CASH_FLOW for cash-only users`() {
        assertEquals(PassbookSecondaryMetric.TOTAL_BALANCE, resolveSecondaryMetric(hasAccounts = true))
        assertEquals(PassbookSecondaryMetric.NET_CASH_FLOW, resolveSecondaryMetric(hasAccounts = false))
    }

    @Test
    fun `formatPassbookAmount masks value when balance is hidden`() {
        assertEquals("••••••", formatPassbookAmount(formatted = "LKR 1,245,890.00", isHidden = true))
        assertEquals("LKR 1,245,890.00", formatPassbookAmount(formatted = "LKR 1,245,890.00", isHidden = false))
    }
}
