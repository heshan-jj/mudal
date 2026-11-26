package com.pennywiseai.tracker.ui.components.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.LocalIsDarkTheme
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.Spacing

/**
 * Where a row sits inside a connected group, which decides which of its
 * corners are rounded. See [GroupedList] for the container.
 */
enum class ListItemPosition {
    Top,
    Middle,
    Bottom,
    Single;

    companion object {
        fun from(index: Int, size: Int): ListItemPosition {
            return when {
                size <= 1 -> Single
                index == 0 -> Top
                index == size - 1 -> Bottom
                else -> Middle
            }
        }
    }
}

internal fun ledgerRowShape(position: ListItemPosition, outerCorner: Dp): RoundedCornerShape =
    when (position) {
        ListItemPosition.Top -> RoundedCornerShape(
            topStart = outerCorner,
            topEnd = outerCorner,
            bottomStart = 0.dp,
            bottomEnd = 0.dp
        )
        ListItemPosition.Middle -> RoundedCornerShape(0.dp)
        ListItemPosition.Bottom -> RoundedCornerShape(
            topStart = 0.dp,
            topEnd = 0.dp,
            bottomStart = outerCorner,
            bottomEnd = outerCorner
        )
        ListItemPosition.Single -> RoundedCornerShape(outerCorner)
    }

internal fun shouldShowTopLedgerDivider(position: ListItemPosition): Boolean =
    position == ListItemPosition.Middle || position == ListItemPosition.Bottom

/**
 * The single source of truth for grouped-row corners in a framed ledger sheet:
 * outer edges take `Dimensions.CornerRadius.large` (12dp) and interior edges
 * stay square so rows meet cleanly on a hairline divider.
 */
@Composable
fun ListItemPosition.toShape(): CornerBasedShape =
    ledgerRowShape(this, Dimensions.CornerRadius.large)

/**
 * A transaction-style row: leading avatar, title + one metadata line, trailing
 * amount.
 *
 * The subtitle uses `bodySmall` on purpose — it carries a joined metadata
 * string ("9 Jan · 3:42 PM · Food · Bal ₹1,234"), and a dense single line of
 * facts wants to stay visually subordinate to the merchant name. Rows whose
 * second line is real supporting *prose* should use
 * [RowLabels][com.pennywiseai.tracker.ui.components.cards.RowLabels] instead,
 * which sets it at `bodyMedium`.
 */
@Composable
fun ListItemCardV2(
    title: String,
    subtitle: String,
    amount: String,
    modifier: Modifier = Modifier,
    amountColor: Color = MaterialTheme.colorScheme.onSurface,
    leadingContent: @Composable (() -> Unit)? = null,
    trailingContent: @Composable (() -> Unit)? = null,
    shape: CornerBasedShape = MaterialTheme.shapes.large,
    contentPadding: Dp = Dimensions.Padding.cardCompact,
    /** Overrides the card's container colour when set (e.g. for selected state). */
    containerColor: Color? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    PennyWiseCardV2(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        contentPadding = contentPadding,
        containerColor = containerColor,
        // Use 0.5dp border on standalone list rows so adjacent rows in a 0dp-gap
        // LazyColumn meet cleanly on a 1dp hairline instead of stacking to 2dp.
        border = BorderStroke(
            width = Dimensions.Component.hairline,
            color = MaterialTheme.colorScheme.outlineVariant.copy(
                alpha = ledgerCardBorderAlpha(LocalIsDarkTheme.current)
            )
        ),
        onClick = onClick,
        onLongClick = onLongClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Keeps a row tappable even when its content is a single short
                // line, without padding out rows that are already tall.
                .defaultMinSize(minHeight = Dimensions.Component.minTouchTarget),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (leadingContent != null) {
                leadingContent()
                Spacer(modifier = Modifier.width(Spacing.smd))
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(Spacing.xxs)
            ) {
                Text(
                    text = title,
                    style = PennyWiseText.rowTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = PennyWiseText.metadata,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(Spacing.sm))

            if (trailingContent != null) {
                trailingContent()
            } else {
                Text(
                    text = amount,
                    style = PennyWiseText.amountRow,
                    color = amountColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
