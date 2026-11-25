# Phase 4 Design: SMS-Aware Recurring, Transfer Pairing & Schema Linking

**Date:** 2026-11-25 (revised after adversarial design review)  
**Status:** Proposed (awaiting user approval)  
**Scope:** Workstream 3 (`docs/FORK_PLAN.md` Phase 4 items 1–8) ahead of `v2.25.0`.

---

## Goals

Make Mudal's recurring bills, recurring templates, and internal transfers accurate for an SMS-first finance tracker:
1. Eliminate double-counting between bank SMS charges and scheduled recurring templates (`RecurringTransactionEntity`), with the bank SMS always winning whether it arrives before or after `RecurringTransactionWorker`.
2. Match incoming SMS charges against both `SubscriptionEntity` (`EXPENSE` & `INCOME`) and `RecurringTransactionEntity` using bank-noise-tolerant merchant normalization, cycle-aligned date windows (including historical inbox scans), and wide price-change tolerance (`0.5x..2.0x`).
3. Persist explicit link columns (`subscription_id`, `recurring_template_id`) on `TransactionEntity` (Room `67 -> 68`, backward-compatible with `v1.x` JSON backups and safely remapped across restore/merge/dedup/delete).
4. Automatically pair tight-window (`±2 min`), different-known-account, exact-amount own-account transfer SMS legs into a single `TransactionType.TRANSFER` row so internal transfers never inflate income or spending and never double-count in balances/totals/discrepancy checks.
5. Surface **Paid / Overdue / Scheduled / Paused** status across Subscriptions and Recurring Templates, let users create either from any existing transaction, and surface the next expected charge on the Home screen.

---

## Architecture & Component Design

### 1. Database Schema, Migration (`67 -> 68`), Backup & Lifecycle Integrity

- **`TransactionEntity`** (`app/src/main/java/com/pennywiseai/tracker/data/database/entity/TransactionEntity.kt`):
  - Add two nullable, Kotlin-defaulted, Room-defaulted fields with non-unique indices:
    - `@ColumnInfo(name = "subscription_id", defaultValue = "NULL") val subscriptionId: Long? = null`
    - `@ColumnInfo(name = "recurring_template_id", defaultValue = "NULL") val recurringTemplateId: Long? = null`
  - Add `Index(value = ["subscription_id"])` and `Index(value = ["recurring_template_id"])` in `@Entity(indices = [...])`.
  - Kotlin `= null` defaults keep `BackupSchemaGuardTest` (`KNOWN_REQUIRED_ENTITY_FIELDS`) and `v1.x` backup compatibility intact.
- **`PennyWiseDatabase`**:
  - Bump `SCHEMA_VERSION = 68` and add `AutoMigration(from = 67, to = 68)`. Commit generated `app/schemas/.../68.json`.
- **Migration Test (`Migration67To68Test`):**
  - Android instrumentation test using `MigrationTestHelper` validating v67 rows migrate cleanly to v68 with `NULL` link columns and indices created (run on attached `Pixel_8(AVD) - 17`, noting SQLite 3.18 / API 26 compatibility check).
- **Backup Export / Import (`BackupExporter.kt`, `BackupImporter.kt`):**
  - `BackupExporter`: null `recurringTemplateId` on `finalTransactions` when `privacy != ExportPrivacy.FULL` (since `exportedRecurringTransactions` is empty in masked/anonymous modes).
  - `BackupImporter` (`replaceAllData` and `mergeData`): import/remap `subscriptions` and `recurringTransactions` **before** `transactions`, building `oldToNewSubscriptionIdMap` and `oldToNewRecurringTemplateIdMap`, and remap/null `subscriptionId` and `recurringTemplateId` on imported `TransactionEntity` rows.
- **Delete & Dedup Integrity:**
  - `SubscriptionRepository.deleteSubscription` and `RecurringTransactionRepository.delete` / `deleteById` clear `transactions.subscription_id = NULL` / `recurring_template_id = NULL` atomically inside a DB transaction before deleting the parent row.
  - `OptimizedSmsReaderWorker` duplicate replacement & `cleanupSmsDuplicates` preserve `duplicate.subscriptionId ?: finalEntity.subscriptionId` and `duplicate.recurringTemplateId ?: finalEntity.recurringTemplateId`.
  - `TransactionDao.deleteRebuildableSmsTransactions`: also purge `'MERGED_TRANSFER_%'` soft-deleted transfer legs so a full inbox rebuild re-ingests and re-pairs both legs cleanly while preserving user deletions (`'DELETED_%'`).

### 2. Shared SMS Reconciliation Service (`RecurringMatcher` + `RecurringReconciliationService`)

One shared service invoked identically by both `SmsTransactionProcessor` and `OptimizedSmsReaderWorker` while holding `BankAccountMergeStore.mutationMutex`:

