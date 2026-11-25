package com.pennywiseai.tracker.data.backup

import android.content.Context
import android.content.SharedPreferences
import androidx.room.withTransaction
import com.pennywiseai.tracker.data.database.PennyWiseDatabase
import com.pennywiseai.tracker.data.database.dao.ProfileDao
import com.pennywiseai.tracker.data.database.dao.RecurringTransactionDao
import com.pennywiseai.tracker.data.database.dao.SubscriptionDao
import com.pennywiseai.tracker.data.database.dao.TransactionDao
import com.pennywiseai.tracker.data.database.entity.ProfileEntity
import com.pennywiseai.tracker.data.database.entity.RecurringTransactionEntity
import com.pennywiseai.tracker.data.database.entity.SubscriptionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.preferences.UserPreferences
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.nio.file.Files
import java.time.LocalDate
import java.time.LocalDateTime

class BackupImporterRecurringLinkTest {

    private val tempDir = Files.createTempDirectory("backup-link-test").toFile().apply { deleteOnExit() }
    private val sharedPrefsEditor = mockk<SharedPreferences.Editor>(relaxed = true).apply {
        every { putStringSet(any(), any()) } returns this
        every { commit() } returns true
    }
    private val sharedPrefs = mockk<SharedPreferences>(relaxed = true) {
        every { edit() } returns sharedPrefsEditor
        every { getStringSet(any(), any()) } returns emptySet()
    }
    private val context = mockk<Context>(relaxed = true) {
        every { filesDir } returns tempDir
        every { getSharedPreferences(any(), any()) } returns sharedPrefs
    }
    private val subscriptionDao = mockk<SubscriptionDao>(relaxed = true)
    private val recurringDao = mockk<RecurringTransactionDao>(relaxed = true)
    private val transactionDao = mockk<TransactionDao>(relaxed = true)
    private val profileDao = mockk<ProfileDao>(relaxed = true)
    private val userPrefs = mockk<UserPreferencesRepository>(relaxed = true)
    private val database = mockk<PennyWiseDatabase>(relaxed = true) {
        every { subscriptionDao() } returns subscriptionDao
        every { recurringTransactionDao() } returns recurringDao
        every { transactionDao() } returns transactionDao
        every { profileDao() } returns profileDao
        every { categoryDao().getAllCategories() } returns flowOf(emptyList())
        every { cardDao().getAllCards() } returns flowOf(emptyList())
        every { accountBalanceDao().getAllBalances() } returns flowOf(emptyList())
        every { merchantMappingDao().getAllMappings() } returns flowOf(emptyList())
        every { merchantAliasDao().getAllAliases() } returns flowOf(emptyList())
        every { unrecognizedSmsDao().getAllUnrecognizedSms() } returns flowOf(emptyList())
        every { chatDao().getAllMessages() } returns flowOf(emptyList())
        every { ruleDao().getAllRules() } returns flowOf(emptyList())
        every { ruleApplicationDao().getAllApplications() } returns flowOf(emptyList())
        every { exchangeRateDao().getAllRatesFlow() } returns flowOf(emptyList())
        every { budgetDao().getAllBudgets() } returns flowOf(emptyList())
        every { budgetDao().getAllBudgetCategories() } returns flowOf(emptyList())
        every { transactionSplitDao().getAllSplits() } returns flowOf(emptyList())
        every { bankNotificationDao().getAllNotifications() } returns flowOf(emptyList())
        every { loanDao().getAllLoans() } returns flowOf(emptyList())
        every { transactionGroupDao().getAllGroups() } returns flowOf(emptyList())
        every { subscriptionDao.getAllSubscriptions() } returns flowOf(emptyList())
        every { recurringDao.getAll() } returns flowOf(emptyList())
        every { transactionDao.getAllTransactions() } returns flowOf(emptyList())
    }
    private val importer = BackupImporter(context, database, userPrefs, mockk(relaxed = true))
    private val exporter = BackupExporter(context, database, userPrefs, mockk(relaxed = true))

