package com.pennywiseai.tracker.data.service

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

@Serializable
data class GeminiGenerateRequest(
    @SerialName("system_instruction")
    val systemInstruction: GeminiContent? = null,
    val contents: List<GeminiContent>,
    val tools: List<GeminiTool>? = null,
    @SerialName("tool_config")
    val toolConfig: GeminiToolConfig? = null
)

@Serializable
data class GeminiContent(
    val role: String = "model",
    val parts: List<GeminiPart>
)

@Serializable
data class GeminiPart(
    val text: String? = null,
    val functionCall: GeminiFunctionCall? = null
)

@Serializable
data class GeminiFunctionCall(
    val name: String,
    val args: JsonObject = JsonObject(emptyMap())
)

@Serializable
data class GeminiTool(
    @SerialName("function_declarations")
    val functionDeclarations: List<GeminiFunctionDeclaration>
)

@Serializable
data class GeminiFunctionDeclaration(
    val name: String,
    val description: String,
    val parameters: GeminiSchema? = null
)

@Serializable
data class GeminiSchema(
    val type: String,
    val description: String? = null,
    val properties: Map<String, GeminiSchema>? = null,
    val required: List<String>? = null
)

@Serializable
data class GeminiToolConfig(
    @SerialName("function_calling_config")
    val functionCallingConfig: GeminiFunctionCallingConfig
)

@Serializable
data class GeminiFunctionCallingConfig(
    val mode: String = "AUTO"
)

@Serializable
data class GeminiStreamResponse(
    val candidates: List<GeminiCandidate>? = null
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null
)

/** Converts JsonElement to primitive Kotlin types for ToolCall arguments */
fun JsonElement.toAny(): Any? = when (this) {
    is JsonPrimitive -> {
        if (isString) content
        else booleanOrNull ?: intOrNull ?: longOrNull ?: doubleOrNull ?: content
    }
    is JsonObject -> this.mapValues { it.value.toAny() }
    is kotlinx.serialization.json.JsonArray -> this.map { it.toAny() }
}

object GeminiToolSchemas {
    val ALL_TOOLS = listOf(
        GeminiTool(
            functionDeclarations = listOf(
                GeminiFunctionDeclaration(
                    name = "add_transaction",
                    description = "Record a transaction the user is telling you about: money they spent, paid, bought, or received.",
                    parameters = GeminiSchema(
                        type = "OBJECT",
                        properties = mapOf(
                            "amount" to GeminiSchema(type = "NUMBER", description = "Amount of money as a number, without currency symbol"),
                            "merchant" to GeminiSchema(type = "STRING", description = "The shop, service or person the money went to or came from. Never a bank or card."),
                            "category" to GeminiSchema(type = "STRING", description = "One of the known category names"),
                            "type" to GeminiSchema(type = "STRING", description = "EXPENSE for money spent or paid (the usual case), INCOME only for money received such as salary, refund or cashback"),
                            "account" to GeminiSchema(type = "STRING", description = "The bank, card or cash the user mentioned, or empty if not mentioned")
                        ),
                        required = listOf("amount", "merchant", "category", "type")
                    )
                ),
                GeminiFunctionDeclaration(
                    name = "delete_transaction",
                    description = "Delete a transaction the user recorded earlier, e.g. 'delete the starbucks coffee from yesterday'.",
                    parameters = GeminiSchema(
                        type = "OBJECT",
                        properties = mapOf(
                            "merchant" to GeminiSchema(type = "STRING", description = "Merchant, shop or description words the user used to identify it"),
                            "amount" to GeminiSchema(type = "NUMBER", description = "Amount if the user mentioned one, else 0"),
                            "daysAgo" to GeminiSchema(type = "INTEGER", description = "How many days ago: 0 for today, 1 for yesterday, -1 if not mentioned")
                        ),
                        required = listOf("merchant")
                    )
                ),
                GeminiFunctionDeclaration(
                    name = "update_transaction",
                    description = "Change the category or merchant name of a transaction the user recorded earlier, e.g. 'the uber yesterday was actually Transportation'.",
                    parameters = GeminiSchema(
                        type = "OBJECT",
                        properties = mapOf(
                            "merchant" to GeminiSchema(type = "STRING", description = "Merchant, shop or description words the user used to identify it"),
                            "amount" to GeminiSchema(type = "NUMBER", description = "Amount if the user mentioned one, else 0"),
                            "daysAgo" to GeminiSchema(type = "INTEGER", description = "How many days ago: 0 for today, 1 for yesterday, -1 if not mentioned"),
                            "newCategory" to GeminiSchema(type = "STRING", description = "The new category, or empty to keep it"),
                            "newMerchant" to GeminiSchema(type = "STRING", description = "The new merchant name, or empty to keep it")
                        ),
                        required = listOf("merchant")
                    )
                ),
                GeminiFunctionDeclaration(
                    name = "month_summary",
                    description = "Look up this month's totals: money spent, money received and number of transactions."
                ),
                GeminiFunctionDeclaration(
                    name = "spending_by_category",
                    description = "Look up how much the user has spent in a category this month.",
                    parameters = GeminiSchema(
                        type = "OBJECT",
                        properties = mapOf(
                            "category" to GeminiSchema(type = "STRING", description = "One of the known category names")
                        ),
                        required = listOf("category")
                    )
                )
            )
        )
    )
}
