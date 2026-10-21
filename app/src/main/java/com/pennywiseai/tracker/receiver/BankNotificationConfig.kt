// Mudal fork: modified from PennyWise AI (AGPL-3.0-only) on 2026-10-21 - see NOTICE.
package com.pennywiseai.tracker.receiver

import android.app.Notification
import com.pennywiseai.parser.core.bank.BankParserFactory

/**
 * Configuration and helpers for ingesting bank notifications.
 *
 * Only notifications from the allowed package list are processed for
 * transaction parsing to preserve user privacy.
 */
object BankNotificationConfig {

    internal val allowedPackages: Map<String, String> = mapOf(
        // Commercial Bank of Ceylon
        "lk.combank.flash" to "COMBANK",
        "com.combank.qplus" to "COMBANK",
        // Sampath Bank
        "com.sampath.vishwa" to "SAMPATHTXN",
        // Nations Trust Bank
        "com.fri_mi" to "NationsSMS",
        // NDB Bank
        "lk.ndb.neos" to "NDBALERT",
        // National Savings Bank
        "lk.nsb.pay" to "NSB"
    )

    /** Sender aliases notification-booked transactions are saved under. */
    val notificationAliases: Set<String> by lazy { allowedPackages.values.toSet() }

    /**
     * Banks that can arrive by notification. Only these can have a cross-channel
     * duplicate, so the (unindexed) nearby lookup is skipped for every other bank.
     */
    val notificationBankNames: Set<String> by lazy {
        notificationAliases.mapNotNull { BankParserFactory.getParser(it)?.getBankName() }.toSet()
    }

    fun isAllowed(packageName: String): Boolean =
        allowedPackages.containsKey(packageName.lowercase())

    fun senderAlias(packageName: String): String =
        allowedPackages[packageName.lowercase()] ?: packageName

    fun extractMessage(notification: Notification): String {
        val extras = notification.extras ?: return ""

        val textParts = buildList {
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.let { add(it) }
            extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { add(it) }
            extras.getCharSequence(Notification.EXTRA_TEXT)?.let { add(it) }
            extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.let { add(it) }
        }

        if (textParts.isNotEmpty()) {
            val merged = textParts.joinToString("\n") { it.toString() }.trim()
            if (merged.isNotBlank()) return merged
        }

        return extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim().orEmpty()
    }
}
