# Attribution & Upstream Heritage

Mudal is an independent fork of [PennyWise](https://github.com/sarim2000/pennywiseai-tracker), created by Mohammad Sarim and open-source contributors, licensed under the **GNU Affero General Public License v3.0 (AGPL-3.0)**.

---

## Upstream Project
- **Original Project:** PennyWise AI Expense Tracker
- **Original Author:** Mohammad Sarim (`sarim2000`)
- **Repository:** [https://github.com/sarim2000/pennywiseai-tracker](https://github.com/sarim2000/pennywiseai-tracker)
- **License:** GNU Affero General Public License v3.0 (AGPL-3.0)

---

## Modifications in Mudal
Mudal forks from PennyWise to provide a focused, privacy-first personal finance tracker tailored for Sri Lankan users:

1. **Rebranding:** App renamed to **Mudal** with distinct launcher branding and iconography under application ID `com.heshan.moneytracker`.
2. **Complete De-commercialization:** Removed all proprietary billing code (Play Billing, Dodo Payments, license activation, RSA device fingerprinting), paywalls, and feature gating. All capabilities (rules, imports, recurring templates, analytics groupings, CSV exports) are 100% unlocked.
3. **Privacy Decoupling:** Replaced remote unrecognized SMS reporting endpoints (`pennywise.zynth.dev`) and device ID harvesting with local, on-device regex masking and user-reviewed GitHub issue templates.
4. **Sri Lankan Localization:** Default base currency set to Sri Lankan Rupee (`LKR`, `Rs.`) with locale-aware number formatting.
5. **Unified Build Pipeline:** Consolidated dual build flavors (`standard` and `fdroid`) into a single, clean reproducible build with emulator compatibility (`x86_64`, `arm64-v8a`, `armeabi-v7a`).

---

## Third-Party Libraries & Assets
- **AndroidX & Jetpack Compose:** Apache 2.0
- **Kotlin & Coroutines:** Apache 2.0
- **Room Database & SQLite:** Apache 2.0
- **Dagger Hilt:** Apache 2.0
- **Ktor Client:** Apache 2.0
- **Haze & Haze Materials (Chris Banes):** Apache 2.0
- **MediaPipe / LiteRT (Google):** Apache 2.0
- **Open Exchange Rates API (`open.er-api.com`):** Open Data
