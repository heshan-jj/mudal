<!-- Mudal fork: new file in Mudal, a fork of PennyWise AI (AGPL-3.0-only), 2026-10-21 - see NOTICE. -->
# Fork Notes: Sri Lankan SMS Expense Tracker

This document records the baseline environment, build verification, audit findings, and operational procedures for this fork of `sarim2000/pennywiseai-tracker`.

---

## 1. Environment & Baseline Versions

- **Host Operating System:** Windows 11 Pro (build 26100.3194)
- **Android Studio:** Android Studio Ladybug / Meerkat (build `AI-261.26222.65.2613.16025427`, 2026.1.3)
- **Java / Gradle JDK:** OpenJDK 25.0.2 (JetBrains Runtime `C:\Program Files\Android\Android Studio\jbr`)
- **Android SDK:** `C:\Users\HeshanJJ\AppData\Local\Android\Sdk`
  - Platforms installed: `android-34`, `android-36`, `android-37.0` (Android 16 VanillaIceCream)
  - Build-tools installed: `36.0.0`, `36.1.0`, `37.0.0`
- **Gradle Version:** 9.7.1 (wrapper)
- **Android Gradle Plugin (AGP):** 9.2.1
- **Kotlin Version:** 2.3.0
- **Git Remotes:**
  - `origin`: `https://github.com/heshan-jj/mudal.git`
  - `upstream`: `https://github.com/sarim2000/pennywiseai-tracker.git`

---

## 2. Test & Build Baseline Results

### `:parser-core:test`
- **Command:** `.\gradlew :parser-core:test`
- **Result:** **100% Green (1,924 tests passed, 0 failed, 0 skipped)**
- **Duration:** 55.068s
- *Note:* Upstream parser count was out of sync with `docs/supported-banks.json` (`expected:<268> but was:<269>`). Passing `UPDATE_SUPPORTED_BANKS=true` refreshed the JSON doc and brought the suite to 100% green without changing code logic.

### `:app:testStandardDebugUnitTest`
- **Command:** `.\gradlew :app:testStandardDebugUnitTest`
- **Result:** **705 tests run, 704 passed, 1 failed** (99.86% pass rate)
- **Duration:** 26m 20s
- **Investigated Failure:**
  - Class: `AccountBalanceRepositoryAccountTypeTest`
  - Test: `a payment on a loan account lowers what is owed, and switching back restores bank behaviour`
  - Assertion: `expected:<239000.00> but was:<238000.00>`
  - **Root Cause:**
    In `AccountBalanceRepositoryAccountTypeTest.kt` (line 247), two `applyTransactionToBalance` calls execute in the exact same millisecond, writing rows with identical timestamps `LocalDateTime.now()`.
    The test mock `AccountBalanceDao.getLatestBalance` resolves queries in memory with:
    ```kotlin
    balanceRows.filter { it.accountId == accountId }.maxByOrNull { it.timestamp }
    ```
    Kotlin's `maxByOrNull` retains the *first* encountered item on comparison ties. Consequently, the mock returns the older loan balance (238,000.00) rather than the newly inserted row (239,000.00).
    In real SQLite Room production code, tie-breaking is handled via SQL `ORDER BY timestamp DESC, id DESC LIMIT 1`.
    *Action taken:* Per Phase 0 rules ("No code changes"), this failure is documented rather than patched in source code.

### APK Assembly
- **Command:** `.\gradlew :app:assembleFdroidDebug :app:assembleStandardDebug`
- Output targets:
  - `app/build/outputs/apk/fdroid/debug/app-fdroid-debug.apk`
  - `app/build/outputs/apk/standard/debug/app-standard-debug.apk`

---

## 3. Inventory of Sri Lankan Bank Parsers

Upstream contains 4 pre-existing Sri Lankan bank parsers under `parser-core/src/commonMain/kotlin/com/pennywiseai/parser/core/bank/`:

