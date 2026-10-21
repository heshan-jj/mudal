<!-- Mudal fork: new file in Mudal, a fork of PennyWise AI (AGPL-3.0-only), 2026-10-21 - see NOTICE. -->
# Attribution & Upstream Heritage

Mudal is an independent fork of [PennyWise AI](https://github.com/sarim2000/pennywiseai-tracker), created by Mohammad Sarim and open-source contributors, licensed under the **GNU Affero General Public License v3.0 (AGPL-3.0-only)**.

- **This project (the fork):** [Mudal](https://github.com/heshan-jj/mudal) — Copyright (C) 2026 Heshan Jayakody and the Mudal contributors.
- **Upstream project:** PennyWise AI Expense Tracker — Copyright (C) Sarim Ahmed and the PennyWise AI contributors.
- **Upstream repository:** <https://github.com/sarim2000/pennywiseai-tracker>
- **License:** GNU Affero General Public License v3.0 (AGPL-3.0-only) — see [`LICENSE`](LICENSE) and [`NOTICE`](NOTICE).
- **Modifications record:** [`docs/MODIFICATIONS.md`](docs/MODIFICATIONS.md).

---

## Modifications in Mudal

Mudal forks PennyWise AI to provide a focused, privacy-first personal finance tracker tailored for Sri Lankan users. Every modified source file carries a `Mudal fork:` notice; the summary below is the human-readable version.

### Phase 1 — Identity, de-commercialisation, privacy cleanup
1. **Rebranding:** App renamed to **Mudal** (මුදල්), with its own launcher icon, splash screen and font under application ID `com.mudal.tracker`. The Kotlin package namespace `com.pennywiseai.tracker` is deliberately left unchanged to keep the diff against upstream small and reviewable.
2. **Complete de-commercialisation:** Removed all billing code (Play Billing, Dodo Payments, licence activation, RSA device fingerprinting), paywall UI, `FreeTierLimits` and every `EntitlementGate` limit. All capabilities (rules, statement imports, CSV export, recurring templates, goals, account merge, webhooks, analytics groupings) are unlocked.
3. **Privacy decoupling:** The remote "report unrecognised SMS" flow (which sent the SMS body to `pennywise.zynth.dev`) was replaced with local, on-device regex masking plus a user-reviewed GitHub issue link. No raw SMS leaves the device.
4. **Sri Lankan defaults:** Base currency `LKR`, `Rs.` formatting, locale-aware numbers and dates.
5. **Single build pipeline:** The dual `standard`/`fdroid` flavours were consolidated into one flavour.

### Phase 2 — Database encryption
6. **SQLCipher:** The Room database is opened through `SupportOpenHelperFactory` with a random 256-bit passphrase wrapped by an Android Keystore AES-256-GCM key (`DatabaseKeyManager`, `KeyStoreHelper`). A lost key surfaces a dedicated recovery screen instead of a crash loop.
7. **FLAG_SECURE** is offered as a setting to hide content in the app switcher.

### Phase 3 — Sri Lankan bank parsers
8. **New parser:** `CommercialBankParser` — Commercial Bank of Ceylon debit-card purchases, ATM withdrawals and branch credits.
9. **Hardened parser:** `SampathBankParser` — audited against 70 real, masked Sampath Bank SMS: ATM cash withdrawals, POS card purchases, `eCom` and `eCom/REV` merchants, internal transfers (`TFR`, `TRRR`, `CEFTLV`), standing payments (`VSRPMT`), interest credits, and amounts written without a leading zero (`LKR .56`).
10. **Focused parser set:** The parser registry was reduced to Sri Lankan banks only — Sampath Bank, Commercial Bank of Ceylon, National Development Bank, National Savings Bank and Nations Trust Bank. The non-Sri-Lankan bank parsers carried by upstream (and their tests) were **removed** from `parser-core`; see `docs/MODIFICATIONS.md` for the file list.
11. **Unrecognised-SMS heuristic:** Unknown alphanumeric senders carrying `LKR` / `Rs.` / `Rs ` amounts now reach the Unrecognised review inbox.

### Documentation and samples
12. `docs/FORK_PLAN.md` (the fork's phased plan), `docs/FORK_NOTES.md` (dated decisions), `docs/PRIVACY.md` (every remaining network endpoint) and `docs/lk-samples/` (real, masked bank SMS used as parser fixtures).

---

## Third-Party Libraries & Assets

| Component | Licence |
|---|---|
| AndroidX & Jetpack Compose | Apache 2.0 |
| Kotlin & Coroutines | Apache 2.0 |
| Room Database & SQLite | Apache 2.0 |
| Dagger Hilt | Apache 2.0 |
| Ktor Client | Apache 2.0 |
| Haze & Haze Materials (Chris Banes) | Apache 2.0 |
| **SQLCipher (`net.zetetic:sqlcipher-android`)** — *added by Mudal* | **BSD-3-Clause** |
| Google Sans (app font) | Apache 2.0 |
| Open Exchange Rates API (`open.er-api.com`) | Open data |

The optional AI assistant calls the **Google Gemini API** using the user's own API key. No model is bundled or downloaded, and Mudal has no key of its own.

---

## Trademarks

"PennyWise", "PennyWise AI", the PennyWise logo and the PennyWise banner artwork are trademarks of Sarim Ahmed and are **not used by Mudal**. Upstream's banner artwork was removed from this repository. Mudal is not endorsed by, affiliated with, or sponsored by the PennyWise project. See [`TRADEMARKS.md`](TRADEMARKS.md).
