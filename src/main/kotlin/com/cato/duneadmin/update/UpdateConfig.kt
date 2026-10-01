package com.cato.duneadmin.update

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours

/** Trusted release location and policy. These values are intentionally not user-editable. */
data class UpdateConfig(
    val owner: String = "ucwxcato",
    val repository: String = "ArkSA-catos-admin-panel",
    val stableOnly: Boolean = true,
    val checkInterval: Duration = 24.hours,
    val msiAssetPattern: Regex = Regex("""^CatosDuneAdmin-[0-9A-Za-z.+-]+\.msi$"""),
) {
    val latestReleaseUrl: String
        get() = "https://api.github.com/repos/$owner/$repository/releases/latest"
}

const val INSTALLED_VERSION = "0.1.3"
