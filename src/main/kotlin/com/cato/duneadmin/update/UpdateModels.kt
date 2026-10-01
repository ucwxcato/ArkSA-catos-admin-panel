package com.cato.duneadmin.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubRelease(
    @SerialName("tag_name")
    val tagName: String,
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("published_at") val publishedAt: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GitHubAsset> = emptyList(),
)

@Serializable
data class GitHubAsset(
    val name: String,
    @SerialName("browser_download_url") val browserDownloadUrl: String,
    val size: Long = 0,
)

data class ParsedRelease(
    val release: GitHubRelease,
    val version: SemVer,
    val msi: GitHubAsset,
    val checksumManifest: GitHubAsset,
)

data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val prerelease: List<Identifier> = emptyList(),
) : Comparable<SemVer> {
    sealed interface Identifier {
        data class Numeric(val value: Int) : Identifier
        data class Text(val value: String) : Identifier
    }

    override fun compareTo(other: SemVer): Int {
        compareValuesBy(this, other, SemVer::major, SemVer::minor, SemVer::patch).let {
            if (it != 0) return it
        }
        if (prerelease.isEmpty() && other.prerelease.isEmpty()) return 0
        if (prerelease.isEmpty()) return 1
        if (other.prerelease.isEmpty()) return -1
        for ((left, right) in prerelease.zip(other.prerelease)) {
            val result = when {
                left is Identifier.Numeric && right is Identifier.Numeric -> left.value.compareTo(right.value)
                left is Identifier.Numeric -> -1
                right is Identifier.Numeric -> 1
                else -> (left as Identifier.Text).value.compareTo((right as Identifier.Text).value)
            }
            if (result != 0) return result
        }
        return prerelease.size.compareTo(other.prerelease.size)
    }

    override fun toString(): String = buildString {
        append("$major.$minor.$patch")
        if (prerelease.isNotEmpty()) {
            append('-')
            append(prerelease.joinToString(".") { identifier ->
                when (identifier) {
                    is Identifier.Numeric -> identifier.value.toString()
                    is Identifier.Text -> identifier.value
                }
            })
        }
    }
}

fun parseSemVer(tag: String): SemVer? {
    val value = tag.removePrefix("v")
    val match = Regex("""^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?$""").matchEntire(value)
        ?: return null
    fun number(index: Int): Int? {
        return match.groupValues[index].toIntOrNull()
    }
    val major = number(1) ?: return null
    val minor = number(2) ?: return null
    val patch = number(3) ?: return null
    val identifiers = match.groupValues[4].takeIf(String::isNotEmpty)?.split('.')?.map { part ->
        if (part.all(Char::isDigit)) {
            if (part.length > 1 && part.startsWith('0')) return null
            SemVer.Identifier.Numeric(part.toIntOrNull() ?: return null)
        } else {
            SemVer.Identifier.Text(part)
        }
    }.orEmpty()
    return SemVer(major, minor, patch, identifiers)
}

fun GitHubRelease.selectFor(config: UpdateConfig, installed: SemVer): ParsedRelease? {
    if (draft || (config.stableOnly && prerelease)) return null
    val version = parseSemVer(tagName) ?: return null
    if (version <= installed) return null
    val msiAssets = assets.filter { config.msiAssetPattern.matches(it.name) }
    if (msiAssets.size != 1) return null
    val checksums = assets.filter { it.name == "SHA256SUMS.txt" }
    if (checksums.size != 1) return null
    return ParsedRelease(this, version, msiAssets.single(), checksums.single())
}
