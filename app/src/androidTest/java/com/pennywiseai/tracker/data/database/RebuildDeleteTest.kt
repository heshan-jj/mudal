package com.pennywiseai.tracker.data.database

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.pennywiseai.tracker.data.database.entity.TransactionEntity
import com.pennywiseai.tracker.data.database.entity.TransactionType
import com.pennywiseai.tracker.receiver.BankNotificationConfig
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.LocalDateTime

/** "Rebuild from scratch" deletes only rows the SMS inbox can bring back. */
@RunWith(AndroidJUnit4::class)
class RebuildDeleteTest {
    private val db = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext, PennyWiseDatabase::class.java
    ).build()

    @After fun close() = db.close()

    private fun row(
        id: Long,
        sender: String?,
        deleted: Boolean = false,
        date: LocalDateTime = LocalDateTime.of(2026, 10, 1, 12, 0),
        type: TransactionType = TransactionType.EXPENSE,
        hash: String = "h$id",
        loanId: Long? = null
    ) = TransactionEntity(
        id = id, amount = BigDecimal("100"), merchantName = "M$id", category = "Others",
        transactionType = type, dateTime = date,
        transactionHash = hash, smsSender = sender, smsBody = sender?.let { "body $id" },
        isDeleted = deleted, loanId = loanId
    )

    @Test fun deletesOnlyActiveSmsRows() = runBlocking {
        val dao = db.transactionDao()
        val notification = BankNotificationConfig.notificationAliases.first()
        listOf(
            row(1, "AD-KOTAKB"),             // SMS: rebuilt from the inbox -> deleted
            row(2, null),                    // manual / chat / CSV -> kept
            row(3, "ICICI PDF"),             // PDF import -> kept
            row(4, notification),            // bank-app notification -> kept
            row(5, "AD-KOTAKB", deleted = true, hash = "DELETED_5_h5"), // user deleted -> kept
            row(6, "AD-KOTAKB", date = LocalDateTime.of(2025, 1, 1, 12, 0)), // older than re-read window -> kept
            // Active consolidated TRANSFER + its MERGED_TRANSFER_<survivorId> partner -> both deleted so resync re-pairs cleanly
            row(7, "AD-KOTAKB", type = TransactionType.TRANSFER),
            row(8, "AD-KOTAKB", deleted = true, hash = "MERGED_TRANSFER_7_h8"),
            // Protected loan TRANSFER + user-deleted TRANSFER keep their MERGED_TRANSFER partners soft-deleted
            row(9, "AD-KOTAKB", type = TransactionType.TRANSFER, loanId = 42L),
            row(10, "AD-KOTAKB", deleted = true, hash = "MERGED_TRANSFER_9_h10"),
            row(11, "AD-KOTAKB", deleted = true, type = TransactionType.TRANSFER, hash = "DELETED_11_h11"),
            row(12, "AD-KOTAKB", deleted = true, hash = "MERGED_TRANSFER_11_h12")
        ).forEach { dao.insertTransaction(it) }

        dao.deleteRebuildableSmsTransactions(
            BankNotificationConfig.notificationAliases.toList(),
            from = LocalDateTime.of(2026, 7, 1, 0, 0)
        )

        val left = db.openHelper.readableDatabase.query("SELECT id FROM transactions ORDER BY id").use { c ->
            buildList { while (c.moveToNext()) add(c.getLong(0)) }
        }
        assertEquals(listOf(2L, 3L, 4L, 5L, 6L, 9L, 10L, 11L, 12L), left)
    }
}
