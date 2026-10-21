<!-- Mudal fork: new file in Mudal, a fork of PennyWise AI (AGPL-3.0-only), 2026-10-21 - see NOTICE. -->
# LK samples — real bank SMS from the owner

Real (masked) transaction SMS collected from the owner's phone, used to write
and audit parsers. See `docs/FORK_PLAN.md` (Phase 3) and `docs/adding-bank-parsers.md`.

**Scope: Sampath Bank and Commercial Bank of Ceylon only, for now.** Other Sri
Lankan banks are deferred — do not implement parsers for them until the owner
supplies samples.

| Bank | File | Samples | Parser status |
|---|---|---|---|
| Sampath Bank | [`sampath.txt`](sampath.txt) | 70 | `SampathBankParser` exists — needs an audit against these samples |
| Commercial Bank of Ceylon | [`commercial-bank.txt`](commercial-bank.txt) | 18 | No parser yet (Phase 3 priority #1) |

## Deferred banks (no samples collected yet)

Seylan, HNB, Bank of Ceylon, People's Bank, DFCC, Pan Asia. For each, the
sample types needed are the same: debit card/POS purchase, ATM withdrawal,
CEFT/transfers in and out, salary/credit, credit-card spend, declined/failed,
balance enquiry, and OTP/promo messages as negatives.

## Open questions (blocking for fixtures)

**Sampath Bank** — the real sender ID is not confirmed (assumed `SAMPATHTXN` from
the existing parser docs). The samples also use channel wording the parser does
not handle yet: `via ATM at <loc> CASH SET`, `via POS at <merchant> <ref>`,
`for TFR <name> -<ref>`, `for TRRR/...`, `for CEFTLV-01-...`, `for VSRPMT-<ref>`,
`for Interest: ...`, and `for eCom/REV <merchant>` (reversal credits).

**Commercial Bank of Ceylon** — the real sender ID is not confirmed. Amount
prefixes differ between message types (`LKR` on card SMS, `Rs.` on branch
credits), dates are `dd/MM/yy hh:mm AM/PM`, card SMS say "authorised" rather
than "debited", and the branch credits carry no date at all.

## Privacy

These files contain real merchant names, locations, amounts and timestamps.
Personal names have been replaced with placeholders (`OWNER`, `PERSON A`,
`PERSON B`) and account numbers are reduced to their last four digits, but the
spending pattern is still a real one. Keep the repository private, or scrub
further, before publishing. Sample messages must never be copied into app
code, tests, or logs (repo rule 4: no PII anywhere).