### 1. Sampath Bank (`SampathBankParser.kt`)
- **Package:** `com.pennywiseai.parser.core.bank.SampathBankParser`
- **Sender IDs:** `SAMPATHTXN` (accounts), `SAMPCCTXN` (credit cards). Matches DLT and operator prefixes (e.g. `AD-SAMPATHTXN`, `VK-SAMPCCTXN`).
- **Patterns Handled:**
  1. *Credit / Income:* `LKR <amt> credited to AC **<last4> for <merchant>` or `<CURRENCY> <amt> credited...`
  2. *Debit / Expense:* `LKR <amt> debited from AC **<last4> for <merchant>`
  3. *Credit Card Expense:* `Cr Crd no..**<last4> Auth Pmt LKR <amt> at <merchant> Avl Bal LKR <bal>`
- **Currency:** Dynamically captures 3-letter currency code, defaults to `LKR`.
- **Test Fixtures:** `parser-core/src/commonTest/kotlin/com/pennywiseai/parser/core/bank/TestSampathBankParser.kt` (covers POS debit, CEFT credit, multi-currency credit in GBP, card authorization, and negative sender matching).

### 2. Nations Trust Bank (`NationsTrustBankParser.kt`)
- **Package:** `com.pennywiseai.parser.core.bank.NationsTrustBankParser`
- **Sender IDs:** `NATIONSSMS`
- **Patterns Handled:**
  1. *Credit Card Spend:* `Transaction Approved on your Card <bin>******<last4> for LKR <amt> at <merchant> Available Bal LKR <bal>`
  - Explicitly filters out card payments (`made to Card #`).
- **Currency:** `LKR`
- **Test Fixtures:** Included in `parser-core/src/commonTest/kotlin/com/pennywiseai/parser/core/bank/TestAllNewParsers.kt`.

### 3. National Savings Bank (`NationalSavingsBankParser.kt`)
- **Package:** `com.pennywiseai.parser.core.bank.NationalSavingsBankParser`
- **Sender IDs:** `NSB`, `*-NSB`, `*-NSB-*`
- **Patterns Handled:**
  1. *CEFT Inward Transfer Deposit:* `Dear MR <NAME>,LKR <amt> Credited to your A/c XXXXXXXX<last4>... AvlBal LKR <bal>.Transaction CEFT Inward Transfer Deposit...`
  2. *ATM POS Transaction Debit:* `...LKR <amt> Debited from your A/c XXXXXXXX<last4>... AvlBal LKR <bal>. @ <merchant>. ATM POS Transaction...`
  3. *Internet-Mobile Withdrawal:* `...LKR <amt> Debited... AvlBal LKR <bal>.Internet-Mobile Withdrawal...`
- **Currency:** `LKR`
- **Test Fixtures:** Included in `parser-core/src/commonTest/kotlin/com/pennywiseai/parser/core/bank/TestAllNewParsers.kt`.

### 4. NDB Bank (`NDBBankParser.kt`)
- **Package:** `com.pennywiseai.parser.core.bank.NDBBankParser`
- **Sender IDs:** `NDBALERT`, `NDB` (uses exact match to prevent false-positives with Indian `INDBNK`).
- **Patterns Handled:**
  1. *POS Debit:* `LKR <amt> debited from AC XXXXXXXX<last4> as POS TXN on <date> at <merchant>. Avl Bal <bal>...`
  2. *CEFTS Outward Transfer:* `LKR <amt> debited from AC XXXXXXXX<last4> on <date> as CEFTS Outward Transfer. Avl Bal <bal>...`
  3. *CEFTS Inward Transfer:* `LKR <amt> credited to AC XXXXXXXX<last4> on <date> as CEFTS Inward Transfer. Avl Bal <bal>...`
- **Currency:** `LKR`
- **Test Fixtures:** Included in `parser-core/src/commonTest/kotlin/com/pennywiseai/parser/core/bank/TestAllNewParsers.kt`.

---

## 4. Trademark & Licensing Obligations

### `TRADEMARKS.md` Requirements:
- The names **"PennyWise"**, **"PennyWise AI"**, the logo mark, the promotional banner, and any confusingly similar branding are reserved exclusively by the original author.
- Forks must adopt a distinct name, unique application ID, new launcher icons, and distinct metadata across repositories, listings, and documentation.

