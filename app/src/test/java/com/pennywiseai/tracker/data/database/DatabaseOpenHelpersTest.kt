// Mudal fork: new file in Mudal, a fork of PennyWise AI (AGPL-3.0-only), 2026-11-25 - see NOTICE.
package com.pennywiseai.tracker.data.database

import android.content.Context
import android.content.SharedPreferences
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseOpenHelpersTest {

    private fun testConfiguration(): SupportSQLiteOpenHelper.Configuration {
        val context = mockk<Context>(relaxed = true)
        val callback = mockk<SupportSQLiteOpenHelper.Callback>(relaxed = true)
        return SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("test.db")
            .callback(callback)
            .build()
    }

    private fun createKeyManager(): DatabaseKeyManager {
        val context = mockk<Context>(relaxed = true)
        val prefs = mockk<SharedPreferences>(relaxed = true)
        every { context.getSharedPreferences(any(), any()) } returns prefs
        return DatabaseKeyManager(context, mockk(relaxed = true))
    }

    @Test
    fun `failing factory throws DatabaseKeyException on writable and readable database and close is safe`() {
        val cause = IllegalStateException("Keystore wiped")
        val factory = FailingOpenHelperFactory("Encrypted database unavailable", cause)
        val helper = factory.create(testConfiguration())

        assertEquals("test.db", helper.databaseName)
        val writeErr = assertThrows(DatabaseKeyException::class.java) {
            helper.writableDatabase
        }
        assertSame(cause, writeErr.cause)

        val readErr = assertThrows(DatabaseKeyException::class.java) {
            helper.readableDatabase
        }
        assertSame(cause, readErr.cause)

        helper.setWriteAheadLoggingEnabled(true)
        helper.close()
    }

    @Test
    fun `key aware factory marks DatabaseKeyManager error and throws DatabaseKeyException when delegate fails`() {
        val manager = createKeyManager()
        val delegateHelper = mockk<SupportSQLiteOpenHelper>(relaxed = true)
        every { delegateHelper.writableDatabase } throws RuntimeException("file is not a database")
        every { delegateHelper.readableDatabase } throws RuntimeException("file is not a database")
        val delegateFactory = SupportSQLiteOpenHelper.Factory { delegateHelper }

        val helper = KeyAwareOpenHelperFactory(delegateFactory, manager).create(testConfiguration())

        assertFalse(manager.hasKeyError())
        assertThrows(DatabaseKeyException::class.java) {
            helper.writableDatabase
        }
        assertTrue(manager.hasKeyError())
        val status = manager.keyStatus.value as DatabaseKeyStatus.Error
        assertTrue(status.message.contains("file is not a database"))
    }

    @Test
    fun `key aware factory delegates cleanly when delegate succeeds`() {
        val manager = createKeyManager()
        val mockDb = mockk<SupportSQLiteDatabase>(relaxed = true)
        val delegateHelper = mockk<SupportSQLiteOpenHelper>(relaxed = true)
        every { delegateHelper.writableDatabase } returns mockDb
        every { delegateHelper.readableDatabase } returns mockDb
        val delegateFactory = SupportSQLiteOpenHelper.Factory { delegateHelper }

        val helper = KeyAwareOpenHelperFactory(delegateFactory, manager).create(testConfiguration())

        assertSame(mockDb, helper.writableDatabase)
        assertSame(mockDb, helper.readableDatabase)
        assertFalse(manager.hasKeyError())
    }

    @Test
    fun `verifyDatabaseReadable returns false and keeps key error when openHelper fails`() {
        val manager = createKeyManager()
        val delegateHelper = mockk<SupportSQLiteOpenHelper>(relaxed = true)
        every { delegateHelper.writableDatabase } throws RuntimeException("wrong key")
        val helper = KeyAwareOpenHelperFactory({ delegateHelper }, manager).create(testConfiguration())

        val ok = verifyDatabaseReadable(helper, manager)

        assertFalse(ok)
        assertTrue(manager.hasKeyError())
    }

    @Test
    fun `verifyDatabaseReadable returns true when openHelper succeeds`() {
        val manager = createKeyManager()
        val mockDb = mockk<SupportSQLiteDatabase>(relaxed = true)
        val cursor = mockk<android.database.Cursor>(relaxed = true)
        every { cursor.moveToFirst() } returns true
        every { mockDb.query("SELECT 1") } returns cursor
        val delegateHelper = mockk<SupportSQLiteOpenHelper>(relaxed = true)
        every { delegateHelper.writableDatabase } returns mockDb
        val helper = KeyAwareOpenHelperFactory({ delegateHelper }, manager).create(testConfiguration())

        val ok = verifyDatabaseReadable(helper, manager)

        assertTrue(ok)
        assertFalse(manager.hasKeyError())
    }
}