    @Before
    fun setUp() {
        mockkStatic("androidx.room.RoomDatabaseKt")
        coEvery { database.withTransaction(any<suspend () -> Any?>()) } coAnswers {
            secondArg<suspend () -> Any?>().invoke()
        }
        coEvery { profileDao.getAllProfiles() } returns listOf(ProfileEntity(id = 1L, name = "Personal", colorHex = "#6366F1"))
        every { userPrefs.userPreferences } returns flowOf(UserPreferences())
        every { userPrefs.getSystemPrompt() } returns flowOf(null)
        every { userPrefs.storedLicense } returns flowOf(null)
        every { userPrefs.getFirstLaunchTime() } returns flowOf(0L)
        every { userPrefs.getHasShownReviewPrompt() } returns flowOf(false)
        every { userPrefs.getLastReviewPromptTime() } returns flowOf(0L)
        every { userPrefs.getLastScanTimestamp() } returns flowOf(0L)
        every { userPrefs.getLastScanPeriod() } returns flowOf(3)
    }

    @After
    fun tearDown() {
        unmockkStatic("androidx.room.RoomDatabaseKt")
    }

    @Test
    fun `importSubscriptionsWithMerge returns backup-to-local ID map for both existing and inserted rows`() = runBlocking {
        val localExisting = SubscriptionEntity(
            id = 42L,
            merchantName = "Netflix",
            amount = BigDecimal("1500.00"),
            nextPaymentDate = LocalDate.of(2026, 11, 21),
            currency = "LKR"
        )
        coEvery { subscriptionDao.getAllSubscriptions() } returns flowOf(listOf(localExisting))
        coEvery { subscriptionDao.insertSubscription(any()) } returns 99L

        val backupSub1 = localExisting.copy(id = 5L)
        val backupSub2 = SubscriptionEntity(
            id = 8L,
            merchantName = "Spotify",
            amount = BigDecimal("375.00"),
            nextPaymentDate = LocalDate.of(2026, 11, 14),
            currency = "LKR"
        )

        var skipped = 0
        val idMap = importer.importSubscriptionsWithMerge(listOf(backupSub1, backupSub2)) { skipped++ }

        assertEquals(0, skipped)
        assertEquals(mapOf(5L to 42L, 8L to 99L), idMap)
        coVerify(exactly = 1) { subscriptionDao.insertSubscription(match { it.id == 0L && it.merchantName == "Spotify" }) }
    }

    @Test
    fun `importRecurringTransactionsWithMerge returns backup-to-local ID map by uid`() = runBlocking {
        val existingTemplate = RecurringTransactionEntity(
            id = 17L,
            uid = "tpl-uid-1",
            merchantName = "CEB",
            amount = BigDecimal("4000.00"),
            currency = "LKR"
        )
        coEvery { recurringDao.getAll() } returns flowOf(listOf(existingTemplate))
        coEvery { recurringDao.insert(any()) } returns 55L

        val backupExisting = existingTemplate.copy(id = 2L)
        val backupNew = RecurringTransactionEntity(
            id = 9L,
            uid = "tpl-uid-2",
            merchantName = "Rent",
            amount = BigDecimal("25000.00"),
            currency = "LKR"
        )

        var skipped = 0
        val idMap = importer.importRecurringTransactionsWithMerge(
            listOf(backupExisting, backupNew),
            resolveProfileId = { it }
        ) { skipped++ }

        assertEquals(0, skipped)
        assertEquals(mapOf(2L to 17L, 9L to 55L), idMap)
        coVerify(exactly = 1) { recurringDao.insert(match { it.id == 0L && it.uid == "tpl-uid-2" }) }
    }

