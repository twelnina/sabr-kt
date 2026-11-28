package io.github.twelnina.sabr.cli

import io.github.twelnina.sabr.innertube.InnerTubeClient
import io.github.twelnina.sabr.innertube.InnerTubeHttpException
import io.github.twelnina.sabr.network.createSabrHttpClient
import kotlinx.io.IOException
import kotlinx.serialization.SerializationException

suspend fun main() {
    val videoId = readlnOrNull()?.trim().orEmpty()
    if (videoId.isEmpty()) {
        System.err.println("Enter a YouTube video ID.")
        return
    }

    val httpClient = createSabrHttpClient()
    try {
        val innerTube = InnerTubeClient(
            httpClient = httpClient,
            clientVersion = "2.20260708.00.00",
            signatureTimestamp = 20779
        )
        val url = innerTube.findSabrUrl(videoId)
        if (url == null) {
            System.err.println("No SABR URL found for $videoId")
        } else {
            println(url)
        }
    } catch (e: InnerTubeHttpException) {
        System.err.println("Innertube Player API returned HTTP ${e.statusCode}.")
    } catch (e: IOException) {
        System.err.println("Player API request failed: ${e.message}")
    } catch (_: SerializationException) {
        System.err.println("Player API returned invalid JSON.")
    } finally {
        httpClient.close()
    }
}
