// Mudal fork: modified from PennyWise AI (AGPL-3.0-only) on 2026-10-22 - see NOTICE.
package com.pennywiseai.tracker.ui.components

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import com.pennywiseai.tracker.presentation.navigation.BottomNavItem
import com.pennywiseai.tracker.presentation.navigation.goHome
import com.pennywiseai.tracker.ui.effects.BlurredAnimatedVisibility
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

@Composable
fun PennyWiseBottomNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    currentDestination: NavDestination?,
    blurEffects: Boolean = true,
    visible: Boolean = true,
    hiddenTabs: Set<String> = emptySet(),
    hazeState: HazeState = remember { HazeState() },
) {
    val navigationItems = BottomNavItem.visible(hiddenTabs)
    val containerColor = NavigationBarDefaults.containerColor
    val blurTint = MaterialTheme.colorScheme.surface

    Box(modifier = modifier) {
        BlurredAnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
        ) {
            NavigationBar(
                containerColor = if (blurEffects) containerColor.copy(alpha = 0.5f) else containerColor,
                modifier = if (blurEffects) Modifier.hazeEffect(
                    state = hazeState,
                    block = fun HazeEffectScope.() {
                        style = HazeDefaults.style(
                            backgroundColor = Color.Transparent,
                            tint = HazeDefaults.tint(blurTint),
                            blurRadius = 20.dp,
                            noiseFactor = -1f,
                        )
                        blurredEdgeTreatment = BlurredEdgeTreatment.Unbounded
                    }
                ) else Modifier
            ) {
                navigationItems.forEach { item ->
                    val selected = currentDestination?.hierarchy?.any {
                        // Match on the route base so query-arg routes (e.g.
                        // "transactions?type=…") still light up their tab.
                        it.route?.substringBefore('?') == item.route
                    } == true
                    NavigationBarItem(
                        selected = selected,
                    onClick = {
                        if (item == BottomNavItem.Home) {
                            navController.goHome()
                        } else {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.startDestinationId) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                        icon = { Icon(imageVector = item.icon, contentDescription = null) },
                        label = { Text(stringResource(item.titleRes)) }
                    )
                }
            }
        }
    }
}
