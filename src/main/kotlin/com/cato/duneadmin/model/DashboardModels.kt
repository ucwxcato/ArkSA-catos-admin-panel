package com.cato.duneadmin.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ItemCatalogEntry(
    @SerialName("template_id") val templateId: String,
    @SerialName("display_name") val displayName: String,
)

@Serializable
data class OnlinePlayerEntry(
    val id: Long,
    val name: String,
    @SerialName("fls_id") val flsId: String = "",
    val map: String = "",
    @SerialName("online_status") val onlineStatus: String,
)

@Serializable
data class TeleportLocation(
    val name: String,
    val x: Double,
    val y: Double,
    val z: Double,
    /** Optional metadata supported by newer dune-admin deployments. */
    val map: String = "",
)

data class MapSummary(
    val serviceName: String,
    val displayName: String,
    val partitionIds: List<Int>,
    val state: MapState,
    val players: Int,
    val port: Int?,
)

enum class MapState(val label: String) {
    RUNNING("RUNNING"),
    STOPPED("STOPPED"),
    UNKNOWN("UNKNOWN"),
}

data class HostMetric(val label: String, val value: String, val detail: String)

/** Compose services that represent playable Dune map instances. */
val knownMapServices = setOf(
    "arrakeen",
    "art-of-kanly",
    "bandit-fortress",
    "deep-desert",
    "deep-desert-pvp",
    "dungeon-hephaestus",
    "dungeon-oldcarthag",
    "dungeon-thepit",
    "ecolab-green-024",
    "ecolab-green-089",
    "ecolab-green-136",
    "ecolab-green-152",
    "ecolab-green-195",
    "faction-outpost-atre",
    "faction-outpost-hark",
    "harko-village",
    "heighliner-dungeon",
    "lostharvest-ecolab-a",
    "lostharvest-ecolab-b",
    "lostharvest-forgottenlab",
    "overland-m-01",
    "overland-s-04",
    "overland-s-06",
    "overland-s-07",
    "overland-s-08",
    "overmap",
    "proces-verbal",
    "survival",
    "sietch-talab",
    "story-paranoid-prayerroom",
    "story-glutton-diningroom",
    "story-destroyed-zanovar",
    "story-orbital-monitor",
    "testing-carthag",
    "testing-hephaestus",
    "testing-waterfat",
)

/** Stable service-to-partition mapping used for occupancy enrichment. */
val mapPartitionsByService = mapOf(
    "survival" to listOf(1),
    "overmap" to listOf(2),
    "arrakeen" to listOf(3),
    "harko-village" to listOf(4),
    "testing-hephaestus" to listOf(5),
    "testing-carthag" to listOf(6),
    "testing-waterfat" to listOf(7),
    "deep-desert" to listOf(8),
    "proces-verbal" to listOf(9),
    "lostharvest-ecolab-a" to listOf(10),
    "lostharvest-ecolab-b" to listOf(11),
    "lostharvest-forgottenlab" to listOf(12),
    "art-of-kanly" to listOf(13),
    "dungeon-hephaestus" to listOf(14),
    "dungeon-oldcarthag" to listOf(15),
    "faction-outpost-atre" to listOf(16),
    "faction-outpost-hark" to listOf(17),
    "heighliner-dungeon" to listOf(18),
    "ecolab-green-089" to listOf(19),
    "ecolab-green-152" to listOf(20),
    "ecolab-green-024" to listOf(21),
    "ecolab-green-195" to listOf(22),
    "ecolab-green-136" to listOf(23),
    "overland-m-01" to listOf(24),
    "overland-s-04" to listOf(25),
    "overland-s-06" to listOf(26),
    "bandit-fortress" to listOf(27),
    "overland-s-07" to listOf(28),
    "overland-s-08" to listOf(29),
    "dungeon-thepit" to listOf(30),
    "deep-desert-pvp" to listOf(31),
    "sietch-talab" to listOf(32),
    "story-paranoid-prayerroom" to listOf(33),
    "story-glutton-diningroom" to listOf(34),
    "story-destroyed-zanovar" to listOf(35),
    "story-orbital-monitor" to listOf(36),
)

val previewMaps = listOf(
    MapSummary("survival", "Survival", listOf(1), MapState.RUNNING, 0, 7777),
    MapSummary("overmap", "Overmap", listOf(2), MapState.RUNNING, 0, 7778),
    MapSummary("arrakeen", "Arrakeen", listOf(3), MapState.STOPPED, 0, 7779),
    MapSummary("harko-village", "Harko Village", listOf(4), MapState.STOPPED, 0, 7780),
    MapSummary("deep-desert", "Deep Desert", listOf(5, 6), MapState.STOPPED, 0, 7784),
)

val previewMetrics = listOf(
    HostMetric("MEMORY", "—", "Connect the private control tunnel"),
    HostMetric("CPU LOAD", "—", "No live sample yet"),
    HostMetric("DISK", "—", "No live sample yet"),
    HostMetric("DUNE", "OFFLINE", "Read-only shell preview"),
)
