// Mudal fork: new file in Mudal (Warm Editorial Ledger UI) - see NOTICE.
package com.pennywiseai.tracker.ui.components.cards

import android.content.res.Configuration
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.pennywiseai.tracker.R
import com.pennywiseai.tracker.data.database.entity.AccountBalanceEntity
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.ui.components.AvatarHelper
import com.pennywiseai.tracker.ui.components.ProfileFilterDropdown
import com.pennywiseai.tracker.ui.components.profileFilterIcon
import com.pennywiseai.tracker.ui.components.spotlightTarget
import com.pennywiseai.tracker.ui.theme.Dimensions
import com.pennywiseai.tracker.ui.theme.PennyWiseText
import com.pennywiseai.tracker.ui.theme.PennyWiseTheme
import com.pennywiseai.tracker.ui.theme.Spacing
import com.pennywiseai.tracker.ui.theme.expense_dark
import com.pennywiseai.tracker.ui.theme.income_dark
import com.pennywiseai.tracker.ui.theme.mudal_green_strong
import com.pennywiseai.tracker.ui.theme.mudal_mint
import com.pennywiseai.tracker.ui.theme.mudal_passbook_dark
import com.pennywiseai.tracker.utils.CurrencyFormatter
import com.pennywiseai.tracker.utils.formatBalance
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.Locale

enum class PassbookSecondaryMetric {
    TOTAL_BALANCE,
    NET_CASH_FLOW
}

internal fun resolveSecondaryMetric(hasAccounts: Boolean): PassbookSecondaryMetric =
    if (hasAccounts) PassbookSecondaryMetric.TOTAL_BALANCE else PassbookSecondaryMetric.NET_CASH_FLOW

