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
  - `origin`: `https://github.com/heshan-jj/pennywiseai-tracker.git`
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

