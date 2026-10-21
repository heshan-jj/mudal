package com.pennywiseai.tracker.data.database

import android.content.Context
import java.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.SecureRandom
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

        @Volatile
        private var instance: DatabaseKeyManager? = null

        fun getInstance(context: Context): DatabaseKeyManager {
            return instance ?: synchronized(this) {
                instance ?: DatabaseKeyManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _keyStatus = MutableStateFlow<DatabaseKeyStatus>(DatabaseKeyStatus.Ready)
    val keyStatus: StateFlow<DatabaseKeyStatus> = _keyStatus.asStateFlow()

    @Volatile
    private var cachedPassphrase: ByteArray? = null

    /**
     * Retrieves the database encryption passphrase, or generates and wraps a new one on first run.
     * Throws [DatabaseKeyException] if key loss is detected (e.g., DB exists but key is lost).
     */
    @Synchronized
    fun getOrCreatePassphrase(): ByteArray {
        cachedPassphrase?.let { return it }

        val dbFile = context.getDatabasePath(PennyWiseDatabase.DATABASE_NAME)
        val encryptedKeyBase64 = prefs.getString(KEY_CIPHERTEXT, null)
        val ivBase64 = prefs.getString(KEY_IV, null)

        val passphrase = try {
            if (encryptedKeyBase64 == null || ivBase64 == null) {
                if (dbFile.exists()) {
                    // Database exists on disk, but encryption key is gone -> KEY LOSS
                    val msg = "Database exists on device but encryption key is missing from storage."
                    Log.e(TAG, msg)
                    _keyStatus.value = DatabaseKeyStatus.Error(msg)
                    throw DatabaseKeyException(msg)
                } else {
                    // First run: generate new random 256-bit passphrase and store wrapped in Android Keystore
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
     * Resets the database and encryption key: deletes old database files and stored wrapped key,
     * generates a fresh key, and marks status as Ready.
     */
    @Synchronized
    fun resetDatabaseAndKey(): ByteArray {
        context.deleteDatabase(PennyWiseDatabase.DATABASE_NAME)
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
