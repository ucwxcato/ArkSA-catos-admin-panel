package com.cato.duneadmin.update

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.deleteIfExists
import kotlin.io.path.writeBytes

sealed interface UpdateResult {
    data object NoUpdate : UpdateResult
    data class Available(val release: ParsedRelease) : UpdateResult
    data class Failed(val message: String) : UpdateResult
}

class UpdateService(
    private val config: UpdateConfig = UpdateConfig(),
    private val client: GitHubReleaseClient = GitHubReleaseClient(config),
) {
    companion object {
        private const val MAX_INSTALLER_BYTES = 250L * 1024 * 1024
        private const val MAX_MANIFEST_BYTES = 1024L * 1024L
    }

    private var lastCheckMillis = 0L

    fun check(force: Boolean = false): UpdateResult {
        val now = System.currentTimeMillis()
        if (!force && now - lastCheckMillis < config.checkInterval.inWholeMilliseconds) {
            return UpdateResult.NoUpdate
        }
        lastCheckMillis = now
        return client.fetchLatest().fold(
            onSuccess = { release ->
                release.selectFor(config, checkNotNull(parseSemVer(INSTALLED_VERSION)))
                    ?.let(UpdateResult::Available)
                    ?: UpdateResult.NoUpdate
            },
            onFailure = { UpdateResult.Failed("Unable to check for updates right now.") },
        )
    }

    fun downloadAndVerify(release: ParsedRelease): Result<Path> = runCatching {
        val installerBytes = client.download(release.msi.browserDownloadUrl, MAX_INSTALLER_BYTES).getOrThrow()
        val manifestBytes = client.download(release.checksumManifest.browserDownloadUrl, MAX_MANIFEST_BYTES).getOrThrow()
        val expectedHash = parseChecksumManifest(manifestBytes.toString(Charsets.UTF_8), release.msi.name)
        val actualHash = sha256(installerBytes)
        require(actualHash.equals(expectedHash, ignoreCase = true)) { "Installer checksum verification failed." }
        val file = Files.createTempFile("CatosDuneAdmin-${release.version}-", ".msi")
        file.writeBytes(installerBytes)
        file
    }

    fun launchInstaller(installer: Path): Result<Unit> = runCatching {
        require(Files.isRegularFile(installer)) { "Verified installer is no longer available." }
        ProcessBuilder("msiexec.exe", "/i", installer.toAbsolutePath().toString(), "/passive")
            .start()
    }

    fun cleanup(installer: Path) {
        installer.deleteIfExists()
    }

    private fun parseChecksumManifest(manifest: String, filename: String): String {
        val matches = manifest.lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .mapNotNull { line ->
                val parts = line.split(Regex("\\s+"), limit = 2)
                if (parts.size == 2 && parts[1].removePrefix("*") == filename) parts[0] else null
            }
            .toList()
        require(matches.size == 1 && matches.single().matches(Regex("[0-9a-fA-F]{64}"))) {
            "Checksum manifest is missing a unique valid MSI digest."
        }
        return matches.single()
    }

    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256")
        .digest(bytes)
        .joinToString("") { "%02x".format(it) }
}
