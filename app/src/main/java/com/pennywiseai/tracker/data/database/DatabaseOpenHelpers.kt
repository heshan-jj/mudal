// Mudal fork: new file in Mudal, a fork of PennyWise AI (AGPL-3.0-only), 2026-11-25 - see NOTICE.
package com.pennywiseai.tracker.data.database

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import kotlin.coroutines.cancellation.CancellationException

/**
 * [SupportSQLiteOpenHelper.Factory] used when database key recovery or migration
 * failed at startup. Keeps Room/Hilt construction from crashing the UI process
 * while ensuring any database read/write fails loudly with [DatabaseKeyException]
 * instead of silently falling back to an in-memory database.
 */
class FailingOpenHelperFactory(
    private val errorMessage: String,
    private val cause: Throwable? = null
) : SupportSQLiteOpenHelper.Factory {

    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        return object : SupportSQLiteOpenHelper {
            override val databaseName: String? = configuration.name

            override fun setWriteAheadLoggingEnabled(enabled: Boolean) = Unit

            override val writableDatabase: SupportSQLiteDatabase
                get() = throw DatabaseKeyException(errorMessage, cause)

            override val readableDatabase: SupportSQLiteDatabase
                get() = throw DatabaseKeyException(errorMessage, cause)

            override fun close() = Unit
        }
    }
}

/**
 * Wraps a delegating [SupportSQLiteOpenHelper.Factory] (such as SQLCipher's
 * [net.zetetic.database.sqlcipher.SupportOpenHelperFactory]) so open-time
 * decryption/corruption errors mark [DatabaseKeyManager] in error state and
 * surface [DatabaseErrorScreen] instead of crashing background coroutines.
 */
class KeyAwareOpenHelperFactory(
    private val delegate: SupportSQLiteOpenHelper.Factory,
    private val databaseKeyManager: DatabaseKeyManager
) : SupportSQLiteOpenHelper.Factory {

    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        val helper = delegate.create(configuration)
        return object : SupportSQLiteOpenHelper {
            override val databaseName: String?
                get() = helper.databaseName

            override fun setWriteAheadLoggingEnabled(enabled: Boolean) {
                helper.setWriteAheadLoggingEnabled(enabled)
            }

            override val writableDatabase: SupportSQLiteDatabase
                get() = openOrMarkError { helper.writableDatabase }

            override val readableDatabase: SupportSQLiteDatabase
                get() = openOrMarkError { helper.readableDatabase }

            override fun close() {
                helper.close()
            }

            private inline fun openOrMarkError(block: () -> SupportSQLiteDatabase): SupportSQLiteDatabase {
                return try {
                    block()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: DatabaseKeyException) {
                    throw e
                } catch (e: Exception) {
                    val msg = "Failed to open encrypted database: ${e.message ?: "unknown error"}"
                    databaseKeyManager.markError(msg, e)
                    throw DatabaseKeyException(msg, e)
                }
            }
        }
    }
}
