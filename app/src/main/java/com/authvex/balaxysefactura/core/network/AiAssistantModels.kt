package com.authvex.balaxysefactura.core.network

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

@Serializable
data class AiChatRequest(
    val message: String,
    val conversationId: String? = null
)

@Serializable
data class AiChatResponse(
    val response: String,
    val conversationId: String,
    val requestId: String,
    val toolsUsed: List<String> = emptyList(),
    val tokensInput: Int = 0,
    val tokensOutput: Int = 0,
    val durationMs: Int = 0,
    val tarjetas: List<AiTarjeta>? = null
)

@Serializable
data class AiTarjeta(
    val tool: String,
    val titulo: String? = null,
    val datos: JsonElement = JsonNull
)

@Serializable
data class AiSuggestionsResponse(
    val suggestions: List<String> = emptyList()
)

@Serializable
data class AiConversationMessage(
    val role: String,
    val content: String,
    val toolsUsed: List<String>? = null,
    val durationMs: Int? = null,
    val createdAt: String = ""
)

@Serializable
data class AccessProfile(
    val capabilities: List<String> = emptyList(),
    val addons: List<String> = emptyList(),
    val permissions: List<String> = emptyList()
)
