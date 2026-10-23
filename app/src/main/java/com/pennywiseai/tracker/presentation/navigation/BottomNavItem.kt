// Mudal fork: modified from PennyWise AI (AGPL-3.0-only) on 2026-10-23 - see NOTICE.
package com.pennywiseai.tracker.presentation.navigation

import com.pennywiseai.tracker.R
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Home
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController

sealed class BottomNavItem(
    val route: String,
    @StringRes val titleRes: Int,
    val icon: ImageVector
) {
    data object Home : BottomNavItem(
        route = "home",
        titleRes = R.string.nav_home,
        icon = Icons.Default.Home
    )

    data object Transactions : BottomNavItem(
        route = "transactions",
        titleRes = R.string.nav_transactions,
        icon = Icons.AutoMirrored.Filled.ReceiptLong
    )

    data object Analytics : BottomNavItem(
        route = "analytics",
        titleRes = R.string.nav_analytics,
        icon = Icons.Default.Analytics
    )
    
    data object Chat : BottomNavItem(
        route = "chat",
        titleRes = R.string.nav_chat,
        icon = Icons.Default.AutoAwesome
    )

    companion object {
        // Getters, not vals: a companion val naming the subclasses can be read
        // before they're initialised and come back null.
        /** Every tab, in bar order. */
        val all: List<BottomNavItem> get() = listOf(Home, Transactions, Analytics, Chat)

        /** Tabs the user can hide from the bar (#912). Home always stays. */
        val hideable: List<BottomNavItem> get() = all - Home

        /** The tabs to draw, given the routes the user hid. */
        fun visible(hiddenRoutes: Set<String>): List<BottomNavItem> =
            all.filter { it == Home || it.route !in hiddenRoutes }

        /**
         * Where to go when [route] is on screen right after the user hid tabs:
         * Home if its tab is now hidden, else null (stay put).
         */
        fun fallbackFor(route: String, hiddenRoutes: Set<String>): String? =
            Home.route.takeIf { route != Home.route && route in hiddenRoutes }
    }
}

/**
 * Home is the nav graph start destination. `navigate("home")` with
 * `launchSingleTop` and `restoreState` is a no-op on some taps, so pop back
 * to the start entry instead.
 */
fun NavHostController.goHome() {
    val start = graph.findStartDestination()
    val here = currentDestination?.route?.substringBefore('?')
    val startRoute = start.route?.substringBefore('?')
    if (here != null && here == startRoute) return
    if (!popBackStack(start.id, inclusive = false)) {
        navigate(startRoute ?: BottomNavItem.Home.route) {
            popUpTo(start.id) { inclusive = false }
            launchSingleTop = true
        }
    }
}
