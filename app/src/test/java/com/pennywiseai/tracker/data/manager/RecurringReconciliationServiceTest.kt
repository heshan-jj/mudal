package com.pennywiseai.tracker.data.manager

import androidx.room.withTransaction
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.dao.RecurringTransactionDao
import com.pennywiseai.tracker.data.database.dao.SubscriptionDao
import com.pennywiseai.tracker.data.database.dao.TransactionDao
import com.pennywiseai.tracker.data.database.entity.RecurringFrequency
import com.pennywiseai.tracker.data.database.entity.RecurringTransactionEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionDirection
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionState
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.repository.AccountBalanceRepository
import com.pennywiseai.tracker.data.repository.RecurringTransactionRepository
import com.pennywiseai.tracker.data.repository.SubscriptionRepository
import com.pennywiseai.tracker.data.repository.TransactionRepository
import com.pennywiseai.tracker.domain.service.RecurringMatcher
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime

class RecurringReconciliationServiceTest {

    private val subscriptionRepository = mockk<SubscriptionRepository>(relaxed = true)
    private val recurringTransactionRepository = mockk<RecurringTransactionRepository>(relaxed = true)
    private val transactionRepository = mockk<TransactionRepository>(relaxed = true)
    private val accountBalanceRepository = mockk<AccountBalanceRepository>(relaxed = true)
    private val database = mockk<PennyWiseDatabase>(relaxed = true)

    private val service = RecurringReconciliationService(
        subscriptionRepository = subscriptionRepository,
        recurringTransactionRepository = recurringTransactionRepository,
        transactionRepository = transactionRepository,
        accountBalanceRepository = accountBalanceRepository,
        database = database
    )