    @Test
    fun `mergeData and replaceAllData remap subscriptionId and recurringTemplateId on imported transactions`() = runBlocking {
        val sub = SubscriptionEntity(id = 5L, merchantName = "Netflix", amount = BigDecimal("1500.00"), nextPaymentDate = LocalDate.of(2026, 11, 21), currency = "LKR")
        val tpl = RecurringTransactionEntity(id = 9L, uid = "tpl-rent", merchantName = "Rent", amount = BigDecimal("25000.00"), currency = "LKR")
        val subTx = TransactionEntity(
            id = 100L, amount = BigDecimal("1500.00"), merchantName = "Netflix", category = "Entertainment",
            transactionType = TransactionType.EXPENSE, dateTime = LocalDateTime.of(2026, 10, 21, 9, 0),
            transactionHash = "h-sub", subscriptionId = 5L, recurringTemplateId = null, isRecurring = true
        )
        val tplTx = TransactionEntity(
            id = 101L, amount = BigDecimal("25000.00"), merchantName = "Rent", category = "Housing",
            transactionType = TransactionType.EXPENSE, dateTime = LocalDateTime.of(2026, 10, 5, 9, 0),
            transactionHash = "h-tpl", subscriptionId = null, recurringTemplateId = 9L, isRecurring = true
        )
        val orphanTx = TransactionEntity(
            id = 102L, amount = BigDecimal("100.00"), merchantName = "Orphan", category = "Others",
            transactionType = TransactionType.EXPENSE, dateTime = LocalDateTime.of(2026, 10, 6, 9, 0),
            transactionHash = "h-orphan", subscriptionId = 999L, recurringTemplateId = 888L
        )
        val backup = PennyWiseBackup(
            database = DatabaseSnapshot(
                subscriptions = listOf(sub),
                recurringTransactions = listOf(tpl),
                transactions = listOf(subTx, tplTx, orphanTx)
            )
        )

        coEvery { subscriptionDao.getAllSubscriptions() } returns flowOf(emptyList())
        coEvery { recurringDao.getAll() } returns flowOf(emptyList())
        coEvery { transactionDao.getAllTransactions() } returns flowOf(emptyList())
        coEvery { subscriptionDao.insertSubscription(any()) } returns 42L
        coEvery { recurringDao.insert(any()) } returns 55L
        coEvery { transactionDao.insertTransaction(any()) } returnsMany listOf(1000L, 1001L, 1002L, 2000L, 2001L, 2002L)

        val replaceResult = importer.replaceAllData(backup)
        assertTrue(replaceResult is ImportResult.Success)
        coVerify {
            transactionDao.insertTransaction(match { it.transactionHash == "h-sub" && it.subscriptionId == 42L && it.recurringTemplateId == null })
            transactionDao.insertTransaction(match { it.transactionHash == "h-tpl" && it.recurringTemplateId == 55L && it.subscriptionId == null })
            transactionDao.insertTransaction(match { it.transactionHash == "h-orphan" && it.subscriptionId == null && it.recurringTemplateId == null })
        }

        val mergeResult = importer.mergeData(backup)
        assertTrue(mergeResult is ImportResult.Success)
    }

    @Test
    fun `BackupExporter strips recurringTemplateId in MASKED and ANONYMOUS exports`() = runBlocking {
        val tx = TransactionEntity(
            id = 1L, amount = BigDecimal("1500.00"), merchantName = "Rent", category = "Housing",
            transactionType = TransactionType.EXPENSE, dateTime = LocalDateTime.of(2026, 10, 5, 9, 0),
            transactionHash = "h1", accountNumber = "12345678", recurringTemplateId = 9L, subscriptionId = 5L
        )
        every { transactionDao.getAllTransactions() } returns flowOf(listOf(tx))

        val full = exporter.createBackup(ExportPrivacy.FULL, includeReceipts = false)
        assertEquals(9L, full.database.transactions.single().recurringTemplateId)

        val masked = exporter.createBackup(ExportPrivacy.MASKED, includeReceipts = false)
        assertNull(masked.database.transactions.single().recurringTemplateId)

        val anon = exporter.createBackup(ExportPrivacy.ANONYMOUS, includeReceipts = false)
        assertNull(anon.database.transactions.single().recurringTemplateId)
    }
}
