// Mudal fork: modified from PennyWise AI (AGPL-3.0-only) on 2026-10-21 - see NOTICE.
import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.SampathBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.*
import java.math.BigDecimal

class SampathBankParserTest {

    @TestFactory
    fun `sampath bank parser handles account and card transactions`(): List<DynamicTest> {
        val parser = SampathBankParser()

        val testCases = listOf(
            ParserTestCase(
                name = "Account credit (LKR)",
                message = "LKR 5,000.00 credited to AC **8758 for PICKME - 37419306\n22/05/2026 23:36:52\nTo Inq Call 0112303050\nGet protected -Do not Share OTP",
                sender = "SAMPATHTXN",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5000.00"),
                    currency = "LKR",
                    type = TransactionType.INCOME,
                    merchant = "PICKME",
                    reference = "37419306",
                    accountLast4 = "8758"
                )
            ),
            ParserTestCase(
                name = "Account debit (LKR)",
                message = "LKR 4,025.00 debited from AC **8758 for WPY_TAOA_041772_WLT@0347310\n18/05/2026 06:56:52\nTo Inq Call 0112303050\nGet protected -Do not Share OTP",
                sender = "SAMPATHTXN",
                expected = ExpectedTransaction(
                    amount = BigDecimal("4025.00"),
                    currency = "LKR",
                    type = TransactionType.EXPENSE,
                    merchant = "WPY_TAOA_041772_WLT@0347310",
                    accountLast4 = "8758"
                )
            ),
            ParserTestCase(
                name = "International credit (GBP)",
                message = "GBP 1,000.00 credited to AC **5012 for Remittance ID : [IR26GBP36623] : REALIZE\n14/05/2026 13:55:02\nTo Inq Call 0112303050\nGet protected -Do not Share OTP",
                sender = "SAMPATHTXN",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1000.00"),
                    currency = "GBP",
                    type = TransactionType.INCOME,
                    merchant = "REALIZE",
                    reference = "IR26GBP36623",
                    accountLast4 = "5012"
                )
            ),
            ParserTestCase(
                name = "Credit card payment (LKR)",
                message = "Cr Crd no..**0282 Auth Pmt LKR 2,100.00 at SPICE ASIA - DELIVERY Avl Bal LKR 250,000.00 Enq Call 0112300604\n- Sampath Bank 23-MAY",
                sender = "SAMPCCTXN",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2100.00"),
                    currency = "LKR",
                    type = TransactionType.EXPENSE,
                    merchant = "SPICE ASIA - DELIVERY",
                    accountLast4 = "0282",
                    balance = BigDecimal("250000.00"),
                    isFromCard = true
                )
            ),
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
            ),
            ParserTestCase(
                name = "POS debit purchase",
                message = "LKR 2,160.00 debited from AC **7700 via POS at KFC-KURUNEGALA KU 80401564\n14/09/2026 12:49:48\nTo Inq Call 0112303050\nGet protected -Do not Share OTP",
                sender = "SAMPATHTXN",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2160.00"),
                    currency = "LKR",
                    type = TransactionType.EXPENSE,
                    merchant = "KFC-KURUNEGALA",
                    reference = "80401564",
                    accountLast4 = "7700",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "POS debit with hyphenated merchant name",
                message = "LKR 440.00 debited from AC **7700 via POS at PERERA - SONS BAKERS ( MA 51001413\n17/09/2026 08:04:24\nTo Inq Call 0112303050\nGet protected -Do not Share OTP",
                sender = "SAMPATHTXN",
                expected = ExpectedTransaction(
                    amount = BigDecimal("440.00"),
                    currency = "LKR",
                    type = TransactionType.EXPENSE,
                    merchant = "PERERA - SONS BAKERS",
                    reference = "51001413",
                    accountLast4 = "7700",
                    isFromCard = true
                )
            ),
            ParserTestCase(
                name = "eCom debit purchase",
                message = "LKR 377.75 debited from AC **7700 for eCom PickMe Co 76134439\n08/10/2026 11:58:45\nTo Inq Call 0112303050\nGet protected -Do not Share OTP",
                sender = "SAMPATHTXN",
                expected = ExpectedTransaction(
                    amount = BigDecimal("377.75"),
                    currency = "LKR",
                    type = TransactionType.EXPENSE,
                    merchant = "PickMe",
                    accountLast4 = "7700"
                )
            ),
            ParserTestCase(
                name = "eCom reversal refund",
                message = "LKR 346.54 credited to AC **7700 for eCom/REV Microsoft*Store MASTTERM\n18/10/2026 15:54:30\nTo Inq Call 0112303050\nGet protected -Do not Share OTP",
                sender = "SAMPATHTXN",
                expected = ExpectedTransaction(
                    amount = BigDecimal("346.54"),
                    currency = "LKR",
                    type = TransactionType.INCOME,
                    merchant = "Microsoft*Store",
                    accountLast4 = "7700"
                )
            ),
            ParserTestCase(
                name = "Outward transfer with reference",
                message = "LKR 20,000.00 debited from AC **7700 for TFR OWNER -000086201821\n17/09/2026 10:05:33\nTo Inq Call 0112303050\nGet protected -Do not Share OTP",
                sender = "SAMPATHTXN",
                expected = ExpectedTransaction(
                    amount = BigDecimal("20000.00"),
                    currency = "LKR",
                    type = TransactionType.EXPENSE,
                    merchant = "TFR OWNER",
                    reference = "000086201821",
                    accountLast4 = "7700"
                )
            ),
            ParserTestCase(
                name = "Amount without leading zero (CEFT debit)",
                message = "LKR .56 debited from AC **9786 for CEFTLV-01-22312167-02-6010-03-0000-04-00\n14/10/2026 18:50:42\nTo Inq Call 0112303050\nGet protected -Do not Share OTP",
                sender = "SAMPATHTXN",
                expected = ExpectedTransaction(
                    amount = BigDecimal("0.56"),
                    currency = "LKR",
                    type = TransactionType.EXPENSE,
                    merchant = "CEFT Transfer",
                    reference = "22312167",
                    accountLast4 = "9786"
                )
            ),
            ParserTestCase(
                name = "TRRR inward transfer with leading zero",
                message = "LKR .56 credited to AC **9786 for TRRR/000000554871/14-10-2026 13:20:43/IF\n14/10/2026 18:50:43\nTo Inq Call 0112303050\nGet protected -Do not Share OTP",
                sender = "SAMPATHTXN",
                expected = ExpectedTransaction(
                    amount = BigDecimal("0.56"),
                    currency = "LKR",
                    type = TransactionType.INCOME,
                    merchant = "Transfer Inward",
                    reference = "000000554871",
                    accountLast4 = "9786"
                )
            ),
            ParserTestCase(
                name = "Bank interest payment",
                message = "LKR 155.05 credited to AC **9786 for Interest: 1xxx9786:Int.Pd:20-08-2026 to 19-09-2026\n20/09/2026 02:38:25\nTo Inq Call 0112303050\nGet protected -Do not Share OTP",
                sender = "SAMPATHTXN",
                expected = ExpectedTransaction(
                    amount = BigDecimal("155.05"),
                    currency = "LKR",
                    type = TransactionType.INCOME,
                    merchant = "Bank Interest",
                    accountLast4 = "9786"
                )
            ),
            ParserTestCase(
                name = "Visa recurring payment",
                message = "LKR 50.00 debited from AC **7700 for VSRPMT-000018281730\n13/10/2026 08:51:26\nTo Inq Call 0112303050\nGet protected -Do not Share OTP",
                sender = "SAMPATHTXN",
                expected = ExpectedTransaction(
                    amount = BigDecimal("50.00"),
                    currency = "LKR",
                    type = TransactionType.EXPENSE,
                    merchant = "Visa Recurring Payment",
                    reference = "000018281730",
                    accountLast4 = "7700"
                )
            ),
            ParserTestCase(
                name = "Transfer label with trailing hyphen stripped",
                message = "LKR 20,000.00 credited to AC **7700 for OWNER-\n20/10/2026 09:34:48\nTo Inq Call 0112303050\nGet protected -Do not Share OTP",
                sender = "SAMPATHTXN",
                expected = ExpectedTransaction(
                    amount = BigDecimal("20000.00"),
                    currency = "LKR",
                    type = TransactionType.INCOME,
                    merchant = "OWNER",
                    accountLast4 = "7700"
                )
            )
        )

        val handleChecks = listOf(
            "SAMPATHTXN" to true,
            "SAMPCCTXN" to true,
            "AD-SAMPATHTXN" to true,
            "HDFCBK" to false,
            "UNKNOWN" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "Sampath Bank Parser"
        )
    }
}
