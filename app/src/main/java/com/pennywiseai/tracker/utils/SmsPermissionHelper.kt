// Mudal fork: new file in Mudal, a fork of PennyWise AI (AGPL-3.0-only), 2026-10-21 - see NOTICE.
package com.pennywiseai.tracker.utils

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Why the SMS permission can be refused, and what the user can do about it.
 *
 * On Android 14+ (API 34) reading SMS is a *restricted permission*. An app
 * installed from outside the Play Store cannot be granted it at all until the
 * user enables **Settings → Apps → Mudal → Allow restricted settings**. The
 * runtime request then comes back denied with no system dialog and no obvious
 * way forward, which is indistinguishable from the app being broken.
 *
 * The only way out is the app's own system settings page, so the app has to
 * detect the situation and point the user at it.
 */
enum class SmsPermissionState {
    /** READ_SMS is granted. */
    GRANTED,

    /** Not granted, but the system will still show the permission dialog. */
    REQUESTABLE,

    /**
     * Not granted and the system will not ask again — the user denied it
     * permanently, or the app is restricted (sideloaded, Android 14+).
     */
    BLOCKED
}

object SmsPermissionHelper {

    fun isGranted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * The current state of the SMS permission.
     *
     * Note: [ActivityCompat.shouldShowRequestPermissionRationale] is false
     * both when a permission was permanently denied *and* when it was never
     * requested. Callers that have not asked yet must not treat the result as
     * [SmsPermissionState.BLOCKED].
     */
    fun state(context: Context): SmsPermissionState {
        if (isGranted(context)) return SmsPermissionState.GRANTED
        val activity = findActivity(context) ?: return SmsPermissionState.REQUESTABLE
        val canAskAgain = ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.READ_SMS
        )
        return if (canAskAgain) SmsPermissionState.REQUESTABLE else SmsPermissionState.BLOCKED
    }

    /**
     * Opens Mudal's system settings page. That is where the SMS permission is
     * granted, and — on Android 14+ for an app installed outside the Play
     * Store — where "Allow restricted settings" lives.
     */
    fun openAppSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null)
        )
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun findActivity(context: Context): Activity? {
        var current: Context = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }
}
