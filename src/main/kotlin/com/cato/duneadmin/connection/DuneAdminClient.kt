package com.cato.duneadmin.connection

import java.net.CookieManager
import java.net.CookiePolicy
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class DuneAdminClient {
    private val cookies = CookieManager(null, CookiePolicy.ACCEPT_ALL)
    private val http = HttpClient.newBuilder()
        .cookieHandler(cookies)
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    var authenticated: Boolean = false
        private set

    fun logout() {
        authenticated = false
        cookies.cookieStore.removeAll()
    }

    fun login(baseUrl: String, username: String, password: String): Result<Unit> = runCatching {
        require(username.isNotBlank()) { "Enter the Dune Admin username." }
        require(password.isNotEmpty()) { "Enter the Dune Admin password." }
        val body = "{\"username\":${jsonString(username)},\"password\":${jsonString(password)}}"
        val response = request(baseUrl, "/api/v1/auth/login", "POST", body)
        check(response.statusCode() in 200..299) { "Dune Admin login failed (HTTP ${response.statusCode()})." }
        authenticated = true
    }

    fun giveItem(
        baseUrl: String,
        playerId: Long,
        template: String,
        quantity: Long,
        quality: Long,
    ): Result<String> = runCatching {
        check(authenticated) { "Log in to Dune Admin first." }
        require(playerId > 0) { "Player ID must be positive." }
        require(template.isNotBlank()) { "Enter an item template ID." }
        require(quantity in 1..10_000) { "Quantity must be between 1 and 10,000." }
        require(quality >= 0) { "Quality cannot be negative." }
        val body = "{" +
            "\"player_id\":$playerId," +
            "\"template\":${jsonString(template.trim())}," +
            "\"qty\":$quantity," +
            "\"quality\":$quality" +
            "}"
        val response = request(baseUrl, "/api/v1/players/give-item", "POST", body)
        check(response.statusCode() in 200..299) {
            "Item grant failed (HTTP ${response.statusCode()}): ${safeBody(response.body())}"
        }
        safeBody(response.body())
    }

    fun fetchItemCatalog(baseUrl: String): Result<String> = runCatching {
        check(authenticated) { "Log in to Dune Admin first." }
        val response = request(baseUrl, "/api/v1/market/catalog", "GET")
        check(response.statusCode() in 200..299) { "Item catalog failed (HTTP ${response.statusCode()})." }
        response.body()
    }

    fun fetchPlayers(baseUrl: String): Result<String> = runCatching {
        check(authenticated) { "Log in to Dune Admin first." }
        val response = request(baseUrl, "/api/v1/players", "GET")
        check(response.statusCode() in 200..299) { "Player list failed (HTTP ${response.statusCode()})." }
        response.body()
    }

    fun fetchTeleportLocations(baseUrl: String): Result<String> = runCatching {
        check(authenticated) { "Log in to Dune Admin first." }
        val response = request(baseUrl, "/api/v1/locations", "GET")
        check(response.statusCode() in 200..299) { "Teleport locations failed (HTTP ${response.statusCode()})." }
        response.body()
    }

    fun teleportToPlayer(baseUrl: String, sourceFlsId: String, targetId: Long): Result<String> = runCatching {
        check(authenticated) { "Log in to Dune Admin first." }
        require(sourceFlsId.isNotBlank()) { "Select the player to move." }
        require(targetId > 0) { "Select a destination player." }
        val body = "{" +
            "\"source_fls_id\":${jsonString(sourceFlsId.trim())}," +
            "\"target_id\":$targetId" +
            "}"
        val response = request(baseUrl, "/api/v1/players/teleport-to-player", "POST", body)
        check(response.statusCode() in 200..299) {
            "Teleport failed (HTTP ${response.statusCode()}): ${safeBody(response.body())}"
        }
        safeBody(response.body())
    }

    fun teleportToLocation(baseUrl: String, sourceFlsId: String, location: String): Result<String> = runCatching {
        check(authenticated) { "Log in to Dune Admin first." }
        require(sourceFlsId.isNotBlank()) { "Select the player to move." }
        require(location.isNotBlank()) { "Select a destination location." }
        val body = "{" +
            "\"fls_id\":${jsonString(sourceFlsId.trim())}," +
            "\"partition_label\":${jsonString(location.trim())}" +
            "}"
        val response = request(baseUrl, "/api/v1/players/teleport", "POST", body)
        check(response.statusCode() in 200..299) {
            "Teleport failed (HTTP ${response.statusCode()}): ${safeBody(response.body())}"
        }
        safeBody(response.body())
    }

    private fun request(baseUrl: String, path: String, method: String, body: String? = null): HttpResponse<String> {
        val builder = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl.trimEnd('/') + path))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
        val publisher = body?.let(HttpRequest.BodyPublishers::ofString) ?: HttpRequest.BodyPublishers.noBody()
        val request = builder.method(method, publisher)
            .build()
        return http.send(request, HttpResponse.BodyHandlers.ofString())
    }

    private fun safeBody(body: String): String = body.trim().take(600).ifBlank { "No response body." }

    private fun jsonString(value: String): String = buildString {
        append('"')
        value.forEach { ch ->
            when (ch) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(ch)
            }
        }
        append('"')
    }
}
