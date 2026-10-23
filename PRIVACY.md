<!-- Mudal fork: modified from PennyWise AI (AGPL-3.0-only) on 2026-10-21 - see NOTICE. -->
# Privacy Policy

**Last Updated: 23 October 2026**

Mudal is a private expense tracker. Parsing, categorisation, budgets and storage run on your phone. There is no account, no analytics and no server that receives your transactions.

## What stays on the device

- Transaction fields taken from bank SMS (amount, merchant, date, category), plus notes, categories, rules, budgets and goals
- App settings, including an optional Gemini API key if you choose to enter one
- The transaction database, encrypted with SQLCipher. The passphrase is a random key wrapped by the Android Keystore and never leaves the phone

Uninstalling the app deletes the database and the Keystore key. Export a backup first if you want to keep the data. You can also delete individual transactions inside the app.

## Permissions

- **`READ_SMS` / `RECEIVE_SMS`** — read bank transaction messages. Read-only: the app cannot send or edit SMS. Only the extracted transaction fields are stored, not a copy of your inbox.
- **`POST_NOTIFICATIONS`** — budget alerts and subscription reminders.
- **`USE_BIOMETRIC`** — optional app lock.
- **`INTERNET`** — only for the requests listed below.

Mudal does not request contacts, location, camera or microphone access.

## What can leave the device

Nothing is sent unless you use one of these. With none of them turned on, the app makes no network requests of its own.

1. **AI assistant (optional).** If you save your own Gemini API key and start a chat, Mudal sends the message and the spending context needed to answer it (merchant names, amounts, categories and dates) to `https://generativelanguage.googleapis.com/`. The key stays in the app's private storage. There is no Mudal-hosted key and no model download. Without a key, the assistant stays off.
2. **Exchange rates.** When multi-currency conversion is on, the app requests `https://open.er-api.com/v6/latest/{currency}`. Only the currency code is sent. Rates are cached on the device.
3. **Webhooks (optional).** If you configure one, transaction JSON is posted to the HTTPS address you entered. Off unless you set it up.
4. **Parser reports.** "Report a parsing problem" opens a GitHub issue draft on `https://github.com/heshan-jj/mudal` in your browser. Account numbers, amounts and VPAs are masked on the device first. Nothing is submitted until you post the issue yourself.

## What Mudal does not use

- Analytics, crash reporting or other telemetry
- Advertising
- Cloud sync of your transactions
- Google Play Services, in-app billing or a payments SDK

## Source

Mudal is AGPL-3.0-only. The code is at [github.com/heshan-jj/mudal](https://github.com/heshan-jj/mudal). The network list above is the same one kept in [`docs/PRIVACY.md`](docs/PRIVACY.md).

## Children

Mudal is not directed at children under 13, and it does not knowingly collect information from them.

## Changes

Updates to this policy are committed to the repository and reflected in the date above.

## Contact

- [GitHub issues](https://github.com/heshan-jj/mudal/issues)
- `jayakodyheshan@gmail.com`
