<!-- Mudal fork: new file in Mudal, a fork of PennyWise AI (AGPL-3.0-only), 2026-10-21 - see NOTICE. -->
# Mudal — Privacy Policy & Network Audit

Mudal is designed from the ground up to be a private, offline-first personal finance tracker. All transaction parsing, categorization, and budgeting calculations occur strictly on your device.

---

## Zero Telemetry & Zero Trackers

- **No Analytics:** No Google Analytics, Firebase, Sentry, or proprietary telemetry SDKs.
- **No Ads:** Free of advertising SDKs or tracking pixels.
- **No Remote SMS Logging:** Bank SMS messages and device identifiers are **never** transmitted to remote servers. Unrecognized SMS reporting opens a local, regex-masked GitHub issue template in your browser for user review before submission.
- **No Billing Services:** `com.android.vending.BILLING` has been completely stripped.

---

## Remaining Network Endpoints

Mudal only makes network calls for user-requested, explicit functionality:

### 1. Exchange Rates (Multi-Currency Support)
- **Endpoint:** `https://open.er-api.com/v6/latest/{CURRENCY}`
- **Trigger:** Only executed when the user activates multi-currency conversion or unified currency mode.
- **Payload:** Standard HTTP GET with the base currency code (e.g., `LKR`, `USD`). No user identifiers, device metadata, or transaction amounts are transmitted.
- **Frequency:** Cached locally; refreshed periodically when multi-currency is active.

### 2. On-Device AI Model Download (Optional)
- **Endpoint:** `https://pub-fcfb3ffddb184540a758a7fe68249908.r2.dev/models/v1/Qwen2.5-1.5B-Instruct-q8-ekv4096.litertlm`
- **Trigger:** Initiated only when the user explicitly taps "Download AI model" in Settings.
- **Integrity Verification:** Downloaded binary is verified on-device against pinned SHA-256 (`faa60663b333290c1496c499828b21d3e3254a788cacd8cce917ce0f761a2dc9`) prior to loading into MediaPipe / LiteRT.
- **Payload:** Plain HTTP GET for the static `.litertlm` weights file. Zero user data sent.

### 3. User Webhooks (Custom Automation)
- **Endpoint:** User-configured URLs (e.g., self-hosted n8n, Home Assistant, personal servers).
- **Trigger:** Only executed if the user sets up and enables automated webhook export.
- **Payload:** Structured JSON payload of transactions sent directly to user-defined destinations. Off by default.

### 4. GitHub Issue Reporting (User-Initiated)
- **Endpoint:** `https://github.com/heshan-jj/mudal/issues/new`
- **Trigger:** When user taps "Report format" or "Ask for help" on unrecognized messages.
- **Protection:** All account numbers, phone numbers, balances, card numbers, and VPAs are masked locally (`[NUMBER]`, `[AMOUNT]`, `[VPA/EMAIL]`) before generating the pre-filled issue link. No data is sent automatically in the background.

---

## Local Data Storage

All data (accounts, transactions, categories, budgets, and encrypted backups) is stored locally in SQLite / Room on the device.
