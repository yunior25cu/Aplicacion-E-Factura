package com.authvex.balaxysefactura.core.repository

import com.authvex.balaxysefactura.core.network.*

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

    open suspend fun getSuggestions(): Result<List<String>> {
        return try {
            val res = aiAssistantApi.suggestions()
            Result.success(res.suggestions)
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
