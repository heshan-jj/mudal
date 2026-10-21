# Fork plan: Sri Lankan SMS expense tracker (fork of PennyWise AI)

**How to use:** save this as `docs/FORK_PLAN.md` in the fork. Give the agent one phase at a time: "Do Phase N from docs/FORK_PLAN.md." Each phase ends with a stop-and-report. Do not let it run ahead.

---

# Section A: Standing context

## Goal
Take `sarim2000/pennywiseai-tracker` (Kotlin, Jetpack Compose, Room, Hilt, KMP parser-core, AGPL-3.0-only) and turn it into a privacy-first Android money manager for Sri Lanka: Sri Lankan bank parsers, LKR defaults, Sinhala and Tamil, no paywalls, no dependence on upstream servers, encrypted DB, plus the few features it lacks (budget rollover and alerts, forecasting).

## Findings from the audit (treat as claims to verify, not facts)
- Build: Gradle 9.7.1, AGP 9.2.1, Kotlin 2.3.x, compileSdk 37, targetSdk 36, minSdk 26, JDK 21+. Needs SDK Platform 37 and a recent Android Studio. The audit did NOT prove a clean build; Phase 0 must.
- Already has: splits, recurring entries, loan accounts, account archiving, custom budget anchor day, subscriptions, net worth, app lock, backups, on-device LLM, multi-currency, 4 Sri Lankan parsers (Sampath, NTB, NSB, NDB), ~100 app test files, migration tests, CI.
- Missing: budget rollover and alert notifications, forecasting, DB encryption (SQLCipher).
- Pro-gated upstream: rules cap, statement imports, CSV export rows, recurring templates, goals, account merge, webhooks, some analytics groupings. The `fdroid` flavor sets `isPro = true`.
- Network touchpoints upstream: Dodo Payments licensing, a Cloudflare Worker license URL, exchange rates (open.er-api.com), model download from upstream's own R2 bucket, "report SMS" flow that sends SMS body + sender to pennywise.zynth.dev.
- `TRADEMARKS.md` reserves the PennyWise name, logo and banner. Forks must rebrand.
- Cashiro is a downstream fork of PennyWise that diverged around v2.15.43 and then fell far behind (834 upstream commits since). Lesson: keep our diff small so we can keep pulling upstream.

