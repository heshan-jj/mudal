# Plan: remove the UPI / contacts feature

**Status:** **done** — 2026-10-22. Executed in full; see `docs/FORK_NOTES.md` §14 for
what actually changed and how it was verified.
**Why now:** the next release is the first one people install by hand, and the
app currently asks for `READ_CONTACTS`. A finance app that wants to read the
whole address book to prettify a merchant name is a bad look — to Play
Protect, to the Play Store's SMS/permissions policy, and to anyone reading the
privacy policy. The feature is also Indian UPI-specific and has no use for a
Sri Lankan user.

**Goal:** Mudal declares no contacts permission, never reads the address book,
and has no UPI-VPA merchant-rewriting code left. The permission disappears
from the install prompt and from the store listing.

**Owner decision:** "remove the UPI feature completely" — the plan below takes
that literally, with two deliberate exceptions listed in §3.

---

## 1. What the feature is today

Mudal inherits one feature from upstream PennyWise AI: when a transaction's
merchant string contains a UPI VPA (`9876543210@paytm`), the user can opt in
to a contacts lookup that renders the person's name (`John Doe`) instead of the
VPA.

It is spread over four layers:

| Layer | What it does |
|---|---|
| Permission | `READ_CONTACTS` in the manifest, requested at runtime when the toggle is switched on |
| Data | `ContactsResolver` — queries `ContactsContract.PhoneLookup` and caches the result |
| Display | `MerchantContactDisplay` (the rewrite rule) + `LocalMerchantDisplay` (a CompositionLocal so every list, card and detail screen shares one rule) |
| Preferences | `useContactsForVpa` (the toggle) and `upiId` (the user's own UPI id, also written into backups) |

---

## 2. Goal state

- `READ_CONTACTS` is not declared, not requested, and not mentioned anywhere.
- No file in the app reads `ContactsContract`.
- No preference, backup field, string, or doc refers to a VPA lookup.
- A merchant string is displayed exactly as the bank sent it.
- Nothing else about the app changes.

---

## 3. Scope decisions (read this before executing)

### 3.1 Removed

| # | Item | File |
|---|---|---|
| 1 | The contact lookup | `app/src/main/java/com/pennywiseai/tracker/data/contacts/ContactsResolver.kt` — delete |
| 2 | The merchant-rewrite rule | `app/src/main/java/com/pennywiseai/tracker/data/contacts/MerchantContactDisplay.kt` — delete |
| 3 | The CompositionLocal | `app/src/main/java/com/pennywiseai/tracker/data/contacts/LocalMerchantDisplay.kt` — delete |
| 4 | The permission | `app/src/main/AndroidManifest.xml` — drop `<uses-permission android:name="android.permission.READ_CONTACTS" />` |
| 5 | The toggle preference | `UserPreferencesRepository.kt` — drop `useContactsForVpa` (+ getter/setter, + the `PreferencesKeys` entry) |
| 6 | The UPI id preference | `UserPreferencesRepository.kt` — drop `upiId` / `PreferencesKeys.UPI_ID` (+ getter/setter) |
| 7 | The backup field | `BackupModels.kt` — drop `PreferencesSnapshot.upiId`; `BackupExporter.kt` — stop writing it |
| 8 | The settings UI | `SettingsScreen.kt` / `SettingsViewModel.kt` — drop the "Contacts" section and the "Replace UPI VPAs with contact names" toggle (+ the `ContactsResolver` injection) |
| 9 | The display wiring | `MainViewModel.kt` (provider), `MainScreen.kt` (provider), `TransactionItem.kt`, `TransactionDetailScreen.kt`, `AnalyticsScreen.kt` (consumers) |
| 10 | The strings | `strings_settings.xml` + every locale: `settings_contacts_section`, `settings_contacts_vpa_title`, `settings_contacts_vpa_subtitle` |
| 11 | The dead pattern copy | `app/src/main/java/com/pennywiseai/tracker/core/CompiledPatterns.kt` — **nothing in the app module uses it**; delete |
| 12 | The dead UPI patterns | `parser-core/.../core/CompiledPatterns.kt` — `UPI_REF`, `VPA_WITH_NAME`, `VPA_PATTERN`, `UPI_REF_NO` are **defined but never referenced** now that the Indian parsers are gone; delete |
| 13 | The UPI payment-request flow | `domain/loan/SplitRules.kt` — the `UpiRequest` object (`isValidUpiId`, `payUri`); `data/share/UpiQr.kt` — the UPI QR generator. Both build an INR `upi://pay` link, which is India's instant-payment system and means nothing in Sri Lanka. |
| 14 | The split sheet's request button | `presentation/split/SplitBill.kt` (the "request payment" button, the "ask for UPI id" dialog, the share intent) and `presentation/split/SplitBillViewModel.kt` (`upiId`, `setUpiId`, the `askUpiFor` state) |
| 15 | The UPI id in Settings | `SettingsScreen.kt` (the "your UPI id" row and its dialog) and `SettingsViewModel.kt` (`upiId`, `setUpiId`) |
| 16 | The UPI id in backups | `BackupImporter.kt` — stop restoring it (in addition to the export side) |

### 3.2 Deliberately kept (with the reason)

| Item | Why it stays |
|---|---|
| `SplitBill.kt` — the "pick from contacts" button in the split sheet | It uses the **system contact picker** (`ActivityResultContracts.PickContact`), which grants a one-off read of the one contact the user picks. It needs **no `READ_CONTACTS` permission**. Splitting a shared bill with friends is exactly as relevant in Sri Lanka as anywhere — and it costs nothing permission-wise. |
| The VPA/email masking in the "report a parsing problem" flow (`docs/PRIVACY.md`) | That is a **privacy** feature: it redacts VPA-like strings before an issue link leaves the device. Unrelated to the contact lookup. |
| `import_statement_result_by_reference` ("By UPI reference" in the statement-import UI) | It is the label for *how* a statement row was matched. The matcher itself is generic, so the label is renamed to "By reference" (see §4, step 5) and the outdated translations are dropped so they fall back to English until Crowdin re-translates. |
| The **split sheet itself** — who owes whom | Only the UPI "request payment" button goes. The split bookkeeping stays; collecting the money is up to the user (cash, eZ Cash, PayHere, a bank transfer). Splitting a shared bill is as relevant in Sri Lanka as anywhere. |
| The five **statement PDF parsers** (GPay, ICICI, Paytm, PhonePe, Slice) | They are statement *formats*, not UPI features. All five are Indian, so they are dead weight for a Sri Lankan user, but removing them is a separate product decision — flagged as a follow-up, not done here. |
| `BankParser.kt:598` — `CompiledPatterns.Cleaning.UPI_SUFFIX` | It strips a trailing "UPI…" from a merchant name. It lives in upstream's shared base class, it is harmless for Sri Lankan merchants, and touching it means diverging from upstream base code for no gain. Rule 1 of the fork plan: keep the diff small. |
| `rules_summary_upi_cashback` ("Amount < 10 from NPCI") | That is a summary of a **user-authored rule** about a bank, not a UPI feature. |

### 3.3 Out of scope

- The other fixes you have queued for the next release.
- Phase 4 (recurring transactions & subscriptions) — unchanged.
- Anything in the `parser-core` bank parsers beyond the four dead constants.

---

## 4. Steps

Each step is independently committable. Do them in order.

**Step 1 — the permission (smallest, biggest visible win)**
1. `AndroidManifest.xml`: remove the `READ_CONTACTS` uses-permission.
2. Verify: `aapt2 dump badging <apk> | grep READ_CONTACTS` prints nothing.

**Step 2 — the data layer**
3. Delete `data/contacts/ContactsResolver.kt`, `MerchantContactDisplay.kt`, `LocalMerchantDisplay.kt`.
4. `UserPreferencesRepository.kt`: remove `useContactsForVpa` and `upiId` (keys, flows, setters).
5. `MainViewModel.kt`: drop the `ContactsResolver` injection and whatever built the display function from it.

**Step 3 — the consumers**
6. `MainScreen.kt`, `TransactionItem.kt`, `TransactionDetailScreen.kt`, `AnalyticsScreen.kt`: replace
   `merchantDisplay(transaction.merchantName) ?: transaction.merchantName` with `transaction.merchantName`,
   and delete the `LocalMerchantDisplay` import and the `CompositionLocalProvider`.
7. `SettingsViewModel.kt` / `SettingsScreen.kt`: remove the `ContactsResolver` injection and the Contacts settings section.

**Step 4 — the backup**
8. `BackupModels.kt`: remove `upiId` from `PreferencesSnapshot`.
9. `BackupExporter.kt`: stop writing it. `BackupImporter.kt`: stop restoring it.
10. `BackupModelsTest.kt`: drop the field from the fixture if the test asserts on it.
    *Old backups are unaffected: `backupJson` sets `ignoreUnknownKeys = true`, so a
    backup that still contains `"upiId"` decodes fine and the value is ignored.*

**Step 4b — the UPI payment-request flow**
11. Delete `data/share/UpiQr.kt` and the `UpiRequest` object in `domain/loan/SplitRules.kt`.
12. `presentation/split/SplitBill.kt`: remove the "request payment" button, the
    "ask for UPI id" dialog, the `upi://pay` share intent and the `askUpiFor` state.
13. `presentation/split/SplitBillViewModel.kt`: remove `upiId`, `setUpiId` and the
    `askUpiFor` plumbing.
14. `SettingsScreen.kt` / `SettingsViewModel.kt`: remove the "your UPI id" row,
    its dialog and `setUpiId`.
15. The strings for all of the above, in every locale.

**Step 5 — strings**
11. Remove the three `settings_contacts_*` strings from `strings_settings.xml` **and every locale**
    (a leftover key in a translation is a lint `UnusedResources` finding).
12. Rename `import_statement_result_by_reference` to a generic "By reference" in
    `strings_rules.xml` and every locale.

**Step 6 — the dead code**
13. Delete `app/src/main/java/com/pennywiseai/tracker/core/CompiledPatterns.kt`.
14. In `parser-core/.../core/CompiledPatterns.kt`, delete `UPI_REF`, `VPA_WITH_NAME`,
    `VPA_PATTERN`, `UPI_REF_NO`. Leave `UPI_SUFFIX` (used by `BankParser`).

**Step 7 — the docs**
15. `PRIVACY.md`: delete the "### Contacts (`READ_CONTACTS`, off by default)" section and the
    "## Contact" section; make sure the permission list no longer mentions contacts.
16. `README.md`, `GLOSSARY.md`, `docs/PRIVACY.md`, the store listing
    (`fastlane/metadata/android/en-US/full_description.txt`): remove any UPI/contacts claim.
17. `docs/FORK_NOTES.md`: a dated entry recording what was removed and why.

**Step 8 — cleanup of the user's stored preferences (optional but tidy)**
18. The two DataStore keys (`use_contacts_for_vpa`, `upi_id`) stay on the device as dead
    values. They are harmless. If wanted, purge them once in `UserPreferencesRepository`
    on first run after the upgrade. *Optional — skip if it complicates the diff.*

---

## 5. Risks

| Risk | Likelihood | Mitigation |
|---|---|---|
| A leftover `LocalMerchantDisplay` reference breaks the build | low | The compiler finds every one; step 6 touches exactly four files, all listed |
| Old backups fail to import | none | `ignoreUnknownKeys = true` — verified in `BackupSerializers.kt` |
| The `BackupSchemaGuardTest` fails on the removed snapshot field | low | The field is being *removed*, not added; the guard is about missing defaults |
| A locale still has the removed strings | medium | Grep every `values*` directory in step 11 |
| Upstream merge friction | low | 12 of the 14 steps touch app-module files upstream rarely changes; the two `CompiledPatterns` edits are in shared code and are one-line deletions each |
| Users lose a setting | none | The only settings lost are the contacts toggle and a UPI id that no longer has a use |
| A merchant that used to show a contact name now shows the VPA | certain | Accepted: the merchant is displayed exactly as the bank sent it |

---

## 6. Verification

```bash
# 1. Nothing reads the address book any more
#    (SplitBill.kt is the one expected hit: it reads the single contact the
#    user picked through the system picker, which needs no permission)
git grep -n -I -E "ContactsContract|ContactsResolver|MerchantContactDisplay" -- app/src parser-core/src shared/src
#    → only app/src/main/java/com/pennywiseai/tracker/presentation/split/SplitBill.kt

# 2. The permission is gone from the source
git grep -n "READ_CONTACTS" -- app/src parser-core/src shared/src
#    → only the comment in SplitBill.kt that explains why it needs no permission

# 3. No UPI/VPA lookup is left in the app
#    (GPayPdfParser.kt is the one expected hit: it reads a UPI *reference* out
#    of a Google Pay statement PDF — that is the statement-import feature)
git grep -n -I -i -E "useContactsForVpa|upiId|settings_contacts|VPA_PATTERN|VPA_WITH_NAME|UPI_REF|split_upi|settings_upi|UpiRequest" -- app/src parser-core/src shared/src
#    → only app/src/main/java/com/pennywiseai/tracker/data/statement/GPayPdfParser.kt

# 4. The build and the tests
./gradlew :parser-core:jvmTest :app:testDebugUnitTest
#    → green

# 5. The shipped APK does not ask for contacts
aapt2 dump badging app/build/outputs/apk/release/*.apk | grep -i contacts
#    → no output

# 6. On the device
adb shell dumpsys package com.mudal.tracker | grep -i READ_CONTACTS
#    → not listed
```

Then install the build and confirm the app still works: onboarding, a real
Sampath SMS becomes a transaction, and the merchant shows the bank's own text.

---

## 7. Acceptance criteria

- [ ] `READ_CONTACTS` is not in the manifest, the merged manifest, or the APK.
- [ ] No `ContactsContract` usage anywhere in the source.
- [ ] The contacts toggle and the UPI id are gone from Settings and from backups.
- [ ] The split-bill contact picker still works (it must not need a permission).
- [ ] `:parser-core:jvmTest` and `:app:testDebugUnitTest` are green.
- [ ] The docs no longer promise or mention a contacts feature.
- [ ] A real SMS still produces one correct transaction on the device.
- [ ] `docs/FORK_NOTES.md` records the change and the date.

---

## 8. Follow-up (not part of this change)

Once this lands, the app's permission list is `READ_SMS`, `RECEIVE_SMS`,
`POST_NOTIFICATIONS`, `INTERNET`, `USE_BIOMETRIC` (+ `USE_FINGERPRINT`).
`RECEIVE_BOOT_COMPLETED`, `WAKE_LOCK`, `ACCESS_NETWORK_STATE` and
`FOREGROUND_SERVICE` come from WorkManager and cannot be removed.

That is the footprint the store listing and the F-Droid submission should
describe — and it is a much smaller one to defend.
