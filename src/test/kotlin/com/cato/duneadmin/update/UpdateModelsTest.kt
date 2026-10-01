package com.cato.duneadmin.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertNotNull
import kotlinx.serialization.json.Json

class UpdateModelsTest {
    @Test
    fun `github release json uses github field names`() {
        val release = Json.decodeFromString<GitHubRelease>("""
            {
              "tag_name": "v0.2.0",
              "html_url": "https://github.com/ucwxcato/ArkSA-catos-admin-panel/releases/tag/v0.2.0",
              "assets": []
            }
        """.trimIndent())
        assertEquals("v0.2.0", release.tagName)
    }

    @Test
    fun `semver uses numeric precedence and v prefix`() {
        assertEquals(1, checkNotNull(parseSemVer("v0.10.0")).compareTo(checkNotNull(parseSemVer("0.9.0"))))
        assertEquals(-1, checkNotNull(parseSemVer("1.0.0-alpha")).compareTo(checkNotNull(parseSemVer("1.0.0"))))
        assertEquals(1, checkNotNull(parseSemVer("1.0.0-alpha.2")).compareTo(checkNotNull(parseSemVer("1.0.0-alpha.1"))))
        assertNull(parseSemVer("v1.0"))
        assertNull(parseSemVer("v01.0.0"))
    }

    @Test
    fun `release selection requires one msi and checksum manifest`() {
        val release = GitHubRelease(
            tagName = "v0.2.0",
            htmlUrl = "https://github.com/ucwxcato/ArkSA-catos-admin-panel/releases/tag/v0.2.0",
            assets = listOf(
                GitHubAsset("CatosDuneAdmin-0.2.0.msi", "https://example.test/app.msi"),
                GitHubAsset("CatosDuneAdmin-0.2.0.exe", "https://example.test/app.exe"),
                GitHubAsset("SHA256SUMS.txt", "https://example.test/SHA256SUMS.txt"),
            ),
        )

        val selected = release.selectFor(UpdateConfig(), checkNotNull(parseSemVer(INSTALLED_VERSION)))
        assertNotNull(selected)
        assertEquals("CatosDuneAdmin-0.2.0.msi", selected.msi.name)
    }

    @Test
    fun `stable channel ignores prerelease and malformed assets`() {
        val release = GitHubRelease(
            tagName = "v0.2.0-alpha.1",
            prerelease = true,
            htmlUrl = "https://example.test/release",
            assets = listOf(GitHubAsset("CatosDuneAdmin-0.2.0-alpha.1.msi", "https://example.test/app.msi")),
        )
        assertNull(release.selectFor(UpdateConfig(), checkNotNull(parseSemVer(INSTALLED_VERSION))))
    }
}
