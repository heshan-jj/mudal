package com.pennywiseai.tracker.data.service

import com.pennywiseai.tracker.domain.service.LlmEvent
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class GeminiLlmServiceTest {

    @Test
    fun testParseSseTextAndToolCall() = runBlocking {
        val sseResponse = """
            data: {"candidates":[{"content":{"parts":[{"text":"I found your "}]}}]}
            
            data: {"candidates":[{"content":{"parts":[{"functionCall":{"name":"spending_by_category","args":{"category":"Food"}}}]}}]}
            
        """.trimIndent()

        val mockEngine = MockEngine { request ->
            assertEquals("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:streamGenerateContent?alt=sse&key=test-api-key", request.url.toString())
            respond(
                content = sseResponse,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/event-stream")
            )
        }
        val client = HttpClient(mockEngine)
        val service = GeminiLlmServiceImpl(client = client)
        service.setApiKey("test-api-key")
        service.createConversation(
            systemPrompt = "system prompt test",
            history = listOf("hello" to true, "hi" to false),
            withTools = true
        )

        val events = service.sendMessageEvents("How much on food?").toList()
        assertEquals(2, events.size)

        assertTrue(events[0] is LlmEvent.Text)
        assertEquals("I found your ", (events[0] as LlmEvent.Text).delta)

        assertTrue(events[1] is LlmEvent.ToolCall)
        val toolCall = events[1] as LlmEvent.ToolCall
        assertEquals("spending_by_category", toolCall.name)
        assertEquals("Food", toolCall.arguments["category"])
    }

    @Test
    fun testAddTransactionToolCallParsing() = runBlocking {
        val sseResponse = """
            data: {"candidates":[{"content":{"parts":[{"functionCall":{"name":"add_transaction","args":{"amount":450.0,"merchant":"Pilawoos","category":"Food","type":"EXPENSE","account":""}}}]}}]}
        """.trimIndent()

        val mockEngine = MockEngine {
            respond(
                content = sseResponse,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/event-stream")
            )
        }
        val client = HttpClient(mockEngine)
        val service = GeminiLlmServiceImpl(client = client)
        service.setApiKey("test-api-key")
        service.createConversation("prompt", emptyList(), withTools = true)

        val events = service.sendMessageEvents("Spent 450 at Pilawoos").toList()
        assertEquals(1, events.size)
        assertTrue(events[0] is LlmEvent.ToolCall)
        val toolCall = events[0] as LlmEvent.ToolCall
        assertEquals("add_transaction", toolCall.name)
        assertEquals(450.0, (toolCall.arguments["amount"] as? Number)?.toDouble() ?: 0.0, 0.001)
        assertEquals("Pilawoos", toolCall.arguments["merchant"])
        assertEquals("Food", toolCall.arguments["category"])
        assertEquals("EXPENSE", toolCall.arguments["type"])
    }
}
