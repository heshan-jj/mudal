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

### 2. AI Assistant (Optional, opt-in)
- **Endpoint:** `https://generativelanguage.googleapis.com/` (Google Gemini API)
- **Trigger:** Only used when the user configures their own Gemini API key in Settings and starts a chat.
- **Key handling:** The API key is entered by the user, stored locally in the app's private DataStore, and sent only to Google. Mudal has no key of its own and no server in between.
- **Payload:** The chat text plus the transaction context the assistant needs to answer questions about your spending (merchant names, amounts, categories and dates from your own database). Nothing is sent until the user enables the assistant and starts a conversation.
- **Note:** This is the one place where data can leave the device. Everything else in Mudal — SMS parsing, categorisation, budgets, storage and the encrypted database — stays on the phone. If you do not configure a key, the assistant stays off and the app makes no AI requests.

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
