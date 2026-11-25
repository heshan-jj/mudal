package com.pennywiseai.tracker.data.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Migration67To68Test {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        PennyWiseDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory()
    )

    @Test
    fun migration67To68AddsNullableLinkColumnsAndIndicesPreservingRows() {
        val dbName = "migration-67-68-test"
        helper.createDatabase(dbName, 67).apply {
            execSQL(
                """
                INSERT INTO transactions (
                    amount, merchant_name, category, transaction_type, date_time,
                    transaction_hash, is_recurring, is_deleted, excluded_from_analytics,
                    created_at, updated_at, currency
                ) VALUES (
                    '1500.00', 'Netflix', 'Entertainment', 'EXPENSE', '2026-10-21T08:28:51',
                    'hash-v67-1', 1, 0, 0,
                    '2026-10-21T08:28:51', '2026-10-21T08:28:51', 'LKR'
                )
                """.trimIndent()
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(dbName, 68, true)
        migrated.query("SELECT merchant_name, subscription_id, recurring_template_id FROM transactions").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("Netflix", cursor.getString(0))
            assertTrue(cursor.isNull(1))
            assertTrue(cursor.isNull(2))
        }
        migrated.query("PRAGMA index_list('transactions')").use { cursor ->
            val indexNames = mutableSetOf<String>()
            while (cursor.moveToNext()) {
                indexNames.add(cursor.getString(cursor.getColumnIndexOrThrow("name")))
            }
            assertTrue(indexNames.any { it.contains("subscription_id") })
            assertTrue(indexNames.any { it.contains("recurring_template_id") })
        }
        migrated.close()
    }
}
