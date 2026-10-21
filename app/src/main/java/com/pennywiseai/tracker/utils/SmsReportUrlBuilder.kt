package com.pennywiseai.tracker.utils

import android.content.Context
import com.pennywiseai.tracker.core.Constants
import java.net.URLEncoder

object SmsReportUrlBuilder {

    /**
     * Masks sensitive user details (account/card numbers, amounts, OTPs, emails/VPAs)
     * entirely on-device before any sharing or issue creation.
     */
    fun maskSms(smsBody: String?): String {
        if (smsBody == null) return ""
        var masked = smsBody
        // Mask amounts: e.g. Rs. 1,500.00 or LKR 250 or $40.50
        masked = masked.replace(Regex("""(?i)(rs\.?|lkr|inr|usd|\$|eur|€|gbp|£)\s*[\d,]+(\.\d+)?"""), "$1 [AMOUNT]")
        // Mask account / card numbers and digit runs >= 4 digits
        masked = masked.replace(Regex("""\b\d{4,}\b"""), "[NUMBER]")
        // Mask VPA / emails
        masked = masked.replace(Regex("""[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+"""), "[VPA/EMAIL]")
        return masked
    }

    /**
     * Builds a pre-filled GitHub issue URL on the Mudal repository with masked SMS details.
     * No device IDs, personal data, or remote server endpoints are ever used.
     */
    fun buildUrl(context: Context, smsBody: String?, smsSender: String?): String {
        val maskedBody = maskSms(smsBody)
        val sender = smsSender ?: "Unknown Sender"
        val title = URLEncoder.encode("[Parser Request] $sender", "UTF-8")
        val issueBody = URLEncoder.encode(
            """### Bank / Sender
$sender

### Masked SMS Sample
```
$maskedBody
```

### Expected Transaction
- **Amount**: 
- **Type** (Debit/Credit): 
- **Account/Card (last 4)**: 
""".trimIndent(),
            "UTF-8"
        )
        return "${Constants.Links.GITHUB_URL}/issues/new?title=$title&body=$issueBody"
    }
}