### `LICENSE` & AGPL-3.0 Section 5 Compliance:
- The codebase is licensed under **GNU Affero General Public License v3.0 (AGPL-3.0-only)**.
- All original copyright notices and license headers must be preserved intact.
- Modifications must be prominently marked (date of change and note of modification).
- An in-app "Licenses and Source Code" attribution page linking to our repository must be provided.
- Any derivative or combined work must also be licensed under AGPL-3.0.

---

## 5. Network Touchpoints Inventory

1. **On-Device AI Model Weights:**
   - URL: `https://pub-fcfb3ffddb184540a758a7fe68249908.r2.dev/models/v1/Qwen2.5-1.5B-Instruct-q8-ekv4096.litertlm`
   - File: `app/src/main/kotlin/com/pennywiseai/tracker/data/ai/ModelDownloader.kt`
   - Behavior: Downloads 1.5B quantized parameter model (~1GB) from upstream Cloudflare R2 bucket.
2. **Exchange Rates API:**
   - URL: `https://open.er-api.com/v6/latest/{currency}`
   - File: `app/src/main/kotlin/com/pennywiseai/tracker/data/remote/ExchangeRateApi.kt`
   - Behavior: Public API query to update multi-currency conversion rates.
3. **Licensing & Pro Entitlements:**
   - Gateway: `https://live.dodopayments.com`
   - License Verification/Transfer: `LICENSE_MOVE_URL` (`https://worker.pennywise.app/...`)
   - Files: `DodoPaymentsGateway.kt`, `LicenseMoveRepository.kt`
4. **Third-Party SMS Web Parser ("Report Unrecognized SMS"):**
   - URL: `https://pennywise.zynth.dev/#message={encoded}&sender={sender}`
   - Files: `SettingsScreen.kt`, `UnrecognizedSmsSheet.kt`
   - **Privacy Concern:** Passes the raw SMS text and bank sender ID directly into the URL fragment of an external web app.
5. **Outbound User Webhooks:**
   - URL: User-specified HTTPS endpoints
   - File: `WebhookDispatcher.kt`
   - Behavior: POSTs serialized transaction JSON payloads upon receipt if configured.

---

## 6. Pro Gates & Commercial Restrictions

The standard build gates multiple features behind `isPro` checks via `FreeTierLimits.kt` and `EntitlementGate.kt`:
- **Rules Limit:** Maximum 3 custom rules.
- **Statement Import Limit:** Maximum 1 bank statement import per month.
- **CSV Export Limit:** Maximum 100 rows per month.
- **Recurring Templates:** Maximum 1 recurring transaction template.
- **Financial Goals:** Maximum 1 active goal.
- **Advanced Features Gated:** Account merging, webhook synchronization, advanced analytics groupings, business/multi-profile accounting.

*Note for F-Droid Flavor:* The `fdroid` build flavor already contains `FdroidBillingGateway.kt` which hardcodes `isPro = true`. In Phase 1, the app will be standardized on an unencumbered single flavor.

---

## 7. Unrecognized SMS Candidate Logic & Sri Lankan Gap

Located in `app/src/main/kotlin/com/pennywiseai/tracker/worker/OptimizedSmsReaderWorker.kt` (`isUnrecognizedCandidate`):

```kotlin
// Upstream regex check
private val CURRENCY_PATTERN = Regex("""[₹$€£]\s?\.?\d|\d[\d.,]*\s?[₹$€£]""")
```
- It inspects alphanumeric senders outside India.
- It looks for known currency symbols (`₹`, `$`, `€`, `£`) or standard 3-letter ISO codes (`Currency.getAvailableCurrencies()`, which includes `LKR`).
- **Critical Gap for Sri Lanka:**
  In Sri Lanka, banks and merchants regularly send notifications formatted with **`Rs.`**, **`Rs `**, or **`SLR`** (e.g. `Rs. 5,420.00`, `Rs 1200.00`).
  Because `CURRENCY_PATTERN` does not match `Rs.` and `Rs` is not a 3-letter ISO code, **any Sri Lankan bank SMS using `Rs.` from an unmapped sender is discarded as non-financial** and never reaches the Unrecognized SMS review queue.
  *Fix scheduled for Phase 3:* Add `(?:Rs\.?|SLR)\s?\d` to candidate detection.

