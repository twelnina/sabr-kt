package io.github.twelnina.sabr.innertube

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull

class InnerTubeClientTest {
    @Test
    fun sendsPlayerRequestAndReturnsSabrUrl() = runTest {
        val sabrUrl = "https://example.com/sabr?token=123"
        val client = mockHttpClient { request ->
            assertEquals(HttpMethod.Post, request.method)
            assertEquals("https://www.youtube.com/youtubei/v1/player", request.url.toString())

            val body = assertIs<TextContent>(request.body)
            assertEquals(ContentType.Application.Json, body.contentType)
            val expectedBody = Json.parseToJsonElement(
                """
                {
                  "videoId": "testVideoId",
                  "context": {"client": {"clientName": "WEB", "clientVersion": "test-version"}},
                  "playbackContext": {"contentPlaybackContext": {"signatureTimestamp": 12345}}
                }
                """.trimIndent()
            )
            assertEquals(expectedBody, Json.parseToJsonElement(body.text))

            respond("""{"streamingData":{"serverAbrStreamingUrl":"$sabrUrl"}}""")
        }

        client.use { httpClient ->
            assertEquals(sabrUrl, createClient(httpClient).findSabrUrl("testVideoId"))
        }
    }

    @Test
    fun returnsNullWhenSabrUrlIsMissingOrUnusable() = runTest {
        val responses = listOf(
            "{}",
            """{"streamingData":{}}""",
            """{"streamingData":{"serverAbrStreamingUrl":"   "}}""",
            """{"streamingData":{"serverAbrStreamingUrl":null}}""",
            """{"streamingData":{"serverAbrStreamingUrl":123}}"""
        )

        for (body in responses) {
            mockHttpClient { respond(body) }.use { httpClient ->
                assertNull(createClient(httpClient).findSabrUrl("testVideoId"), "Response: $body")
            }
        }
    }

    @Test
    fun throwsHttpExceptionBeforeParsingResponse() = runTest {
        val client = mockHttpClient { respond("Forbidden", HttpStatusCode.Forbidden) }

        client.use { httpClient ->
            val error = assertFailsWith<InnerTubeHttpException> {
                createClient(httpClient).findSabrUrl("testVideoId")
            }
            assertEquals(403, error.statusCode)
        }
    }

    @Test
    fun rejectsMalformedJsonInSuccessfulResponse() = runTest {
        val client = mockHttpClient { respond("{") }

        client.use { httpClient ->
            assertFailsWith<SerializationException> {
                createClient(httpClient).findSabrUrl("testVideoId")
            }
        }
    }

    private fun mockHttpClient(handler: MockRequestHandler) = HttpClient(MockEngine) {
        engine { addHandler(handler) }
    }

    private fun createClient(httpClient: HttpClient) = InnerTubeClient(
        httpClient = httpClient,
        clientVersion = "test-version",
        signatureTimestamp = 12345
    )
}
