# Google Gemini 3.5 Flash-Lite Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the legacy 1.6 GB on-device LiteRT-LM model download with Google Gemini Cloud API (`gemini-3.5-flash-lite`) via user-provided API key, supporting streaming financial chat and smart transaction function calling.

**Architecture:** A Ktor-based `GeminiLlmServiceImpl` implements `LlmService`, streaming Server-Sent Events (SSE) from the Gemini API and parsing text chunks and tool calls. `UserPreferencesRepository` stores the key in DataStore preferences (excluded from backups). Chat and Settings screens are updated to configure and validate keys, and all LiteRT-LM dependencies and downloader artifacts are removed.

**Tech Stack:** Kotlin, Jetpack Compose, Material 3, Ktor Client (Android, ContentNegotiation, Serialization), Kotlinx Coroutines/Flow, Room Database, Hilt.

**Spec:** `docs/superpowers/specs/2026-10-21-gemini-api-integration-design.md`

## Global Constraints
- Target Gemini Model: `gemini-3.5-flash-lite`
- Gemini API Endpoint: `https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:streamGenerateContent?alt=sse&key=`
- All database mutations from AI (`add_transaction`, `update_transaction`, `delete_transaction`) must require explicit user confirmation via UI cards; no automatic database writes.
- User API key must remain strictly device-local and excluded from `PennyWiseBackup` / `PreferencesSnapshot`.
- Sliding window of 20 messages for chat context to prevent excessive token/data usage.

## Review Focus
1. **Empty/Revoked API key**: If key is missing, blank, or revoked (HTTP 400/403), UI displays clear banner without crashing and directs user to enter a valid key.
2. **Offline/Timeout resilience**: When no network connection is available or request times out, catch network exceptions and surface a friendly error message, resetting loading state.
3. **Double-send / In-flight race**: While an AI stream is loading, send button and input are disabled to prevent duplicate concurrent API requests.
4. **Tool Call & Confirmation handling**: Tool calls (`add_transaction`) emit `LlmEvent.ToolCall`, post `PendingChatAction` card, and only persist to Room upon user tap.
5. **Context Windowing**: Chat with > 20 messages correctly takes only the most recent 20 messages while always preserving the system instruction from `AiContextRepository`.

---

### Task 1: API Key Storage in UserPreferencesRepository & Backup Isolation

**Files:**
- Modify: `pennywiseai-tracker/app/src/main/java/com/pennywiseai/tracker/data/preferences/UserPreferencesRepository.kt`
- Test: `pennywiseai-tracker/app/src/test/java/com/pennywiseai/tracker/data/preferences/UserPreferencesGeminiKeyTest.kt`

**Interfaces:**
- Produces:
  - `UserPreferencesRepository.geminiApiKey: Flow<String?>`
  - `suspend UserPreferencesRepository.updateGeminiApiKey(key: String?)`
  - `suspend UserPreferencesRepository.isGeminiApiKeyConfigured(): Boolean`

- [ ] **Step 1: Write the failing test**

```kotlin
package com.pennywiseai.tracker.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UserPreferencesGeminiKeyTest {
    private lateinit var context: Context
    private lateinit var repository: UserPreferencesRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        repository = UserPreferencesRepository(context)
    }

    @Test
    fun testGeminiApiKeyStorageAndTrimming() = runTest {
        assertNull(repository.geminiApiKey.first())
        assertFalse(repository.isGeminiApiKeyConfigured())

        repository.updateGeminiApiKey("  AIzaSyTestKey123  ")
        assertEquals("AIzaSyTestKey123", repository.geminiApiKey.first())
        assertTrue(repository.isGeminiApiKeyConfigured())

        repository.updateGeminiApiKey("")
        assertNull(repository.geminiApiKey.first())
        assertFalse(repository.isGeminiApiKeyConfigured())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.pennywiseai.tracker.data.preferences.UserPreferencesGeminiKeyTest"`
Expected: FAIL with compilation error (unresolved `geminiApiKey`, `updateGeminiApiKey`).

- [ ] **Step 3: Implement `geminiApiKey` and `updateGeminiApiKey` in `UserPreferencesRepository.kt`**

