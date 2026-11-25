// Mudal fork: new file in Mudal, a fork of PennyWise AI (AGPL-3.0-only), 2026-11-25 - see NOTICE.
package com.pennywiseai.tracker.data.database

import android.util.Log
import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File

/**
 * Atomically converts an existing unencrypted SQLite database file on disk to
 * an encrypted SQLCipher database using the supplied [passphrase] before Room
 * opens it.
 */
object PlaintextDatabaseMigrator {

    private const val TAG = "PlaintextDatabaseMigrator"

    /**
     * Checks whether [dbFile] starts with the plaintext `"SQLite format 3\u0000"` header.
     * If so, checkpoints any WAL, exports all schema/data/user_version into a fresh
     * SQLCipher file using [passphrase], verifies the encrypted copy opens cleanly,
     * and swaps it atomically in place of [dbFile].
     *
     * Returns `Result.success(true)` if upgraded, `Result.success(false)` if [dbFile]
     * did not need migration (missing or already encrypted), or `Result.failure(e)`
     * leaving the original [dbFile] intact on error.
     */
    fun migrateIfNeeded(dbFile: File, passphrase: ByteArray): Result<Boolean> {
        val tmpFile = File("${dbFile.path}.tmp")
        val bakFile = File("${dbFile.path}.bak")

        // Recover if an earlier swap crashed between renameTo(bakFile) and renameTo(dbFile)
        if (!dbFile.exists() && bakFile.exists()) {
            if (!bakFile.renameTo(dbFile)) {
                return Result.failure(IllegalStateException("Failed to recover original database from .bak"))
            }
        }

        if (!DatabaseKeyManager.isPlaintextSqlite(dbFile)) {
            return Result.success(false)
        }

        deleteIfExists(tmpFile)
        deleteIfExists(bakFile)

        return try {
            val userVersion = checkpointAndReadVersion(dbFile)

            SQLiteDatabase.openOrCreateDatabase(tmpFile, passphrase, null, null).use { encDb ->
                encDb.rawExecSQL("ATTACH DATABASE ? AS plain KEY ''", dbFile.absolutePath)
                try {
                    encDb.rawQuery("SELECT sqlcipher_export('main', 'plain')", emptyArray<String>())
                        .use { it.moveToFirst() }
                    encDb.rawExecSQL("PRAGMA user_version = $userVersion")
                } finally {
                    runCatching { encDb.rawExecSQL("DETACH DATABASE plain") }
                }
            }

            check(!DatabaseKeyManager.isPlaintextSqlite(tmpFile)) {
                "Migrated database still has plaintext SQLite header"
            }
            verifyEncryptedCopy(tmpFile, passphrase, userVersion)

            swapFiles(dbFile, tmpFile, bakFile)
            Log.i(TAG, "Successfully migrated plaintext database to SQLCipher (user_version=$userVersion)")
            Result.success(true)
        } catch (e: Exception) {
            Log.e(TAG, "Plaintext-to-SQLCipher migration failed: ${e.message}", e)
            deleteIfExists(tmpFile)
            Result.failure(e)
        }
    }

    private fun checkpointAndReadVersion(dbFile: File): Int {
        SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE).use { plainDb ->
            plainDb.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", emptyArray<String>()).use {
                it.moveToFirst()
            }
            return plainDb.rawQuery("PRAGMA user_version", emptyArray<String>()).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
        }
    }

    private fun verifyEncryptedCopy(tmpFile: File, passphrase: ByteArray, expectedVersion: Int) {
        SQLiteDatabase.openDatabase(
            tmpFile.path,
            passphrase,
            null,
            SQLiteDatabase.OPEN_READONLY,
            null
        ).use { verifyDb ->
            val actualVersion = verifyDb.rawQuery("PRAGMA user_version", emptyArray<String>()).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else -1
            }
            check(actualVersion == expectedVersion) {
                "Encrypted copy user_version mismatch: expected $expectedVersion, got $actualVersion"
            }
        }
    }

    private fun swapFiles(dbFile: File, tmpFile: File, bakFile: File) {
        check(dbFile.renameTo(bakFile)) {
            "Failed to move original database to backup location"
        }
        if (!tmpFile.renameTo(dbFile)) {
            bakFile.renameTo(dbFile)
            error("Failed to move encrypted database into place")
        }
        // Clear plaintext WAL/SHM/journal sidecars after the encrypted file is live
        for (suffix in listOf("-wal", "-shm", "-journal")) {
            deleteIfExists(File("${dbFile.path}$suffix"))
            deleteIfExists(File("${tmpFile.path}$suffix"))
        }
        deleteIfExists(bakFile)
    }

    private fun deleteIfExists(file: File) {
        if (file.exists()) {
            file.delete()
        }
    }
}
