# Phase 4 Implementation Plan: SMS-Aware Recurring, Transfer Pairing & Schema Linking

> **For agentic workers:** Execute directly on `main` task-by-task using TDD, `./init.sh`, AVD instrumentation migration verification, and fresh `pro` code review before committing.

**Goal:** Eliminate SMS/template recurring double-counting (bank wins, wide price-change tolerance, historical cycle support), persist `subscription_id`/`recurring_template_id` across Room `67 -> 68` and backups, auto-pair tight-window own-account SMS transfer pairs into single `TRANSFER` rows, and update Subscriptions / Recurring / Transaction Detail / Home UIs.

**Spec:** [2026-11-25-phase4-sms-recurring-and-transfer-pairing-design.md](../specs/2026-11-25-phase4-sms-recurring-and-transfer-pairing-design.md)

---

### Task 1: Schema `67 -> 68`, Link Columns, Backup Remapping, Delete/Dedup Cleanup & Migration Test

**Files:**
- Modify: `app/src/main/java/com/pennywiseai/tracker/data/database/entity/TransactionEntity.kt`
- Modify: `app/src/main/java/com/pennywiseai/tracker/data/database/PennyWiseDatabase.kt`
- Modify: `app/src/main/java/com/pennywiseai/tracker/data/database/dao/TransactionDao.kt`
- Modify: `app/src/main/java/com/pennywiseai/tracker/data/repository/SubscriptionRepository.kt`
- Modify: `app/src/main/java/com/pennywiseai/tracker/data/repository/RecurringTransactionRepository.kt`
- Modify: `app/src/main/java/com/pennywiseai/tracker/data/backup/BackupExporter.kt`
- Modify: `app/src/main/java/com/pennywiseai/tracker/data/backup/BackupImporter.kt`
- Modify: `app/src/main/java/com/pennywiseai/tracker/data/repository/TransactionRepository.kt`
- Create: `app/src/androidTest/java/com/pennywiseai/tracker/data/database/Migration67To68Test.kt`
- Create: `app/src/test/java/com/pennywiseai/tracker/data/backup/BackupImporterRecurringLinkTest.kt`

1. Update `TransactionEntity`: add `subscriptionId: Long? = null` (`subscription_id`) and `recurringTemplateId: Long? = null` (`recurring_template_id`) with `defaultValue = "NULL"` and add `Index(value = ["subscription_id"])`, `Index(value = ["recurring_template_id"])`.
2. Update `PennyWiseDatabase`: set `SCHEMA_VERSION = 68` and add `AutoMigration(from = 67, to = 68)`.
3. Update `TransactionDao`:
   - `clearSubscriptionLinks(subscriptionId: Long)` & `clearRecurringTemplateLinks(templateId: Long)`
   - `getTransactionsForSubscriptionInRange(subscriptionId, start, end)` / `getTransactionsForTemplateInRange(templateId, start, end)`
   - `getActiveLinkedSubscriptions(ids)` / `getLatestLinkedTransactionsForSubscriptions()` & `getLatestLinkedTransactionsForTemplates()`
   - Update `deleteRebuildableSmsTransactions` to also delete `'MERGED_TRANSFER_%'` soft-deleted counterpart transfer rows so Force Resync re-ingests both legs cleanly while keeping user-deleted `'DELETED_%'` rows untouched.
   - Add `findSelfTransferCandidates(amount, start, end, excludeId)` for tight-window pairing.
4. Update `SubscriptionRepository.deleteSubscription` & `RecurringTransactionRepository.delete` / `deleteById` to clear child transaction links before deleting the parent row inside `database.withTransaction`.
5. Update `BackupExporter` (null `recurringTemplateId` on non-`FULL` export) and `BackupImporter`: in both `replaceAllData` and `mergeData`, import `subscriptions` and `recurringTransactions` **before** `transactions`, collect `oldToNewSubscriptionIdMap` and `oldToNewRecurringTemplateIdMap` (mapping both newly inserted and deduped-existing local rows), and remap/null `subscriptionId` and `recurringTemplateId` on imported `TransactionEntity` rows.
6. Write `BackupImporterRecurringLinkTest` (JVM) and `Migration67To68Test` (`MigrationTestHelper` in `androidTest`). Run `:app:kspDebugKotlin` so Room exports `app/schemas/.../68.json`, run `BackupSchemaGuardTest`, `BackupImporterRecurringLinkTest`, and `MigrationSqlCompatibilityTest`.

---

### Task 2: `RecurringMatcher` (Pure Domain Matching Engine — TDD)

**Files:**
- Create: `app/src/main/java/com/pennywiseai/tracker/domain/service/RecurringMatcher.kt`
- Create: `app/src/test/java/com/pennywiseai/tracker/domain/service/RecurringMatcherTest.kt`

