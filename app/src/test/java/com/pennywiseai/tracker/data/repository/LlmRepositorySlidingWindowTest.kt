package com.pennywiseai.tracker.data.repository

import com.pennywiseai.tracker.data.database.dao.ChatDao
import com.pennywiseai.tracker.data.database.entity.ChatMessage
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.domain.service.LlmEvent
import com.pennywiseai.tracker.domain.service.LlmService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class LlmRepositorySlidingWindowTest {

    private val llmService = mockk<LlmService>(relaxed = true)
    private val chatDao = mockk<ChatDao>(relaxed = true)
    private val userPrefs = mockk<UserPreferencesRepository>(relaxed = true)
    private val aiContextRepo = mockk<AiContextRepository>(relaxed = true)

    private val repository = LlmRepository(
        llmService = llmService,
        chatDao = chatDao,
        aiContextRepository = aiContextRepo,
        userPreferencesRepository = userPrefs,
        categoryRepository = mockk(relaxed = true),
        accountBalanceRepository = mockk(relaxed = true),
        transactionRepository = mockk(relaxed = true)
    )

    @Test
    fun testSlidingWindowPassesOnlyLast20MessagesToLlmService() = runBlocking {
        coEvery { userPrefs.isGeminiApiKeyConfigured() } returns true
        every { llmService.hasActiveConversation() } returns false
        every { llmService.sendMessageEvents(any()) } returns flowOf(LlmEvent.Text("Response"))

        // Create 30 historical messages
        val historyMessages = (1..30).map { i ->
            ChatMessage(
                message = "Message $i",
                isUser = i % 2 == 1,
                isSystemPrompt = false
            )
        }
        val systemMsg = ChatMessage(message = "SYSTEM PROMPT", isUser = false, isSystemPrompt = true)
        val allContext = listOf(systemMsg) + historyMessages
        coEvery { chatDao.getAllMessagesForContext() } returns allContext

        val historySlot = slot<List<Pair<String, Boolean>>>()
        coEvery { llmService.createConversation(any(), capture(historySlot), any()) } returns Result.success(Unit)

        repository.sendMessageStream("New message").toList()

        coVerify { llmService.createConversation(any(), any(), true) }
        val capturedHistory = historySlot.captured
        assertEquals(20, capturedHistory.size)
        assertEquals("Message 11", capturedHistory.first().first)
        assertEquals("Message 30", capturedHistory.last().first)
    }

    @Test
    fun testNoExceptionWhenHistoryExceeds1200Tokens() = runBlocking {
        coEvery { userPrefs.isGeminiApiKeyConfigured() } returns true
        every { llmService.hasActiveConversation() } returns true
        every { llmService.sendMessageEvents(any()) } returns flowOf(LlmEvent.Text("All good"))

        // Create large messages exceeding 10,000 characters
        val largeMessages = (1..10).map { i ->
            ChatMessage(
                message = "Long text ".repeat(200), // ~2000 chars each
                isUser = true,
                isSystemPrompt = false
            )
        }
        coEvery { chatDao.getAllMessagesForContext() } returns largeMessages

        // Should NOT throw "Chat memory is full"
        val results = repository.sendMessageStream("Another question").toList()
        assertTrue(results.isNotEmpty())
        assertEquals("All good", results.first())
    }
}
