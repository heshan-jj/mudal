// Mudal fork: new file in Mudal, a fork of PennyWise AI (AGPL-3.0-only), 2026-10-21 - see NOTICE.
import com.pennywiseai.parser.core.TransactionType
import com.pennywiseai.parser.core.bank.CommercialBankParser
import com.pennywiseai.parser.core.test.ExpectedTransaction
import com.pennywiseai.parser.core.test.ParserTestCase
import com.pennywiseai.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class TestCommercialBankParser {

    @TestFactory
    fun `commercial bank parser handles card purchases atm withdrawals and branch credits`(): List<DynamicTest> {
        val parser = CommercialBankParser()

        val testCases = listOf(
            ParserTestCase(
                name = "Card purchase with merchant",
                message = "Dear Cardholder, Purchase at TERRA LOUNGE CAFE & RESKURUNEGALA KGLK for LKR 1,144.00 on 07/03/26 04:25 PM has been authorised on your debit card ending #4101.",
                sender = "COMBANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1144.00"),
                    currency = "LKR",
                    type = TransactionType.EXPENSE,
                    merchant = "TERRA LOUNGE CAFE & RESKURUNEGALA KGLK",
                    accountLast4 = "4101"
                )
            ),
            ParserTestCase(
                name = "Card purchase at bakery with LK suffix stripped",
                message = "Dear Cardholder, Purchase at PERERA & SONS BAKERS PVT HOMAGAMA LK for LKR 300.00 on 03/02/26 11:37 AM has been authorised on your debit card ending #4101.",
                sender = "COMBANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("300.00"),
                    currency = "LKR",
                    type = TransactionType.EXPENSE,
                    merchant = "PERERA & SONS BAKERS PVT HOMAGAMA",
                    accountLast4 = "4101"
                )
            ),
            ParserTestCase(
                name = "ATM withdrawal",
                message = "Withdrawal at ICBS AKUB3 KU BZ3 LK for LKR 1,500.00 on 17/02/26 09:22 AM from card ending #4101.",
                sender = "COMBANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1500.00"),
                    currency = "LKR",
                    type = TransactionType.EXPENSE,
                    merchant = "ATM - ICBS AKUB3 KU BZ3",
                    accountLast4 = "4101"
                )
            ),
            ParserTestCase(
                name = "Branch credit (Rs. prefix)",
                message = "Credit for Rs. 400.00 to 8000123415 at 11:17 at DIGITAL BANKING DIVISION",
                sender = "COMBANK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("400.00"),
                    currency = "LKR",
                    type = TransactionType.INCOME,
                    merchant = "DIGITAL BANKING DIVISION",
                    accountLast4 = "3415"
                )
            )
        )

        val handleChecks = listOf(
            "COMBANK" to true,
            "CBC" to true,
            "COMBNK" to true,
            "AD-COMBANK" to true,
            "COMMERCIAL" to true,
            "HDFCBK" to false,
            "UNKNOWN" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleChecks,
            suiteName = "Commercial Bank Parser"
        )
    }
}