1. Write failing `RecurringMatcherTest` covering:
   - Sampath & Commercial Bank merchant normalization (`eCom Google Spotify Music Mo MASTTERM`, `eCom Dialog Axiata PLC Co T7064677`, `via POS at KFC-KURUNEGALA KU 80401564`, `eCom Netflix`).
   - Reversal exclusion (`eCom/REV` in raw `smsBody`) & ambiguous placeholder rejection (`Visa Recurring Payment`, `CEFT Transfer`, `Transfer Inward`, `Bank Interest`).
   - Price change: Netflix `1,000 -> 1,500` (`1.5x`) matches; subsequent `1,500 -> 2,100` matches once price updates; `50` micro-charge rejected against `1,500`; two different merchants with identical amount rejected.
   - Cycle window bounds (`DAILY ±1d`, `WEEKLY ±3d`, `MONTHLY/QUARTERLY/SEMI-ANNUAL/ANNUAL -7d..+10d`), historical backward stepping for inbox scans (`Sept 19` + `Oct 21` when schedule sits at `Nov 21`), and skipping already-linked cycles.
   - Direction matching (`EXPENSE`/`CREDIT` -> expense sub/template; `INCOME` -> income sub/template) & account preference tie-breaking.
2. Implement `RecurringMatcher` cleanly as a pure stateless object/class -> observe GREEN.

---

### Task 3: `RecurringReconciliationService` & `RecurringTransactionRepository.materializeDue()` Lock (TDD)

**Files:**
- Create: `app/src/main/java/com/pennywiseai/tracker/data/manager/RecurringReconciliationService.kt`
- Modify: `app/src/main/java/com/pennywiseai/tracker/data/repository/RecurringTransactionRepository.kt`
- Modify: `app/src/main/java/com/pennywiseai/tracker/data/repository/SubscriptionRepository.kt`
- Modify: `app/src/main/java/com/pennywiseai/tracker/data/database/dao/SubscriptionDao.kt`
- Modify: `app/src/main/java/com/pennywiseai/tracker/domain/usecase/AddTransactionUseCase.kt`
- Modify: `app/src/main/java/com/pennywiseai/tracker/domain/usecase/MarkSubscriptionPaidUseCase.kt`
- Modify: `app/src/main/java/com/pennywiseai/tracker/domain/usecase/GenerateIncomeAutopayUseCase.kt`
- Create: `app/src/test/java/com/pennywiseai/tracker/data/manager/RecurringReconciliationServiceTest.kt`

1. Add `SubscriptionDao.getActiveSubscriptionsList()` & `markPaidWithAmount(id, paidAt, nextPaymentDate, amount)` (`RecurringTransactionDao` `getActiveList()` & `updateNextDueDateAndAmount(...)`).
2. Implement `RecurringTransactionRepository.materializeDue()` using `BankAccountMergeStore.mutationMutex.withLock`, setting `recurringTemplateId = template.id` & `isRecurring = true`, and skipping cycles that already have an active linked transaction (`recurring_template_id = template.id`).
3. Implement `RecurringReconciliationService`:
   - `prepareForIncomingTransaction(...)`: evaluates active subscriptions & active recurring templates against incoming parsed/rule-evaluated `TransactionEntity` + resolved card/account info, returns match metadata (matched `SubscriptionEntity` or `RecurringTransactionEntity`, target cycle due date, and whether an active `smsBody == null` placeholder exists to supersede).
   - `commitMatch(...)`:
     - For matched subscription (when `tx` was inserted): sets `lastPaidAt`, `nextPaymentDate` anchored to `cycleDue` (no billing-day drift, no schedule regression on historical SMS), and updates `amount` if current/newer cycle settled cleanly.
     - For matched recurring template: if superseding a materialized placeholder (`sms_body IS NULL`), updates the placeholder row cleanly in place (checking `getTransactionByHash(smsHash) == null`) + shifts manual/delta balance safely; otherwise records the SMS row link and advances `nextDueDate` past `maxOf(template.nextDueDate, cycleDue)` without regressing historical cycles.
4. Update `AddTransactionUseCase`, `MarkSubscriptionPaidUseCase`, and `GenerateIncomeAutopayUseCase` to persist `subscriptionId = sub.id`.
5. Write & pass `RecurringReconciliationServiceTest` (double-count regression, worker-first SMS-later placeholder takeover, unmatched cycle materialization, historical out-of-order safety).

---

### Task 4: `SelfTransferAutoPairer` & SMS Pipeline Integration (TDD)

**Files:**
- Create: `app/src/main/java/com/pennywiseai/tracker/data/manager/SelfTransferAutoPairer.kt`
- Modify: `app/src/main/java/com/pennywiseai/tracker/data/manager/SmsTransactionProcessor.kt`
- Modify: `app/src/main/java/com/pennywiseai/tracker/worker/OptimizedSmsReaderWorker.kt`
- Create: `app/src/test/java/com/pennywiseai/tracker/data/manager/SelfTransferAutoPairerTest.kt`

