package com.pennywiseai.tracker.ui.components.cards

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class LedgerSurfaceStyleTest {
    @Test
    fun `ledgerCardBorderAlpha distinguishes light and dark modes for AMOLED clarity`() {
        assertEquals(0.65f, ledgerCardBorderAlpha(isDark = false))
        assertEquals(0.35f, ledgerCardBorderAlpha(isDark = true))
    }

    @Test
    fun `ledgerRowShape rounds outer corners only and squares interior edges`() {
        assertEquals(
            RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp, bottomStart = 0.dp, bottomEnd = 0.dp),
            ledgerRowShape(ListItemPosition.Top, 12.dp)
        )
        assertEquals(
            RoundedCornerShape(0.dp),
            ledgerRowShape(ListItemPosition.Middle, 12.dp)
        )
        assertEquals(
            RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp, bottomStart = 12.dp, bottomEnd = 12.dp),
            ledgerRowShape(ListItemPosition.Bottom, 12.dp)
        )
        assertEquals(
            RoundedCornerShape(12.dp),
            ledgerRowShape(ListItemPosition.Single, 12.dp)
        )
    }

    @Test
    fun `shouldShowTopLedgerDivider is true only for Middle and Bottom rows`() {
        assertEquals(false, shouldShowTopLedgerDivider(ListItemPosition.Single))
        assertEquals(false, shouldShowTopLedgerDivider(ListItemPosition.Top))
        assertEquals(true, shouldShowTopLedgerDivider(ListItemPosition.Middle))
        assertEquals(true, shouldShowTopLedgerDivider(ListItemPosition.Bottom))
    }
}
