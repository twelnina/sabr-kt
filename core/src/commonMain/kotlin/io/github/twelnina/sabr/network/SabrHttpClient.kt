package io.github.twelnina.sabr.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO

internal fun createSabrHttpClient(): HttpClient = HttpClient(CIO)
