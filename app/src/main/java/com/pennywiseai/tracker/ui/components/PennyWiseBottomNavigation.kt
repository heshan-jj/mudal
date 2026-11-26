// Mudal fork: modified from PennyWise AI (AGPL-3.0-only) on 2026-10-22 - see NOTICE.
package com.pennywiseai.tracker.ui.components

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavHostController
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.presentation.navigation.BottomNavItem
import com.pennywiseai.tracker.presentation.navigation.goHome
import com.pennywiseai.tracker.ui.components.cards.ledgerCardBorderAlpha
import com.pennywiseai.tracker.ui.effects.BlurredAnimatedVisibility
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.mudal_green
import com.pennywiseai.tracker.ui.theme.mudal_green_strong
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeEffectScope
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect

internal fun shouldShowDockLabel(
    visibleTabCount: Int,
    isSelected: Boolean,
    fontScale: Float,
): Boolean = isSelected || fontScale < 1.5f || visibleTabCount < 3

@Composable
fun PennyWiseBottomNavigation(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    currentDestination: NavDestination?,
    blurEffects: Boolean = true,
    visible: Boolean = true,
    hiddenTabs: Set<String> = emptySet(),
    hazeState: HazeState = remember { HazeState() },
    onAddClick: (() -> Unit)? = null,
) {
    val navigationItems = BottomNavItem.visible(hiddenTabs)
    val isDark = isSystemInDarkTheme()
    val fontScale = LocalDensity.current.fontScale

    val pillColor = if (isDark) MaterialTheme.colorScheme.surfaceContainerHigh else mudal_green_strong
    val pillBorder = if (isDark) {
        BorderStroke(
            width = Dimensions.Component.hairlineBorder,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = ledgerCardBorderAlpha(isDark = true))
        )
    } else {
        null
    }
    val activeIndicatorColor = if (isDark) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.White.copy(alpha = 0.16f)
    }
    val activeContentColor = if (isDark) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        Color.White
    }
    val inactiveContentColor = if (isDark) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        Color.White.copy(alpha = 0.78f)
    }

    Box(modifier = modifier) {
        BlurredAnimatedVisibility(
            visible = visible,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(
                        horizontal = Dimensions.Padding.content,
                        vertical = Dimensions.Component.floatingDockBottomMargin
                    ),
                horizontalArrangement = Arrangement.spacedBy(Spacing.smd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(Dimensions.Component.floatingDockHeight)
                        .clip(CircleShape)
                        .then(
                            if (blurEffects) Modifier.hazeEffect(
                                state = hazeState,
                                block = fun HazeEffectScope.() {
                                    style = HazeDefaults.style(
                                        backgroundColor = Color.Transparent,
                                        tint = HazeDefaults.tint(pillColor.copy(alpha = 0.88f)),
                                        blurRadius = 20.dp,
                                        noiseFactor = -1f,
                                    )
                                    blurredEdgeTreatment = BlurredEdgeTreatment.Rectangle
                                }
                            ) else Modifier
                        ),
                    shape = CircleShape,
                    color = if (blurEffects) pillColor.copy(alpha = 0.88f) else pillColor,
                    border = pillBorder,
                    shadowElevation = Dimensions.Elevation.fab,
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = Spacing.xs, vertical = Spacing.xs),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xxs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        navigationItems.forEach { item ->
                            val isSelected = currentDestination?.hierarchy?.any {
                                it.route?.substringBefore('?') == item.route
                            } == true
                            val labelText = stringResource(item.titleRes)
                            val showLabel = shouldShowDockLabel(
                                visibleTabCount = navigationItems.size,
                                isSelected = isSelected,
                                fontScale = fontScale
                            )
                            val contentColor = if (isSelected) activeContentColor else inactiveContentColor

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .defaultMinSize(
                                        minWidth = Dimensions.Component.minTouchTarget,
                                        minHeight = Dimensions.Component.minTouchTarget
                                    )
                                    .clip(CircleShape)
                                    .background(if (isSelected) activeIndicatorColor else Color.Transparent)
                                    .semantics {
                                        selected = isSelected
                                    }
                                    .clickable(
                                        role = Role.Tab,
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
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center,
                                    modifier = Modifier.padding(horizontal = Spacing.xs)
                                ) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = if (showLabel) null else labelText,
                                        tint = contentColor,
                                        modifier = Modifier.size(Dimensions.Icon.inline)
                                    )
                                    if (showLabel) {
                                        Text(
                                            text = labelText,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                                            color = contentColor,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (onAddClick != null) {
                    val addContainerColor = if (isDark) MaterialTheme.colorScheme.primary else mudal_green
                    val addContentColor = if (isDark) MaterialTheme.colorScheme.onPrimary else Color.White

                    Surface(
                        onClick = onAddClick,
                        modifier = Modifier.size(Dimensions.Component.floatingDockAddSize),
                        shape = CircleShape,
                        color = addContainerColor,
                        contentColor = addContentColor,
                        shadowElevation = Dimensions.Elevation.fab,
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.home_fab_add),
                                modifier = Modifier.size(Dimensions.Icon.medium)
                            )
                        }
                    }
                }
            }
        }
    }
}