---

## 8. Upstream Sync Procedure

To synchronize changes from upstream `sarim2000/pennywiseai-tracker` without code divergence:

```bash
# 1. Fetch latest upstream changes
git fetch upstream

# 2. Test merge on an isolated scratch branch
git checkout -b sync-test
git merge upstream/main

# 3. Resolve any conflicts, run tests
./gradlew :parser-core:test
./gradlew :app:testStandardDebugUnitTest

# 4. If green, fast-forward/rebase main and delete scratch branch
git checkout main
git merge sync-test
git branch -d sync-test
```

---

## 9. End-to-End Emulator SMS Verification

- **Device:** Android Emulator `emulator-5554` (AVD `Pixel_8`, API 37.1 Google APIs Play Store x86_64)
- **Installed Artifact:** `app/build/outputs/apk/standard/debug/app-standard-debug.apk` (Package: `com.pennywiseai.tracker.debug`)
  *(Note: Standard flavor is required for x86_64 emulator execution because F-Droid ABI filter is arm-only).*
- **Injected SMS Command:**
  ```bash
  adb -s emulator-5554 emu sms send SAMPATHTXN "LKR 5,000.00 credited to AC **8758 for PICKME - 37419306"
  ```
- **Logcat Verification:**
  ```
  10-21 12:51:51.317 10332 11453 D SmsBroadcastReceiver: Transaction saved with ID: 1
  ```
- **UI Hierarchy Output:**
  ```
  text="Recent Transactions"
  text="Pickme"
  text="21 Oct · 12:51 PM · Income"
  text="+රු5,000"
  text="Bank Accounts"
  text="Sampath Bank"
  text="••8758"
  text="Savings"
  ```
- **Screenshot Evidence:** Saved to session artifact directory `phase_0_emulator_sms_success.png`.

---

# Phase 1: Identity, De-commercialisation, Privacy Cleanup & LKR Defaults

**Date:** 2026-10-21  
**Status:** Completed & Verified  

## 1. Summary of Changes

- **Identity & Package ID:**
  - Application ID changed to `com.heshan.moneytracker` (debug build: `com.heshan.moneytracker.debug`).
  - App display name updated to **Mudal**.
  - Generated original launcher icons (monogram geometric 'M' icon with `#0F1F1C` dark emerald background).
  - Preserved internal Kotlin package structure (`com.pennywiseai.tracker.*`) to maintain clean upstream git compatibility.
- **Single Unified Build Flavor:**
  - Flattened `standard` and `fdroid` build variants into a single unified build.
  - Retained `x86_64`, `arm64-v8a`, and `armeabi-v7a` ABIs.
  - Stripped Play Billing, Play In-App Updates, and Play In-App Review dependencies and permissions (`com.android.vending.BILLING` completely removed from merged manifest).
- **De-commercialisation & Feature Unlocking:**
  - Deleted Dodo Payments integration, RSA public key verification, `LicenseClient`, `LicenseManager`, and `LicensePolicy`.
  - Deleted paywall UI components: `UpgradeSheet`, `UpgradeViewModel`, `LicenseKeyDialog`, `ProHomeCard`, and `SupportDevelopment` dialogs.
  - Unlocked all previously gated features to 100% capacity: unlimited rules, statement imports, CSV export rows, recurring templates, financial goals, account merges, webhooks, and analytics groupings.
- **Privacy & Upstream Decoupling:**
  - Decoupled upstream endpoints (`pennywise.zynth.dev`, upstream Discord server).
  - Replaced remote SMS issue reporting with local regex anonymization (`SmsReportUrlBuilder`) that masks digit sequences, card numbers, amounts, and emails/VPA into GitHub issue links for `heshan-jj/mudal`.
  - Created `docs/PRIVACY.md` detailing every remaining network interaction (exchange rates, model downloads, webhooks, and GitHub reporting).
  - Added `ATTRIBUTION.md` retaining AGPL-3.0 attribution for Mohammad Sarim and PennyWise.
  - Updated `README.md` to reflect Mudal identity.
