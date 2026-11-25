package io.github.twelnina.sabr.innertube

import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

internal class InnerTubeClient(
    private val httpClient: HttpClient,
    private val clientVersion: String,
    private val signatureTimestamp: Int
) {
    private suspend fun requestPlayer(videoId: String): HttpResponse {
        val body = buildJsonObject {
            put("videoId", videoId)
            putJsonObject("context") {
                putJsonObject("client") {
                    put("clientName", "WEB")
                    put("clientVersion", clientVersion)
                }
            }
            putJsonObject("playbackContext") {
                putJsonObject("contentPlaybackContext") {
                    put("signatureTimestamp", signatureTimestamp)
                }
            }
        }

        return httpClient.post("https://www.youtube.com/youtubei/v1/player") {
            contentType(ContentType.Application.Json)
            setBody(body.toString())
        }
    }

    private fun extractSabrUrl(body: String): String? {
        val response = Json.parseToJsonElement(body) as? JsonObject ?: return null
        val streamingData = response["streamingData"] as? JsonObject ?: return null
        val url = streamingData["serverAbrStreamingUrl"] as? JsonPrimitive ?: return null

        return url.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
    }

    suspend fun findSabrUrl(videoId: String): String? {
        val response = requestPlayer(videoId)
        if (!response.status.isSuccess()) {
            throw InnerTubeHttpException(response.status.value)
        }

        return extractSabrUrl(response.bodyAsText())
    }
}

internal class InnerTubeHttpException(val statusCode: Int) :
    Exception("Innertube Player API returned HTTP $statusCode")
