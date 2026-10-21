// Mudal fork: new file in Mudal, a fork of PennyWise AI (AGPL-3.0-only), 2026-10-21 - see NOTICE.
package com.pennywiseai.tracker.data.database

import androidx.core.net.toUri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pennywiseai.tracker.data.backup.BackupExporter
import com.pennywiseai.tracker.data.backup.BackupImporter
import com.pennywiseai.tracker.data.backup.ExportResult
import com.pennywiseai.tracker.data.backup.ImportResult
import com.pennywiseai.tracker.data.backup.ImportStrategy
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.data.preferences.IgnoredAccountsStore
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.FileInputStream
import java.math.BigDecimal
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class SqlCipherEncryptionTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val testDbName = "sqlcipher_test.db"
    private val correctPassphrase = "test-correct-secret-passphrase-32bytes!".toByteArray()
    private val wrongPassphrase = "wrong-passphrase-attempt-32bytes!!".toByteArray()

    @Before
    fun setUp() {
        System.loadLibrary("sqlcipher")
        context.deleteDatabase(testDbName)
    }

    @Test
    fun testEncryptedDatabaseCreationAndRejectionOnWrongKey() {
        runBlocking {
            val dbFile = context.getDatabasePath(testDbName)

            // 1. Create and write to database with correct passphrase
            val openHelperFactory = SupportOpenHelperFactory(correctPassphrase)
            val db = Room.databaseBuilder(context, PennyWiseDatabase::class.java, testDbName)
                .openHelperFactory(openHelperFactory)
                .addMigrations(*PennyWiseDatabase.ALL_MIGRATIONS)
                .build()

            val sampleTx = TransactionEntity(
                amount = BigDecimal("1500.50"),
                merchantName = "Keells Super",
                category = "Groceries",
                transactionType = TransactionType.EXPENSE,
                accountNumber = "1234",
                dateTime = LocalDateTime.now(),
                transactionHash = "hash_keells_test_1234",
                smsBody = "LKR 1500.50 spent at Keells",
                currency = "LKR"
            )

            val insertedId = db.transactionDao().insertTransaction(sampleTx)
            assertTrue("Transaction should be inserted with valid ID", insertedId > 0)

            val loadedTx = db.transactionDao().getTransactionById(insertedId)
            assertNotNull(loadedTx)
            assertEquals(BigDecimal("1500.50"), loadedTx?.amount)
            assertEquals("Keells Super", loadedTx?.merchantName)

            // Close connection
            db.close()

            // 2. Verify raw file header on disk is NOT standard plaintext SQLite
            assertTrue("Database file must exist on disk", dbFile.exists())
            val headerBytes = ByteArray(16)
            FileInputStream(dbFile).use { it.read(headerBytes) }
            val headerString = String(headerBytes)
            assertFalse(
                "Database file must NOT have plaintext 'SQLite format 3' magic header",
                headerString.startsWith("SQLite format 3")
            )

            // 3. Attempt to open the exact same database file with WRONG passphrase
            val wrongFactory = SupportOpenHelperFactory(wrongPassphrase)
            val wrongDb = Room.databaseBuilder(context, PennyWiseDatabase::class.java, testDbName)
                .openHelperFactory(wrongFactory)
                .addMigrations(*PennyWiseDatabase.ALL_MIGRATIONS)
                .build()

            var wrongKeyFailed = false
            try {
                // Trigger SQLite access
                wrongDb.transactionDao().getTransactionById(insertedId)
            } catch (e: Exception) {
                wrongKeyFailed = true
            } finally {
                wrongDb.close()
            }
            assertTrue("Accessing encrypted database with wrong key must throw an exception", wrongKeyFailed)

            // 4. Reopen with CORRECT passphrase and verify all data is intact
            val reopenDb = Room.databaseBuilder(context, PennyWiseDatabase::class.java, testDbName)
                .openHelperFactory(openHelperFactory)
                .addMigrations(*PennyWiseDatabase.ALL_MIGRATIONS)
                .build()

            val recoveredTx = reopenDb.transactionDao().getTransactionById(insertedId)
            assertNotNull(recoveredTx)
            assertEquals(BigDecimal("1500.50"), recoveredTx?.amount)
            assertEquals("Keells Super", recoveredTx?.merchantName)
            reopenDb.close()

            // Cleanup
            context.deleteDatabase(testDbName)
        }
    }

    @Test
    fun testBackupRoundTripWithEncryptedDatabase() {
        runBlocking {
            val userPrefsRepo = UserPreferencesRepository(context)
            val ignoredStore = IgnoredAccountsStore(context)

            // 1. Setup encrypted database and insert sample transaction
            val openHelperFactory = SupportOpenHelperFactory(correctPassphrase)
            val initialDb = Room.databaseBuilder(context, PennyWiseDatabase::class.java, testDbName)
                .openHelperFactory(openHelperFactory)
                .addMigrations(*PennyWiseDatabase.ALL_MIGRATIONS)
                .build()

            val tx = TransactionEntity(
                amount = BigDecimal("3500.00"),
                merchantName = "Cargills Food City",
                category = "Food",
                transactionType = TransactionType.EXPENSE,
                accountNumber = "9988",
                dateTime = LocalDateTime.now(),
                transactionHash = "hash_cargills_backup_test",
                smsBody = "LKR 3500.00 spent at Cargills",
                currency = "LKR"
            )
            initialDb.transactionDao().insertTransaction(tx)

            // 2. Export backup from encrypted DB
            val exporter = BackupExporter(context, initialDb, userPrefsRepo, ignoredStore)
            val exportResult = exporter.exportBackup()
            assertTrue("Backup export should succeed", exportResult is ExportResult.Success)
            val backupFile = (exportResult as ExportResult.Success).file
            assertTrue("Exported backup file should exist", backupFile.exists())

            // Close and delete the initial database
            initialDb.close()
            context.deleteDatabase(testDbName)

            // 3. Create fresh encrypted database (simulating new key / clean state)
            val freshKey = "new-fresh-secret-passphrase-32bytes!!".toByteArray()
            val freshFactory = SupportOpenHelperFactory(freshKey)
            val freshDb = Room.databaseBuilder(context, PennyWiseDatabase::class.java, testDbName)
                .openHelperFactory(freshFactory)
                .addMigrations(*PennyWiseDatabase.ALL_MIGRATIONS)
                .build()

            // 4. Restore backup into fresh encrypted database
            val importer = BackupImporter(context, freshDb, userPrefsRepo, ignoredStore)
            val importResult = importer.importBackup(backupFile.toUri(), ImportStrategy.REPLACE_ALL)
            assertTrue("Backup import should succeed: $importResult", importResult is ImportResult.Success)

            // 5. Verify transaction is restored
            val restoredTxs = freshDb.transactionDao().getAllTransactions().first()
            assertTrue("Restored transactions should not be empty", restoredTxs.isNotEmpty())
            val restoredTx = restoredTxs.firstOrNull { it.merchantName == "Cargills Food City" }
            assertNotNull("Cargills transaction must be restored", restoredTx)
            assertEquals(BigDecimal("3500.00"), restoredTx?.amount)

            freshDb.close()
            backupFile.delete()
            context.deleteDatabase(testDbName)
        }
    }
}
