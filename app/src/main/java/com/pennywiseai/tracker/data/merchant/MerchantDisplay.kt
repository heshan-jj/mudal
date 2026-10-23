// Mudal fork: new file in Mudal, a fork of PennyWise AI (AGPL-3.0-only), 2026-10-21 - see NOTICE.
package com.pennywiseai.tracker.data.merchant

import androidx.compose.runtime.compositionLocalOf

/**
 * How a merchant label is rendered everywhere in the app.
 *
 * The rule is one rule: a **user-defined alias** (#583) wins, otherwise a
 * known shop is shown under its brand name, and everything else keeps the
 * bank's text with the trailing city or terminal code removed.
 *
 * The previous version of this rule also resolved a UPI VPA
 * (`9876543210@paytm`) to a contact name from the address book. That is gone
 * with the rest of the UPI feature: it needed the `READ_CONTACTS`
 * permission, and it was an Indian-UPI-only concern.
 */
fun displayMerchantName(
    merchant: String?,
    aliases: Map<String, String> = emptyMap()
): String? {
    if (merchant.isNullOrBlank()) return merchant
    aliases[merchant]?.let { if (it.isNotBlank()) return it }
    return cleanMerchantName(merchant)
}

/**
 * Provided once at the top of the app so every transaction-rendering
 * composable (lists, cards, detail screens, analytics) applies the same rule
 * without prop-drilling the alias map everywhere.
 */
val LocalMerchantDisplay = compositionLocalOf<(String?) -> String?> { { it } }
