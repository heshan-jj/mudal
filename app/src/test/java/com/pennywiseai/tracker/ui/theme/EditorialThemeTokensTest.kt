package com.pennywiseai.tracker.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class EditorialThemeTokensTest {
    @Test
    fun `typography uses a single unified font family across display, title, and body roles`() {
        val typo = getTypography(fontFamily = FontFamily.SansSerif)
        assertEquals(FontFamily.SansSerif, typo.displayLarge.fontFamily)
        assertEquals(FontFamily.SansSerif, typo.displaySmall.fontFamily)
        assertEquals(FontFamily.SansSerif, typo.headlineMedium.fontFamily)
        assertEquals(FontFamily.SansSerif, typo.headlineSmall.fontFamily)
        assertEquals(FontFamily.SansSerif, typo.titleLarge.fontFamily)
        assertEquals(FontFamily.SansSerif, typo.titleMedium.fontFamily)
        assertEquals(FontFamily.SansSerif, typo.titleSmall.fontFamily)
        assertEquals(FontFamily.SansSerif, typo.bodyLarge.fontFamily)
        assertEquals(FontFamily.SansSerif, typo.bodyMedium.fontFamily)
        assertEquals(FontFamily.SansSerif, typo.labelLarge.fontFamily)
    }

    @Test
    fun `editorial shape scale uses tightened radii and zero grouped list gap`() {
        assertEquals(RoundedCornerShape(4.dp), Shapes.extraSmall)
        assertEquals(RoundedCornerShape(6.dp), Shapes.small)
        assertEquals(RoundedCornerShape(10.dp), Shapes.medium)
        assertEquals(RoundedCornerShape(12.dp), Shapes.large)
        assertEquals(RoundedCornerShape(20.dp), Shapes.extraLarge)
        assertEquals(0.dp, Spacing.Layout.groupedListGap)
        assertEquals(1.dp, Dimensions.Component.hairlineBorder)
    }
}
