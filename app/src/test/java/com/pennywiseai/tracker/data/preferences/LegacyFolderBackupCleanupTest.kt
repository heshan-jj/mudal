package com.pennywiseai.tracker.data.preferences

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.work.WorkManager
import com.pennywiseai.tracker.cleanupLegacyScheduledFolderBackup
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class LegacyFolderBackupCleanupTest {

    private class InMemoryDataStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
        private val mutex = Mutex()
        private val state = MutableStateFlow(initial)
        var updateDataCalls: Int = 0
            private set

        override val data: Flow<Preferences> = state.asStateFlow()
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences =
            mutex.withLock {
                updateDataCalls++
                val updated = transform(state.value)
                state.value = updated
                updated
            }
    }

    private val enabledKey = booleanPreferencesKey("scheduled_folder_backup_enabled")
    private val uriKey = stringPreferencesKey("scheduled_folder_backup_tree_uri")
    private val lastTimestampKey = longPreferencesKey("scheduled_folder_backup_last_timestamp")
    private val folderReceiptsKey = booleanPreferencesKey("folder_backup_include_receipts")
    private val manualReceiptsKey = booleanPreferencesKey("export_include_receipts")

    @Test
    fun `clearLegacyScheduledFolderBackup removes legacy keys, returns uri, and preserves unrelated prefs`() = runBlocking {
        val store = InMemoryDataStore(
            preferencesOf(
                enabledKey to true,
                uriKey to "content://com.android.externalstorage.documents/tree/primary%3ABackups",
                lastTimestampKey to 1700000000000L,
                folderReceiptsKey to true,
                manualReceiptsKey to false
            )
        )
        val repo = UserPreferencesRepository(
            context = mockk(relaxed = true),
            customDataStore = store
        )

        val clearedUri = repo.clearLegacyScheduledFolderBackup()

        assertEquals(
            "content://com.android.externalstorage.documents/tree/primary%3ABackups",
            clearedUri
        )
        val remaining = store.data.first()
        assertFalse(remaining.contains(enabledKey))
        assertFalse(remaining.contains(uriKey))
        assertFalse(remaining.contains(lastTimestampKey))
        assertFalse(remaining.contains(folderReceiptsKey))
        assertEquals(false, remaining[manualReceiptsKey])
        assertEquals(1, store.updateDataCalls)
    }

    @Test
    fun `clearLegacyScheduledFolderBackup removes partial legacy keys without uri`() = runBlocking {
        val store = InMemoryDataStore(
            preferencesOf(
                enabledKey to false,
                folderReceiptsKey to true,
                manualReceiptsKey to true
            )
        )
        val repo = UserPreferencesRepository(
            context = mockk(relaxed = true),
            customDataStore = store
        )

        val clearedUri = repo.clearLegacyScheduledFolderBackup()

        assertNull(clearedUri)
        val remaining = store.data.first()
        assertFalse(remaining.contains(enabledKey))
        assertFalse(remaining.contains(folderReceiptsKey))
        assertEquals(true, remaining[manualReceiptsKey])
        assertEquals(1, store.updateDataCalls)
    }

    @Test
    fun `clearLegacyScheduledFolderBackup skips write when no legacy keys exist`() = runBlocking {
        val store = InMemoryDataStore(preferencesOf(manualReceiptsKey to true))
        val repo = UserPreferencesRepository(
            context = mockk(relaxed = true),
            customDataStore = store
        )

        val clearedUri = repo.clearLegacyScheduledFolderBackup()

        assertNull(clearedUri)
        assertEquals(0, store.updateDataCalls)
        assertEquals(true, store.data.first()[manualReceiptsKey])
    }

    @Test
    fun `cleanupLegacyScheduledFolderBackup cancels work, clears prefs, and releases persistable uri permission`() = runBlocking {
        val treeUriString = "content://com.android.externalstorage.documents/tree/primary%3ABackups"
        val store = InMemoryDataStore(
            preferencesOf(
                enabledKey to true,
                uriKey to treeUriString
            )
        )
        val repo = UserPreferencesRepository(
            context = mockk(relaxed = true),
            customDataStore = store
        )
        val workManager = mockk<WorkManager>(relaxed = true)
        val contentResolver = mockk<ContentResolver>(relaxed = true)
        val parsedUri = mockk<Uri>()

        mockkStatic(Uri::class)
        try {
            every { Uri.parse(treeUriString) } returns parsedUri

            cleanupLegacyScheduledFolderBackup(
                workManagerProvider = { workManager },
                userPreferencesRepository = repo,
                contentResolver = contentResolver
            )

            verify(exactly = 1) { workManager.cancelUniqueWork("scheduled_folder_backup") }
            verify(exactly = 1) {
                contentResolver.releasePersistableUriPermission(
                    parsedUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            }
            val remaining = store.data.first()
            assertFalse(remaining.contains(enabledKey))
            assertFalse(remaining.contains(uriKey))
        } finally {
            unmockkStatic(Uri::class)
        }
    }

    @Test
    fun `cleanupLegacyScheduledFolderBackup continues when workManager throws and skips blank uri`() = runBlocking {
        val store = InMemoryDataStore(
            preferencesOf(
                enabledKey to true,
                uriKey to "   "
            )
        )
        val repo = UserPreferencesRepository(
            context = mockk(relaxed = true),
            customDataStore = store
        )
        val contentResolver = mockk<ContentResolver>(relaxed = true)

        cleanupLegacyScheduledFolderBackup(
            workManagerProvider = { throw IllegalStateException("WorkManager unavailable") },
            userPreferencesRepository = repo,
            contentResolver = contentResolver
        )

        val remaining = store.data.first()
        assertFalse(remaining.contains(enabledKey))
        assertFalse(remaining.contains(uriKey))
        verify(exactly = 0) { contentResolver.releasePersistableUriPermission(any(), any()) }
    }
}