- **Sri Lankan Defaults:**
  - Changed default currency from `INR` to `LKR`.
  - Added `Rs.` currency symbol mapping for `LKR` and updated Sri Lankan locale resolution.

## 2. Test & Verification Evidence

- **Unit Tests:**
  - `:parser-core:test`: 1,927 tests passed (0 failures).
  - `:app:testDebugUnitTest`: 692 tests passed (0 failures).
- **Build:**
  - `:app:assembleDebug`: Clean build completed; APK generated at `app/build/outputs/apk/debug/app-debug.apk`.
- **Side-by-Side Coexistence on Pixel 8 Emulator:**
  - Installed `com.heshan.moneytracker.debug` alongside existing `com.pennywiseai.tracker.debug`. Both apps run independently with isolated data stores.

---

# Phase 2: Database Encryption (SQLCipher) & Security Hardening

**Date:** 2026-10-21  
**Status:** Completed & Verified  

## 1. Summary of Changes

- **SQLCipher Room Integration:**
  - Integrated `net.zetetic:sqlcipher-android:4.19.1` in `app/build.gradle.kts` and `gradle/libs.versions.toml`.
  - Configured Room database initialization with `SupportOpenHelperFactory(passphrase)` in both `DatabaseModule.kt` and `PennyWiseDatabase.getInstance()`.
  - Native library `libsqlcipher.so` automatically bundled and verified for `x86_64` (emulator) and ARM architectures.
- **Passphrase Generation & Android Keystore Wrapping:**
  - Implemented `DatabaseKeyManager` and `KeyStoreHelper` / `AndroidKeyStoreHelper`:
    - Generates 32 cryptographically secure random bytes on first launch.
    - Master key generated in hardware-backed `AndroidKeyStore` (`AES-256-GCM`, `NoPadding`, user authentication false for headless background workers).
    - Wrapped key and IV stored securely in private app preferences.
    - Decrypts and provides cached key at runtime.
- **Graceful Key Loss & Crash-Loop Prevention:**
  - If the database file exists on disk but Keystore master key is corrupted/lost:
    - `DatabaseModule` catches the key error and builds an in-memory database fallback to guarantee Hilt dependency injection never throws during process startup.
    - `PennyWiseApplication.onCreate()` detects key loss and pauses background jobs touching Room.
    - `MainActivity` detects key loss and renders `DatabaseErrorScreen` (Material 3).
    - Offers clear user actions: "Restore from Backup" (via file picker) or "Reset Database (Start Fresh)" with confirmation.
- **Security Settings & Recents Obscuring:**
  - `allowBackup="false"` verified in `AndroidManifest.xml`.
  - Added `FLAG_SECURE` user setting ("Hide content in app switcher") stored in `UserPreferencesRepository`.
  - Added UI switch in the Security section of `SettingsScreen.kt`.
  - Dynamically sets `WindowManager.LayoutParams.FLAG_SECURE` in `MainActivity.kt` to block screenshots and obscure previews in the recent apps switcher.

## 2. Test & Verification Evidence

- **Unit Tests:**
  - `DatabaseKeyManagerTest` (5 tests on JVM): 100% green (tests key generation, wrapping, unwrapping, missing key error, decryption failure, and database reset).
  - `:app:testDebugUnitTest`: 697 tests passed (0 failures).
  - `:parser-core:test`: 1,927 tests passed (0 failures).
- **Connected Instrumentation Tests on Pixel 8 Emulator:**
  - `SqlCipherEncryptionTest.testEncryptedDatabaseCreationAndRejectionOnWrongKey`: PASSED (verified encrypted database creation, verified raw binary header does NOT contain "SQLite format 3", verified wrong key throws `net.zetetic.database.sqlcipher.SQLiteNotADatabaseException: file is not a database (code 26)`, and verified recovery with correct key).
  - `SqlCipherEncryptionTest.testBackupRoundTripWithEncryptedDatabase`: PASSED (verified full export of data from encrypted DB, wiping DB, fresh encrypted DB initialization with new key, and full restore via `BackupImporter.REPLACE_ALL`).
