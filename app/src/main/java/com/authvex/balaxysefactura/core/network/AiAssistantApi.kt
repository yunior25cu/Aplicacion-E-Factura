package com.authvex.balaxysefactura.core.network

import retrofit2.http.*

interface AiAssistantApi {
    @POST("AiAssistant/chat")
    suspend fun chat(@Body request: AiChatRequest): AiChatResponse

    @POST("AiAssistant/acciones")
    suspend fun ejecutarAccion(@Body request: AiAccionRequest): AiAccionResultado

    @GET("AiAssistant/suggestions")
    suspend fun suggestions(): AiSuggestionsResponse

    @GET("AiAssistant/conversations")
    suspend fun conversations(): List<AiConversationSummary>

    @GET("AiAssistant/conversations/{conversationId}/messages")
    suspend fun messages(@Path("conversationId") conversationId: String): List<AiConversationMessage>

    @POST("AiAssistant/feedback")
    suspend fun feedback(@Body request: AiFeedbackRequest): AiFeedbackResponse
}