1. **Merchant Normalization (`normalizeMerchant`):**
   - Check raw `smsBody` first: ignore refund/reversal messages (`eCom/REV` or channel refund cue) so reversals never mark an expense or income schedule as paid.
   - Lowercase merchant name; strip prefixes (`ecom/rev`, `ecom`, `via pos at`, `via atm at`, `atm -`, `tfr`, `vsrpmt-`, `ceft`); strip trailing gateway/card/terminal suffixes (`\s+(co|mo|si|vi)\b.*`, `mastterm.*`, `\s*\(\s*[a-z]{2}\s+\d{5,}`, `\s+\d{5,}\s*$`); replace punctuation (`*`, `-`, `_`, `/`) with spaces and collapse whitespace.
   - Compare substantive tokens (length `>= 3`, non-numeric): match requires exact normalized equality OR all substantive tokens of the shorter name matching full tokens of the longer name (with guard against overly generic 1-word matches like `"google"` matching `"google spotify music"` unless both share the full normalized token set or exact brand phrase).
   - Never match on amount alone. Exclude generic/ambiguous channel placeholders (`"Visa Recurring Payment"`, `"Transfer Inward"`, `"CEFT Transfer"`, `"Bank Interest"`).

2. **Amount, Currency, Account & Direction:**
   - Currency must match case-insensitively (`tx.currency.equals(target.currency, ignoreCase = true)`). Positive amounts only.
   - If the subscription/template specifies `bankName`/`accountLast4` (not `"Manual Entry"` and non-blank `accountLast4`), prefer/require compatible funding account (resolving card `accountNumber` via `cardRepository` if needed) when multiple candidates share a merchant name.
   - **Direction / Type:**
     - `SubscriptionEntity` (`state == ACTIVE`): `direction == EXPENSE` matches `EXPENSE` or `CREDIT`; `direction == INCOME` matches `INCOME` (removing the old `EXPENSE`-only SQL filter so income subscriptions in SMS samples like `LKR 1,000/1,500 credited to AC **7700 for Netflix` reconcile against real bank SMSes rather than silently auto-posting phantoms).
     - `RecurringTransactionEntity` (`isActive == true`): matches `tx.transactionType == template.transactionType || (template.transactionType == EXPENSE && tx.transactionType == CREDIT)`.
   - **Price-change tolerance:** ratio `0.5 <= (txAmount / targetAmount) <= 2.0` (matches Netflix LKR `1,000 -> 1,500` `1.5x`, while rejecting `50` micro-reloads against `1,500`).

3. **Cycle Alignment (Current & Historical Inbox Scans) & Schedule Drift Prevention:**
   - Cycle windows around anchor `cycleDue`:
     - `DAILY`: `[cycleDue - 1d, cycleDue + 1d]`
     - `WEEKLY`: `[cycleDue - 3d, cycleDue + 3d]`
     - `MONTHLY` / `QUARTERLY` / `SEMI-ANNUAL` / `ANNUAL`: `[cycleDue - 7d, cycleDue + 10d]`
   - **Forward & Historical Alignment:**
     - Check active `nextPaymentDate` / `nextDueDate` window first.
     - If `txDate < anchorDue - beforeDays` (e.g., full inbox scan or Force Resync replaying past Sept/Oct SMSes when schedule is already at Nov), step `cycleDue` **backward** by cadence multiples (up to 24 cycles) to find the matching historical cycle window.
     - If `sub.nextPaymentDate == null` (legacy rows), derive a pseudo-window around `txDate` using `sub.lastPaidAt` or existing linked transactions so repeated micro-charges cannot re-match within one cycle.
   - **Anti-double-booking per cycle:** reject a candidate cycle if an active SMS-backed linked transaction (`subscription_id = sub.id` or `recurring_template_id = template.id`, `sms_body IS NOT NULL`) already sits inside that `[cycleDue - beforeDays, cycleDue + afterDays]` window.