internal fun formatPassbookAmount(formatted: String, isHidden: Boolean): String =
    if (isHidden) "••••••" else formatted

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PassbookHomeHeader(
    userName: String,
    profileImageUri: String?,
    profileBackgroundColor: Int,
    profiles: List<ProfileEntity>,
    selectedProfileId: Long?,
    onProfileSelected: (Long?) -> Unit,
    onAvatarClick: () -> Unit,
    onMenuClick: () -> Unit,
    cycleEnd: LocalDate?,
    totalBalance: BigDecimal,
    monthlyChange: BigDecimal,
    monthlyChangePercent: Int,
    currency: String,
    currentMonthIncome: BigDecimal,
    currentMonthExpenses: BigDecimal,
    currentMonthLent: BigDecimal,
    currentMonthTotal: BigDecimal,
    spendingHistory: List<BigDecimal>,
    lastMonthSpendingHistory: List<BigDecimal>,
    availableCurrencies: List<String>,
    isUnifiedMode: Boolean,
    isApproximate: Boolean,
    isBalanceHidden: Boolean,
    onToggleBalanceVisibility: () -> Unit,
    onCurrencyClick: () -> Unit,
    onShowBreakdown: () -> Unit,
    accountBalances: List<AccountBalanceEntity>,
    creditCards: List<AccountBalanceEntity>,
    onAccountClick: (String, String) -> Unit,
    isScanning: Boolean,
    scanRotation: Float,
    showNewUserSyncHint: Boolean,
    onScanSms: () -> Unit,
    onLongPressScanSms: () -> Unit,
    onScanButtonPositioned: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val view = LocalView.current
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    var showProfileFilterMenu by remember { mutableStateOf(false) }

    val chevronRotation by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        animationSpec = tween(Dimensions.Animation.medium),
        label = "passbookChevron"
    )

    val headerContainerColor = if (isDark) mudal_passbook_dark else mudal_green_strong
    val onHeaderPrimary = Color.White
    val onHeaderMuted = Color.White.copy(alpha = 0.78f)
    val onHeaderHairline = Color.White.copy(alpha = 0.16f)
    val chipFill = Color.White.copy(alpha = 0.12f)

    val today = LocalDate.now()
    val (daysLeft, hour) = remember(today, cycleEnd) {
        val lastDay = cycleEnd ?: today.withDayOfMonth(today.lengthOfMonth())
        ChronoUnit.DAYS.between(today, lastDay).toInt() to LocalTime.now().hour
    }
    val monthName = today.month.getDisplayName(java.time.format.TextStyle.FULL, Locale.getDefault())
    val greetingSubtitle = when {
        daysLeft == 0 -> stringResource(R.string.greeting_last_day_of_month, monthName)
        daysLeft in 1..7 -> pluralStringResource(R.plurals.greeting_days_left_in_month, daysLeft, daysLeft, monthName)
        else -> stringResource(
            when (hour) {
                in 5..11 -> R.string.greeting_good_morning
                in 12..16 -> R.string.greeting_good_afternoon
                in 17..21 -> R.string.greeting_good_evening
                else -> R.string.greeting_good_night
            }
        )
    }

    val isMoreSpending = monthlyChange >= BigDecimal.ZERO
    val absPercent = kotlin.math.abs(monthlyChangePercent)
    val changeText = if (isMoreSpending) {
        stringResource(R.string.balance_card_change_more, absPercent)
    } else {
        stringResource(R.string.balance_card_change_less, absPercent)
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(
            bottomStart = Dimensions.CornerRadius.passbookBottom,
            bottomEnd = Dimensions.CornerRadius.passbookBottom
        ),
        color = headerContainerColor,
        border = if (isDark) {
            BorderStroke(
                width = Dimensions.Component.hairlineBorder,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = ledgerCardBorderAlpha(isDark = true))
            )
        } else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = Dimensions.Padding.content, vertical = Spacing.md)
                .animateContentSize(animationSpec = tween(Dimensions.Animation.medium))
        ) {
            // ── Top Utility Row ───────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.medium)
                        .clickable(onClick = onAvatarClick)
                        .padding(vertical = Spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.smd)
                ) {
                    PassbookAvatar(
                        userName = userName,
                        profileImageUri = profileImageUri,
                        profileBackgroundColor = profileBackgroundColor
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = greetingSubtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = onHeaderMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = userName.ifBlank { "Mudal" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = onHeaderPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Box {
                        IconButton(
                            onClick = { showProfileFilterMenu = true },
                            modifier = Modifier.defaultMinSize(
                                minWidth = Dimensions.Component.minTouchTarget,
                                minHeight = Dimensions.Component.minTouchTarget
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(Dimensions.Component.iconButton)
                                    .clip(CircleShape)
                                    .background(chipFill),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = profileFilterIcon(profiles, selectedProfileId),
                                    contentDescription = stringResource(R.string.home_profile_filter),
                                    tint = onHeaderPrimary,
                                    modifier = Modifier.size(Dimensions.Icon.inline)
                                )
                            }
                        }
                        ProfileFilterDropdown(
                            expanded = showProfileFilterMenu,
                            profiles = profiles,
                            selectedProfileId = selectedProfileId,
                            onProfileSelected = onProfileSelected,
                            onDismiss = { showProfileFilterMenu = false }
                        )
                    }

                    IconButton(
                        onClick = onMenuClick,
                        modifier = Modifier.defaultMinSize(
                            minWidth = Dimensions.Component.minTouchTarget,
                            minHeight = Dimensions.Component.minTouchTarget
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .size(Dimensions.Component.iconButton)
                                .clip(CircleShape)
                                .background(chipFill),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreHoriz,
                                contentDescription = stringResource(R.string.home_more_options),
                                tint = onHeaderPrimary,
                                modifier = Modifier.size(Dimensions.Icon.medium)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.md))

            // ── Hero Spending Block (tap to expand breakdown) ──────────────
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .clickable {
                        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        isExpanded = !isExpanded
                    }
                    .padding(vertical = Spacing.xs)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        Text(
                            text = stringResource(R.string.balance_card_this_month),
                            style = MaterialTheme.typography.labelLarge,
                            color = onHeaderMuted
                        )
                        if (availableCurrencies.size > 1 || isUnifiedMode) {
                            Surface(
                                onClick = onCurrencyClick,
                                shape = CircleShape,
                                color = chipFill
                            ) {
                                Text(
                                    text = if (isUnifiedMode) "$currency ≈" else currency,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = mudal_mint,
                                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xxs)
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                onToggleBalanceVisibility()
                            },
                            modifier = Modifier.defaultMinSize(
                                minWidth = Dimensions.Component.minTouchTarget,
                                minHeight = Dimensions.Component.minTouchTarget
                            )
                        ) {
                            Icon(
                                imageVector = if (isBalanceHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null,
                                tint = onHeaderMuted,
                                modifier = Modifier.size(Dimensions.Icon.inline)
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = null,
                            tint = onHeaderMuted,
                            modifier = Modifier
                                .size(Dimensions.Icon.inline)
                                .rotate(chevronRotation)
                        )
                    }
                }

                val heroFormatted = formatPassbookAmount(
                    formatted = CurrencyFormatter.formatCurrency(currentMonthExpenses, currency),
                    isHidden = isBalanceHidden
                )
                Text(
                    text = heroFormatted,
                    style = PennyWiseText.heroAmount,
                    color = onHeaderPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(Spacing.xs))

                Surface(
                    shape = CircleShape,
                    color = chipFill
                ) {
                    Text(
                        text = changeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isMoreSpending) Color(0xFFFFD6CE) else mudal_mint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs)
                    )
                }

                // ── Expanded Breakdown ─────────────────────────────────────
                if (isExpanded) {
                    Spacer(modifier = Modifier.height(Spacing.md))

                    if (spendingHistory.size >= 2) {
                        BalanceSparkline(
                            data = spendingHistory,
                            lineColor = mudal_mint,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(64.dp),
                            currency = currency,
                            isBalanceHidden = isBalanceHidden,
                            comparisonData = lastMonthSpendingHistory.takeIf { it.size >= 2 },
                            comparisonLineColor = Color.White.copy(alpha = 0.35f)
                        )
                        Spacer(modifier = Modifier.height(Spacing.sm))
                    }

                    HorizontalDivider(color = onHeaderHairline)
                    Spacer(modifier = Modifier.height(Spacing.sm))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
                        verticalArrangement = Arrangement.spacedBy(Spacing.sm)
                    ) {
                        PassbookExpandedStat(
                            label = "Income",
                            value = formatPassbookAmount(CurrencyFormatter.formatCurrency(currentMonthIncome, currency), isBalanceHidden),
                            accentColor = income_dark
                        )
                        PassbookExpandedStat(
                            label = "Expenses",
                            value = formatPassbookAmount(CurrencyFormatter.formatCurrency(currentMonthExpenses, currency), isBalanceHidden),
                            accentColor = expense_dark
                        )
                        if (currentMonthLent > BigDecimal.ZERO) {
                            PassbookExpandedStat(
                                label = "Lent",
                                value = formatPassbookAmount(CurrencyFormatter.formatCurrency(currentMonthLent, currency), isBalanceHidden),
                                accentColor = Color(0xFFFFB68E)
                            )
                        }
                        PassbookExpandedStat(
                            label = "Saved",
                            value = formatPassbookAmount(CurrencyFormatter.formatCurrency(currentMonthTotal, currency), isBalanceHidden),
                            accentColor = mudal_mint
                        )
                    }

                    if (accountBalances.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(Spacing.sm))
                        HorizontalDivider(color = onHeaderHairline)
                        Spacer(modifier = Modifier.height(Spacing.sm))

                        Text(
                            text = stringResource(R.string.balance_card_accounts),
                            style = MaterialTheme.typography.labelMedium,
                            color = onHeaderMuted
                        )
                        Spacer(modifier = Modifier.height(Spacing.xs))

                        accountBalances.forEach { account ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onAccountClick(account.bankName, account.accountLast4) }
                                    .padding(vertical = Spacing.xs),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (account.accountLast4 == AccountBalanceEntity.WALLET_ACCOUNT_MARKER) {
                                        account.bankName
                                    } else {
                                        "${account.bankName} •• ${account.accountLast4}"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = onHeaderPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(Spacing.sm))
                                Text(
                                    text = formatPassbookAmount(account.formatBalance(), isBalanceHidden),
                                    style = PennyWiseText.amountRow,
                                    color = onHeaderPrimary
                                )
                            }
                        }

                        if (creditCards.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            Text(
                                text = stringResource(R.string.balance_card_credit_cards),
                                style = MaterialTheme.typography.labelMedium,
                                color = onHeaderMuted
                            )
                            creditCards.forEach { card ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onAccountClick(card.bankName, card.accountLast4) }
                                        .padding(vertical = Spacing.xs),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${card.bankName} •• ${card.accountLast4}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = onHeaderPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(Spacing.sm))
                                    Text(
                                        text = formatPassbookAmount(
                                            CurrencyFormatter.formatCurrency(card.balance, card.currency),
                                            isBalanceHidden
                                        ),
                                        style = PennyWiseText.amountRow,
                                        color = onHeaderPrimary
                                    )
                                }
                            }
                        }

                        if (isApproximate) {
                            Spacer(modifier = Modifier.height(Spacing.xs))
                            Text(
                                text = stringResource(R.string.balance_card_approximate_note),
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFFFD6CE)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(Spacing.sm))
                    Surface(
                        onClick = onShowBreakdown,
                        shape = CircleShape,
                        color = chipFill
                    ) {
                        Text(
                            text = stringResource(R.string.balance_card_total),
                            style = MaterialTheme.typography.labelMedium,
                            color = mudal_mint,
                            modifier = Modifier.padding(horizontal = Spacing.smd, vertical = Spacing.xs)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.smd))
            HorizontalDivider(color = onHeaderHairline)
            Spacer(modifier = Modifier.height(Spacing.smd))

            // ── Summary & Inline Scan SMS Strip ───────────────────────────
            val secondaryMetric = resolveSecondaryMetric(hasAccounts = accountBalances.isNotEmpty())
            val secondaryLabel = when (secondaryMetric) {
                PassbookSecondaryMetric.TOTAL_BALANCE -> stringResource(R.string.balance_card_total)
                PassbookSecondaryMetric.NET_CASH_FLOW -> "Net"
            }
            val secondaryRaw = when (secondaryMetric) {
                PassbookSecondaryMetric.TOTAL_BALANCE ->
                    "${CurrencyFormatter.formatCurrency(totalBalance, currency)}${if (isApproximate) "*" else ""}"
                PassbookSecondaryMetric.NET_CASH_FLOW ->
                    CurrencyFormatter.formatCurrency(currentMonthTotal, currency)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.smd),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Income",
                        style = MaterialTheme.typography.labelSmall,
                        color = onHeaderMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatPassbookAmount(
                            CurrencyFormatter.formatCurrency(currentMonthIncome, currency),
                            isBalanceHidden
                        ),
                        style = PennyWiseText.amountRow,
                        color = mudal_mint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Box(
                    modifier = Modifier
                        .width(Dimensions.Component.hairlineBorder)
                        .height(28.dp)
                        .background(onHeaderHairline)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = secondaryLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = onHeaderMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatPassbookAmount(secondaryRaw, isBalanceHidden),
                        style = PennyWiseText.amountRow,
                        color = onHeaderPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    modifier = Modifier
                        .spotlightTarget(onScanButtonPositioned)
                        .defaultMinSize(minHeight = Dimensions.Component.minTouchTarget)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onTap = { onScanSms() },
                                onLongPress = {
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    onLongPressScanSms()
                                }
                            )
                        },
                    shape = CircleShape,
                    color = mudal_mint,
                    contentColor = mudal_green_strong
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = Spacing.smd, vertical = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Sync,
                            contentDescription = stringResource(R.string.home_fab_sync),
                            modifier = Modifier
                                .size(Dimensions.Icon.inline)
                                .then(if (isScanning) Modifier.rotate(scanRotation) else Modifier)
                        )
                        Text(
                            text = stringResource(R.string.home_fab_sync),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                    }
                }
            }

            if (showNewUserSyncHint) {
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(
                    text = stringResource(R.string.home_fab_sync_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = onHeaderMuted,
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
private fun PassbookAvatar(
    userName: String,
    profileImageUri: String?,
    profileBackgroundColor: Int,
) {
    val avatarBg = if (profileBackgroundColor != 0) Color(profileBackgroundColor) else mudal_mint
    Box(
        modifier = Modifier
            .size(Dimensions.Icon.avatar)
            .clip(CircleShape)
            .background(avatarBg),
        contentAlignment = Alignment.Center
    ) {
        val avatarResId = profileImageUri?.let { AvatarHelper.resolveAvatarDrawable(it) }
        if (avatarResId != null) {
            Image(
                painter = painterResource(id = avatarResId),
                contentDescription = userName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else if (profileImageUri != null) {
            AsyncImage(
                model = profileImageUri,
                contentDescription = userName,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            val initials = remember(userName) {
                val parts = userName.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
                when {
                    parts.size >= 2 -> "${parts.first().first()}${parts.last().first()}".uppercase()
                    parts.size == 1 -> parts.first().take(2).uppercase()
                    else -> "M"
                }
            }
            Text(
                text = initials,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = mudal_green_strong
            )
        }
    }
}

@Composable
private fun PassbookExpandedStat(
    label: String,
    value: String,
    accentColor: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Box(
                modifier = Modifier
                    .size(Spacing.sm)
                    .clip(CircleShape)
                    .background(accentColor)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.78f)
            )
        }
        Text(
            text = value,
            style = PennyWiseText.amountRow,
            color = Color.White
        )
    }
}

@Composable
fun PassbookHomeHeaderSkeleton(modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    val headerColor = if (isDark) mudal_passbook_dark else mudal_green_strong
    val shimmerFill = Color.White.copy(alpha = 0.14f)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(
            bottomStart = Dimensions.CornerRadius.passbookBottom,
            bottomEnd = Dimensions.CornerRadius.passbookBottom
        ),
        color = headerColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = Dimensions.Padding.content, vertical = Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.smd)
            ) {
                Box(
                    modifier = Modifier
                        .size(Dimensions.Icon.avatar)
                        .clip(CircleShape)
                        .background(shimmerFill)
                )
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Box(
                        modifier = Modifier
                            .width(96.dp)
                            .height(12.dp)
                            .clip(CircleShape)
                            .background(shimmerFill)
                    )
                    Box(
                        modifier = Modifier
                            .width(136.dp)
                            .height(16.dp)
                            .clip(CircleShape)
                            .background(shimmerFill)
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Box(
                    modifier = Modifier
                        .width(110.dp)
                        .height(14.dp)
                        .clip(CircleShape)
                        .background(shimmerFill)
                )
                Box(
                    modifier = Modifier
                        .width(210.dp)
                        .height(36.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(shimmerFill)
                )
            }

            HorizontalDivider(color = Color.White.copy(alpha = 0.16f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(96.dp)
                        .height(32.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(shimmerFill)
                )
                Box(
                    modifier = Modifier
                        .width(96.dp)
                        .height(32.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(shimmerFill)
                )
                Box(
                    modifier = Modifier
                        .width(104.dp)
                        .height(40.dp)
                        .clip(CircleShape)
                        .background(shimmerFill)
                )
            }
        }
    }
}

@Preview(name = "Passbook Header Light")
@Preview(name = "Passbook Header Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PassbookHomeHeaderPreview() {
    PennyWiseTheme {
        PassbookHomeHeader(
            userName = "Heshan",
            profileImageUri = null,
            profileBackgroundColor = 0,
            profiles = emptyList(),
            selectedProfileId = null,
            onProfileSelected = {},
            onAvatarClick = {},
            onMenuClick = {},
            cycleEnd = null,
            totalBalance = BigDecimal("185420.50"),
            monthlyChange = BigDecimal("-4200"),
            monthlyChangePercent = -12,
            currency = "LKR",
            currentMonthIncome = BigDecimal("240000"),
            currentMonthExpenses = BigDecimal("64280"),
            currentMonthLent = BigDecimal.ZERO,
            currentMonthTotal = BigDecimal("175720"),
            spendingHistory = emptyList(),
            lastMonthSpendingHistory = emptyList(),
            availableCurrencies = listOf("LKR", "USD"),
            isUnifiedMode = false,
            isApproximate = false,
            isBalanceHidden = false,
            onToggleBalanceVisibility = {},
            onCurrencyClick = {},
            onShowBreakdown = {},
            accountBalances = emptyList(),
            creditCards = emptyList(),
            onAccountClick = { _, _ -> },
            isScanning = false,
            scanRotation = 0f,
            showNewUserSyncHint = false,
            onScanSms = {},
            onLongPressScanSms = {},
            onScanButtonPositioned = {}
        )
    }
}