    @Before
    fun setUp() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { database.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            secondArg<suspend () -> Any?>().invoke()
        }
    }

    @After
    fun tearDown() {
        unmockkStatic("androidx.room.RoomDatabaseKt")
    }

    @Test
    fun `matches template and advances nextDueDate so worker will not double book`() = runBlocking {
        val ceb = RecurringTransactionEntity(
            id = 21L,
            uid = "tpl-ceb",
            merchantName = "CEB Electricity",
            amount = BigDecimal("4000.00"),
            currency = "LKR",
            transactionType = TransactionType.EXPENSE,
            frequency = RecurringFrequency.MONTHLY,
            dayOfMonth = 5,
            nextDueDate = LocalDate.of(2026, 10, 5)
        )
        coEvery { subscriptionRepository.getActiveSubscriptionsList() } returns emptyList()
        coEvery { recurringTransactionRepository.getActiveList() } returns listOf(ceb)
        coEvery { transactionRepository.getTransactionsForTemplateInRange(21L, any(), any()) } returns emptyList()

        val smsTx = TransactionEntity(
            amount = BigDecimal("4350.00"),
            merchantName = "CEB",
            category = "Utilities",
            transactionType = TransactionType.EXPENSE,
            dateTime = LocalDateTime.of(2026, 10, 5, 14, 12),
            smsBody = "LKR 4,350.00 debited from AC **7700 for CEB",
            bankName = "Sampath Bank",
            accountNumber = "7700",
            transactionHash = "sms-ceb-oct",
            currency = "LKR"
        )

        val prepared = service.prepareForIncomingTransaction(smsTx, resolvedAccountLast4 = "7700")
        assertTrue(prepared is RecurringReconciliationService.PreparedMatch.Template)
        val enriched = service.enrichEntity(smsTx, prepared)
        assertTrue(enriched.isRecurring)
        assertEquals(21L, enriched.recurringTemplateId)

        service.commitScheduleUpdate(prepared, enriched)

        coVerify(exactly = 1) {
            recurringTransactionRepository.updateNextDueDateAndAmount(
                id = 21L,
                nextDueDate = LocalDate.of(2026, 11, 5),
                amount = BigDecimal("4350.00")
            )
        }
    }

    @Test
    fun `historical subscription match links transaction without regressing future schedule`() = runBlocking {
        val netflix = SubscriptionEntity(
            id = 7L,
            merchantName = "Netflix",
            amount = BigDecimal("1500.00"),
            nextPaymentDate = LocalDate.of(2026, 11, 21),
            state = SubscriptionState.ACTIVE,
            bankName = "Sampath Bank",
            accountLast4 = "7700",
            currency = "LKR",
            direction = SubscriptionDirection.INCOME,
            billingCycle = "Monthly",
            lastPaidAt = LocalDate.of(2026, 10, 21)
        )
        coEvery { subscriptionRepository.getActiveSubscriptionsList() } returns listOf(netflix)
        coEvery { subscriptionRepository.advance(any(), any(), any()) } answers {
            val d = firstArg<LocalDate>()
            val cycle = secondArg<String>()
            val rev = thirdArg<Boolean>()
            RecurringMatcher.cycleAdvance(d, cycle, rev)
        }
        coEvery { transactionRepository.getTransactionsForSubscriptionInRange(7L, any(), any()) } returns emptyList()

        val oldSeptSms = TransactionEntity(
            amount = BigDecimal("1000.00"),
            merchantName = "Netflix",
            category = "Entertainment",
            transactionType = TransactionType.INCOME,
            dateTime = LocalDateTime.of(2026, 9, 19, 19, 37, 29),
            smsBody = "LKR 1,000.00 credited to AC **7700 for Netflix",
            bankName = "Sampath Bank",
            accountNumber = "7700",
            transactionHash = "sms-netflix-sept",
            currency = "LKR"
        )

        val prepared = service.prepareForIncomingTransaction(oldSeptSms, resolvedAccountLast4 = "7700")
        assertTrue(prepared is RecurringReconciliationService.PreparedMatch.Subscription)
        val enriched = service.enrichEntity(oldSeptSms, prepared)
        assertEquals(7L, enriched.subscriptionId)

        service.commitScheduleUpdate(prepared, enriched)

        // Historical Sept cycle must NOT regress Nov 21 schedule or overwrite Oct 21 lastPaidAt/1500 price.
        coVerify(exactly = 0) { subscriptionRepository.markPaidWithAmount(any(), any(), any(), any()) }
        coVerify(exactly = 0) { subscriptionRepository.markPaid(any(), any(), any()) }
    }

    @Test
    fun `when materialized template placeholder already exists, incoming SMS replaces placeholder`() = runBlocking {
        val template = RecurringTransactionEntity(
            id = 30L,
            uid = "tpl-ceb-2",
            merchantName = "CEB",
            amount = BigDecimal("4000.00"),
            currency = "LKR",
            transactionType = TransactionType.EXPENSE,
            frequency = RecurringFrequency.MONTHLY,
            dayOfMonth = 5,
            nextDueDate = LocalDate.of(2026, 11, 5),
            bankName = "Sampath Bank",
            accountLast4 = "7700"
        )
        val placeholder = TransactionEntity(
            id = 500L,
            amount = BigDecimal("4000.00"),
            merchantName = "CEB",
            category = "Utilities",
            transactionType = TransactionType.EXPENSE,
            dateTime = LocalDateTime.of(2026, 10, 5, 0, 0),
            smsBody = null,
            bankName = "Sampath Bank",
            accountNumber = "7700",
            transactionHash = "RECURRING_30_4000_CEB",
            isRecurring = true,
            recurringTemplateId = 30L,
            currency = "LKR"
        )

        coEvery { subscriptionRepository.getActiveSubscriptionsList() } returns emptyList()
        coEvery { recurringTransactionRepository.getActiveList() } returns listOf(template)
        coEvery { transactionRepository.getTransactionsForTemplateInRange(30L, any(), any()) } returns listOf(placeholder)
        coEvery { transactionRepository.getTransactionByHash("sms-ceb-oct5") } returns null

        val smsTx = TransactionEntity(
            amount = BigDecimal("4200.00"),
            merchantName = "CEB",
            category = "Utilities",
            transactionType = TransactionType.EXPENSE,
            dateTime = LocalDateTime.of(2026, 10, 5, 10, 15),
            smsBody = "LKR 4,200.00 debited from AC **7700 for CEB",
            smsSender = "SAMPATHTXN",
            bankName = "Sampath Bank",
            accountNumber = "7700",
            transactionHash = "sms-ceb-oct5",
            currency = "LKR"
        )

        val prepared = service.prepareForIncomingTransaction(smsTx, resolvedAccountLast4 = "7700")
        assertTrue(prepared is RecurringReconciliationService.PreparedMatch.Template)
        val tplMatch = prepared as RecurringReconciliationService.PreparedMatch.Template
        assertEquals(500L, tplMatch.placeholderToReplace?.id)

        val replacedId = service.replaceMaterializedPlaceholder(tplMatch, smsTx)
        assertEquals(500L, replacedId)
        coVerify(exactly = 1) {
            transactionRepository.updateTransaction(
                match {
                    it.id == 500L &&
                        it.amount == BigDecimal("4200.00") &&
                        it.smsBody == smsTx.smsBody &&
                        it.transactionHash == "sms-ceb-oct5" &&
                        it.recurringTemplateId == 30L &&
                        it.isRecurring
                }
            )
        }
        coVerify(exactly = 1) { accountBalanceRepository.applyTransactionBalanceShift(placeholder, any()) }
    }

    @Test
    fun `unmatched template materializes on due date and skips if SMS already linked`() = runBlocking {
        val dao = mockk<RecurringTransactionDao>(relaxed = true)
        val txnDao = mockk<TransactionDao>(relaxed = true)
        every { database.transactionDao() } returns txnDao
        val repo = RecurringTransactionRepository(dao, database, accountBalanceRepository)

        val dueTpl = RecurringTransactionEntity(
            id = 40L,
            uid = "tpl-rent",
            merchantName = "House Rent",
            amount = BigDecimal("25000.00"),
            currency = "LKR",
            transactionType = TransactionType.EXPENSE,
            frequency = RecurringFrequency.MONTHLY,
            dayOfMonth = 5,
            nextDueDate = LocalDate.of(2026, 10, 5)
        )
        coEvery { dao.getDue(LocalDate.of(2026, 10, 5)) } returns listOf(dueTpl)
        coEvery { accountBalanceRepository.insertTransactionWithBalance(any(), any(), any()) } returns 888L

        // Unmatched -> creates transaction with recurringTemplateId = 40L
        coEvery { txnDao.getTransactionsForTemplateInRange(40L, any(), any()) } returns emptyList()
        val created = repo.materializeDue(LocalDate.of(2026, 10, 5))
        assertEquals(1, created)
        coVerify(exactly = 1) {
            accountBalanceRepository.insertTransactionWithBalance(
                match { it.recurringTemplateId == 40L && it.isRecurring && it.amount == BigDecimal("25000.00") },
                any(),
                any()
            )
        }

        // Already linked -> skips creating a duplicate transaction
        coEvery { txnDao.getTransactionsForTemplateInRange(40L, any(), any()) } returns listOf(
            TransactionEntity(
                id = 900L,
                amount = BigDecimal("25000.00"),
                merchantName = "House Rent",
                category = "Others",
                transactionType = TransactionType.EXPENSE,
                dateTime = LocalDateTime.of(2026, 10, 5, 9, 0),
                transactionHash = "sms-rent",
                recurringTemplateId = 40L
            )
        )
        val skippedCount = repo.materializeDue(LocalDate.of(2026, 10, 5))
        assertEquals(0, skippedCount)
    }

    @Test
    fun `replacing materialized placeholder with explicit balanceAfter deletes placeholder balance row instead of inserting now-delta`() = runBlocking {
        val template = RecurringTransactionEntity(
            id = 31L,
            uid = "tpl-water",
            merchantName = "Water Board",
            amount = BigDecimal("1200.00"),
            currency = "LKR",
            transactionType = TransactionType.EXPENSE,
            frequency = RecurringFrequency.MONTHLY,
            dayOfMonth = 5,
            nextDueDate = LocalDate.of(2026, 11, 5),
            bankName = "Sampath Bank",
            accountLast4 = "7700"
        )
        val placeholder = TransactionEntity(
            id = 600L,
            amount = BigDecimal("1200.00"),
            merchantName = "Water Board",
            category = "Utilities",
            transactionType = TransactionType.EXPENSE,
            dateTime = LocalDateTime.of(2026, 10, 5, 0, 0),
            smsBody = null,
            bankName = "Sampath Bank",
            accountNumber = "7700",
            transactionHash = "RECURRING_31_1200_WB",
            isRecurring = true,
            recurringTemplateId = 31L,
            currency = "LKR"
        )
        coEvery { subscriptionRepository.getActiveSubscriptionsList() } returns emptyList()
        coEvery { recurringTransactionRepository.getActiveList() } returns listOf(template)
        coEvery { transactionRepository.getTransactionsForTemplateInRange(31L, any(), any()) } returns listOf(placeholder)
        coEvery { transactionRepository.getTransactionByHash("sms-wb-oct5") } returns null

        val smsWithExplicitBalance = TransactionEntity(
            amount = BigDecimal("1350.00"),
            merchantName = "Water Board",
            category = "Utilities",
            transactionType = TransactionType.EXPENSE,
            dateTime = LocalDateTime.of(2026, 10, 5, 11, 0),
            smsBody = "LKR 1,350.00 debited from AC **7700 Bal 48,650.00",
            smsSender = "SAMPATHTXN",
            bankName = "Sampath Bank",
            accountNumber = "7700",
            balanceAfter = BigDecimal("48650.00"),
            transactionHash = "sms-wb-oct5",
            currency = "LKR"
        )

        val prepared = service.prepareForIncomingTransaction(smsWithExplicitBalance, "7700")
            as RecurringReconciliationService.PreparedMatch.Template
        service.replaceMaterializedPlaceholder(prepared, smsWithExplicitBalance, resolvedAccountLast4 = "7700")

        coVerify(exactly = 1) { accountBalanceRepository.deleteBalancesForTransaction(600L) }
        coVerify(exactly = 0) { accountBalanceRepository.applyTransactionBalanceShift(any(), any()) }
        // Price change sticks even when nextDueDate already advanced on worker materialization.
        coVerify(exactly = 1) {
            recurringTransactionRepository.updateNextDueDateAndAmount(
                id = 31L,
                nextDueDate = LocalDate.of(2026, 11, 5),
                amount = BigDecimal("1350.00")
            )
        }
    }

    @Test
    fun `17-day cycle window finds already linked early SMS without double linking`() = runBlocking {
        val sub = SubscriptionEntity(
            id = 55L,
            merchantName = "Netflix",
            amount = BigDecimal("1500.00"),
            nextPaymentDate = LocalDate.of(2026, 10, 21),
            state = SubscriptionState.ACTIVE,
            bankName = "Sampath Bank",
            accountLast4 = "7700",
            currency = "LKR",
            direction = SubscriptionDirection.EXPENSE,
            billingCycle = "Monthly"
        )
        val earlyOct14 = TransactionEntity(
            id = 701L,
            amount = BigDecimal("1500.00"),
            merchantName = "Netflix",
            category = "Entertainment",
            transactionType = TransactionType.EXPENSE,
            dateTime = LocalDateTime.of(2026, 10, 14, 9, 0),
            smsBody = "Netflix oct14",
            bankName = "Sampath Bank",
            accountNumber = "7700",
            transactionHash = "h701",
            subscriptionId = 55L,
            currency = "LKR"
        )
        coEvery { subscriptionRepository.getActiveSubscriptionsList() } returns listOf(sub)
        coEvery { recurringTransactionRepository.getActiveList() } returns emptyList()
        coEvery {
            transactionRepository.getTransactionsForSubscriptionInRange(
                55L,
                match { !it.toLocalDate().isAfter(LocalDate.of(2026, 10, 14)) },
                any()
            )
        } returns listOf(earlyOct14)

        val secondSmsOct31 = earlyOct14.copy(
            id = 0L,
            dateTime = LocalDateTime.of(2026, 10, 31, 10, 0),
            transactionHash = "h702"
        )
        val prepared = service.prepareForIncomingTransaction(secondSmsOct31, "7700")
        assertTrue(prepared is RecurringReconciliationService.PreparedMatch.None)
    }

    @Test
    fun `deleting subscription or recurring template clears child foreign-link ids transactionally`() = runBlocking {
        val subDao = mockk<SubscriptionDao>(relaxed = true)
        val tplDao = mockk<RecurringTransactionDao>(relaxed = true)
        val txnDao = mockk<TransactionDao>(relaxed = true)
        every { database.transactionDao() } returns txnDao

        val subRepo = SubscriptionRepository(subDao, mockk(relaxed = true), txnDao, database)
        subRepo.deleteSubscription(88L)
        coVerify { txnDao.clearSubscriptionLinks(88L) }
        coVerify { subDao.deleteSubscriptionById(88L) }

        val tplRepo = RecurringTransactionRepository(tplDao, database, accountBalanceRepository)
        tplRepo.deleteById(99L)
        coVerify { txnDao.clearRecurringTemplateLinks(99L) }
        coVerify { tplDao.deleteById(99L) }
    }
}
