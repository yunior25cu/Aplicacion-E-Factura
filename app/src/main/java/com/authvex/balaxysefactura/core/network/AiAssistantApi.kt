package com.authvex.balaxysefactura.core.network

import retrofit2.http.*

interface AiAssistantApi {
    @POST("AiAssistant/chat")
    suspend fun chat(@Body request: AiChatRequest): AiChatResponse

    @GET("AiAssistant/suggestions")
    suspend fun suggestions(): AiSuggestionsResponse

    @GET("AiAssistant/conversations/{conversationId}/messages")
    suspend fun messages(@Path("conversationId") conversationId: String): List<AiConversationMessage>
}