Add `PreferencesKeys.GEMINI_API_KEY`:
```kotlin
val GEMINI_API_KEY = stringPreferencesKey("gemini_api_key")
```
Add public flow and methods:
```kotlin
val geminiApiKey: Flow<String?> = context.dataStore.data
    .map { preferences -> preferences[PreferencesKeys.GEMINI_API_KEY] }

suspend fun updateGeminiApiKey(key: String?) {
    context.dataStore.edit { preferences ->
        val trimmed = key?.trim()
        if (trimmed.isNullOrEmpty()) {
            preferences.remove(PreferencesKeys.GEMINI_API_KEY)
        } else {
            preferences[PreferencesKeys.GEMINI_API_KEY] = trimmed
        }
    }
}

suspend fun isGeminiApiKeyConfigured(): Boolean {
    return !geminiApiKey.first().isNullOrBlank()
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.pennywiseai.tracker.data.preferences.UserPreferencesGeminiKeyTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pennywiseai/tracker/data/preferences/UserPreferencesRepository.kt app/src/test/java/com/pennywiseai/tracker/data/preferences/UserPreferencesGeminiKeyTest.kt
git commit -m "feat(ai): add geminiApiKey preference storage and backup exclusion"
```

---

### Task 2: Gemini API Models, Tool Schema & SSE Streaming Client

**Files:**
- Create: `pennywiseai-tracker/app/src/main/java/com/pennywiseai/tracker/data/service/GeminiApiModels.kt`
- Create: `pennywiseai-tracker/app/src/main/java/com/pennywiseai/tracker/data/service/GeminiLlmServiceImpl.kt`
- Modify: `pennywiseai-tracker/app/src/main/java/com/pennywiseai/tracker/di/LlmModule.kt`
- Test: `pennywiseai-tracker/app/src/test/java/com/pennywiseai/tracker/data/service/GeminiLlmServiceTest.kt`

**Interfaces:**
- Consumes:
  - `PennyWiseTools` schemas
  - `UserPreferencesRepository.geminiApiKey`
  - `LlmService` interface (`initialize`, `createConversation`, `sendMessageEvents`, `hasActiveConversation`, `reset`, `isInitialized`)
- Produces:
  - `GeminiLlmServiceImpl`: streams `LlmEvent.Text` and `LlmEvent.ToolCall` from `streamGenerateContent` SSE response.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.pennywiseai.tracker.data.service

