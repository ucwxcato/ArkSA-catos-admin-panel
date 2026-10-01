package com.cato.duneadmin.update

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlinx.serialization.json.Json

class GitHubReleaseClient(
    private val config: UpdateConfig = UpdateConfig(),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val http = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    fun fetchLatest(): Result<GitHubRelease> = runCatching {
        val request = HttpRequest.newBuilder(URI(config.latestReleaseUrl))
            .timeout(Duration.ofSeconds(20))
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "CatosDuneAdmin/$INSTALLED_VERSION")
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            error("GitHub release check returned HTTP ${response.statusCode()}")
        }
        json.decodeFromString<GitHubRelease>(response.body())
    }

    fun download(url: String, maximumBytes: Long): Result<ByteArray> = runCatching {
        val uri = URI(url)
        require(uri.scheme == "https") { "Update download must use HTTPS." }
        val request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofMinutes(5))
            .header("Accept", "application/octet-stream")
            .header("User-Agent", "CatosDuneAdmin/$INSTALLED_VERSION")
            .GET()
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofByteArray())
        val finalUri = response.uri()
        val host = finalUri.host.lowercase()
        val trustedHost = host == "github.com" || host.endsWith(".github.com") ||
            host == "githubusercontent.com" || host.endsWith(".githubusercontent.com")
        require(finalUri.scheme == "https" && trustedHost) {
            "Update download redirected to an unexpected host."
        }
        require(response.statusCode() == 200) { "Update download returned HTTP ${response.statusCode()}" }
        require(response.body().size.toLong() <= maximumBytes) { "Update installer is too large." }
        response.body()
    }
}
