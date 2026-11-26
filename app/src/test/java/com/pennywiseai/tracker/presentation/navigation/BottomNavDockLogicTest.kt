package com.pennywiseai.tracker.presentation.navigation

import com.pennywiseai.tracker.ui.components.shouldShowDockLabel
import org.junit.Assert.assertEquals
import org.junit.Test

class BottomNavDockLogicTest {
    @Test
    fun `normal font scale shows labels for all tabs`() {
        assertEquals(true, shouldShowDockLabel(visibleTabCount = 4, isSelected = false, fontScale = 1.0f))
        assertEquals(true, shouldShowDockLabel(visibleTabCount = 4, isSelected = true, fontScale = 1.0f))
    }

    @Test
    fun `large font scale hides inactive labels when 3+ tabs visible to prevent clipping`() {
        assertEquals(false, shouldShowDockLabel(visibleTabCount = 4, isSelected = false, fontScale = 2.0f))
        assertEquals(true, shouldShowDockLabel(visibleTabCount = 4, isSelected = true, fontScale = 2.0f))
        assertEquals(true, shouldShowDockLabel(visibleTabCount = 2, isSelected = false, fontScale = 2.0f))
    }

    @Test
    fun `hiding all optional tabs still keeps Home visible`() {
        val allHidden = BottomNavItem.hideable.map { it.route }.toSet()
        assertEquals(listOf(BottomNavItem.Home), BottomNavItem.visible(allHidden))
    }
}
