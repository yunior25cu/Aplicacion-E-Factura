package com.authvex.balaxysefactura.core.repository

import com.authvex.balaxysefactura.core.network.*
import kotlinx.serialization.json.JsonElement

open class AiAssistantRepository(
    private val aiAssistantApi: AiAssistantApi,
    private val accessApi: AccessApi,
    val enabled: Boolean
) {

    private var cachedCanUse: Boolean? = null

    open suspend fun sendMessage(message: String, conversationId: String?): Result<AiChatResponse> {
        val trimmed = message.trim()
        if (trimmed.isEmpty()) {
            return Result.failure(AppError.Validation("El mensaje no puede estar vacío"))
        }
        if (trimmed.length > 10000) {
            return Result.failure(AppError.Validation("El mensaje no puede superar los 10.000 caracteres"))
        }

        return try {
            val response = aiAssistantApi.chat(AiChatRequest(message = trimmed, conversationId = conversationId))
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }

    open suspend fun executeAction(herramienta: String, argumentos: JsonElement?): Result<AiAccionResultado> {
        return try {
            val response = aiAssistantApi.ejecutarAccion(AiAccionRequest(herramienta = herramienta, argumentos = argumentos))
            Result.success(response)
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }

    open suspend fun getSuggestions(): Result<List<String>> {
        return try {
            val res = aiAssistantApi.suggestions()
            Result.success(res.suggestions)
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }

    open suspend fun getMessages(conversationId: String): Result<List<AiConversationMessage>> {
        return try {
            val list = aiAssistantApi.messages(conversationId)
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(ErrorMapper.fromThrowable(e))
        }
    }

    open suspend fun canUseAssistant(): Boolean {
        if (!enabled) return false
        cachedCanUse?.let { return it }

        return try {
            val profile = accessApi.me()
            val canUse = "ai.assistant" in profile.capabilities
            cachedCanUse = canUse
            canUse
        } catch (_: Exception) {
            false
        }
    }

    open fun invalidateAccess() {
        cachedCanUse = null
    }
}
