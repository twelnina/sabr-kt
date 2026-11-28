package io.github.twelnina.sabr.innertube

import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.*

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

        return url.takeIf { it.isString }?.content?.trim()?.takeIf(::isAbsoluteHttpUrl)
    }

    suspend fun findSabrUrl(videoId: String): String? {
        val response = requestPlayer(videoId)
        if (!response.status.isSuccess()) {
            throw InnerTubeHttpException(response.status.value)
        }

        return extractSabrUrl(response.bodyAsText())
    }
}


private fun isAbsoluteHttpUrl(value: String): Boolean {
    val authorityAndPath = value.substringAfter("://", missingDelimiterValue = "")
    if (authorityAndPath.isEmpty() || authorityAndPath.startsWith('/')) return false
    if (value.any { it.isWhitespace() || it.isISOControl() || it in "\\\"<>^`{|}" }) return false
    if (value.hasInvalidPercentEscape()) return false

    val url = try {
        URLBuilder().takeFrom(value)
    } catch (_: URLParserException) {
        return false
    }

    return (url.protocolOrNull == URLProtocol.HTTP || url.protocolOrNull == URLProtocol.HTTPS) &&
            url.host.isNotEmpty()
}

private fun String.hasInvalidPercentEscape(): Boolean {
    val invalidPercentEscape = Regex("%(?![0-9A-Fa-f]{2})")
    return invalidPercentEscape.containsMatchIn(this)
}

internal class InnerTubeHttpException(val statusCode: Int) :
    Exception("Innertube Player API returned HTTP $statusCode")
