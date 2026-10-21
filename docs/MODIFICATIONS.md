<!-- Mudal fork: new file in Mudal, a fork of PennyWise AI (AGPL-3.0-only), 2026-10-21 - see NOTICE. -->
# Modification record (AGPL-3.0-only)

Mudal is a modified version of [PennyWise AI](https://github.com/sarim2000/pennywiseai-tracker)
(Copyright (C) Sarim Ahmed and the PennyWise AI contributors, AGPL-3.0-only).

This file records what Mudal changed, as required by AGPL-3.0 §5(a). Every source file
that Mudal modified also carries a `Mudal fork:` notice at the top of the file with the
date of modification. See [`../NOTICE`](../NOTICE) for the copyright and licence statement
and [`../ATTRIBUTION.md`](../ATTRIBUTION.md) for the summary.

Comparison base: `upstream/main` = `sarim2000/pennywiseai-tracker` @ `27e7563c`.
Generated: 2026-10-21.

**Totals: 176 files modified, 24 files added, 342 files removed.**

---

## 1. Phase 1 — Identity, de-commercialisation, privacy cleanup

- Rebrand to **Mudal** (`com.heshan.moneytracker`), own launcher icon, splash screen and font.
- Removed the billing/licensing stack, paywall UI and all feature limits (`EntitlementGate` now
  reports Pro unconditionally; `FreeTierLimits` deleted).
- Replaced the remote unrecognised-SMS report flow with local masking (`SmsReportUrlBuilder`).
- LKR / `Rs.` currency and number defaults; single build flavour; `applicationId` changed.
- Removed upstream's Discord, website and funding links.

## 2. Phase 2 — Database encryption

- SQLCipher (`net.zetetic:sqlcipher-android`) + Android Keystore key wrapping.
- `DatabaseKeyManager`, `KeyStoreHelper`, `DatabaseErrorScreen` (new files).
- FLAG_SECURE toggle in Settings.

## 3. Phase 3 — Sri Lankan bank parsers

- `CommercialBankParser` + `TestCommercialBankParser` (new files).
- `SampathBankParser` hardened against 70 real masked SMS.
- Parser registry reduced to Sri Lankan banks; **the non-Sri-Lankan bank parsers and their
  tests were removed** (full list in §5).
- Unrecognised-SMS candidate heuristic now accepts `LKR` / `Rs.` / `Rs ` amounts.
- Currency set locked to `LKR`, `USD`, `EUR` across the UI, formatters and models.

---

## 4. Files added by Mudal

- `./` — `ATTRIBUTION.md`, `NOTICE`, `_mod_counts.txt`, `gen_mods.py`
- `app/src/androidTest/java/com/pennywiseai/tracker/data/database/` — `SqlCipherEncryptionTest.kt`
- `app/src/main/java/com/pennywiseai/tracker/data/database/` — `DatabaseKeyManager.kt`, `KeyStoreHelper.kt`
- `app/src/main/java/com/pennywiseai/tracker/ui/screens/database/` — `DatabaseErrorScreen.kt`
- `app/src/main/res/font/` — `google_sans_bold.ttf`, `google_sans_bold_italic.ttf`, `google_sans_italic.ttf`, `google_sans_medium.ttf`, `google_sans_medium_italic.ttf`, `google_sans_regular.ttf`
- `app/src/test/java/com/pennywiseai/tracker/data/database/` — `DatabaseKeyManagerTest.kt`
- `app/src/test/java/com/pennywiseai/tracker/utils/` — `SmsReportUrlBuilderTest.kt`
- `docs/` — `FORK_NOTES.md`, `FORK_PLAN.md`, `PRIVACY.md`
- `docs/lk-samples/` — `README.md`, `commercial-bank.txt`, `sampath.txt`
- `parser-core/src/main/kotlin/com/pennywiseai/parser/core/bank/` — `CommercialBankParser.kt`
- `parser-core/src/test/kotlin/` — `TestCommercialBankParser.kt`

## 5. Files removed by Mudal

Removed because Mudal supports Sri Lankan banks only; the equivalents are maintained
upstream (see the upstream repository for the full set).

**`app/src/fdroid/java/com/pennywiseai/tracker/ui/components/`** (1):

`SupportWebOption`

**`app/src/fdroid/res/values/`** (1):

`strings`

**`app/src/main/java/com/pennywiseai/tracker/billing/`** (1):

`FreeTierLimits`

**`app/src/main/java/com/pennywiseai/tracker/billing/license/`** (3):

`LicenseClient`, `LicenseManager`, `LicensePolicy`

**`app/src/main/java/com/pennywiseai/tracker/presentation/paywall/`** (4):

`LicenseKeyDialog`, `UpgradeSheet`, `UpgradeUiState`, `UpgradeViewModel`

**`app/src/main/java/com/pennywiseai/tracker/ui/components/`** (2):

`ProHomeCard`, `SupportDevelopment`

**`app/src/main/java/com/pennywiseai/tracker/utils/`** (1):

`DeviceEncryption`

**`app/src/standard/`** (1):

`AndroidManifest`

**`app/src/standard/java/com/pennywiseai/tracker/billing/`** (1):

`PlayBillingGateway`

**`app/src/standard/java/com/pennywiseai/tracker/data/manager/`** (2):

`InAppReviewManager`, `InAppUpdateManager`

**`app/src/standard/java/com/pennywiseai/tracker/di/`** (1):

`BillingModule`

**`app/src/standard/java/com/pennywiseai/tracker/ui/components/`** (1):

`SupportWebOption`

**`app/src/test/java/com/pennywiseai/tracker/billing/`** (1):

`EntitlementGateTest`

**`app/src/test/java/com/pennywiseai/tracker/billing/license/`** (1):

`LicensePolicyTest`

**`app/src/test/java/com/pennywiseai/tracker/presentation/home/`** (1):

`ProHomeCardEligibilityTest`

**`parser-core/src/main/kotlin/com/pennywiseai/parser/core/bank/`** (171):

`ADCBParser`, `AMEXBankParser`, `AUBankParser`, `AccessBankParser`, `AdelFiParser`, `AirtelPaymentsBankParser`, `AlRajhiBankParser`, `AlecuBankParser`, `AlinmaBankParser`, `AltanaFCUParser`, `ApolloParser`, `ArabBankParser`, `AwashBankParser`, `AxisBankParser`, `BAACBankParser`, `BPCEParser`, `BRACBankParser`, `BSFBankParser`, `BancoAgricolaParser`, `BancoCuscatlanParser`, `BancoPromericaParser`, `BancolombiaParser`, `BandhanBankParser`, `BangkokBankParser`, `BankAlEtihadParser`, `BankMuscatParser`, `BankOfAbyssiniaParser`, `BankOfBarodaParser`, `BankOfIndiaParser`, `BankinoBankParser`, `BaseElSalvadorBankParser`, `BaseIndianBankParser`, `BaseIranianBankParser`, `BaseThailandBankParser`, `BkashParser`, `BluBankParser`, `CBEBankParser`, `CIBEgyptParser`, `CIMBThaiParser`, `CanaraBankParser`, `CashfreeParser`, `CentralBankOfIndiaParser`, `CharlesSchwabParser`, `ChaseBankParser`, `ChaseUKParser`, `CitiBankParser`, `CitizensBankParser`, `CityUnionBankParser`, `CrdbBankParser`, `CredParser`, `D360BankParser`, `DBSBankParser`, `DOPBankParser`, `DashenBankParser`, `DhanlaxmiBankParser`, `DiamondTrustBankParser`, `DiscoverCardParser`, `EMolaParser`, `EmiratesIslamicParser`, `EmiratesNBDParser`, `EnparaBankParser`, `EquitasBankParser`, `EquityBankKenyaParser`, `EverestBankParser`, `FABParser`, `FaysalBankParser`, `FederalBankParser`, `FinancialMessageFields`, `FinancialMessageSafety`, `GSBBankParser`, `GTBankParser`, `GreaterBankParser`, `HDFCBankParser`, `HDFCMutualFundParser`, `HSBCBankParser`, `HuntingtonBankParser`, `ICICIBankParser`, `IDBIBankParser`, `IDFCFirstBankParser`, `IPPBParser`, `IndianBankParser`, `IndianOverseasBankParser`, `IndusIndBankParser`, `IslamicInternationalArabBankParser`, `JKBankParser`, `JaizBankParser`, `JanaSmallFinanceBankParser`, `JioPayParser`, `JioPaymentsBankParser`, `JordanIslamicBankParser`, `JupiterBankParser`, `JuspayParser`, `KTCCreditCardParser`, `KarnatakaBankParser`, `KasikornBankParser`, `KeralaBankParser`, `KeralaGraminBankParser`, `KeystoneBankParser`, `KotakBankParser`, `KrungThaiBankParser`, `KrungsriBankParser`, `LaxmiBankParser`, `LazyPayParser`, `LivBankParser`, `LumbiniBikashBankParser`, `MBankCZParser`, `MPESAParser`, `MPesaMozambiqueParser`, `MPesaTanzaniaParser`, `MachchhapuchreBankParser`, `ManjushreeFinanceParser`, `MashreqBankParser`, `MellatBankParser`, `MelliBankParser`, `MillenniumBimParser`, `MixxByYasParser`, `MoniepointParser`, `NMBBankParser`, `NMBTanzaniaParser`, `NSDLPaymentsBankParser`, `NabilBankParser`, `NationalBankOfEgyptParser`, `NaviMutualFundParser`, `NavyFederalParser`, `NepalBankParser`, `NepalSBIBankParser`, `OldHickoryParser`, `OneCardParser`, `OpayBankParser`, `PNBBankParser`, `ParsianBankParser`, `PasargadBankParser`, `PluxeeBankParser`, `PrimeCommercialBankParser`, `PriorbankParser`, `PunjabSindBankParser`, `SBIBankParser`, `SNBAlAhliBankParser`, `STCBankParser`, `SVCBankParser`, `SabbBankParser`, `SafwaIslamicBankParser`, `SaraswatBankParser`, `SaudiTransactionMessageGuards`, `SelcomPesaParser`, `SiamCommercialBankParser`, `SiddharthaBankParser`, `SiketBankParser`, `SliceParser`, `SouthIndianBankParser`, `SparkasseRheinMaasParser`, `StandardBankMozambiqueParser`, `StandardCharteredBankParser`, `StandardCharteredNepalParser`, `StandardCharteredNigeriaParser`, `TBankParser`, `TTBBankParser`, `TamilnadMercantileBankParser`, `TelebirrParser`, `TigoPesaParser`, `Trading212Parser`, `UAEBankParser`, `UCOBankParser`, `UOBThailandParser`, `UnionBankParser`, `UtkarshBankParser`, `VFDBankParser`, `YesBankParser`, `ZamZamBankParser`, `ZemenBankParser`, `ZenithBankParser`

**`parser-core/src/test/kotlin/`** (86):

`CrdbBankParserTest`, `PNBBankParserTest`, `PluxeeBankParserTest`, `SmsReportRegressionTest`, `TestADCBParser`, `TestAUBankParser`, `TestAccessBankParser`, `TestAlRajhiBankParser`, `TestAlecuBankParser`, `TestAlinmaBankParser`, `TestAllNewParsers`, `TestApolloParser`, `TestAwashBankParser`, `TestAxisBankParser`, `TestBSFBankParser`, `TestBancolombiaParser`, `TestBandhanBankParser`, `TestBankOfAbyssiniaBankParser`, `TestBankOfBarodaParser`, `TestBankOfIndiaParser`, `TestBankinoBankParser`, `TestBkashParser`, `TestBluBankParser`, `TestCBEBankParser`, `TestCIBEgyptParser`, `TestCentralBankOfIndiaParser`, `TestCharlesSchwabParser`, `TestChaseBankParser`, `TestCitiBankParser`, `TestD360BankParser`, `TestDashenBankParser`, `TestDhanlaxmiBankParser`, `TestDiscoverCardParser`, `TestEMolaParser`, `TestEquitasBankParser`, `TestEverestBankParser`, `TestFABParser`, `TestFABXML`, `TestFederalBankMandateParsing`, `TestFederalBankOutgoingParser`, `TestFederalBankParser`, `TestGTBankParser`, `TestHDFCBankParser`, `TestHDFCMandateParsing`, `TestHDFCMutualFundParser`, `TestHSBCBankParser`, `TestHuntingtonBankParser`, `TestICICIBankParser`, `TestIDFCFirstBankParser`, `TestIndianBankParser`, `TestIndusIndBankParser`, `TestJaizBankParser`, `TestJuspayParser`, `TestKeralaGraminBankParser`, `TestKeystoneBankParser`, `TestKotakBankParser`, `TestLaxmiBankParser`, `TestLivBankParser`, `TestMBankCZParser`, `TestMPESAParser`, `TestMashreqBankParser`, `TestMillenniumBimParser`, `TestMoniepointParser`, `TestNMBBankParser`, `TestNaviMutualFundParser`, `TestNavyFederalParser`, `TestNigeriaEndToEnd`, `TestOldHickoryParser`, `TestOpayBankParser`, `TestPriorbankParser`, `TestSBIParser`, `TestSaraswatBankParser`, `TestSiketBankParser`, `TestSouthIndianBankParser`, `TestStandardBankMozambiqueParser`, `TestStandardCharteredBankParser`, `TestStandardCharteredNigeriaParser`, `TestTBankParser`, `TestTelebirrParser`, `TestUSParsers`, `TestVFDBankParser`, `TestYesBankParser`, `TestZamZamBankParser`, `TestZemenBankParser`, `TestZenithBankParser`, `XMLTestUtils`

**`parser-core/src/test/kotlin/com/pennywiseai/parser/core/bank/`** (62):

`AdelFiParserTest`, `AltanaFCUParserTest`, `ArabBankParserTest`, `BPCEParserTest`, `BRACBankParserTest`, `BancoAgricolaParserTest`, `BancoCuscatlanParserTest`, `BancoPromericaParserTest`, `BankAlEtihadParserTest`, `BankMuscatParserTest`, `CanaraBankParserTest`, `CashfreeParserTest`, `ChaseUKParserTest`, `CitizensBankParserTest`, `CredParserTest`, `DOPBankParserTest`, `DiamondTrustBankParserTest`, `EmiratesIslamicParserTest`, `EmiratesNBDParserTest`, `EnparaBankParserTest`, `EquityBankKenyaParserTest`, `FaysalBankParserTest`, `GreaterBankParserTest`, `HSBCBankParserTest`, `IslamicInternationalArabBankParserTest`, `JanaSmallFinanceBankParserTest`, `JioPayParserTest`, `JordanIslamicBankParserTest`, `JupiterBankParserTest`, `KarnatakaBankParserTest`, `KeralaBankParserTest`, `LumbiniBikashBankParserTest`, `MPesaMozambiqueParserTest`, `MachchhapuchreBankParserTest`, `ManjushreeFinanceParserTest`, `MellatBankParserTest`, `MelliBankParserTest`, `MixxByYasParserTest`, `NMBTanzaniaParserTest`, `NSDLPaymentsBankParserTest`, `NabilBankParserTest`, `NationalBankOfEgyptParserTest`, `NepalBankParserTest`, `NepalSBIBankParserTest`, `ParsianBankParserTest`, `PasargadBankParserTest`, `PrimeCommercialBankParserTest`, `PunjabSindBankParserTest`, `SNBAlAhliBankParserTest`, `STCBankParserTest`, `SVCBankParserTest`, `SabbBankParserTest`, `SafwaIslamicBankParserTest`, `SiddharthaBankParserTest`, `SliceParserTest`, `SparkasseRheinMaasParserTest`, `StandardCharteredNepalParserTest`, `TamilnadMercantileBankParserTest`, `TanzaniaParserTest`, `ThailandBankParsersTest`, `Trading212ParserTest`, `UCOBankParserTest`

**`parser-core/src/test/resources/`** (1):

`fab_sms_test_data_anonymized`

---

## 6. Files modified by Mudal

Every file below carries a `Mudal fork:` notice at the top. Grouped by area.

**`./`** (7)
**`.github/ISSUE_TEMPLATE/`** (3)
**`.github/workflows/`** (1)
**`.idea/`** (1)
**`app/`** (1)
**`app/src/debug/res/values/`** (1)
**`app/src/main/`** (1)
**`app/src/main/java/com/pennywiseai/tracker/`** (3)
**`app/src/main/java/com/pennywiseai/tracker/backup/folder/`** (2)
**`app/src/main/java/com/pennywiseai/tracker/billing/`** (2)
**`app/src/main/java/com/pennywiseai/tracker/core/`** (1)
**`app/src/main/java/com/pennywiseai/tracker/data/backup/`** (3)
**`app/src/main/java/com/pennywiseai/tracker/data/csv/`** (2)
**`app/src/main/java/com/pennywiseai/tracker/data/currency/`** (2)
**`app/src/main/java/com/pennywiseai/tracker/data/database/`** (1)
**`app/src/main/java/com/pennywiseai/tracker/data/manager/`** (2)
**`app/src/main/java/com/pennywiseai/tracker/data/preferences/`** (2)
**`app/src/main/java/com/pennywiseai/tracker/data/repository/`** (4)
**`app/src/main/java/com/pennywiseai/tracker/data/rules/`** (1)
**`app/src/main/java/com/pennywiseai/tracker/data/share/`** (1)
**`app/src/main/java/com/pennywiseai/tracker/di/`** (2)
**`app/src/main/java/com/pennywiseai/tracker/presentation/accounts/`** (2)
**`app/src/main/java/com/pennywiseai/tracker/presentation/add/`** (1)
**`app/src/main/java/com/pennywiseai/tracker/presentation/goals/`** (2)
**`app/src/main/java/com/pennywiseai/tracker/presentation/home/`** (2)
**`app/src/main/java/com/pennywiseai/tracker/presentation/recurring/`** (2)
**`app/src/main/java/com/pennywiseai/tracker/presentation/statement/`** (1)
**`app/src/main/java/com/pennywiseai/tracker/presentation/transactions/`** (4)
**`app/src/main/java/com/pennywiseai/tracker/receiver/`** (1)
**`app/src/main/java/com/pennywiseai/tracker/ui/components/`** (1)
**`app/src/main/java/com/pennywiseai/tracker/ui/screens/analytics/`** (1)
**`app/src/main/java/com/pennywiseai/tracker/ui/screens/onboarding/`** (1)
**`app/src/main/java/com/pennywiseai/tracker/ui/screens/rules/`** (1)
**`app/src/main/java/com/pennywiseai/tracker/ui/screens/settings/`** (3)
**`app/src/main/java/com/pennywiseai/tracker/ui/screens/settings/webhooks/`** (1)
**`app/src/main/java/com/pennywiseai/tracker/ui/theme/`** (2)
**`app/src/main/java/com/pennywiseai/tracker/ui/viewmodel/`** (3)
**`app/src/main/java/com/pennywiseai/tracker/utils/`** (3)
**`app/src/main/java/com/pennywiseai/tracker/worker/`** (1)
**`app/src/main/res/drawable/`** (1)
**`app/src/main/res/drawable-nodpi/`** (10)
**`app/src/main/res/mipmap-anydpi-v26/`** (2)
**`app/src/main/res/values/`** (7)
**`app/src/main/res/values-ar-rSA/`** (4)
**`app/src/main/res/values-be-rBY/`** (2)
**`app/src/main/res/values-cs-rCZ/`** (4)
**`app/src/main/res/values-de-rDE/`** (4)
**`app/src/main/res/values-es/`** (2)
**`app/src/main/res/values-fr-rFR/`** (4)
**`app/src/main/res/values-hi-rIN/`** (2)
**`app/src/main/res/values-ne-rNP/`** (2)
**`app/src/main/res/values-night/`** (1)
**`app/src/main/res/values-pt-rBR/`** (4)
**`app/src/main/res/values-pt-rPT/`** (4)
**`app/src/main/res/values-ru-rRU/`** (4)
**`app/src/main/res/values-sw-rKE/`** (2)
**`app/src/main/res/values-th-rTH/`** (3)
**`app/src/test/java/com/pennywiseai/tracker/data/backup/`** (2)
**`app/src/test/java/com/pennywiseai/tracker/data/preferences/`** (1)
**`app/src/test/java/com/pennywiseai/tracker/data/repository/`** (3)
**`app/src/test/java/com/pennywiseai/tracker/presentation/add/`** (1)
**`app/src/test/java/com/pennywiseai/tracker/receiver/`** (1)
**`app/src/test/java/com/pennywiseai/tracker/utils/`** (1)
**`app/src/test/java/com/pennywiseai/tracker/worker/`** (1)
**`docs/`** (1)
**`fastlane/metadata/android/en-US/`** (2)
**`fastlane/metadata/android/en-US/changelogs/`** (10)
**`gradle/`** (1)
**`parser-core/src/main/kotlin/com/pennywiseai/parser/core/bank/`** (3)
**`parser-core/src/test/kotlin/`** (1)
**`pennywise-web/server/`** (1)
**`pennywise-web/server/src/main/kotlin/ui/`** (7)
**`pennywise-web/server/src/main/resources/`** (2)
**`pennywise-web/server/src/main/resources/static/fonts/`** (1)

