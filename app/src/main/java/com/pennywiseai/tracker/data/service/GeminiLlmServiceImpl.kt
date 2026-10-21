package com.pennywiseai.tracker.data.service

import android.util.Log
import com.pennywiseai.tracker.data.preferences.UserPreferencesRepository
import com.pennywiseai.tracker.domain.service.LlmEvent
import com.pennywiseai.tracker.domain.service.LlmService
import io.ktor.client.HttpClient
import io.ktor.client.engine.android.Android
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.header
import io.ktor.client.request.preparePost
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeminiLlmServiceImpl(
    private val client: HttpClient,
    private val userPreferencesRepository: UserPreferencesRepository? = null
) : LlmService {

    @Inject
    constructor(userPreferencesRepository: UserPreferencesRepository) : this(
        client = HttpClient(Android) {
            install(HttpTimeout) {
                requestTimeoutMillis = 60_000
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 60_000
            }
            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    coerceInputValues = true
                    encodeDefaults = true
                })
            }
        },
        userPreferencesRepository = userPreferencesRepository
    )

    private val jsonParser = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private var inMemoryApiKey: String? = null
    private var systemPrompt: String = ""
    private val conversationHistory = mutableListOf<Pair<String, Boolean>>()
    private var enableTools: Boolean = false
    private var hasActiveConv: Boolean = false
    private val lock = Mutex()

    fun setApiKey(key: String) {
        inMemoryApiKey = key.trim()
    }

    private suspend fun getEffectiveApiKey(): String? {
        inMemoryApiKey?.takeIf { it.isNotBlank() }?.let { return it }
        return userPreferencesRepository?.geminiApiKey?.first()?.trim()?.takeIf { it.isNotBlank() }
    }

    override suspend fun initialize(modelPath: String): Result<Unit> {
        val key = getEffectiveApiKey()
        return if (!key.isNullOrBlank()) {
            Result.success(Unit)
        } else {
            Result.failure(IllegalStateException("Gemini API key is not configured."))
        }
    }

    override suspend fun createConversation(
        systemPrompt: String,
        history: List<Pair<String, Boolean>>,
        withTools: Boolean
    ): Result<Unit> = withContext(Dispatchers.IO) {
        lock.withLock {
            this@GeminiLlmServiceImpl.systemPrompt = systemPrompt
            this@GeminiLlmServiceImpl.conversationHistory.clear()
            this@GeminiLlmServiceImpl.conversationHistory.addAll(history)
            this@GeminiLlmServiceImpl.enableTools = withTools
            this@GeminiLlmServiceImpl.hasActiveConv = true
            Result.success(Unit)
        }
    }

    override fun sendMessage(message: String): Flow<String> = sendMessageEvents(message).mapNotNull { event ->
        (event as? LlmEvent.Text)?.delta
    }

    override fun sendMessageEvents(message: String): Flow<LlmEvent> = flow {
        val apiKey = getEffectiveApiKey()
            ?: throw IllegalStateException("Gemini API key is not set. Please configure it in Settings or Chat.")

        val contentsList = mutableListOf<GeminiContent>()
        lock.withLock {
            conversationHistory.forEach { (text, isUser) ->
                contentsList.add(
                    GeminiContent(
                        role = if (isUser) "user" else "model",
                        parts = listOf(GeminiPart(text = text))
                    )
                )
            }
            contentsList.add(
                GeminiContent(
                    role = "user",
                    parts = listOf(GeminiPart(text = message))
                )
            )
        }

        val requestPayload = GeminiGenerateRequest(
            systemInstruction = if (systemPrompt.isNotBlank()) {
                GeminiContent(
                    role = "system",
                    parts = listOf(GeminiPart(text = systemPrompt))
                )
            } else null,
            contents = contentsList,
            tools = if (enableTools) GeminiToolSchemas.ALL_TOOLS else null,
            toolConfig = if (enableTools) GeminiToolConfig(GeminiFunctionCallingConfig("AUTO")) else null
        )

        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:streamGenerateContent?alt=sse&key=$apiKey"

        client.preparePost(endpoint) {
            contentType(ContentType.Application.Json)
            setBody(jsonParser.encodeToString(GeminiGenerateRequest.serializer(), requestPayload))
        }.execute { httpResponse ->
            val channel = httpResponse.bodyAsChannel()
            while (true) {
                val line = channel.readUTF8Line()
                if (line == null) {
                    break
                }
                val trimmed = line.trim()
                if (!trimmed.startsWith("data:")) continue
                val jsonPayload = trimmed.removePrefix("data:").trim()
                if (jsonPayload.isEmpty()) continue

                try {
                    val streamChunk = jsonParser.decodeFromString<GeminiStreamResponse>(jsonPayload)
                    streamChunk.candidates?.forEach { candidate ->
                        candidate.content?.parts?.forEach { part ->
                            part.text?.takeIf { it.isNotEmpty() }?.let { emit(LlmEvent.Text(it)) }
                            part.functionCall?.let { fc ->
                                val argsMap = fc.args.mapValues { it.value.toAny() }
                                emit(LlmEvent.ToolCall(fc.name, argsMap))
                            }
                        }
                    }
                } catch (_: Throwable) {
                    // Ignore malformed partial chunks
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    override fun hasActiveConversation(): Boolean = hasActiveConv

    override suspend fun closeConversation() {
        lock.withLock {
            hasActiveConv = false
            conversationHistory.clear()
        }
    }

    override suspend fun reset() {
        closeConversation()
    }

    override fun isInitialized(): Boolean = true
}
