package com.pennywiseai.tracker.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class UserPreferencesGeminiKeyTest {

    private class InMemoryDataStore(initial: Preferences = emptyPreferences()) : DataStore<Preferences> {
        private val state = MutableStateFlow(initial)
        override val data: Flow<Preferences> = state.asStateFlow()
        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            val updated = transform(state.value)
            state.value = updated
            return updated
        }
    }

    @Test
    fun testGeminiApiKeyStorageAndTrimming() = runBlocking {
        val inMemoryDataStore = InMemoryDataStore()

        val repo = UserPreferencesRepository(
            context = io.mockk.mockk(relaxed = true),
            customDataStore = inMemoryDataStore
        )

        assertNull(repo.geminiApiKey.first())
        assertFalse(repo.isGeminiApiKeyConfigured())

        repo.updateGeminiApiKey("  AIzaSyTestKey123  ")
        assertEquals("AIzaSyTestKey123", repo.geminiApiKey.first())
        assertTrue(repo.isGeminiApiKeyConfigured())

        repo.updateGeminiApiKey("")
        assertNull(repo.geminiApiKey.first())
        assertFalse(repo.isGeminiApiKeyConfigured())

        repo.updateGeminiApiKey("   ")
        assertNull(repo.geminiApiKey.first())
        assertFalse(repo.isGeminiApiKeyConfigured())
    }
}
