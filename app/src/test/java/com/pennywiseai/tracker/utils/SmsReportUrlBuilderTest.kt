// Mudal fork: new file in Mudal, a fork of PennyWise AI (AGPL-3.0-only), 2026-10-21 - see NOTICE.
package com.pennywiseai.tracker.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsReportUrlBuilderTest {

    @Test
    fun `maskSms replaces sensitive details`() {
        val raw = "Your account 123456789 was debited by Rs. 2,500.50 for purchase at Keells. UPI: user@okaxis"
        val masked = SmsReportUrlBuilder.maskSms(raw)

        assertFalse(masked.contains("123456789"))
        assertFalse(masked.contains("2,500.50"))
        assertFalse(masked.contains("user@okaxis"))
        assertTrue(masked.contains("Keells"))
        assertTrue(masked.contains("[NUMBER]"))
        assertTrue(masked.contains("[AMOUNT]"))
        assertTrue(masked.contains("[VPA/EMAIL]"))
    }
}