import com.pennywiseai.tracker.domain.service.LlmEvent
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class GeminiLlmServiceTest {

    @Test
    fun testParseSseTextAndToolCall() = runTest {
        val sseResponse = """
            data: {"candidates":[{"content":{"parts":[{"text":"I found your "}]}}]}
            
            data: {"candidates":[{"content":{"parts":[{"functionCall":{"name":"spending_by_category","args":{"category":"Food"}}}]}}]}
            
        """.trimIndent()

        val mockEngine = MockEngine { request ->
            respond(
                content = sseResponse,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/event-stream")
            )
        }
        val client = HttpClient(mockEngine)
        val service = GeminiLlmServiceImpl(client = client)
        service.setApiKey("test-api-key")
        service.createConversation("system prompt", emptyList(), withTools = true)

        val events = service.sendMessageEvents("How much on food?").toList()
        assertEquals(2, events.size)
        assertTrue(events[0] is LlmEvent.Text)
        assertEquals("I found your ", (events[0] as LlmEvent.Text).delta)
        assertTrue(events[1] is LlmEvent.ToolCall)
        val toolCall = events[1] as LlmEvent.ToolCall
        assertEquals("spending_by_category", toolCall.name)
        assertEquals("Food", toolCall.arguments["category"])
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.pennywiseai.tracker.data.service.GeminiLlmServiceTest"`
Expected: FAIL with compilation error (unresolved `GeminiLlmServiceImpl`).

- [ ] **Step 3: Implement `GeminiApiModels.kt` and `GeminiLlmServiceImpl.kt`**

In `GeminiApiModels.kt`:
- Define serializable request DTOs:
  - `GeminiGenerateRequest(systemInstruction, contents, tools, toolConfig)`
  - `GeminiContent(role, parts)`
  - `GeminiPart(text, functionCall)`
  - `GeminiFunctionCall(name, args)`
  - `GeminiToolsConfig(functionDeclarations)`
- Define tool declaration schemas for `add_transaction`, `update_transaction`, `delete_transaction`, `month_summary`, `spending_by_category`.
- Define SSE line parser splitting `data: ` and extracting `candidates[0].content.parts`.

In `GeminiLlmServiceImpl.kt`:
- Implement `LlmService`.
- Target endpoint: `https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:streamGenerateContent?alt=sse&key=$apiKey`.
- Map incoming stream lines into `LlmEvent.Text` and `LlmEvent.ToolCall`.
- In `LlmModule.kt`: bind `GeminiLlmServiceImpl` as the singleton implementation of `LlmService`.

- [ ] **Step 4: Run test to verify it passes**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.pennywiseai.tracker.data.service.GeminiLlmServiceTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pennywiseai/tracker/data/service/GeminiApiModels.kt app/src/main/java/com/pennywiseai/tracker/data/service/GeminiLlmServiceImpl.kt app/src/main/java/com/pennywiseai/tracker/di/LlmModule.kt app/src/test/java/com/pennywiseai/tracker/data/service/GeminiLlmServiceTest.kt
git commit -m "feat(ai): implement GeminiLlmServiceImpl for gemini-3.5-flash-lite"
```

---

### Task 3: LlmRepository Sliding Window & Tool Interception

**Files:**
- Modify: `pennywiseai-tracker/app/src/main/java/com/pennywiseai/tracker/data/repository/LlmRepository.kt`
- Test: `pennywiseai-tracker/app/src/test/java/com/pennywiseai/tracker/data/repository/LlmRepositorySlidingWindowTest.kt`

**Interfaces:**
- Consumes:
  - `UserPreferencesRepository.geminiApiKey`
  - `AiContextRepository.getChatContext()`
  - `GeminiLlmServiceImpl`
- Produces:
  - `LlmRepository.sendMessageStream(userMessage): Flow<String>` (windowed to 20 messages, no 1,200 token cap, tool interception preserved)

- [ ] **Step 1: Write the failing test**

```kotlin
package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.entity.ChatMessage
import org.junit.Assert.*
import org.junit.Test

class LlmRepositorySlidingWindowTest {

    @Test
    fun testSlidingWindowSelectsLast20Messages() {
        val messages = (1..35).map { index ->
            ChatMessage(id = index.toLong(), message = "Message $index", isUser = index % 2 == 1, isSystemPrompt = false)
        }
        val windowed = messages.takeLast(20)
        assertEquals(20, windowed.size)
        assertEquals("Message 16", windowed.first().message)
        assertEquals("Message 35", windowed.last().message)
    }
}
```

- [ ] **Step 2: Run test to verify it passes/fails**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.pennywiseai.tracker.data.repository.LlmRepositorySlidingWindowTest"`
Expected: PASS (verifies sliding window logic unit).

- [ ] **Step 3: Update `LlmRepository.kt`**

- Remove LiteRT model file check (`modelRepository.getModelFile()`, `verifyModelIntegrity()`).
- Verify `userPreferencesRepository.isGeminiApiKeyConfigured()` in `ensureConversation()`. If not configured, throw user-friendly error `ApiKeyMissingException`.
- In `sendMessageStream()`:
  - Remove token count throwing `"Chat memory is full. Please clear the chat to continue."`.
  - Slice history with `takeLast(20)` when feeding `llmService.createConversation()`.
  - Retain tool call interception (`PennyWiseTools.ADD_TRANSACTION`, `DELETE_TRANSACTION`, `UPDATE_TRANSACTION`) producing `PendingChatAction`.

- [ ] **Step 4: Run unit tests to verify repository behavior**

Run: `.\gradlew.bat :app:testDebugUnitTest --tests "com.pennywiseai.tracker.data.repository.*"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pennywiseai/tracker/data/repository/LlmRepository.kt app/src/test/java/com/pennywiseai/tracker/data/repository/LlmRepositorySlidingWindowTest.kt
git commit -m "feat(ai): update LlmRepository with 20-message sliding window and Gemini key check"
```

---

### Task 4: UI: ChatScreen Gemini Setup Card & ViewModel Updates

**Files:**
- Modify: `pennywiseai-tracker/app/src/main/java/com/pennywiseai/tracker/ui/screens/chat/ChatViewModel.kt`
- Modify: `pennywiseai-tracker/app/src/main/java/com/pennywiseai/tracker/ui/screens/chat/ChatScreen.kt`
- Test: Verify compilation and UI state logic in `ChatViewModelTest.kt`

**Interfaces:**
- Consumes:
  - `UserPreferencesRepository.geminiApiKey`
  - `UserPreferencesRepository.updateGeminiApiKey`
- Produces:
  - `ChatViewModel.isApiKeyConfigured: StateFlow<Boolean>`
  - `ChatViewModel.saveApiKey(key: String)`
  - `ChatScreen`: displays `GeminiApiKeyCard` when `!isApiKeyConfigured` with key input, "Get Free Key" link to `https://aistudio.google.com/app/apikey`, and "Save" button.

- [ ] **Step 1: Write test for `ChatViewModel` API key state flow**

```kotlin
@Test
fun testChatViewModelReflectsApiKeyState() = runTest {
    // Verifies viewModel.isApiKeyConfigured updates immediately when UserPreferencesRepository emits key
}
```

- [ ] **Step 2: Update `ChatViewModel.kt`**

- Remove `modelDownloader` references (`downloadProgress`, `downloadedMB`, `totalMB`, `checkAndResumeDownload`).
- Add `isApiKeyConfigured: StateFlow<Boolean>` backed by `userPreferencesRepository.geminiApiKey.map { !it.isNullOrBlank() }`.
- Add `fun saveApiKey(key: String)` calling `userPreferencesRepository.updateGeminiApiKey(key)`.

- [ ] **Step 3: Update `ChatScreen.kt`**

- Remove `ModelState.DOWNLOADING` / `NOT_DOWNLOADED` progress views.
- If `!isApiKeyConfigured`: render `GeminiApiKeySetupCard`:
  - Title: "Setup Gemini AI"
  - Subtitle: "Enter your Google Gemini API key to chat and parse transactions. Free and takes 1 minute to generate at Google AI Studio."
  - OutlinedTextField for key (obscured with password toggle).
  - Button "Get Free Key" launching URL `https://aistudio.google.com/app/apikey`.
  - Button "Save Key" calling `viewModel.saveApiKey(enteredKey)`.
- If `isApiKeyConfigured`: render the active chat message list, bottom bar, and confirmation cards.

- [ ] **Step 4: Verify test suite and compile**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: SUCCESS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/pennywiseai/tracker/ui/screens/chat/ChatViewModel.kt app/src/main/java/com/pennywiseai/tracker/ui/screens/chat/ChatScreen.kt
git commit -m "feat(ui): add Gemini API key setup card to ChatScreen and drop download UI"
```

---

### Task 5: UI: SettingsScreen Gemini Configuration Card

**Files:**
- Modify: `pennywiseai-tracker/app/src/main/java/com/pennywiseai/tracker/ui/screens/settings/SettingsViewModel.kt`
- Modify: `pennywiseai-tracker/app/src/main/java/com/pennywiseai/tracker/ui/screens/settings/SettingsScreen.kt`

**Interfaces:**
- Consumes:
  - `UserPreferencesRepository.geminiApiKey`
  - `UserPreferencesRepository.updateGeminiApiKey`
- Produces:
  - Settings section "AI Assistant (Gemini 3.5 Flash-Lite)" showing configured state, masked key (`AIzaSy...****`), and edit/clear dialog.

- [ ] **Step 1: Update `SettingsViewModel.kt`**

- Remove `modelDownloader` and `modelRepository` dependencies from `SettingsViewModel`.
- Expose `geminiApiKey: StateFlow<String?>`.
- Expose `updateGeminiApiKey(key: String?)`.

- [ ] **Step 2: Update `SettingsScreen.kt`**

- Replace the "AI Model Download" card (`R.string.settings_ai_model_title`) with "Google Gemini AI Settings".
- Show status: "Configured (gemini-3.5-flash-lite)" or "Key Required".
- Tapping opens dialog to enter/update key or clear the saved key.

- [ ] **Step 3: Verify build and compile**

Run: `.\gradlew.bat :app:compileDebugKotlin`
Expected: SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/pennywiseai/tracker/ui/screens/settings/SettingsViewModel.kt app/src/main/java/com/pennywiseai/tracker/ui/screens/settings/SettingsScreen.kt
git commit -m "feat(ui): replace model download settings with Gemini API key management"
```

---

### Task 6: Cleanup: Removal of LiteRT-LM & ModelDownloader

**Files:**
- Delete: `pennywiseai-tracker/app/src/main/java/com/pennywiseai/tracker/data/manager/ModelDownloader.kt`
- Delete: `pennywiseai-tracker/app/src/main/java/com/pennywiseai/tracker/data/repository/ModelRepository.kt`
- Delete: `pennywiseai-tracker/app/src/main/java/com/pennywiseai/tracker/data/service/LlmServiceImpl.kt`
- Modify: `pennywiseai-tracker/app/build.gradle.kts` (remove `libs.litertlm.android`)
- Modify: `pennywiseai-tracker/gradle/libs.versions.toml` (remove `litertlm`)

- [ ] **Step 1: Remove legacy files and dependencies**

- Delete `ModelDownloader.kt`, `ModelRepository.kt`, and old `LlmServiceImpl.kt`.
- Remove `implementation(libs.litertlm.android)` from `app/build.gradle.kts`.
- Remove `litertlm` from `gradle/libs.versions.toml`.

- [ ] **Step 2: Run full build and unit tests**

Run: `.\gradlew.bat testDebugUnitTest`
Expected: 100% tests PASS (0 failures).

- [ ] **Step 3: Install APK on Pixel 8 emulator and verify**

Run: `.\gradlew.bat installDebug`
Verify: App launches smoothly, Chat screen shows "Setup Gemini AI", Settings shows Gemini AI configuration.

- [ ] **Step 4: Commit**

```bash
git add -u
git commit -m "refactor(ai): remove LiteRT-LM dependency and legacy model download subsystem"
```