1. Write failing `SelfTransferAutoPairerTest`:
   - All 5 Sampath own-account debit/credit pairs in `docs/lk-samples/sampath.txt` (`17/09`, `13/10`, `18/10`, `20/10 09:34`, `20/10 09:42`) auto-pair into 1 surviving `TRANSFER` row (outgoing row promoted to `TRANSFER` with `fromAccount`/`toAccount`/`fromBankName`/`toBankName`, counterpart leg marked `isDeleted = true` with `'MERGED_TRANSFER_'` hash prefix), testing BOTH arrival orders (debit first and credit first).
   - Same-account reversals (`Microsoft*Store` `346.54`, `Google One` `150.00`, same-account `CEFTLV`/`TRRR` `.56`), coincidental third-party credits (`PERSON A` + ATM/POS), outside `±120s`, and card-to-same-underlying-account pairs are NOT auto-paired.
2. Implement `SelfTransferAutoPairer` cleanly (including a `sweepExistingUnpairedTransfers()` method run idempotently once after v68 upgrade / scan completion so existing inbox pairs are reconciled too).
3. Wire both `RecurringReconciliationService` AND `SelfTransferAutoPairer` into BOTH `SmsTransactionProcessor` and `OptimizedSmsReaderWorker`:
   - Crucial ordering: in both callers, capture/apply the incoming leg's own `INCOME`/`EXPENSE` balance update FIRST (`balanceUpdates.send` / `processBalanceUpdate`) while `finalEntity` has its real `INCOME`/`EXPENSE` type, and invoke `SelfTransferAutoPairer` AFTER balance capture/recording (plus preserve `subscriptionId`/`recurringTemplateId` during duplicate replacement and `cleanupSmsDuplicates` in `OptimizedSmsReaderWorker`).
   - Run `SelfTransferAutoPairerTest` -> GREEN.

---

### Task 5: UI & ViewModel Updates

**Files:**
- Modify: `app/src/main/java/com/pennywiseai/tracker/presentation/subscriptions/SubscriptionsViewModel.kt`
- Modify: `app/src/main/java/com/pennywiseai/tracker/presentation/subscriptions/SubscriptionsScreen.kt`
- Modify: `app/src/main/java/com/pennywiseai/tracker/presentation/recurring/RecurringTransactionsViewModel.kt`
- Modify: `app/src/main/java/com/pennywiseai/tracker/presentation/recurring/RecurringTransactionsScreen.kt`
- Modify: `app/src/main/java/com/pennywiseai/tracker/presentation/transactions/TransactionDetailViewModel.kt`
- Modify: `app/src/main/java/com/pennywiseai/tracker/presentation/transactions/TransactionDetailScreen.kt`
- Modify: `app/src/main/java/com/pennywiseai/tracker/presentation/home/HomeViewModel.kt`
- Modify: `app/src/main/java/com/pennywiseai/tracker/presentation/home/HomeScreen.kt`
- Modify: `app/src/main/res/values/strings_analytics.xml`, `strings_home.xml`, `strings_transactions.xml` (plus check localized placeholder parity)

1. **`SubscriptionsScreen` / `SubscriptionsViewModel`:**
   - Remove the `+1 month` future-pushing loop so overdue subscriptions surface clearly.
   - Use `paidDate = maxOfNotNull(sub.lastPaidAt, latestLinkedTxnDate)` so **Paid MMM d** never displays `"Paid null"`.
   - Render distinct **Paid**, **Overdue**, or **Scheduled** indicators + helper subtitle clarifying SMS bill tracking.
2. **`RecurringTransactionsScreen` / `RecurringTransactionsViewModel`:**
   - Add `bankName`, `accountLast4`, `profileId`, and linked-account selector to `RecurringFormState` & `RecurringEditorDialog`.
   - Derive and render **Paused**, **Paid**, **Overdue**, or **Scheduled** status per template using linked transactions (`recurring_template_id`).
3. **`TransactionDetailScreen` / `TransactionDetailViewModel`:**
   - Preserve `subscriptionId` and `recurringTemplateId` inside `saveChanges()`.
   - Add `"Track as Subscription"` and `"Make Recurring Template"` actions (showing existing linked badge if already linked, stepping historical due dates past `today` with `lastPaidAt = txDate`, mapping `CREDIT -> EXPENSE` for templates, and explaining SMS vs manual template behavior).
4. **`HomeScreen` / `HomeViewModel`:**
   - Include active non-paused `recurringTransactions` alongside active `subscriptions` when computing `UpcomingSubscriptionsCard` visibility and `"Next: <Merchant> · <Amount> · <Status>"`, routing taps to Subscriptions or Recurring cleanly.

---

### Task 6: Verification, Documentation, Review & Commit

1. Check localized string placeholder parity (`./init.sh`), `BackupSchemaGuardTest`, `MigrationSqlCompatibilityTest`, `LegacyFolderBackupCleanupTest`, and all new Phase 4 unit tests via `./init.sh`.
2. Run instrumentation test `Migration67To68Test` (and `SqlCipherEncryptionTest`) on the attached Android emulator `Pixel_8(AVD) - 17`.
3. Update `docs/FORK_PLAN.md` (check off completed Phase 4 items).
4. Dispatch a fresh `pro` code reviewer on the full Phase 4 diff; fix any valid findings, re-verify `./init.sh` + instrumentation test if code changes, commit locally on `main` (do not push without user confirmation).
