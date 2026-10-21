<!-- Mudal fork: new file in Mudal, a fork of PennyWise AI (AGPL-3.0-only), 2026-10-21 - see NOTICE. -->
# Fork plan: Sri Lankan SMS expense tracker (fork of PennyWise AI)

**Revision 3** — trimmed to a shippable v1.0 scope, with the Phase 2 gaps
filled in (plaintext→encrypted upgrade, key-loss handling, backup rules), the
optional phases demoted, and Phase 4 added for SMS-aware recurring transactions
and subscriptions. What changed and why is listed at the end.

**How to use:** this file lives at `docs/FORK_PLAN.md` in the fork. Give the agent
one phase at a time: "Do Phase N from docs/FORK_PLAN.md." Each phase ends with a
stop-and-report. Do not let it run ahead.

---

## Release scope (read this first)

**v1.0 = Phase 1 + Phase 2 + Phase 3 (Sampath + Commercial Bank only) + Phase 4 (recurring transactions & subscriptions) + Phase 8 (release).**

Everything else in this document is **post-v1.0** and optional. If time is
short, stop at v1.0 and ship. A working app for two banks beats a complete app
for none.

| Phase | Scope | Status |
|---|---|---|
| 0 | Fork + baseline verification | done — `phase-0-done` |
| 1 | Identity, de-commercialise, privacy cleanup | done — `phase-1-done` (`8c26effa`) |
| 2 | Database encryption (SQLCipher) | **in progress** — see outstanding items below |
| 3 | Parsers: Sampath + Commercial Bank | done — parsers implemented, audited against real samples, tests green |
| 4 | Recurring transactions & subscriptions (SMS-aware) | **must-have for v1.0** |
| 5 | Sinhala localisation | optional, post-v1.0 |
| 6 | Budget rollover + alerts | optional, post-v1.0 |
| 7 | Offline build variant | optional, post-v1.0 |
| 8 | Release (F-Droid + GitHub Releases) | not started |

**Next phase: finish Phase 2.** Outstanding items are listed in the Phase 2
section — they are the known blockers, not a wish list.

---

# Section A: Standing context

## Goal
Take `sarim2000/pennywiseai-tracker` (Kotlin, Jetpack Compose, Room, Hilt, KMP parser-core, AGPL-3.0-only) and turn it into a privacy-first Android money manager for Sri Lanka: Sri Lankan bank parsers, LKR defaults, Sinhala and Tamil, no paywalls, no dependence on upstream servers, encrypted DB, plus the few features it lacks (budget rollover and alerts, forecasting).

## Findings from the audit (verified in Phase 0 — treat as facts now)
- Build: Gradle 9.7.1, AGP 9.2.1, Kotlin 2.3.0, compileSdk 37, targetSdk 36, minSdk 26, JDK 21+. Needs SDK Platform 37 and a recent Android Studio.
- Already has: splits, recurring entries, loan accounts, account archiving, custom budget anchor day, subscriptions, net worth, app lock, backups, on-device LLM, multi-currency, 4 Sri Lankan parsers (Sampath, NTB, NSB, NDB), ~100 app test files, migration tests, CI.
- Missing: budget rollover and alert notifications, forecasting, DB encryption (SQLCipher).
- Pro-gated upstream: rules cap, statement imports, CSV export rows, recurring templates, goals, account merge, webhooks, some analytics groupings. The `fdroid` flavor sets `isPro = true`.
- Network touchpoints upstream: Dodo Payments licensing, a Cloudflare Worker license URL, exchange rates (open.er-api.com), model download from upstream's own R2 bucket, "report SMS" flow that sends SMS body + sender to pennywise.zynth.dev.
- `TRADEMARKS.md` reserves the PennyWise name, logo and banner. Forks must rebrand.
- Cashiro is a downstream fork of PennyWise that diverged around v2.15.43 and then fell far behind (834 upstream commits since). Lesson: keep our diff small so we can keep pulling upstream.
- **Recurring transactions and subscriptions already exist** upstream (`RecurringTransactionsScreen`, `SubscriptionsScreen`) and are ungated after Phase 1. They were built for a manual-entry workflow, not an SMS-first one, and today they double-count charges and miss price changes — see Phase 4.