4. **Bank Wins & Atomic Updates:**
   - Match order: active Subscriptions first, then active Recurring Templates (at most one target linked per SMS).
   - **Matched Subscription:**
     - Set `isRecurring = true, subscriptionId = sub.id`.
     - After insert commits (`rowId != -1L`), only if the matched cycle is current or newer (`sub.lastPaidAt == null || !txDate.isBefore(sub.lastPaidAt)`):
       - Compute next cycle date from the cycle anchor `cycleDue` (preserving billing day-of-month, never drifting on a late SMS, and stepping past `maxOf(sub.nextPaymentDate ?: txDate, txDate)`).
       - Update `lastPaidAt = txDate`, `nextPaymentDate = newNextDate`, and update `sub.amount = tx.amount` when newer cycles confirm a price change (so subsequent hikes compare against the latest price and totals stay accurate).
     - Update `AddTransactionUseCase`, `MarkSubscriptionPaidUseCase` (`execute` & `linkExisting`), and `GenerateIncomeAutopayUseCase` so all created/linked transactions set `subscriptionId = sub.id`.
   - **Matched Recurring Template:**
     - **Mutex:** `RecurringTransactionRepository.materializeDue()` acquires `BankAccountMergeStore.mutationMutex.withLock` so catch-up materialization never races SMS ingestion, sets `recurringTemplateId = template.id` & `isRecurring = true`, and skips a cycle if an active linked transaction (`recurring_template_id = template.id`) already falls inside `[nextDueDate - beforeDays, nextDueDate + afterDays]`.
     - **In-place Placeholder Replacement (worker ran first, SMS arrived later):** if a materialized placeholder (`recurring_template_id = template.id AND sms_body IS NULL AND is_deleted = 0`) exists in `[cycleDue - beforeDays, cycleDue + afterDays]` and `getTransactionByHash(smsHash) == null`, update that placeholder row in place with the real SMS fields (`amount`, `merchantName`, `dateTime`, `smsBody`, `smsSender`, `bankName`, `accountNumber`, `balanceAfter`, `reference`, `transactionHash = smsHash`, `isRecurring = true`, `recurringTemplateId = template.id`) and adjust balances cleanly via `applyTransactionBalanceShift` (and `recomputeManualBalance` if manual account) or SMS authoritative `balanceAfter` without double-subtracting.
     - Advance `nextDueDate` past `maxOf(template.nextDueDate, cycleDue)` (and update `template.amount` if current/newer cycle settled at a new price) without regressing historical scans.

### 3. Own-Account Transfer Auto-Pairing (`SelfTransferAutoPairer`)

Invoked identically by `SmsTransactionProcessor` and `OptimizedSmsReaderWorker` **under `BankAccountMergeStore.mutationMutex` and AFTER each leg captures/applies its own `INCOME`/`EXPENSE` balance update** (critical because Sampath AC SMSes and Commercial Bank branch/debit SMSes have `balanceAfter == null`, where `BalanceCalculator` treats `TRANSFER` as a no-op!):

- **Eligibility Criteria (all required):**
  - Saved row is `EXPENSE` or `INCOME` (not `TRANSFER`, not `CREDIT`, not `isDeleted`, not `loanId != null`, not `lentSharesTotal != null`, not goal/split-linked, not `eCom/REV` reversal).
  - Query non-deleted opposite-side (`INCOME` <-> `EXPENSE`) candidates in `[dateTime - 120s, dateTime + 120s]` with exact same `amount` (`compareTo == 0`) and case-insensitive `currency`.
  - Resolve effective account identity `(bankName, resolvedAccountLast4)` (resolving card numbers to underlying `CardEntity.accountLast4` when linked): **both legs must have non-blank `accountNumber` AND different account identities** (`acctA != acctB`), ensuring same-account reversals (`Microsoft*Store`, `Google One`, same-account `CEFTLV`/`TRRR`) never pair.
  - **Reference & Transfer Cue Check (prevents coincidental same-amount cross-account merges):**
    - If both legs have non-blank `reference`: references must match.
    - At least one leg carries an explicit transfer cue (`TFR`, `TRANSFER`, `NEFT`, `IMPS`, `RTGS`, `CEFT`, `SLIPS`, `OWNER`) OR neither leg is a `POS` / `ATM` / `eCom` retail spend — covering all 5 Sampath `TFR OWNER -<ref>` <-> `OWNER-` pairs while rejecting accidental `PERSON A` + unrelated ATM/POS coincidences.
  - Pick closest timestamp in `[−120s, +120s]`.
- **Single-Row Consolidation (avoids 2x TRANSFER counting across balances, totals, and discrepancies):**
  - Update the outgoing (`EXPENSE`) row in place to `transactionType = TransactionType.TRANSFER`, `fromAccount = expenseAccount`, `toAccount = incomeAccount`, `fromBankName = expense.bankName`, `toBankName = income.bankName`.
  - Preserve the incoming (`INCOME`) leg's already-recorded `AccountBalanceEntity` snapshot, and mark the incoming leg row `is_deleted = 1, transaction_hash = "MERGED_TRANSFER_" + id + "_" + transactionHash` inside the same DB transaction (`getAnyBySms` skips it on future scans; Force Resync purges `"MERGED_TRANSFER_%"` cleanly).
- **Existing-DB Catch-Up:** run a one-time idempotent pass after migration `67 -> 68` / next SMS scan to pair any already-stored pre-v68 `EXPENSE`/`INCOME` transfer legs meeting the same strict criteria.
- Keep `computeSelfTransferPairs(±60m)` in `TransactionsViewModel` for delayed cross-bank or account-less manual rows.

