// Mudal fork: new file in Mudal, a fork of PennyWise AI (AGPL-3.0-only), 2026-10-21 - see NOTICE.
package com.pennywiseai.tracker.data.database

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileInputStream
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

sealed interface DatabaseKeyStatus {
    object Ready : DatabaseKeyStatus
    data class Error(val message: String, val cause: Throwable? = null) : DatabaseKeyStatus
}

class DatabaseKeyException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

@Singleton
class DatabaseKeyManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val keyStoreHelper: KeyStoreHelper = AndroidKeyStoreHelper()
) {
    companion object {
        private const val TAG = "DatabaseKeyManager"
        private const val PREFS_NAME = "mudal_db_encryption_prefs"
        private const val KEY_CIPHERTEXT = "encrypted_db_key"
        private const val KEY_IV = "db_key_iv"
        private const val KEY_LEN_BYTES = 32
        private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
        private val SIDECAR_SUFFIXES = listOf("-wal", "-shm", "-journal", ".tmp", ".bak")

        fun isPlaintextSqlite(dbFile: File): Boolean {
            if (!dbFile.exists() || !dbFile.isFile || dbFile.length() < SQLITE_HEADER.size) {
                return false
            }
            return try {
                val buffer = ByteArray(SQLITE_HEADER.size)
                val read = FileInputStream(dbFile).use { it.read(buffer) }
                read == SQLITE_HEADER.size && buffer.contentEquals(SQLITE_HEADER)
            } catch (_: Exception) {
                false
            }
        }

        fun deleteDatabaseFiles(dbFile: File): Boolean {
            var allDeleted = !dbFile.exists() || dbFile.delete()
            for (suffix in SIDECAR_SUFFIXES) {
                val sidecar = File("${dbFile.path}$suffix")
                if (sidecar.exists() && !sidecar.delete()) {
                    allDeleted = false
                }
            }
            return allDeleted
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _keyStatus = MutableStateFlow<DatabaseKeyStatus>(DatabaseKeyStatus.Ready)
    val keyStatus: StateFlow<DatabaseKeyStatus> = _keyStatus.asStateFlow()

    @Volatile
    private var cachedPassphrase: ByteArray? = null

    /**
     * Retrieves the database encryption passphrase, or generates and wraps a new one on first run.
     * Throws [DatabaseKeyException] if key loss is detected (e.g., encrypted DB exists but key is lost).
     */
    @Synchronized
    fun getOrCreatePassphrase(): ByteArray {
        cachedPassphrase?.let { return it }

        val dbFile = context.getDatabasePath(PennyWiseDatabase.DATABASE_NAME)
        val encryptedKeyBase64 = prefs.getString(KEY_CIPHERTEXT, null)
        val ivBase64 = prefs.getString(KEY_IV, null)

        val passphrase = try {
            if (encryptedKeyBase64 == null || ivBase64 == null) {
                if (dbFile.exists() && !isPlaintextSqlite(dbFile)) {
                    // Encrypted database exists on disk, but encryption key is gone -> KEY LOSS
                    val msg = "Database exists on device but encryption key is missing from storage."
                    Log.e(TAG, msg)
                    _keyStatus.value = DatabaseKeyStatus.Error(msg)
                    throw DatabaseKeyException(msg)
                } else {
                    // First run or legacy plaintext database awaiting upgrade
                    generateAndStoreNewPassphrase()
                }
            } else {
                // Key exists: unwrap via Android Keystore
                val ciphertext = Base64.getDecoder().decode(encryptedKeyBase64)
                val iv = Base64.getDecoder().decode(ivBase64)
                keyStoreHelper.decrypt(ciphertext, iv)
            }
        } catch (e: Exception) {
            val msg = "Failed to access database encryption key: ${e.message}"
            Log.e(TAG, msg, e)
            _keyStatus.value = DatabaseKeyStatus.Error(msg, e)
            throw DatabaseKeyException(msg, e)
        }

        cachedPassphrase = passphrase
        _keyStatus.value = DatabaseKeyStatus.Ready
        return passphrase
    }

    private fun generateAndStoreNewPassphrase(): ByteArray {
        val rawPassphrase = ByteArray(KEY_LEN_BYTES).apply {
            SecureRandom().nextBytes(this)
        }
        val (ciphertext, iv) = keyStoreHelper.encrypt(rawPassphrase)
        prefs.edit()
            .putString(KEY_CIPHERTEXT, Base64.getEncoder().encodeToString(ciphertext))
            .putString(KEY_IV, Base64.getEncoder().encodeToString(iv))
            .commit()
        return rawPassphrase
    }

    fun hasKeyError(): Boolean = _keyStatus.value is DatabaseKeyStatus.Error

    fun markError(message: String, cause: Throwable? = null) {
        _keyStatus.value = DatabaseKeyStatus.Error(message, cause)
    }

    /**
     * Resets the database and encryption key: deletes old database files (including WAL/SHM sidecars)
     * and stored wrapped key, generates a fresh key, and marks status as Ready.
     */
    @Synchronized
    fun resetDatabaseAndKey(): ByteArray {
        context.deleteDatabase(PennyWiseDatabase.DATABASE_NAME)
        deleteDatabaseFiles(context.getDatabasePath(PennyWiseDatabase.DATABASE_NAME))
        prefs.edit().clear().commit()
        try {
            keyStoreHelper.removeMasterKey()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to remove master key: ${e.message}")
        }
        cachedPassphrase = null
        val newPassphrase = generateAndStoreNewPassphrase()
        cachedPassphrase = newPassphrase
        _keyStatus.value = DatabaseKeyStatus.Ready
        return newPassphrase
    }
}