## Non-negotiable rules
1. **Keep the diff small.** Add new files instead of editing existing ones where possible. Do NOT rename Kotlin packages. Change `applicationId`, app name, icons and strings only. Leave `iosApp` and KMP structure alone (just don't build iOS).
2. **Upstream sync:** add an `upstream` remote. Keep our work on `main` via small commits. Document the sync procedure in `docs/FORK_NOTES.md` and test it once (merge or rebase upstream into a scratch branch, report conflicts). **Sync before every phase that touches the Room schema** — each migration we add makes future merges harder.
3. **Never invent bank SMS formats.** Every parser is written from real, masked samples supplied by the owner. No samples means no parser; list the bank in `docs/lk-banks-needing-samples.md` instead.
4. **Privacy:** no raw SMS leaves the device, ever. Never log SMS bodies. No analytics.
5. **Licensing:** keep LICENSE (AGPL-3.0-only) and all original copyright notices. Add `NOTICE`/`ATTRIBUTION.md` crediting PennyWise (and Cashiro for any ported code). Mark our changes. Add an in-app "Source code and licenses" screen linking our repo.
6. **No third-party brand assets we don't own.** Do not add bank logos for new banks. Use initials/colour chips.
7. **Prove it by running it.** Claims like "builds" or "tests pass" mean the command ran and the output is quoted in the report. The gate is `./init.sh` (see `CLAUDE.md`), not ad-hoc gradle invocations.
8. Money stays in whatever integer/decimal type upstream uses. Don't change money handling.
9. Every Room schema change ships with a migration and a migration test, following the existing pattern.
10. No secrets in git. Release signing comes from `local.properties`/CI secrets only.
11. **Backups and exports are part of the privacy boundary.** No plaintext transaction file may be written to shared storage without an explicit user action, and every export format must be documented in `PRIVACY.md`.
12. **Record deviations.** If the agent does something other than what this plan says, write it down in `docs/FORK_NOTES.md` (short, dated) before reporting the phase done. A silent deviation is a bug.
13. **One real-world event = one transaction.** A charge that arrives as an SMS must not also be created by a recurring template, and a transfer between the user's own accounts must not be counted as income and spending at the same time. Reconciliation lives in the app layer, not in the parser — the parser only reports what the bank said.

## Working agreement
- Work autonomously inside the phase. Record decisions in `docs/FORK_NOTES.md` (short, dated).
- Conventional commits; tag `phase-N-done`.
- Before reporting done: affected Gradle tests green, debug app builds and launches on the emulator, `git status` clean.
- End each phase with a report: what changed, commands run with results, decisions, anything needing the owner (real SMS, real-device test), risks.
- Do not start the next phase.

---

# Phase 0: Fork, verify the baseline — DONE

No code changes.

1. Fork on GitHub, clone, add `upstream` remote.
2. Environment: Android Studio current stable, Gradle JDK = Studio's JBR (21+), install SDK Platforms 36 and 37 and build-tools.
3. Build and run, quoting real output:
   - `./gradlew :parser-core:test` — **1,924 passed, 0 failed**
   - `./gradlew :app:testStandardDebugUnitTest` — **705 run, 704 passed, 1 failed** (documented mock tie-break issue, not patched per "no code changes")
   - `./gradlew :app:assembleFdroidDebug` and `assembleStandardDebug`
   - install and launch on the emulator; inject a test SMS with `adb emu sms send` using a sender an existing parser accepts and confirm a transaction appears.
4. Record in `docs/FORK_NOTES.md`: working versions, exact commands, test counts, any failures or flaky tests, build time.
5. Inventory the 4 Sri Lankan parsers: file paths, sender IDs they accept, which patterns exist, fixture coverage.
6. Read `TRADEMARKS.md`, `LICENSE`, and list exactly what must change for a compliant fork.
7. Locate and list every network call, every Pro gate, and the unrecognised-SMS candidate logic.

Acceptance: green baseline proven, or an exact error and the minimal fix. Owner confirms the app runs on the Pixel.

---

# Phase 1: Identity, de-commercialise, privacy cleanup — DONE

1. **Rebrand:** new `applicationId`, app name, original launcher icon and splash (placeholder is fine, original), strings, README, remove upstream Discord/website links and banner. Do not rename Kotlin packages.
2. **Remove billing:** ship a single flavor derived from the `fdroid` flavor. Delete Dodo Payments code, license activation (`LICENSE_MOVE_URL`, `RSA_PUBLIC_KEY`), upgrade sheets and Pro UI. Remove `FreeTierLimits` and `EntitlementGate` usage so no limit exists anywhere (rules, imports, CSV rows, recurring templates, goals, merge, webhooks, analytics groupings, profiles). Verify with grep that no limit constants or billing dependencies remain, and that the merged manifest has no billing permissions.
3. **Upstream-server dependencies:**
   - Replace the "report unrecognised SMS" flow (sends SMS body to pennywise.zynth.dev) with a local flow: show a masked preview (digit runs, names, balances masked, user-editable) and copy it to the clipboard or open a pre-filled issue on OUR repo. Sender and date only unless the user explicitly confirms the masked body.
   - Model download: the on-device LiteRT-LM model and its upstream R2 host were removed on 2026-10-22. The optional AI assistant now calls the Google Gemini API with a user-supplied key, so the app no longer depends on any upstream-hosted file.
   - Exchange rates: keep, but make it clearly optional and off until the user enables multi-currency. Note the endpoint in PRIVACY.md.
4. Add `docs/PRIVACY.md` listing every network endpoint that remains, when it is used, and what is sent.
5. Add `ATTRIBUTION.md`, in-app licenses screen, update README.

Acceptance: app installs side by side with the original under the new applicationId; no reference to Pro/billing/Dodo remains (grep evidence); network audit lists only the documented endpoints; tests still green.

### Phase 1 leftovers (still open)

- [x] **Model host** — resolved on 2026-10-22: the on-device model and its upstream R2 download were removed outright. The optional AI assistant uses the user's own Gemini API key, so there is no longer any dependency on upstream-hosted files.
- [ ] **Backup encryption.** Manual export writes plaintext JSON, and the scheduled folder backup writes it unattended. Rule 11 says no plaintext transaction file may reach shared storage without an explicit user action — the scheduled backup needs encrypting with the device key, or switching off.

---

# Phase 2: Database encryption — IN PROGRESS

**Why before first release:** a plaintext→encrypted conversion path is expensive
and easy to get wrong. Do it before anyone but the owner has real data.

**Owner prerequisite (easy to miss):** the app is already installed on the
owner's Pixel with a **plaintext** database from Phase 0/1. Phase 2 must handle
that upgrade, not just a fresh install. "Before first release" protects other
users; it does not protect the owner's device.

1. Add SQLCipher for Android (`net.zetetic:sqlcipher-android`) and open Room with `SupportOpenHelperFactory`.
2. Generate a random passphrase on first run, store it wrapped by Android Keystore (AES-256-GCM). **No user-authentication requirement** on the wrapping key — background SMS parsing and widgets must keep working.
3. **Plaintext → encrypted upgrade path (required, was missing in revision 1).** An existing plaintext database must be detected and converted, not silently abandoned:
   - detect by the file header (`SQLite format 3` = plaintext),
   - open it unencrypted, re-encrypt into a new file with the wrapped key,
   - swap atomically, only then delete the plaintext file.
   - Test it: create a plaintext fixture DB, run the conversion, assert the rows are readable afterwards and the file is no longer plaintext SQLite.
4. **Key loss: never a crash loop, never a silent empty app.** Show the error screen and offer restore-from-backup or reset. **Do not fall back to an in-memory database** — it turns every failure into an app that looks empty and silently discards parsed SMS, widgets and backups.
5. **Reset/restore must close the database before deleting it.** Deleting the file while Room still holds it open sends later writes to an unlinked inode. Close → delete → rebuild the instance (or restart the process) → only then import a backup.
6. **Backup rules are explicit.** Set `allowBackup=false`, or write explicit `data_extraction_rules.xml` exclusions for `databases/` and the wrapped-key preferences. A Keystore key never leaves the device, so a synced encrypted database cannot be decrypted after restore — backing it up is pointless and lands the user on the error screen.
7. **Backup threat model: decide and document.** Minimum bar — no plaintext transaction file is written to shared storage without an explicit user action; manual export stays plaintext JSON (documented in `PRIVACY.md`); scheduled folder backup must be encrypted with the device key or disabled. Passphrase-encrypted export is a post-v1.0 item.
8. Make sure the KMP `shared`/`parser` modules are unaffected. Check WorkManager workers and widgets open the DB through the same encrypted path.
9. Re-run migration tests against the encrypted setup. Add tests: wrong key fails cleanly; backup JSON export/restore round-trip still works; plaintext→encrypted conversion works.
10. Offer FLAG_SECURE (hide in recents) as a setting.
11. Verify on a real device: pull the DB with `adb` and confirm it is not readable as plain SQLite; confirm the owner's pre-existing data survived the upgrade.

### Outstanding items (finish these before reporting Phase 2 done)

- [ ] Plaintext→encrypted upgrade path + test (item 3) — **the owner's device hits this on first launch**
- [ ] Remove the in-memory fallback; fail loudly into the error screen (item 4)
- [ ] Close-before-delete in `resetDatabaseAndKey()`; rebuild the instance after (item 5)
- [x] `allowBackup=false` — already set in `AndroidManifest.xml`; `data_extraction_rules.xml` / `backup_rules.xml` are inert while it is (item 6)
- [ ] Decide and document the backup-encryption story (item 7)
- [ ] Delete the now-dead `PennyWiseDatabase.getInstance()` path — it builds a second Room instance with a separate key manager, so key-loss detected there never reaches the UI
- [ ] `System.loadLibrary("sqlcipher")` must not be swallowed by a bare `catch (Throwable)` — log and rethrow, so "library missing" is not reported as "file is not a database"
- [ ] `./init.sh app` green with quoted output (the gate, not ad-hoc gradle runs)

Acceptance: encrypted DB proven, the owner's existing data survived the upgrade, all existing DB tests pass, restore from backup works, `./init.sh app` green with quoted output.

---

# Phase 3: Sri Lankan bank parsers (needs real samples from the owner)

**Scope for v1.0: Sampath Bank and Commercial Bank of Ceylon only.** Both are
already sampled — see `docs/lk-samples/` and `docs/lk-samples/README.md`. Other
banks are one at a time, as samples arrive. The bottleneck is the owner's sample
collection, not the code, so do not treat the list below as a backlog.

**Owner prerequisite per bank:** collect real transaction SMS from your own phone, with account numbers, names and balances masked (for example `XXXX1234`, `Rs. 1,234.50` is fine, balances may be replaced with a fake number of the same format). Put them in `docs/lk-samples/<bank>.txt`, one SMS per line block, with the real sender ID noted. Include: debit card/POS, ATM, CEFT/transfers out and in, salary/credit, credit card spend, declined/failed, balance enquiry, OTP and promo messages (as negatives).

**Cost per bank for the owner:** ~30 minutes to collect and mask. Agent work: ~2 hours. Never invent a format to avoid the collection (rule 3).

**Owner still to confirm for the two sampled banks:** the real sender IDs (Sampath assumed `SAMPATHTXN`; Commercial Bank unknown), and whether the two Commercial Bank "Credit for Rs." messages are income or something else.

The agent:
1. Audit the Sampath parser against the owner's real samples; fix gaps and add fixtures. Known gaps from the samples: `via ATM at <loc> CASH SET`, `via POS at <merchant> <ref>`, `for TFR <name> -<ref>`, `for TRRR/…`, `for CEFTLV-01-…`, `for VSRPMT-<ref>`, `for Interest: …`, `for eCom/REV <merchant>` (reversal credits), amounts written `LKR .56` with no leading zero, and merchant strings where the trailing `" - <ref>"` strip mangles `PERERA - SONS BAKERS ( MA 51001413`. **Note, and do not "fix" in the parser:** a `"credited to AC **NNNN for <note>"` message is syntactically a credit, but the note is often the user's own transfer label (`Netflix`, `Seylan card`, `Expenses`, `OWNER-Cash`), so it is often an internal move or a payment out, not income. The parser reports what the bank said; Phase 4 item 6 reconciles it.
2. Add a Commercial Bank parser following the smallest existing pattern (`BankParser` base, `getBankName`, `getCurrency` = LKR, `canHandle(sender)` from the real sender ID, amount/type/merchant/balance/account-last4 extraction). Three message shapes to cover: card purchase (`Dear Cardholder, Purchase at … has been authorised on your debit card ending #4101.`), ATM withdrawal (`Withdrawal at … from card ending #4101.`), and branch credit (`Credit for Rs. … at <hh:mm> at DIGITAL BANKING DIVISION` — no date in the body). Currency prefixes differ (`LKR` vs `Rs.`), dates are `dd/MM/yy hh:mm AM/PM`, and "authorised" still means a spend.
3. Handle Sri Lankan formats: `Rs.` / `Rs ` / `LKR` prefixes, comma thousands, two-decimal amounts, date formats actually seen in samples, masked card numbers, multi-line messages, trailing promo or OTP text.
4. Fixtures use the owner's masked real samples plus negative cases. Use the existing `ParserTestUtils` dynamic tests.
5. Register in `BankParserFactory` (note dispatch is in list order via `canHandle`; avoid sender-matching overlaps and add a test that no sender matches two parsers).
6. Update the unrecognised-SMS candidate heuristic so `Rs.`/`Rs `/`LKR` amounts from unknown alphanumeric senders land in the Unrecognised queue (verify with tests).
7. Banks without samples go in `docs/lk-banks-needing-samples.md` with the exact sample types needed. Do not implement them.
8. Update the supported-banks list/script. Use initials/colour chips instead of logos.
9. Keep `docs/lk-samples/README.md` in sync with the live scope (which banks are in, what is still needed).

Acceptance: `:parser-core:test` green; every new parser has fixtures from real masked samples; owner tests on the real device with live SMS.

Optional: offer the Sri Lankan parsers upstream as PRs to PennyWise (smaller fork, shared maintenance).

---

# Phase 4: Recurring transactions & subscriptions (SMS-aware) — MUST-HAVE for v1.0

**Do not build this from scratch.** Both features already exist upstream and are ungated after Phase 1:
- **Subscriptions** — `SubscriptionsScreen` (`MainScreen.kt`), `SubscriptionEntity`/`SubscriptionDao`/`SubscriptionRepository`, `MarkSubscriptionPaidUseCase`, `SubscriptionTabContent`. The SMS path already matches a parsed charge to a subscription (`SmsTransactionProcessor` → `matchTransactionToSubscription`).
- **Recurring templates** — `RecurringTransactionsScreen` (`PennyWiseNavHost.kt`), `RecurringTransactionEntity`/`Dao`/`Repository`, `RecurringTransactionWorker` → `RecurringTransactionRepository.materializeDue()`.

**What is missing is the part that matters for an SMS-first tracker: the bank already tells us when a recurring charge happens, so the app must not book it twice, and it must keep recognising it when the price changes.**

1. **Recurring templates must be matched by the SMS path, not only subscriptions.** Today `SmsTransactionProcessor` checks `matchTransactionToSubscription` and never looks at templates. So a template for "CEB electricity, LKR 4,000, due on the 5th" plus the bank's SMS for that same charge books **two** transactions in one cycle. Fix: match parsed charges against active templates as well.
2. **The bank's charge wins.** When an SMS matches a template or subscription for a cycle, that SMS transaction is the real one — real amount, real timestamp, real merchant. The template must then **not** materialise a second transaction for that cycle: advance its next due date and skip that cycle. The template's existing deterministic hash (`RECURRING_{id}_{amount}_{merchant}_{dueDate}`) only stops the template double-posting *itself*; it does nothing about the SMS.
3. **Matching must survive real bank strings.**
   - *Merchant*: case-insensitive, tolerant of bank noise (`eCom`, `REV`, `Mo MASTTERM`, `Co 76134439`, trailing refs). Compare normalised tokens, not exact strings.
   - *Amount*: the current subscription match is exact within 5% (`SubscriptionRepository.areAmountsEqual`). Real prices change — the owner's own samples show Netflix at LKR 1,000.00 (19/09) then LKR 1,500.00 (21/10). Use a wider tolerance (e.g. ±15%) or "same merchant within the billing window". Never match on amount alone.
   - *Date*: the charge must fall inside the cycle's window (due date ±N days), so a charge months later cannot match a stale template.
4. **Link the charge to what it fulfils.** `TransactionEntity` has `is_recurring` but no link column. Add a nullable `recurring_template_id` / `subscription_id` (migration + migration test, rule 9), set it on a match, and drive "upcoming vs paid" from the link instead of re-matching by string.
5. **Separate the two concepts in the data and the UI.**
   - *Subscription* = a recurring charge billed by someone else (Netflix, Dialog, CEB) — tracked against the real charge.
   - *Recurring template* = spend the user schedules themselves (rent in cash, allowance) — materialised by the app when no bank SMS arrives.
   Let the user create either one from an existing transaction or a parsed SMS, and state plainly which one the SMS path reconciles against.
6. **Own-account transfers must not distort income and spending.** The Sampath samples contain matching debit/credit pairs for one transfer seconds apart (17/09 10:05:33, 20/10 09:34:48, 20/10 09:42:02, 18/10 08:20:13, 13/10 08:48:22). These are internal moves — not income, not spending — yet the bank labels them "credited ... for <note>", so a parser can only call them income. Detect and pair them in the app (same amount, seconds apart, matching reference) and treat them as transfers. This is rule 13, and it is a prerequisite for budget rollover (6a) and the forecast (6b) to mean anything.
7. **Tests** — fixtures from the owner's samples in `docs/lk-samples/`:
   - double-count regression: one template + one SMS in the same cycle → exactly one transaction
   - price change: Netflix LKR 1,000 → LKR 1,500 still matches the subscription and advances it
   - no cross-match: two different merchants with the same amount must not match each other
   - transfer pair: debit + credit seconds apart → one transfer, zero net income/spend
   - unmatched cycle: a template whose SMS never arrives still materialises on the due date
8. **UI**: show *paid* / *overdue* / *scheduled* per subscription and template, and surface the next expected charge on the Home screen.

Acceptance: no double counting in any scenario above; the owner's real samples replayed through the app produce no duplicate and no phantom income; `:app:testStandardDebugUnitTest` green; migration + migration test for any schema change; `./init.sh app` green with quoted output.

---

# Phase 5: Sinhala localisation — OPTIONAL, post-v1.0

**Deferred on purpose.** Full string translation of a large Compose app, layout
verification with long strings, and font/rendering checks is weeks of work —
and the output still needs a native speaker to review. Shipping unverified
Sinhala/Tamil is worse than shipping English. Do this after v1.0, and only if a
native reviewer is lined up. Sinhala first; Tamil only after Sinhala is
reviewed.

1. Default currency LKR, number and date formats checked (e.g. `Rs. 1,234.50`).
2. Default categories and merchant rules for Sri Lanka as an editable data file: supermarkets (Keells, Cargills Food City, Arpico, Laugfs), fuel (Ceypetco, IOC), telco and internet (Dialog, Mobitel, SLT, Hutch, Airtel), utilities (CEB, LECO, NWSDB), transport (PickMe, Uber), shopping (Daraz), streaming (Netflix, Spotify, YouTube). Use the existing rules mechanism. Add eval fixtures for messy merchant strings and report accuracy.
3. Sinhala string resources: draft all strings, mark as `needs-native-review` in `docs/i18n.md`. Verify fonts and text rendering, layout with long strings, and that dates/numbers still format correctly.
4. Onboarding text explains SMS permission and privacy plainly.

Acceptance: fresh install defaults to LKR with sensible categories; Sinhala renders correctly with long strings; every string marked `needs-native-review` has been reviewed by a native speaker before release.

---

# Phase 6: Budget rollover and alerts — OPTIONAL, post-v1.0

**Deferred on purpose.** Each item here is a Room migration, a migration test,
and new UI. Anomaly flags and "committed amounts" are nice-to-haves that upstream
may add anyway, and upstream is 800+ commits ahead.

**Prefer features that do not touch the Room schema** — every migration we add
makes future upstream merges harder (rule 2).

5a. **Budget rollover and alerts:** optional rollover of unspent budget (stored per period so history is stable, use the existing snapshot entity if it fits); local notifications at 80% and 100% once per period per budget via WorkManager; `POST_NOTIFICATIONS` requested at the right moment; no exact-alarm permission.

5b. **Cash-flow forecast:** deterministic, no ML. Project the next 30 days from recurring templates, subscriptions and average daily discretionary spend. Show "will I stay within budget?" and a low-balance warning against a user-set floor per account. Pure Kotlin logic with unit tests (edge cases: month ends, anchor-day budgets, accounts with no history).

5c. *(optional)* Anomaly flags: unusually large amount for a merchant, first-time merchant with large amount, possible duplicate (same merchant and amount within 10 minutes), foreign-currency charge. Suggestions only, dismissible, never auto-edit data.

5d. *(optional)* Committed amounts: show upcoming subscription and recurring charges separately from "spent" on budget screens if not already present.

5e. Verify CSV export, rules, goals, imports work without limits after Phase 1.

Acceptance: new tests green, migration tests green, manual flows work on emulator.

---

# Phase 7: Offline build variant — OPTIONAL, lowest priority

**Deferred on purpose.** A whole extra flavour — new applicationId suffix,
merged release manifest, a CI job that fails the build, and double the release
test surface — to be able to say "the release variant has no INTERNET
permission". The honest privacy story is already covered by Phase 1's endpoint
audit, `PRIVACY.md`, and the absence of analytics. Revisit only if a Play Store
listing or a privacy-focused buyer makes it worth the release-matrix cost.

1. Add an `offline` build variant whose merged release manifest has no `INTERNET` permission: exchange-rate fetch, model download and webhooks disabled (model import via file picker instead). Add a CI step that fails if that variant's merged manifest contains INTERNET.
2. Decide on cloud backup: skip Google Drive (needs OAuth app verification). Optionally port Cashiro's WebDAV provider (AGPL-compatible, attribute it) into the connected variant only. Local scheduled backup stays the default.

---

# Phase 8: Release

1. CI: tests, debug build, lint, offline-manifest check (only if the optional Phase 7 variant exists). Fix anything flaky.
2. Signed release APK via CI secrets, GitHub Releases workflow, F-Droid metadata (unique applicationId, fastlane metadata folder, reproducibility notes). Do not target Play Store yet — its SMS-permission policy is a separate project, and F-Droid needs no store account.
3. Docs: README with screenshots, `PRIVACY.md`, `CONTRIBUTING.md`, "How to add a Sri Lankan bank" guide with a worked example and masking instructions, issue templates ("Add or fix a bank parser" asking for masked samples).
4. Security pass: manifest exported components, no sensitive logging (grep test), dependency audit, DB encryption verified again on the release build, backup behaviour re-checked against `PRIVACY.md`.
5. Run the upstream sync dry run once more and record the result.
6. Confirm the release notes say plainly which banks are supported and which are not.

Acceptance: fresh clone to running app following the README; release APK passes a manual smoke test on a real device.

---

# Appendix: Android Studio notes

- Open the repo root in Android Studio. Settings > Build Tools > Gradle > Gradle JDK = the bundled JBR (21+).
- Install SDK Platform 37 and 36 from SDK Manager.
- Emulator SMS: `adb emu sms send <sender> "<text>"`. Emulator senders may reject alphanumeric IDs; test parsers with unit tests first, then on a real device with live SMS.
- Real device: enable Developer options and USB debugging, `adb devices`, run the app from Studio.

---

# Revision 3 changelog

**Revision 3 — added Phase 4 (recurring transactions & subscriptions).**
- New **Phase 4: Recurring transactions & subscriptions (SMS-aware)** — a v1.0 must-have. Both features already exist upstream and are ungated after Phase 1; the phase fixes the parts that break under an SMS-first workflow: templates are not matched by the SMS path (double counting), subscription matching is exact within 5% (misses price changes — Netflix LKR 1,000 → 1,500 in the owner's own samples), there is no link column on the transaction, and own-account transfer pairs are booked as income *and* spending.
- New rule 13: **one real-world event = one transaction.**
- Phases 4–7 renumbered to 5–8; the release scope line now reads v1.0 = Phases 1+2+3(2 banks)+4+8.
- Phase 3 now points at Phase 4 for the "credited ... for &lt;note&gt;" semantics instead of leaving it to the parser.

**Revision 2 — trimmed to a shippable v1.0 scope.**

*Trimmed:*
- Added a release scope up front. Everything else is marked optional/post-v1.0.
- Sinhala/Tamil → Sinhala first, needs a native reviewer, post-v1.0.
- Budget rollover and alerts split into 5a–5e; 5a (rollover + alerts) is the only candidate for the next cycle.
- Offline build variant marked optional/lowest priority.
- Phase 3: bank list reduced to the two sampled banks, with the owner's per-bank cost stated so it reads as "as samples arrive", not as a backlog.

*Filled in:*
- Phase 2: plaintext→encrypted upgrade path (the owner's own device needs it), no in-memory fallback, close-before-delete on reset, explicit `allowBackup` rules, backup threat model, extra tests, and a checklist of outstanding items.
- Rule 2: sync before every schema-touching phase. New rules 11 (backups are part of the privacy boundary) and 12 (record deviations).
- Phase 0/1 marked done with the verified test numbers.
