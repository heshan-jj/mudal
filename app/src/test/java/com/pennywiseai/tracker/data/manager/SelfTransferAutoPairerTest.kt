package com.pennywiseai.tracker.data.manager

import androidx.room.withTransaction
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.entity.CardEntity
import com.pennywiseai.tracker.data.database.entity.CardType
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.CardRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDateTime

class SelfTransferAutoPairerTest {

    private val transactionRepository = mockk<TransactionRepository>(relaxed = true)
    private val cardRepository = mockk<CardRepository>(relaxed = true)
    private val database = mockk<PennyWiseDatabase>(relaxed = true)

    private val pairer = SelfTransferAutoPairer(
        transactionRepository = transactionRepository,
        cardRepository = cardRepository,
        database = database
    )

    @Before
    fun setUp() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { database.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            secondArg<suspend () -> Any?>().invoke()
        }
        coEvery { cardRepository.getCard(any(), any()) } returns null
    }

    @After
    fun tearDown() {
        unmockkStatic("androidx.room.RoomDatabaseKt")
    }

    private fun smsTx(
        id: Long,
        type: TransactionType,
        amount: String,
        account: String,
        merchant: String,
        smsBody: String,
        dateTime: LocalDateTime,
        reference: String? = null,
        bankName: String = "Sampath Bank"
    ) = TransactionEntity(
        id = id,
        amount = BigDecimal(amount),
        merchantName = merchant,
        category = if (type == TransactionType.INCOME) "Income" else "Transfers",
        transactionType = type,
        dateTime = dateTime,
        smsBody = smsBody,
        bankName = bankName,
        smsSender = "SAMPATHTXN",
        accountNumber = account,
        transactionHash = "hash-$id",
        reference = reference,
        currency = "LKR"
    )

    @Test
    fun `all five Sampath transfer pairs consolidate cleanly in sweep with zero net income or spend`() = runBlocking {
        val dt1 = LocalDateTime.of(2026, 9, 17, 10, 5, 33)
        val dt2 = LocalDateTime.of(2026, 10, 20, 9, 42, 2)
        val dt3 = LocalDateTime.of(2026, 10, 20, 9, 34, 48)
        val dt4 = LocalDateTime.of(2026, 10, 18, 8, 20, 13)
        val dt5 = LocalDateTime.of(2026, 10, 13, 8, 48, 22)

        val rows = mutableListOf(
            smsTx(1, TransactionType.EXPENSE, "20000.00", "7700", "TFR OWNER", "LKR 20,000.00 debited from AC **7700 for TFR OWNER -000086201821", dt1, "000086201821"),
            smsTx(2, TransactionType.INCOME, "20000.00", "9786", "OWNER", "LKR 20,000.00 credited to AC **9786 for OWNER-", dt1, null),
            smsTx(3, TransactionType.EXPENSE, "5000.00", "9786", "TFR OWNER", "LKR 5,000.00 debited from AC **9786 for TFR OWNER -000090445607", dt2, "000090445607"),
            smsTx(4, TransactionType.INCOME, "5000.00", "7700", "OWNER", "LKR 5,000.00 credited to AC **7700 for OWNER-", dt2, null),
            smsTx(5, TransactionType.EXPENSE, "20000.00", "9786", "TFR OWNER", "LKR 20,000.00 debited from AC **9786 for TFR OWNER -000090444609", dt3, "000090444609"),
            smsTx(6, TransactionType.INCOME, "20000.00", "7700", "OWNER", "LKR 20,000.00 credited to AC **7700 for OWNER-", dt3, null),
            smsTx(7, TransactionType.EXPENSE, "50000.00", "7700", "TFR OWNER", "LKR 50,000.00 debited from AC **7700 for TFR OWNER -000090203456", dt4, "000090203456"),
            smsTx(8, TransactionType.INCOME, "50000.00", "9786", "OWNER", "LKR 50,000.00 credited to AC **9786 for OWNER-", dt4, null),
            smsTx(9, TransactionType.EXPENSE, "1127.00", "9786", "TFR OWNER", "LKR 1,127.00 debited from AC **9786 for TFR OWNER -000089580155", dt5, "000089580155"),
            smsTx(10, TransactionType.INCOME, "1127.00", "7700", "OWNER", "LKR 1,127.00 credited to AC **7700 for OWNER-", dt5, null)
        )
        coEvery { transactionRepository.getActiveSmsIncomeExpenseTransactions() } returns rows.toList()
        val updatedRows = mutableMapOf<Long, TransactionEntity>()
        coEvery { transactionRepository.updateTransaction(any()) } answers {
            val t = firstArg<TransactionEntity>()
            updatedRows[t.id] = t
        }

        val pairedCount = pairer.sweepExistingUnpairedTransfers()

        assertEquals(5, pairedCount)
        val activeTransfers = rows.map { updatedRows[it.id] ?: it }.filter { !it.isDeleted }
        assertEquals(5, activeTransfers.size)
        assertTrue(activeTransfers.all { it.transactionType == TransactionType.TRANSFER })
        assertEquals("7700", updatedRows[1L]?.fromAccount)
        assertEquals("9786", updatedRows[1L]?.toAccount)
        assertTrue(updatedRows[2L]!!.isDeleted)
        assertTrue(updatedRows[2L]!!.transactionHash.startsWith("MERGED_TRANSFER_1_"))
    }

    @Test
    fun `credit arriving before debit pairs cleanly and keeps outgoing leg as TRANSFER`() = runBlocking {
        val dt = LocalDateTime.of(2026, 10, 18, 8, 20, 13)
        val incomeLeg = smsTx(20L, TransactionType.INCOME, "50000.00", "9786", "OWNER", "LKR 50,000.00 credited to AC **9786 for OWNER-", dt)
        val debitLeg = smsTx(21L, TransactionType.EXPENSE, "50000.00", "7700", "TFR OWNER", "LKR 50,000.00 debited from AC **7700 for TFR OWNER -000090203456", dt, "000090203456")

        coEvery { transactionRepository.findSelfTransferCandidates(BigDecimal("50000.00"), any(), any(), 21L) } returns listOf(incomeLeg)

        val paired = pairer.tryPairSavedTransaction(debitLeg)

        assertTrue(paired)
        coVerify {
            transactionRepository.updateTransaction(
                match {
                    it.id == 21L &&
                        it.transactionType == TransactionType.TRANSFER &&
                        it.fromAccount == "7700" &&
                        it.toAccount == "9786" &&
                        !it.isDeleted
                }
            )
        }
        coVerify {
            transactionRepository.updateTransaction(
                match {
                    it.id == 20L &&
                        it.isDeleted &&
                        it.transactionHash == "MERGED_TRANSFER_21_hash-20"
                }
            )
        }
    }

    @Test
    fun `sweep prefers tightest time gap instead of letting earlier coincidental row steal exact match`() = runBlocking {
        val t0 = LocalDateTime.of(2026, 10, 20, 10, 0, 0)
        val t40 = t0.plusSeconds(40)
        val earlierIncome = smsTx(40L, TransactionType.INCOME, "5000.00", "9786", "OWNER", "LKR 5,000.00 credited to AC **9786 for OWNER-", t0)
        val exactDebit = smsTx(41L, TransactionType.EXPENSE, "5000.00", "7700", "TFR OWNER", "LKR 5,000.00 debited from AC **7700 for TFR OWNER", t40)
        val exactIncome = smsTx(42L, TransactionType.INCOME, "5000.00", "9786", "OWNER", "LKR 5,000.00 credited to AC **9786 for OWNER-", t40)

        coEvery { transactionRepository.getActiveSmsIncomeExpenseTransactions() } returns listOf(earlierIncome, exactDebit, exactIncome)
        val updatedRows = mutableMapOf<Long, TransactionEntity>()
        coEvery { transactionRepository.updateTransaction(any()) } answers {
            val t = firstArg<TransactionEntity>()
            updatedRows[t.id] = t
        }

        val pairedCount = pairer.sweepExistingUnpairedTransfers()

        assertEquals(1, pairedCount)
        assertTrue(updatedRows[42L]?.isDeleted == true)
        assertEquals("MERGED_TRANSFER_41_hash-42", updatedRows[42L]?.transactionHash)
        assertFalse(40L in updatedRows)
    }

    @Test
    fun `rejects same-account reversals, coincidental non-transfer credits, and card-to-same-account`() = runBlocking {
        // 1. Same-account eCom/REV reversal (Microsoft Store 346.54 on AC **7700)
        val dtRev = LocalDateTime.of(2026, 10, 18, 15, 54, 18)
        val debitMs = smsTx(30L, TransactionType.EXPENSE, "346.54", "7700", "Microsoft*Store", "LKR 346.54 debited from AC **7700 for eCom Microsoft*Store Si MASTTERM", dtRev)
        val revMs = smsTx(31L, TransactionType.INCOME, "346.54", "7700", "Microsoft*Store", "LKR 346.54 credited to AC **7700 for eCom/REV Microsoft*Store MASTTERM", dtRev.plusSeconds(12))

        coEvery { transactionRepository.findSelfTransferCandidates(BigDecimal("346.54"), any(), any(), 31L) } returns listOf(debitMs)
        assertFalse(pairer.tryPairSavedTransaction(revMs))

        // 2. Coincidental ATM withdrawal + PERSON A credit on different accounts without transfer cue
        val dtCoin = LocalDateTime.of(2026, 9, 19, 21, 24, 10)
        val personCredit = smsTx(32L, TransactionType.INCOME, "3600.00", "7700", "PERSON A", "LKR 3,600.00 credited to AC **7700 for PERSON A", dtCoin)
        val atmDebit = smsTx(33L, TransactionType.EXPENSE, "3600.00", "9786", "ATM - ICBS", "LKR 3,600.00 debited from AC **9786 via ATM at ICBS CASH SET", dtCoin.plusSeconds(45))

        coEvery { transactionRepository.findSelfTransferCandidates(BigDecimal("3600.00"), any(), any(), 33L) } returns listOf(personCredit)
        assertFalse(pairer.tryPairSavedTransaction(atmDebit))

        // 3. Debit card #4101 linked to account 3415 + INCOME on account 3415 -> same underlying account
        val dtCard = LocalDateTime.of(2026, 10, 20, 12, 0, 0)
        coEvery { cardRepository.getCard("Commercial Bank", "4101") } returns CardEntity(
            cardLast4 = "4101",
            bankName = "Commercial Bank",
            accountLast4 = "3415",
            cardType = CardType.DEBIT
        )
        val cardSpend = smsTx(34L, TransactionType.EXPENSE, "1500.00", "4101", "TFR", "Purchase for LKR 1,500.00 #4101", dtCard, bankName = "Commercial Bank")
        val acctCredit = smsTx(35L, TransactionType.INCOME, "1500.00", "3415", "OWNER", "Credit for Rs. 1,500.00", dtCard.plusSeconds(10), bankName = "Commercial Bank")

        coEvery { transactionRepository.findSelfTransferCandidates(BigDecimal("1500.00"), any(), any(), 35L) } returns listOf(cardSpend)
        assertFalse(pairer.tryPairSavedTransaction(acctCredit))
    }

    @Test
    fun `pairs cross-bank Sri Lankan CEFTLV transfer with different bank references 5 minutes apart`() = runBlocking {
        val dt = LocalDateTime.of(2026, 10, 22, 11, 15, 0)
        val sampathDebit = smsTx(
            id = 50L,
            type = TransactionType.EXPENSE,
            amount = "15000.00",
            account = "7700",
            merchant = "CEFTLV",
            smsBody = "LKR 15,000.00 debited from AC **7700 for CEFTLV -REF111",
            dateTime = dt,
            reference = "REF111",
            bankName = "Sampath Bank"
        )
        val combankCredit = smsTx(
            id = 51L,
            type = TransactionType.INCOME,
            amount = "15000.00",
            account = "3415",
            merchant = "CEFTS Credit",
            smsBody = "Your A/C **3415 credited with LKR 15,000.00 via CEFTS Ref CB999",
            dateTime = dt.plusMinutes(5),
            reference = "CB999",
            bankName = "Commercial Bank"
        )

        coEvery { transactionRepository.findSelfTransferCandidates(BigDecimal("15000.00"), any(), any(), 51L) } returns listOf(sampathDebit)

        assertTrue(pairer.tryPairSavedTransaction(combankCredit))
    }
}