- **Real Device / adb Verification:**
  - Pulled `pennywise_database` from Pixel 8 emulator running Mudal (`com.heshan.moneytracker.debug`).
  - Read raw 32-byte header: `header.startswith(b'SQLite format 3') == False`.
  - Executed SQLite connection on pulled database: `sqlite3.connect(...)` threw `DatabaseError: file is not a database` (proving plaintext reading is impossible).

---

## 10. Repository rename and AGPL compliance pass (2026-10-21)

The owner renamed the GitHub repository to **Mudal**
(`heshan-jj/pennywiseai-tracker` -> `heshan-jj/mudal`). Everything that pointed at
the old slug was updated, and the AGPL-3.0 obligations were audited against
upstream's `TRADEMARKS.md` before the first public push.

### Repository plumbing
- `origin` remote now points at `https://github.com/heshan-jj/mudal.git`; `upstream`
  is unchanged (`sarim2000/pennywiseai-tracker`).
- In-app links: `Constants.Links.GITHUB_URL` and the Settings "Source code &
  licenses" / "Report an issue" entries now point at `heshan-jj/mudal`.
- Docs: `docs/PRIVACY.md`, `docs/FORK_NOTES.md` and the issue templates point at the
  new repository.

### AGPL-3.0 compliance
- `NOTICE` added: copyright for Mudal (Heshan Jayakody and the Mudal contributors),
  the upstream copyright, the AGPL-3.0-only grant, the source-offer URL, and the
  trademark statement.
- `ATTRIBUTION.md` rewritten: upstream credit, the three modification phases, the
  third-party licence table (SQLCipher added: BSD-3-Clause), and a trademark section.
- `docs/MODIFICATIONS.md` added: the AGPL-3.0 section 5(a) record. It lists every
  added, modified and removed file against `upstream/main` (176 modified, 24 added,
  342 removed at the time of writing).
- Every modified source file now carries a `Mudal fork:` notice with the date of
  modification, at the top of the file. New files carry a "new file in Mudal" notice.
- The backup **format marker** (`"PennyWise Backup v1.3"`) is deliberately left
  unchanged: it is an on-disk format identifier, not branding, and renaming it would
  make every existing backup fail to import. The app's own name never appears in the
  payload.

### Trademark cleanup (upstream `TRADEMARKS.md` items 1 and 2)
- Upstream's banner artwork (`banner.png`) removed from the repository.
- `funding.json` rewritten for Mudal (it previously pointed at upstream's author,
  project, download statistics and donation page). `.github/FUNDING.yml` removed.
- Issue templates re-pointed at the Mudal repository; upstream's Discord invite
  removed from `config.yml` and `SECURITY.md`.
- Release and CI workflows now build the single remaining variant
  (`assembleRelease` / `bundleRelease` / `:app:testDebugUnitTest` / `:parser-core:jvmTest`);
  the removed `standard` and `fdroid` flavour tasks are gone.
- User-facing strings re-branded in every locale (`PennyWise` -> `Mudal`), plus the
  debug build's app name, the app-switcher theme names, the export file names and the
  HTTP `User-Agent`. Upstream's own class names (`PennyWiseDatabase`,
  `PennyWiseApplication`, `PennyWiseApp`, `PennyWiseScaffold`) and the Kotlin
  package namespace are unchanged on purpose.

### Privacy
- The repository is public, so `docs/lk-samples/` was scrubbed: personal names that
  appeared as transfer labels are replaced with `OWNER` / `PERSON A` / `PERSON B`, and
  the 10-digit identifier in the Commercial Bank samples is already masked. The parser
  fixtures were updated to match. Merchant names, amounts and timestamps are kept,
  because the fixtures need them to be realistic.
- `docs/lk-samples/README.md` records the scope (Sampath + Commercial Bank only) and
  the open questions (real sender IDs).

### Documentation accuracy
- `docs/supported-banks.json`, the README bank block, the web resources and the store
  listing were regenerated with `scripts/update-supported-banks.sh`. The fork now
  advertises **5 banks in 1 country**, not the 167 banks upstream ships. The
  generator's country pluralisation was fixed ("1 country", not "1 countries").
- `docs/issue-tracker.md` (upstream's issue list) was removed; the fork tracks its own
  issues.
