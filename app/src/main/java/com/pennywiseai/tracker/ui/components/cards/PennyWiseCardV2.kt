package com.pennywiseai.tracker.ui.components.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.LocalIsDarkTheme

internal fun ledgerCardBorderAlpha(isDark: Boolean): Float =
    if (isDark) 0.35f else 0.65f

/**
 * The app's standard card container — Warm Editorial Ledger style.
 *
 * Crisp paper surface (`surface` in light mode, `surfaceContainerLow` in
 * dark/AMOLED) with a 1dp hairline border (`outlineVariant`), tightened
 * `shapes.large` (12dp) corners, and 0dp elevation.
 *
 * Pass [contentPadding] rather than padding the content yourself, so the
 * ripple on a clickable card covers the whole surface.
 */
@Composable
fun PennyWiseCardV2(
    modifier: Modifier = Modifier,
    shape: CornerBasedShape = MaterialTheme.shapes.large,
    /**
     * Default-only convenience for callers that just want to swap the
     * container colour (e.g. a selected-state tint) without constructing a
     * full [CardColors]. Wired into the default value of [colors]; if a
     * caller passes [colors] explicitly, that wins and this value is unused.
     */
    containerColor: androidx.compose.ui.graphics.Color? = null,
    colors: CardColors = CardDefaults.cardColors(
        containerColor = containerColor ?: if (LocalIsDarkTheme.current) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            MaterialTheme.colorScheme.surface
        }
    ),
    elevation: CardElevation = CardDefaults.cardElevation(
        defaultElevation = Dimensions.Elevation.card
    ),
    border: BorderStroke? = null,
    onClick: (() -> Unit)? = null,
    /**
     * Optional long-press handler. Routes the card through
     * [Modifier.combinedClickable] so tap and long-press resolve on the same
     * gesture surface — important when the card lives inside a scrolling
     * container or another drag-aware parent (e.g. SwipeToDismissBox) that
     * would otherwise race with a child pointerInput.
     *
     * **Requires [onClick]** to also be non-null. A long-press-only card
     * would still announce as a button to accessibility but no-op on tap;
     * we fail fast rather than ship that affordance.
     */
    onLongClick: (() -> Unit)? = null,
    contentPadding: Dp = Dimensions.Padding.card,
    content: @Composable ColumnScope.() -> Unit
) {
    val effectiveBorder = border ?: BorderStroke(
        width = Dimensions.Component.hairlineBorder,
        color = MaterialTheme.colorScheme.outlineVariant.copy(
            alpha = ledgerCardBorderAlpha(LocalIsDarkTheme.current)
        )
    )

    when {
        onLongClick != null -> {
            // Combined click + long-click. Material's Card composable doesn't
            // accept onLongClick directly, so we wrap a non-clickable Card with
            // combinedClickable on the outer modifier. Require onClick here so
            // the card never advertises a button affordance whose tap is a
            // no-op (would mislead screen readers and touch users).
            val tap = requireNotNull(onClick) {
                "PennyWiseCardV2: onLongClick requires onClick to also be non-null."
            }
            Card(
                modifier = modifier.combinedClickable(
                    onClick = tap,
                    onLongClick = onLongClick
                ),
                colors = colors,
                shape = shape,
                elevation = elevation,
                border = effectiveBorder
            ) {
                Column(modifier = Modifier.padding(contentPadding)) { content() }
            }
        }
        onClick != null -> {
            Card(
                modifier = modifier,
                onClick = onClick,
                colors = colors,
                shape = shape,
                elevation = elevation,
                border = effectiveBorder
            ) {
                Column(modifier = Modifier.padding(contentPadding)) { content() }
            }
        }
        else -> {
            Card(
                modifier = modifier,
                colors = colors,
                shape = shape,
                elevation = elevation,
                border = effectiveBorder
            ) {
                Column(modifier = Modifier.padding(contentPadding)) { content() }
            }
        }
    }
}
