package com.authvex.balaxysefactura.core.network

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit

class AiAssistantRouteTest {

    private lateinit var server: MockWebServer
    private lateinit var aiApi: AiAssistantApi
    private lateinit var accessApi: AccessApi
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setup() {
        server = MockWebServer()
        val retrofit = Retrofit.Builder()
            .baseUrl(server.url("/api/v1/"))
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        aiApi = retrofit.create(AiAssistantApi::class.java)
        accessApi = retrofit.create(AccessApi::class.java)
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `chat posts to correct route with expected json body without conversationId`() = runBlocking {
        val jsonResponse = """
            {
              "response": "Hola",
              "conversationId": "c123",
              "requestId": "r456",
              "toolsUsed": ["deuda_vencida"],
              "tokensInput": 100,
              "tokensOutput": 50,
              "durationMs": 1200,
              "tarjetas": null
            }
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setBody(jsonResponse))

        val response = aiApi.chat(AiChatRequest(message = "Hola asistente"))

        val request = server.takeRequest()
        assertEquals("/api/v1/AiAssistant/chat", request.path)
        assertEquals("POST", request.method)

        val requestBodyJson = json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("Hola asistente", requestBodyJson["message"]?.jsonPrimitive?.content)
        assertFalse(requestBodyJson.containsKey("conversationId") && requestBodyJson["conversationId"]?.jsonPrimitive?.content != null)

        assertEquals("Hola", response.response)
        assertEquals("c123", response.conversationId)
        assertEquals("r456", response.requestId)
        assertNull(response.tarjetas)
    }

    @Test
    fun `chat posts with conversationId when provided`() = runBlocking {
        val jsonResponse = """
            {
              "response": "Respuesta",
              "conversationId": "c123",
              "requestId": "r456",
              "tarjetas": [
                {
                  "tool": "deuda_vencida",
                  "titulo": "Deuda",
                  "datos": { "total_vencido": 184300 }
                }
              ]
            }
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setBody(jsonResponse))

        val response = aiApi.chat(AiChatRequest(message = "¿Y el mes pasado?", conversationId = "c123"))

        val request = server.takeRequest()
        val requestBodyJson = json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertEquals("c123", requestBodyJson["conversationId"]?.jsonPrimitive?.content)

        assertEquals(1, response.tarjetas?.size)
        assertEquals("deuda_vencida", response.tarjetas?.first()?.tool)
        assertEquals("Deuda", response.tarjetas?.first()?.titulo)
    }

    @Test
    fun `suggestions uses correct route`() = runBlocking {
        val jsonResponse = """
            {
              "suggestions": ["¿Cómo viene el mes?", "¿Quién me debe más?"]
            }
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setBody(jsonResponse))

        val response = aiApi.suggestions()

        val request = server.takeRequest()
        assertEquals("/api/v1/AiAssistant/suggestions", request.path)
        assertEquals("GET", request.method)

        assertEquals(2, response.suggestions.size)
        assertEquals("¿Cómo viene el mes?", response.suggestions[0])
    }

    @Test
    fun `messages uses correct route with encoded conversationId and deserializes list with extra fields`() = runBlocking {
        val jsonResponse = """
            [
              {
                "role": "user",
                "content": "Hola",
                "createdAt": "2026-10-10T10:00:00Z",
                "extraField": "unused"
              },
              {
                "role": "assistant",
                "content": "Hola, ¿en qué puedo ayudarte?",
                "toolsUsed": ["deuda_vencida"],
                "durationMs": 1500,
                "createdAt": "2026-10-10T10:00:01Z"
              }
            ]
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setBody(jsonResponse))

        val conversationIdWithSpecialChars = "conv/123-abc"
        val response = aiApi.messages(conversationIdWithSpecialChars)

        val request = server.takeRequest()
        assertEquals("/api/v1/AiAssistant/conversations/conv%2F123-abc/messages", request.path)
        assertEquals("GET", request.method)

        assertEquals(2, response.size)
        assertEquals("user", response[0].role)
        assertEquals("Hola", response[0].content)
        assertEquals("assistant", response[1].role)
        assertEquals("Hola, ¿en qué puedo ayudarte?", response[1].content)
        assertEquals(listOf("deuda_vencida"), response[1].toolsUsed)
    }

    @Test
    fun `access me uses correct route and parses profile with extra fields`() = runBlocking {
        val jsonResponse = """
            {
              "capabilities": ["efactura.sales", "ai.assistant"],
              "addons": ["ai_chat"],
              "permissions": ["P_Usuario"],
              "extraFieldUnused": "value"
            }
        """.trimIndent()

        server.enqueue(MockResponse().setResponseCode(200).setBody(jsonResponse))

        val response = accessApi.me()

        val request = server.takeRequest()
        assertEquals("/api/v1/Access/me", request.path)
        assertEquals("GET", request.method)

        assertTrue("ai.assistant" in response.capabilities)
        assertTrue("efactura.sales" in response.capabilities)
    }
}
