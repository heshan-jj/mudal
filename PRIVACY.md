<!-- Mudal fork: modified from PennyWise AI (AGPL-3.0-only) on 2026-10-21 - see NOTICE. -->
# Privacy Policy

**Last Updated: August 2026**

## Our Commitment to Privacy

Mudal is built with privacy as the core principle. We believe your financial data should remain yours alone.

## On-Device Processing

**Transaction processing runs locally on your device.** Parsing, categorisation, budgets and storage never leave the phone. The only data that ever leaves your device goes through the explicit outbound requests listed under Internet Permission below (the optional AI assistant is the one feature that sends transaction context to a server — and only if you turn it on with your own API key):

- ✅ **Local by default** - Parsing, categorisation and budgets run on your device; your transaction database never uploads anywhere. The optional AI assistant is off unless you give it your own API key.
- ✅ **No data collection** - No analytics, telemetry, or accounts; the one exception is the parse-report flow (see Internet Permission)
- ✅ **No tracking** - No analytics, no telemetry, no user tracking
- ✅ **No ads** - No advertising networks or tracking pixels
- ✅ **Offline core** - Everything except the optional AI assistant works with no internet connection at all

## Data Storage

### What We Store (Locally Only)
- Transaction details extracted from SMS (amount, merchant, date, category)
- Your custom categories and notes
- App preferences and settings

### Where It's Stored
- All data is stored in a local SQLite database on your device
- Database is protected by Android's app sandboxing
- Data is only accessible to Mudal app

### Data Deletion
- Uninstalling the app completely removes all data
- You can delete individual transactions at any time
- Export your data before uninstalling if you want to keep records

## Permissions

### SMS Permissions (Read-Only)
- **`READ_SMS` / `RECEIVE_SMS`**: to read bank transaction SMS as they arrive
- **Scope**: Read-only access, we cannot send or modify messages
- **Processing**: SMS parsing happens entirely on-device
- **Storage**: Only extracted transaction fields are stored, not full messages

### Notifications (`POST_NOTIFICATIONS`)
- Subscription due-date reminders and budget alerts

### Biometrics (`USE_BIOMETRIC`)
- Optional app lock via fingerprint or face unlock

### Contacts (`READ_CONTACTS`, off by default)
- Mudal ships this permission for one opt-in feature: contact lookup, which can resolve a UPI VPA like `merchant@ybl` into a name from your address book so merchant lists read naturally
- The lookup runs on-device and the feature stays dormant until you enable it in Settings

### Internet Permission
- **AI assistant**: Optional. Uses the Google Gemini API (`https://generativelanguage.googleapis.com/`) with your own API key, stored locally in the app's private storage. Off until you configure a key.
- **Exchange Rates**: Multi-currency features fetch current rates from open.er-api.com; only the currency code is sent
- **Parse Reports**: The "report a parsing problem" action opens pennywise.zynth.dev with the SMS text and sender ID pre-filled. The link also carries an encrypted device identifier — your Android device ID plus a timestamp, RSA-encrypted on-device before it leaves. Opening the page sends these details to the server right away to generate a parsed preview; submitting the report itself remains a separate user action
- **App Updates**: Google Play Store variant uses Play Services for app updates (F-Droid variant does not)
- **Without the AI assistant**: everything works offline, no internet required

No location, camera, or microphone permission is requested.

## Third-Party Services

Mudal does **NOT** use:
- ❌ Cloud storage, backup, or sync of your transaction data
- ❌ Analytics services (Google Analytics, Firebase, etc.)
- ❌ Crash reporting services
- ❌ Advertising networks
- ❌ Social media SDKs
- ❌ Payment processors

**Note**: The Google Play Store variant includes Play Services for app updates only. The F-Droid variant has no Google services.

## AI Features

### On-Device AI Assistant
- Uses the Google Gemini API with your own API key; Mudal runs no model locally and downloads nothing
- Model runs entirely on your device; inference needs no network
- After initial download, no internet connection required
- Chat history is saved locally in the app's database so conversations survive restarts; it's never transmitted
- AI insights are generated locally from your local transaction data
- Model file stored in app's private storage

## Data Export

When you export your data:
- CSV/PDF files are created locally on your device
- You control where to share or save them
- No automatic uploads or backups

## Open Source Transparency

Mudal is fully open source:
- Review our code at [GitHub](https://github.com/heshan-jj/mudal)
- Verify our privacy claims yourself
- Contribute to make it even better

## Children's Privacy

Mudal is not directed at children under 13. We do not knowingly collect information from children.

## Changes to Privacy Policy

Any changes to this privacy policy will be:
- Updated in the app repository
- Reflected in the "Last Updated" date
- Communicated through release notes

## Contact

For privacy concerns or questions:
- Open an issue on [GitHub](https://github.com/heshan-jj/mudal/issues)
- Join our [Discord community](https://discord.gg/H3xWeMWjKQ)

## Summary

**Your financial data lives on your phone.** Transactions, balances, budgets, and chat history all sit in a local database inside the app sandbox, and uninstalling removes everything. Mudal has no analytics or cloud-sync backend; the outbound requests listed under Internet Permission are the complete list.

---

*Mudal - Privacy-first expense tracking with on-device AI*