### 4. UI & UX Updates (`FORK_PLAN.md` items 5 & 8)

1. **Subscriptions (`SubscriptionsScreen.kt`, `SubscriptionsViewModel.kt`):**
   - Remove the `+1 month` loop that masked overdue dates (`SubscriptionsScreen.kt:669-672`).
   - Compute status using `paidDate = maxOfNotNull(sub.lastPaidAt, latestLinkedTxnDate)`:
     - **Paid:** `paidDate` falls in the current billing cycle -> primary badge `"Paid MMM d"` (never `"Paid null"`).
     - **Overdue:** not paid this cycle and `nextPaymentDate < today` -> error badge `"Overdue · MMM d"`.
     - **Scheduled:** `nextPaymentDate >= today` -> `"Due today"` / `"Due tomorrow"` / `"In N days"` / `"MMM d"`.
   - Show subtle subtitle explaining Subscriptions track recurring bills reconciled from bank SMS.

2. **Recurring Templates (`RecurringTransactionsScreen.kt`, `RecurringTransactionsViewModel.kt`):**
   - Observe latest linked transaction per template (`recurring_template_id`); add `bankName`, `accountLast4`, and `profileId` to `RecurringFormState` + account selector in `RecurringEditorDialog`.
   - Display explicit status pill per template: **Paused** (`!isActive`), **Paid** (linked txn in current cycle), **Overdue** (`isActive && nextDueDate < today`), or **Scheduled**, plus subtitle stating manual/cash templates auto-add if no SMS arrives.

3. **Create Subscription or Recurring Template from Transaction Detail (`TransactionDetailScreen.kt`, `TransactionDetailViewModel.kt`):**
   - On eligible active transactions (`transactionType != TRANSFER`), show linked target chip if `subscriptionId != null || recurringTemplateId != null`; otherwise offer **Track as Subscription** and **Make Recurring Template** actions with helper copy distinguishing the two.
   - `saveChanges()` preserves `subscriptionId` and `recurringTemplateId` alongside `loanId`/`lentSharesTotal` (`#688`).
   - Creating from a historical transaction advances `nextDueDate`/`nextPaymentDate` past `today` and sets `lastPaidAt = txDate` (avoiding unwanted overdue/catch-up floods), and maps `TransactionType.CREDIT` -> `EXPENSE` for recurring templates.

4. **Home Screen Next Expected Charge (`HomeScreen.kt`, `HomeViewModel.kt`):**
   - Show `UpcomingSubscriptionsCard` whenever either active `subscriptions` OR active `recurringTemplates` (non-paused, non-hidden/ended) is non-empty.
   - Compute earliest upcoming/overdue outflow (excluding paused/ended) across both lists and display a concise `"Next: <Merchant> · <Amount> · <Status>"` line, routing taps to `SubscriptionsScreen` or `RecurringTransactionsScreen` based on whether templates/subscriptions own the displayed schedule.

---

## Verification Plan

1. **Unit Tests (JVM, with real fixtures from `docs/lk-samples/sampath.txt` & `commercial-bank.txt`):**
   - `RecurringMatcherTest`: merchant normalization (`Spotify`, `Dialog`, `POS`), `eCom/REV` reversals ignored, cross-merchant same-amount rejected, Netflix `1,000 -> 1,500` hike matched (updating amount to `1,500` so subsequent `2,100` matches), micro-charge `50` vs `1,500` rejected, historical cycle alignment & anti-drift.
   - `RecurringReconciliationTest`: template + SMS in same cycle -> 1 transaction; worker materializes placeholder first then SMS arrives -> 1 transaction (bank wins + balance shift accurate); unmatched cycle -> `materializeDue()` creates transaction with `recurringTemplateId`; delete parent clears child IDs.
   - `SelfTransferAutoPairerTest`: all 5 Sampath transfer pairs consolidate into 1 `TRANSFER` row with zero net income/spend and preserved balances (both arrival orders); same-account reversals (`Microsoft*Store` `346.54`, `Google One` `150.00`, same-account `CEFTLV`/`TRRR` `.56`) and coincidental third-party credits (`PERSON A` + ATM/POS) rejected.
   - `BackupImporterRecurringLinkTest`: `replaceAllData` and `mergeData` remap `subscriptionId`/`recurringTemplateId` cleanly and drop masked `recurringTemplateId`.
2. **Instrumentation Migration Test (AVD):**
   - `Migration67To68Test` on `Pixel_8(AVD) - 17` verifying v67 -> v68 `AutoMigration` preserves existing transactions with `NULL` link columns and creates indices.
3. **Full Gate:**
   - `./init.sh` + fresh `pro` code review before committing on `main`.