## Non-negotiable rules
1. **Keep the diff small.** Add new files instead of editing existing ones where possible. Do NOT rename Kotlin packages. Change `applicationId`, app name, icons and strings only. Leave `iosApp` and KMP structure alone (just don't build iOS).
2. **Upstream sync:** add an `upstream` remote. Keep our work on `main` via small commits. Document the sync procedure in `docs/FORK_NOTES.md` and test it once (merge or rebase upstream into a scratch branch, report conflicts).
3. **Never invent bank SMS formats.** Every parser is written from real, masked samples supplied by the owner. No samples means no parser; list the bank in `docs/lk-banks-needing-samples.md` instead.
4. **Privacy:** no raw SMS leaves the device, ever. Never log SMS bodies. No analytics.
5. **Licensing:** keep LICENSE (AGPL-3.0-only) and all original copyright notices. Add `NOTICE`/`ATTRIBUTION.md` crediting PennyWise (and Cashiro for any ported code). Mark our changes. Add an in-app "Source code and licenses" screen linking our repo.
6. **No third-party brand assets we don't own.** Do not add bank logos for new banks. Use initials/colour chips.
7. **Prove it by running it.** Claims like "builds" or "tests pass" mean the command ran and the output is quoted in the report.
8. Money stays in whatever integer/decimal type upstream uses. Don't change money handling.
9. Every Room schema change ships with a migration and a migration test, following the existing pattern.
10. No secrets in git. Release signing comes from `local.properties`/CI secrets only.

## Working agreement
- Work autonomously inside the phase. Record decisions in `docs/FORK_NOTES.md` (short, dated).
- Conventional commits; tag `phase-N-done`.
- Before reporting done: affected Gradle tests green, debug app builds and launches on the emulator, `git status` clean.
- End each phase with a report: what changed, commands run with results, decisions, anything needing the owner (real SMS, real-device test), risks.
- Do not start the next phase.

---

# Phase 0: Fork, verify the baseline

No code changes.

1. Fork on GitHub, clone, add `upstream` remote.
2. Environment: Android Studio current stable, Gradle JDK = Studio's JBR (21+), install SDK Platforms 36 and 37 and build-tools. If the installed Studio is too old for AGP 9.2.1, report the minimum version needed.
3. Build and run, quoting real output:
   - `./gradlew :parser-core:test`
   - `./gradlew :app:testStandardDebugUnitTest` (and the fdroid equivalent if different)
   - `./gradlew :app:assembleFdroidDebug` and `assembleStandardDebug`
   - install and launch on the emulator; inject a test SMS with `adb emu sms send` using a sender an existing parser accepts (e.g. a Sampath sender) and confirm a transaction appears.
4. Record in `docs/FORK_NOTES.md`: working versions, exact commands, test counts, any failures or flaky tests, build time.
5. Inventory the 4 Sri Lankan parsers: file paths, sender IDs they accept, which patterns exist, fixture coverage.
6. Read `TRADEMARKS.md`, `LICENSE`, and list exactly what must change for a compliant fork.
7. Locate and list every network call, every Pro gate, and the unrecognised-SMS candidate logic (how a financial SMS from an unknown sender is detected, and whether it recognises "Rs." / "Rs " / "LKR" amounts).

Acceptance: green baseline proven, or an exact error and the minimal fix. Owner confirms the app runs on the Pixel.

---

# Phase 1: Identity, de-commercialise, privacy cleanup (do before any real user data exists)

1. **Rebrand:** new `applicationId`, app name, original launcher icon and splash (placeholder is fine, original), strings, README, remove upstream Discord/website links and banner. Do not rename Kotlin packages. Changing `applicationId` later would break installs and backups, so do it now.
2. **Remove billing:** ship a single flavor derived from the `fdroid` flavor. Delete Dodo Payments code, license activation (`LICENSE_MOVE_URL`, `RSA_PUBLIC_KEY`), upgrade sheets and Pro UI. Remove `FreeTierLimits` and `EntitlementGate` usage so no limit exists anywhere (rules, imports, CSV rows, recurring templates, goals, merge, webhooks, analytics groupings, profiles). Verify with grep that no limit constants or billing dependencies remain, and that the merged manifest has no billing permissions.
3. **Upstream-server dependencies:**
   - Replace the "report unrecognised SMS" flow (sends SMS body to pennywise.zynth.dev) with a local flow: show a masked preview (digit runs, names, balances masked, user-editable) and copy it to the clipboard or open a pre-filled issue on OUR repo. Sender and date only unless the user explicitly confirms the masked body.
   - Model download: do not use upstream's R2 bucket. Find an official host for the same `.litertlm` model (e.g. Hugging Face litert-community) with SHA-256 verification, or support importing a model file via the file picker. Document the choice.
   - Exchange rates: keep, but make it clearly optional and off until the user enables multi-currency. Note the endpoint in PRIVACY.md.
4. Add `docs/PRIVACY.md` listing every network endpoint that remains, when it is used, and what is sent.
5. Add `ATTRIBUTION.md`, in-app licenses screen, update README.

Acceptance: app installs side by side with the original under the new applicationId; no reference to Pro/billing/Dodo remains (grep evidence); network audit lists only the documented endpoints; tests still green.

---

# Phase 2: Database encryption (before first release so no plaintext migration is ever needed)

1. Add SQLCipher for Android (`net.zetetic:sqlcipher-android`) and open Room with `SupportOpenHelperFactory`.
2. Generate a random passphrase on first run, store it wrapped by Android Keystore. Handle key loss gracefully: clear error screen, never a crash loop, offer restore from backup.
3. Make sure the KMP shared/parser modules are unaffected. Check WorkManager workers and widgets also open the DB through the same encrypted path.
4. Set `allowBackup=false` (or explicit encrypted backup rules). Offer FLAG_SECURE (hide in recents) as a setting.
5. Re-run migration tests against the encrypted setup. Add tests: wrong key fails cleanly; backup JSON export/restore round-trip still works.
6. Verify on a real device that the database file is not readable as plain SQLite (pull it with adb and try to open it).

Acceptance: encrypted DB proven, all existing DB tests pass, restore from backup works.

---

# Phase 3: Sri Lankan bank parsers (needs real samples from the owner)

**Owner prerequisite:** collect real transaction SMS from your own phone per bank, with account numbers, names and balances masked (for example `XXXX1234`, `Rs. 1,234.50` is fine, balances may be replaced with a fake number of the same format). Put them in `docs/lk-samples/<bank>.txt`, one SMS per line block, with the real sender ID noted. Include: debit card/POS, ATM, CEFT/transfers out and in, salary/credit, credit card spend, declined/failed, balance enquiry, OTP and promo messages (as negatives).

The agent:
1. Audit the 4 existing parsers (Sampath, NTB, NSB, NDB) against the owner's real samples; fix gaps and add fixtures.
2. For each bank with real samples, add a parser following the smallest existing pattern (`BankParser` base, `getBankName`, `getCurrency` = LKR, `canHandle(sender)` from the real sender IDs, amount/type/merchant/balance/account-last4 extraction). Banks to attempt, in priority order: Commercial Bank, HNB, Bank of Ceylon, People's Bank, Seylan, DFCC, Pan Asia, plus wallets (eZ Cash, mCash) if samples exist.
3. Handle Sri Lankan formats: `Rs.` / `Rs ` / `LKR` prefixes, comma thousands, two-decimal amounts, date formats actually seen in samples, masked card numbers, multi-line messages, trailing promo or OTP text.
4. Fixtures use the owner's masked real samples plus negative cases. Use the existing `ParserTestUtils` dynamic tests.
5. Register in `BankParserFactory` (note dispatch is in list order via `canHandle`; avoid sender-matching overlaps and add a test that no sender matches two parsers).
6. Update the unrecognised-SMS candidate heuristic so `Rs.`/`Rs `/`LKR` amounts from unknown alphanumeric senders land in the Unrecognised queue (verify with tests).
7. Banks without samples go in `docs/lk-banks-needing-samples.md` with the exact sample types needed. Do not implement them.
8. Update the supported-banks list/script. Use initials/colour chips instead of logos.

Acceptance: `:parser-core:test` green; every new parser has fixtures from real masked samples; owner tests on the real device with live SMS.

Optional: offer the Sri Lankan parsers upstream as PRs to PennyWise (smaller fork, shared maintenance).

---

# Phase 4: Sri Lankan defaults and localisation

1. Default currency LKR, number and date formats checked (e.g. `Rs. 1,234.50`).
2. Default categories and merchant rules for Sri Lanka as an editable data file: supermarkets (Keells, Cargills Food City, Arpico, Laugfs), fuel (Ceypetco, IOC), telco and internet (Dialog, Mobitel, SLT, Hutch, Airtel), utilities (CEB, LECO, NWSDB), transport (PickMe, Uber), shopping (Daraz), streaming (Netflix, Spotify, YouTube). Use the existing rules mechanism. Add eval fixtures for messy merchant strings and report accuracy.
3. Sinhala and Tamil string resources: first draft all strings, mark as `needs-native-review` in `docs/i18n.md`. Verify fonts and text rendering, layout with long strings, and that dates/numbers still format correctly.
4. Onboarding text explains SMS permission and privacy plainly.

Acceptance: fresh install defaults to LKR with sensible categories; language switch works for en/si/ta; tests for rules.

---

# Phase 5: Feature gaps

Do each as its own commit with tests and migrations.

1. **Budget rollover and alerts:** optional rollover of unspent budget (stored per period so history is stable, use the existing snapshot entity if it fits); local notifications at 80% and 100% once per period per budget via WorkManager; `POST_NOTIFICATIONS` requested at the right moment; no exact-alarm permission.
2. **Cash-flow forecast:** deterministic, no ML. Project the next 30 days from recurring templates, subscriptions and average daily discretionary spend. Show "will I stay within budget?" and a low-balance warning against a user-set floor per account. Pure Kotlin logic with unit tests (edge cases: month ends, anchor-day budgets, accounts with no history).
3. **Anomaly flags:** unusually large amount for a merchant, first-time merchant with large amount, possible duplicate (same merchant and amount within 10 minutes), foreign-currency charge. Suggestions only, dismissible, never auto-edit data.
4. **Committed amounts:** show upcoming subscription and recurring charges separately from "spent" on budget screens if not already present.
5. Verify CSV export, rules, goals, imports work without limits after Phase 1.

Acceptance: new tests green, migration tests green, manual flows work on emulator.

---

# Phase 6: Network minimisation (optional hardening)

1. Add an `offline` build variant whose merged release manifest has no `INTERNET` permission: exchange-rate fetch, model download and webhooks disabled (model import via file picker instead). Add a CI step that fails if that variant's merged manifest contains INTERNET.
2. Decide on cloud backup: skip Google Drive (needs OAuth app verification). Optionally port Cashiro's WebDAV provider (AGPL-compatible, attribute it) into the connected variant only. Local scheduled backup stays the default.

---

# Phase 7: Release

1. CI: tests, debug build, lint, offline-manifest check. Fix anything flaky.
2. Signed release APK via CI secrets, GitHub Releases workflow, F-Droid metadata (unique applicationId, fastlane metadata folder, reproducibility notes). Do not target Play SMS policy yet.
3. Docs: README with screenshots, `PRIVACY.md`, `CONTRIBUTING.md`, "How to add a Sri Lankan bank" guide with a worked example and masking instructions, issue templates ("Add or fix a bank parser" asking for masked samples).
4. Security pass: manifest exported components, no sensitive logging (grep test), dependency audit, DB encryption verified again on the release build.
5. Run the upstream sync dry run once more and record the result.

Acceptance: fresh clone to running app following the README; release APK passes a manual smoke test on a real device.

---

# Appendix: Android Studio notes

- Open the repo root in Android Studio. Settings > Build Tools > Gradle > Gradle JDK = the bundled JBR (21+).
- Install SDK Platform 37 and 36 from SDK Manager.
- Emulator SMS: `adb emu sms send <sender> "<text>"`. Emulator senders may reject alphanumeric IDs; test parsers with unit tests first, then on a real device with live SMS.
- Real device: enable Developer options and USB debugging, `adb devices`, run the app from Studio.