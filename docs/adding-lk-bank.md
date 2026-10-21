# How to add a Sri Lankan bank parser

Mudal only ships parsers for banks whose SMS format we have **real, masked
samples** for. Rule 3 of the fork plan is absolute: never invent a bank SMS
format. A parser written from a guess will silently eat the wrong messages and
miss the right ones.

This guide is the Sri Lankan version of [`adding-bank-parsers.md`](adding-bank-parsers.md).
It assumes you have read that one.

---

## 1. Collect the samples (the owner does this)

For each bank, open the SMS app on your own phone and collect the messages the
bank sends for **every kind of transaction you actually get**:

| What to collect | Why it matters |
|---|---|
| Debit / credit alerts | the core format |
| Card / POS purchases | often a different sender (`SAMPCCTXN` vs `SAMPATHTXN`) |
| ATM withdrawals | often carries a location and a `CASH SET` suffix |
| Bank transfers in and out | carries references, sometimes a different sender |
| Standing payments / mandates | recurring debits with their own wording |
| Interest, refunds, reversals | credits that are not income |
| Failed / declined transactions | must **not** create a transaction |
| Balance enquiries | must **not** create a transaction |
| One OTP and one promotional message | negatives — the parser must ignore them |

### Masking rules

The repository is **public**. Mask before you commit:

| Field | What to do | Example |
|---|---|---|
| Account number | keep the last 4 digits only | `AC **7700` |
| Card number | keep the last 4 digits only | `card ending #4101` |
| Names of people | replace with a placeholder | `OWNER`, `PERSON A` |
| Phone numbers | replace all but the last 2 digits | `8XXXXXXXX15` |
| Amounts | keep them — they are what makes a fixture realistic | `LKR 1,005.00` |
| Merchant names | keep them — same reason | `SERENTY INN HOMAGAMA LK` |
| Dates and times | keep them | `08/10/2026 15:42:22` |

> Amounts, merchants and timestamps are a spending fingerprint. That is a
> deliberate trade-off: a fixture with fake numbers does not exercise the
> parser's real edge cases (leading-zero amounts, truncated merchant strings,
> duplicated references). If you would rather not publish yours, keep the raw
> file outside the repository and add only a redacted copy.

### Where the samples go

```
docs/lk-samples/<bank>.txt
```

- One SMS per block, blocks separated by a blank line.
- A `#` header at the top with the bank name, the **real sender ID**, the
  accounts involved, the date format and anything unusual you noticed.
- The same file is the source of truth for the parser's fixtures.

See [`lk-samples/README.md`](lk-samples/README.md) for the banks currently in
scope and the questions still open.

---

## 2. Write the parser

Follow the smallest existing pattern — copy
`parser-core/src/main/kotlin/com/pennywiseai/parser/core/bank/SampathBankParser.kt`
and adapt it. A parser needs:

```kotlin
class MyBankParser : BankParser() {
    override fun getBankName() = "My Bank"
    override fun getCurrency() = "LKR"
    override fun canHandle(sender: String) = normalized.contains("MYBANK")

    override fun isTransactionMessage(message: String) = /* ... */
    override fun extractAmount(message: String): BigDecimal? = /* ... */
    override fun extractTransactionType(message: String) = /* ... */
    override fun extractMerchant(message: String, sender: String) = /* ... */
    override fun extractAccountLast4(message: String) = /* ... */
}
```

Rules that keep the parsers consistent:

- **Amount:** accept `LKR 1,005.00`, `Rs. 1,005.00`, `Rs 1,005.00` and
  `LKR .56` (no leading zero). Strip the thousands separator.
- **Type:** `credited to` is income, `debited from` is an expense — but see the
  caveat in the Sampath section below.
- **Merchant:** the text after `for `, up to the end of the line. Strip a
  trailing ` - <ref>`, but do not strip a ` - ` that is part of the business
  name (`PERERA - SONS BAKERS`).
- **Account:** `AC **7700` and `card ending #4101` both yield the last 4.
- **Negatives:** OTP, promo and balance-enquiry messages must return `null`.

### The "credit that is not income" trap

Sampath's format `credited to AC **7700 for <note>` is *syntactically* a
credit, but the note is often the user's own transfer label (`Netflix`,
`Seylan card`, `Expenses`) — an internal move, not income. The parser reports
what the bank said; reconciling it is the app's job (fork plan, Phase 4). Do
not try to "fix" this in the parser.

---

## 3. Write the fixtures

Every sample becomes a test case in
`parser-core/src/test/kotlin/Test<Bank>BankParser.kt`, using the shared
`ParserTestUtils` helpers (see
[`parser-test-standards.md`](parser-test-standards.md)):

```kotlin
ParserTestCase(
    name = "ATM cash withdrawal",
    message = "LKR 1,005.00 debited from AC **7700 via ATM at Makumbura MMC - CASH SET\n08/10/2026 15:42:22\nTo Inq Call 0112303050\nGet protected -Do not Share OTP",
    sender = "SAMPATHTXN",
    expected = ExpectedTransaction(
        amount = BigDecimal("1005.00"),
        currency = "LKR",
        type = TransactionType.EXPENSE,
        merchant = "ATM - Makumbura MMC",
        accountLast4 = "7700"
    )
)
```

Cover at least: one of every message shape, the negative cases, and every
edge case you found in the samples (leading-zero amounts, truncated merchant
strings, missing fields).

---

## 4. Register and check for overlap

Add the parser to `BankParserFactory`. Dispatch is in list order via
`canHandle`, so **one sender must not match two parsers** — the factory test
fails if it does. Put the more specific sender first.

Then regenerate the catalogue:

```bash
./scripts/update-supported-banks.sh
```

That rewrites `docs/supported-banks.json`, the README bank block, the web
resources and the store listing coverage claim. Commit the regenerated files —
CI fails if they are stale.

---

## 5. Verify

```bash
./gradlew :parser-core:jvmTest
```

Then, on a real phone with a real SIM: send yourself a transaction from that
bank, let the SMS arrive, and confirm the app creates exactly one transaction
with the right amount, merchant and account. Fix the parser from what you see.
