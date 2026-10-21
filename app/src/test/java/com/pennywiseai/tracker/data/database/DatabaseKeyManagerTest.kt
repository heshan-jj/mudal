// Mudal fork: new file in Mudal, a fork of PennyWise AI (AGPL-3.0-only), 2026-10-21 - see NOTICE.
package com.pennywiseai.tracker.data.database

import android.content.Context
import android.content.SharedPreferences
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.security.GeneralSecurityException

class DatabaseKeyManagerTest {

    private val context = mockk<Context>(relaxed = true)
    private val prefs = mockk<SharedPreferences>(relaxed = true)
    private val editor = mockk<SharedPreferences.Editor>(relaxed = true)
    private val dbFile = mockk<File>(relaxed = true)
    private val fakeKeyStoreHelper = FakeKeyStoreHelper()

    private val prefStorage = mutableMapOf<String, String>()

    @Before
    fun setup() {
        prefStorage.clear()
        fakeKeyStoreHelper.shouldFailDecrypt = false
        fakeKeyStoreHelper.keyRemoved = false

        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { context.getDatabasePath(any()) } returns dbFile

        every { prefs.getString(any(), null) } answers { prefStorage[firstArg<String>()] }
        every { prefs.edit() } returns editor

        every { editor.putString(any(), any()) } answers {
            prefStorage[firstArg<String>()] = secondArg<String>()
            editor
        }
        every { editor.commit() } returns true
        every { editor.clear() } answers {
            prefStorage.clear()
            editor
        }
    }

    @Test
    fun `first run with no db file generates new passphrase and stores wrapped key`() {
        every { dbFile.exists() } returns false

        val manager = DatabaseKeyManager(context, fakeKeyStoreHelper)
        val key = manager.getOrCreatePassphrase()

        assertNotNull(key)
        assertEquals(32, key.size)
        assertTrue(prefStorage.containsKey("encrypted_db_key"))
        assertTrue(prefStorage.containsKey("db_key_iv"))
        assertEquals(DatabaseKeyStatus.Ready, manager.keyStatus.value)
    }

    @Test
    fun `subsequent run loads and unwraps existing passphrase`() {
        every { dbFile.exists() } returns false

        val manager1 = DatabaseKeyManager(context, fakeKeyStoreHelper)
        val key1 = manager1.getOrCreatePassphrase()

        // Second instance simulating next app launch
        every { dbFile.exists() } returns true
        val manager2 = DatabaseKeyManager(context, fakeKeyStoreHelper)
        val key2 = manager2.getOrCreatePassphrase()

        assertArrayEquals(key1, key2)
        assertEquals(DatabaseKeyStatus.Ready, manager2.keyStatus.value)
    }

    @Test
    fun `db file exists but keys missing from storage triggers key loss error`() {
        every { dbFile.exists() } returns true
        // prefs empty

        val manager = DatabaseKeyManager(context, fakeKeyStoreHelper)

        assertThrows(DatabaseKeyException::class.java) {
            manager.getOrCreatePassphrase()
        }

        assertTrue(manager.hasKeyError())
        val status = manager.keyStatus.value
        assertTrue(status is DatabaseKeyStatus.Error)
    }

    @Test
    fun `keystore decryption failure triggers key loss error`() {
        every { dbFile.exists() } returns false
        val manager1 = DatabaseKeyManager(context, fakeKeyStoreHelper)
        manager1.getOrCreatePassphrase()

        // Decryption fails
        fakeKeyStoreHelper.shouldFailDecrypt = true
        every { dbFile.exists() } returns true
        val manager2 = DatabaseKeyManager(context, fakeKeyStoreHelper)

        assertThrows(DatabaseKeyException::class.java) {
            manager2.getOrCreatePassphrase()
        }

        assertTrue(manager2.hasKeyError())
    }

    @Test
    fun `resetDatabaseAndKey deletes db file and clears prefs and generates fresh key`() {
        every { dbFile.exists() } returns false
        val manager = DatabaseKeyManager(context, fakeKeyStoreHelper)
        val key1 = manager.getOrCreatePassphrase()

        val key2 = manager.resetDatabaseAndKey()

        verify { context.deleteDatabase(PennyWiseDatabase.DATABASE_NAME) }
        assertTrue(fakeKeyStoreHelper.keyRemoved)
        assertNotNull(key2)
        assertFalse(manager.hasKeyError())
    }

    private class FakeKeyStoreHelper : KeyStoreHelper {
        private val masterKey = ByteArray(32) { 0x5A.toByte() }
        var shouldFailDecrypt = false
        var keyRemoved = false

        override fun encrypt(plaintext: ByteArray): Pair<ByteArray, ByteArray> {
            val iv = ByteArray(12) { 0x02.toByte() }
            val cipher = ByteArray(plaintext.size) { i -> (plaintext[i].toInt() xor masterKey[i % 32].toInt()).toByte() }
            return Pair(cipher, iv)
        }

        override fun decrypt(ciphertext: ByteArray, iv: ByteArray): ByteArray {
            if (shouldFailDecrypt) throw GeneralSecurityException("Invalid key")
            return ByteArray(ciphertext.size) { i -> (ciphertext[i].toInt() xor masterKey[i % 32].toInt()).toByte() }
        }

        override fun removeMasterKey() {
            keyRemoved = true
        }
    }
}
