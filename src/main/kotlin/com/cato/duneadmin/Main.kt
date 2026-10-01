package com.cato.duneadmin

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.cato.duneadmin.connection.SshTunnelManager
import com.cato.duneadmin.connection.DuneAdminClient
import com.cato.duneadmin.model.MapState
import com.cato.duneadmin.model.MapSummary
import com.cato.duneadmin.model.HostMetric
import com.cato.duneadmin.model.ItemCatalogEntry
import com.cato.duneadmin.model.knownMapServices
import com.cato.duneadmin.model.mapPartitionsByService
import com.cato.duneadmin.model.OnlinePlayerEntry
import com.cato.duneadmin.model.TeleportLocation
import com.cato.duneadmin.platform.WindowsTitleBar
import com.cato.duneadmin.ui.CatosDuneTheme
import com.cato.duneadmin.ui.MochaColors
import com.cato.duneadmin.ui.SciFiMetricColors
import com.cato.duneadmin.update.ParsedRelease
import com.cato.duneadmin.update.UpdateResult
import com.cato.duneadmin.update.UpdateService
import java.awt.Color as AwtColor
import java.awt.Dimension
import java.util.prefs.Preferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

fun main() = application {
    Window(
        onCloseRequest = ::exitApplication,
        state = rememberWindowState(width = 1_280.dp, height = 900.dp),
        title = "Catos Dune Admin",
    ) {
        window.minimumSize = Dimension(1_080, 600)
        window.background = AwtColor(0x1E, 0x16, 0x14)
        WindowsTitleBar.apply(window)
        CatosDuneTheme { DashboardApp(onExit = ::exitApplication) }
    }
}

@Composable
private fun DashboardApp(onExit: () -> Unit) {
    var page by remember { mutableStateOf(AppPage.DASHBOARD) }
    var query by remember { mutableStateOf("") }
    var serverHost by remember { mutableStateOf(loadSavedServerHost()) }
    var password by remember { mutableStateOf("") }
    var connectionText by remember { mutableStateOf("Disconnected — the server adapter has not been added yet.") }
    var connectionState by remember { mutableStateOf(ConnectionState.DISCONNECTED) }
    var liveMaps by remember { mutableStateOf(emptyList<MapSummary>()) }
    var liveMetrics by remember { mutableStateOf(emptyList<HostMetric>()) }
    var selectedMapService by remember { mutableStateOf("") }
    var mapActionText by remember { mutableStateOf("") }
    var mapActionBusy by remember { mutableStateOf(false) }
    var adminUsername by remember { mutableStateOf("") }
    var adminPassword by remember { mutableStateOf("") }
    var adminAuthText by remember { mutableStateOf("") }
    var adminAuthBusy by remember { mutableStateOf(false) }
    var adminLoggedIn by remember { mutableStateOf(false) }
    var itemCatalog by remember { mutableStateOf(emptyList<ItemCatalogEntry>()) }
    var onlinePlayers by remember { mutableStateOf(emptyList<OnlinePlayerEntry>()) }
    var teleportSourceId by remember { mutableStateOf("") }
    var teleportTargetId by remember { mutableStateOf("") }
    var teleportDestination by remember { mutableStateOf("PLAYER") }
    var teleportLocation by remember { mutableStateOf("") }
    var teleportLocations by remember { mutableStateOf(emptyList<TeleportLocation>()) }
    var teleportConfirmation by remember { mutableStateOf("") }
    var teleportText by remember { mutableStateOf("") }
    var teleportPreviewReady by remember { mutableStateOf(false) }
    var teleportBusy by remember { mutableStateOf(false) }
    var grantPlayerId by remember { mutableStateOf("") }
    var grantTemplate by remember { mutableStateOf("") }
    var grantQuantity by remember { mutableStateOf("1") }
    var grantQuality by remember { mutableStateOf("0") }
    var grantConfirmation by remember { mutableStateOf("") }
    var grantText by remember { mutableStateOf("") }
    var grantBusy by remember { mutableStateOf(false) }
    var updateResult by remember { mutableStateOf<UpdateResult?>(null) }
    var updateBusy by remember { mutableStateOf(false) }
    var verifiedInstaller by remember { mutableStateOf<java.nio.file.Path?>(null) }
    val tunnel = remember { SshTunnelManager() }
    val admin = remember { DuneAdminClient() }
    val updater = remember { UpdateService() }
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) { onDispose { tunnel.stop() } }
    val connected = connectionState == ConnectionState.CONNECTED
    LaunchedEffect(Unit) {
        delay(2_000)
        updateResult = withContext(Dispatchers.IO) { updater.check() }
    }
    LaunchedEffect(connectionState) {
        if (!connected) {
            liveMaps = emptyList()
            liveMetrics = emptyList()
            return@LaunchedEffect
        }
        while (isActive && tunnel.isRunning()) {
            val result = withContext(Dispatchers.IO) { tunnel.readRunningDuneContainers() }
            val players = withContext(Dispatchers.IO) { tunnel.readPartitionPlayerCounts() }
                .getOrNull()
                ?.let(::parsePartitionPlayerCounts)
            result.onSuccess { output -> liveMaps = parseRunningMaps(output, players) }
            result.onFailure { error ->
                liveMaps = emptyList()
                liveMetrics = emptyList()
                connectionState = ConnectionState.DISCONNECTED
                connectionText = "Disconnected: live map status failed: ${error.message}"
                tunnel.stop()
            }
            withContext(Dispatchers.IO) { tunnel.readHostMetrics() }
                .onSuccess { output -> liveMetrics = parseHostMetrics(output) }
            delay(5_000)
        }
    }
    val maps = remember(query, connected, liveMaps) {
        if (!connected) emptyList() else liveMaps.filter { map ->
            map.state == MapState.RUNNING &&
                (query.isBlank() || map.serviceName.contains(query, true) || map.displayName.contains(query, true))
        }
    }

    Surface(Modifier.fillMaxSize(), color = MochaColors.Background) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Header(query, connected, page, { page = it }) { query = it }
            Spacer(Modifier.height(16.dp))
            ConnectionBanner(connectionState, connectionText, serverHost, {
                serverHost = it
                saveServerHost(it)
            }, password, { password = it }) {
                if (connectionState == ConnectionState.CONNECTED) {
                    tunnel.stop()
                    password = ""
                    connectionState = ConnectionState.DISCONNECTED
                    connectionText = "Disconnected"
                } else {
                    if (serverHost.isBlank()) {
                        connectionText = "Enter the SSH server host first."
                        return@ConnectionBanner
                    }
                    if (password.isBlank()) {
                        connectionText = "Enter the SSH password first."
                        return@ConnectionBanner
                    }
                    connectionState = ConnectionState.CONNECTING
                    connectionText = "Opening one SSH tunnel to Hetzner..."
                    val connectionHost = serverHost
                    val connectionPassword = password
                    password = ""
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { tunnel.start(connectionHost, connectionPassword) }
                        result.fold(
                            onSuccess = {
                                connectionState = ConnectionState.CONNECTED
                                connectionText = "Tunnel active at ${tunnel.localUrl()}"
                            },
                            onFailure = { error ->
                                connectionState = ConnectionState.DISCONNECTED
                                connectionText = error.message ?: "Unable to open the SSH tunnel"
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            when (page) {
                AppPage.DASHBOARD -> Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MetricsPanel(connected, liveMetrics, Modifier.weight(.85f).fillMaxHeight())
                    MapsPanel(connected, maps, Modifier.weight(1.45f).fillMaxHeight())
                }
                AppPage.MAPS -> Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    MapsPanel(connected, maps, Modifier.weight(1.45f).fillMaxHeight())
                    OperatorPanel(
                        connected = connected,
                        selectedService = selectedMapService,
                        onServiceChanged = { selectedMapService = it },
                        actionText = mapActionText,
                        actionBusy = mapActionBusy,
                        onMapAction = { start ->
                            if (selectedMapService !in knownMapServices || mapActionBusy) return@OperatorPanel
                            mapActionBusy = true
                            mapActionText = "${if (start) "Starting" else "Stopping"} $selectedMapService..."
                            scope.launch {
                                withContext(Dispatchers.IO) { tunnel.controlMap(selectedMapService, start) }
                                    .fold(
                                        onSuccess = { mapActionText = "Map command completed for $selectedMapService." },
                                        onFailure = { mapActionText = "Map command failed: ${it.message}" },
                                    )
                                mapActionBusy = false
                            }
                        },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
                AppPage.TELEPORT -> TeleportPage(
                    connected = connected,
                    players = onlinePlayers,
                    adminLoggedIn = adminLoggedIn,
                    sourceId = teleportSourceId,
                    onSourceChanged = { teleportSourceId = it },
                    destination = teleportDestination,
                    onDestinationChanged = { teleportDestination = it },
                    targetId = teleportTargetId,
                    onTargetChanged = { teleportTargetId = it },
                    location = teleportLocation,
                    onLocationChanged = { teleportLocation = it },
                    locations = teleportLocations,
                    confirmation = teleportConfirmation,
                    onConfirmationChanged = { teleportConfirmation = it },
                    resultText = teleportText,
                    onPreview = {
                        teleportPreviewReady = true
                        teleportText = "Preview ready. ${if (teleportDestination == "PLAYER") "The selected player will move to the destination player's current position." else "The selected player will move to the saved location '$teleportLocation'."}"
                    },
                    previewReady = teleportPreviewReady,
                    busy = teleportBusy,
                    onExecute = {
                        if (!connected || !adminLoggedIn || teleportBusy) return@TeleportPage
                        teleportBusy = true
                        teleportText = "Sending teleport command..."
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                if (teleportDestination == "PLAYER") {
                                    val target = teleportTargetId.toLongOrNull()
                                    if (target == null) Result.failure(IllegalArgumentException("Select a valid destination player."))
                                    else admin.teleportToPlayer(tunnel.localUrl(), teleportSourceId, target)
                                } else {
                                    admin.teleportToLocation(tunnel.localUrl(), teleportSourceId, teleportLocation)
                                }
                            }
                            result.fold(
                                onSuccess = { teleportText = "Teleport sent successfully."; teleportPreviewReady = false; teleportConfirmation = "" },
                                onFailure = { teleportText = it.message ?: "Teleport failed." },
                            )
                            teleportBusy = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                AppPage.ITEM_GRANTS -> ItemGrantsPage(
                    connected = connected,
                    items = itemCatalog,
                    players = onlinePlayers,
                    adminLoggedIn = adminLoggedIn,
                    authText = adminAuthText,
                    authBusy = adminAuthBusy,
                    username = adminUsername,
                    onUsernameChanged = { adminUsername = it },
                    password = adminPassword,
                    onPasswordChanged = { adminPassword = it },
                    onLogin = {
                        if (!connected || adminAuthBusy) return@ItemGrantsPage
                        adminAuthBusy = true
                        adminAuthText = "Signing in to Dune Admin..."
                        val username = adminUsername
                        val password = adminPassword
                        adminPassword = ""
                        scope.launch {
                            withContext(Dispatchers.IO) { admin.login(tunnel.localUrl(), username, password) }
                                .fold(
                                    onSuccess = {
                                        adminLoggedIn = true
                                        adminAuthText = "Dune Admin session active; loading players and item catalog..."
                                        scope.launch {
                                            val catalog = withContext(Dispatchers.IO) { admin.fetchItemCatalog(tunnel.localUrl()) }
                                            val players = withContext(Dispatchers.IO) { admin.fetchPlayers(tunnel.localUrl()) }
                                            val locations = withContext(Dispatchers.IO) { admin.fetchTeleportLocations(tunnel.localUrl()) }
                                            catalog.onSuccess { itemCatalog = decodeCatalog(it) }
                                            players.onSuccess {
                                                onlinePlayers = decodePlayers(it).filter { player ->
                                                    player.onlineStatus.equals("online", ignoreCase = true)
                                                }
                                            }
                                            locations.onSuccess { teleportLocations = decodeTeleportLocations(it) }
                                            adminAuthText = "Dune Admin session active: ${onlinePlayers.size} online players, ${itemCatalog.size} item templates, ${teleportLocations.size} teleport locations."
                                        }
                                    },
                                    onFailure = {
                                        admin.logout()
                                        adminLoggedIn = false
                                        adminAuthText = it.message ?: "Dune Admin login failed."
                                    },
                                )
                            adminAuthBusy = false
                        }
                    },
                    playerId = grantPlayerId,
                    onPlayerIdChanged = { grantPlayerId = it },
                    template = grantTemplate,
                    onTemplateChanged = { grantTemplate = it },
                    quantity = grantQuantity,
                    onQuantityChanged = { grantQuantity = it },
                    quality = grantQuality,
                    onQualityChanged = { grantQuality = it },
                    confirmation = grantConfirmation,
                    onConfirmationChanged = { grantConfirmation = it },
                    resultText = grantText,
                    busy = grantBusy,
                    onGrant = {
                        if (!connected || !adminLoggedIn || grantBusy) return@ItemGrantsPage
                        val player = grantPlayerId.toLongOrNull()
                        val qty = grantQuantity.toLongOrNull()
                        val quality = grantQuality.toLongOrNull()
                        if (player == null || qty == null || quality == null || grantTemplate.isBlank()) {
                            grantText = "Enter a valid player ID, template, quantity, and quality."
                            return@ItemGrantsPage
                        }
                        if (grantConfirmation != "GRANT") {
                            grantText = "Type GRANT exactly to confirm this item mutation."
                            return@ItemGrantsPage
                        }
                        grantBusy = true
                        grantText = "Sending one guarded item-grant request..."
                        val template = grantTemplate
                        grantConfirmation = ""
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                admin.giveItem(tunnel.localUrl(), player, template, qty, quality)
                            }.fold(
                                onSuccess = { grantText = "Grant completed: $it" },
                                onFailure = { grantText = "Grant failed: ${it.message}" },
                            )
                            grantBusy = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
                AppPage.OPERATIONS -> FeaturePage(
                    "OPERATIONS / AUDIT",
                    "One place for receipts and uncertain outcomes.",
                    "Operation polling, sanitized results, audit history, and recovery for interrupted actions will appear here as mutation workflows are added.",
                    Modifier.fillMaxWidth().weight(1f),
                )
            }
            Spacer(Modifier.height(12.dp))
            UpdateBanner(
                result = updateResult,
                busy = updateBusy,
                verifiedInstaller = verifiedInstaller,
                onCheck = {
                    if (updateBusy) return@UpdateBanner
                    updateBusy = true
                    scope.launch {
                        updateResult = withContext(Dispatchers.IO) { updater.check(force = true) }
                        updateBusy = false
                    }
                },
                onDownload = { release ->
                    if (updateBusy) return@UpdateBanner
                    updateBusy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { updater.downloadAndVerify(release) }
                        result.onSuccess { verifiedInstaller = it }
                        result.onFailure { updateResult = UpdateResult.Failed("Update download or verification failed.") }
                        updateBusy = false
                    }
                },
                onInstall = {
                    val installer = verifiedInstaller ?: return@UpdateBanner
                    scope.launch {
                        withContext(Dispatchers.IO) { updater.launchInstaller(installer) }
                            .onSuccess { onExit() }
                            .onFailure { updateResult = UpdateResult.Failed("Windows Installer could not be started.") }
                    }
                },
            )
            Spacer(Modifier.height(0.dp))
        }
    }
}

@Composable
private fun UpdateBanner(
    result: UpdateResult?,
    busy: Boolean,
    verifiedInstaller: java.nio.file.Path?,
    onCheck: () -> Unit,
    onDownload: (ParsedRelease) -> Unit,
    onInstall: () -> Unit,
) {
    val available = result as? UpdateResult.Available
    val message = when (result) {
        null -> "Checking for updates..."
        UpdateResult.NoUpdate -> "Catos Dune Admin is up to date."
        is UpdateResult.Failed -> result.message
        is UpdateResult.Available -> "Version ${result.release.version} is available."
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("PHASE 1 SHELL  ·  Live control tunnel not connected", color = MochaColors.TextSecondary, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(message, color = MochaColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        Button(onClick = onCheck, enabled = !busy) { Text("CHECK") }
        if (available != null && verifiedInstaller == null) {
            Button(onClick = { onDownload(available.release) }, enabled = !busy) { Text("DOWNLOAD MSI") }
        }
        if (verifiedInstaller != null) {
            Text("VERIFIED", color = MochaColors.Success, style = MaterialTheme.typography.bodySmall)
            Button(onClick = onInstall, enabled = !busy) { Text("INSTALL & RESTART") }
        }
    }
}

private enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

private val appPreferences: Preferences = Preferences.userRoot().node("dev.catosaurluna/CatosDuneAdmin")

private fun loadSavedServerHost(): String = runCatching {
    appPreferences.get("ssh_host", "")
}.getOrDefault("")

private fun saveServerHost(host: String) {
    runCatching {
        if (host.isBlank()) appPreferences.remove("ssh_host") else appPreferences.put("ssh_host", host.trim())
        appPreferences.flush()
    }
}

private enum class AppPage(val label: String) {
    DASHBOARD("DASHBOARD"),
    MAPS("MAPS"),
    TELEPORT("TELEPORT"),
    ITEM_GRANTS("ITEM GRANTS"),
    OPERATIONS("OPERATIONS"),
}

private fun parseRunningMaps(output: String, players: Map<Int, Int>?): List<MapSummary> = output.lineSequence()
    .mapNotNull { line ->
        val fields = line.split('\t')
        val service = fields.getOrNull(0)?.trim().orEmpty()
        if (service.isBlank() || service !in knownMapServices) return@mapNotNull null
        val display = service.replace('-', ' ').replaceFirstChar { it.uppercase() }
        val port = Regex("(\\d{4,5})->\\d{4,5}/udp").find(fields.getOrNull(3).orEmpty())?.groupValues?.get(1)?.toIntOrNull()
        val partitions = mapPartitionsByService[service].orEmpty()
        val playerCount = if (players == null) -1 else partitions.sumOf { players[it] ?: 0 }
        MapSummary(service, display, partitions, MapState.RUNNING, playerCount, port)
    }
    .distinctBy { it.serviceName }
    .sortedBy { it.displayName }
    .toList()

private fun parsePartitionPlayerCounts(output: String): Map<Int, Int> = output.lineSequence()
    .mapNotNull { line ->
        val fields = line.trim().split('|')
        val partition = fields.getOrNull(0)?.toIntOrNull()
        val players = fields.getOrNull(1)?.toIntOrNull()
        if (partition != null && players != null) partition to players else null
    }
    .toMap()

private val apiJson = Json { ignoreUnknownKeys = true }

private fun decodeCatalog(body: String): List<ItemCatalogEntry> =
    runCatching { apiJson.decodeFromString<List<ItemCatalogEntry>>(body) }.getOrDefault(emptyList())

private fun decodePlayers(body: String): List<OnlinePlayerEntry> =
    runCatching { apiJson.decodeFromString<List<OnlinePlayerEntry>>(body) }.getOrDefault(emptyList())

private fun decodeTeleportLocations(body: String): List<TeleportLocation> =
    runCatching { apiJson.decodeFromString<List<TeleportLocation>>(body) }.getOrDefault(emptyList())

private fun parseHostMetrics(output: String): List<HostMetric> {
    val values = output.lineSequence().mapNotNull { line ->
        line.split('\t', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1].trim() }
    }.toMap()
    val used = values["memory_used_mb"] ?: "—"
    val total = values["memory_total_mb"] ?: "—"
    return listOf(
        HostMetric("MEMORY", if (used != "—" && total != "—") "$used / $total MB" else "—", "Used / total"),
        HostMetric("CPU LOAD", values["cpu_load_1m"] ?: "—", "1-minute load average"),
        HostMetric("DISK", values["disk_used_pct"] ?: "—", "Root filesystem used"),
        HostMetric("UPTIME", values["uptime"] ?: "—", "Hetzner host"),
    )
}

@Composable
private fun Header(
    query: String,
    connected: Boolean,
    page: AppPage,
    onPageChanged: (AppPage) -> Unit,
    onQueryChanged: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f)) {
            Text("CATOS DUNE ADMIN", style = MaterialTheme.typography.headlineSmall)
            Text("Private operator dashboard for the Hetzner world", color = MochaColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            modifier = Modifier.width(320.dp),
            singleLine = true,
            enabled = connected,
            label = { Text("Search maps") },
            placeholder = { Text("Try: Arrakeen, overmap...") },
        )
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AppPage.entries.forEach { item ->
                Button(
                    onClick = { onPageChanged(item) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (item == page) MochaColors.Accent else MochaColors.SurfaceHighest,
                    ),
                ) { Text(item.label) }
            }
        }
    }
}

@Composable
private fun ConnectionBanner(
    state: ConnectionState,
    message: String,
    serverHost: String,
    onHostChanged: (String) -> Unit,
    password: String,
    onPasswordChanged: (String) -> Unit,
    onToggle: () -> Unit,
) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MochaColors.SurfaceElevated),
        border = BorderStroke(1.dp, MochaColors.Border),
        shape = RoundedCornerShape(10.dp),
    ) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("CONTROL TUNNEL", style = MaterialTheme.typography.labelLarge, color = MochaColors.Warning)
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MochaColors.TextSecondary)
            }
            OutlinedTextField(
                value = serverHost,
                onValueChange = onHostChanged,
                modifier = Modifier.width(210.dp),
                singleLine = true,
                enabled = state != ConnectionState.CONNECTED && state != ConnectionState.CONNECTING,
                label = { Text("Server IP / host") },
            )
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChanged,
                modifier = Modifier.width(230.dp),
                singleLine = true,
                enabled = state != ConnectionState.CONNECTED && state != ConnectionState.CONNECTING,
                label = { Text("SSH password") },
                visualTransformation = PasswordVisualTransformation(),
            )
            Button(
                onClick = onToggle,
                enabled = state != ConnectionState.CONNECTING,
                colors = ButtonDefaults.buttonColors(disabledContainerColor = MochaColors.SurfaceHighest),
            ) { Text(if (state == ConnectionState.CONNECTED) "DISCONNECT" else if (state == ConnectionState.CONNECTING) "CONNECTING" else "CONNECT") }
        }
    }
}

@Composable
private fun MetricsPanel(connected: Boolean, metrics: List<HostMetric>, modifier: Modifier) {
    Panel("HOST & DUNE", modifier) {
        if (!connected) {
            EmptyState("No server data", "Connect the control tunnel to load metrics.")
        } else {
            if (metrics.isEmpty()) {
                EmptyState("Loading host metrics", "Waiting for the first live sample.")
            } else metrics.forEach { metric ->
                Column(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
                    Text(metric.label, color = metricColor(metric.label), style = MaterialTheme.typography.labelMedium)
                    Text(metric.value, style = MaterialTheme.typography.titleLarge, color = if (metric.value == "OFFLINE") SciFiMetricColors.Offline else metricColor(metric.label))
                    Text(metric.detail, color = MochaColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

private fun metricColor(label: String) = when (label) {
    "MEMORY" -> SciFiMetricColors.Memory
    "CPU LOAD" -> SciFiMetricColors.Cpu
    "DISK" -> SciFiMetricColors.Disk
    "UPTIME" -> SciFiMetricColors.Uptime
    else -> MochaColors.TextPrimary
}

@Composable
private fun MapsPanel(connected: Boolean, maps: List<MapSummary>, modifier: Modifier) {
    Panel("MAPS", modifier) {
        if (!connected) {
            EmptyState("No map data", "Connect the control tunnel to view maps.")
        } else {
            Text("${maps.size} running maps", color = MochaColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(10.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(maps, key = { it.serviceName }) { map -> MapRow(map) }
            }
        }
    }
}

@Composable
private fun MapRow(map: MapSummary) {
    Card(colors = CardDefaults.cardColors(containerColor = MochaColors.SurfaceElevated), border = BorderStroke(1.dp, MochaColors.Border), shape = RoundedCornerShape(8.dp)) {
        Row(Modifier.fillMaxWidth().padding(11.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(map.displayName, style = MaterialTheme.typography.titleMedium)
                Text("${map.serviceName}  ·  partition ${map.partitionIds.joinToString()}", color = MochaColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
                Text(
                    if (map.players >= 0) "${map.players} players  ·  UDP ${map.port ?: "—"}"
                    else "Players unknown  ·  UDP ${map.port ?: "—"}",
                    color = MochaColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(map.state.label, color = if (map.state == MapState.RUNNING) SciFiMetricColors.Running else MochaColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun OperatorPanel(
    connected: Boolean,
    selectedService: String,
    onServiceChanged: (String) -> Unit,
    actionText: String,
    actionBusy: Boolean,
    onMapAction: (Boolean) -> Unit,
    modifier: Modifier,
) {
    var mapMenuExpanded by remember { mutableStateOf(false) }
    val mapOptions = knownMapServices
        .filter { selectedService.isBlank() || it.contains(selectedService, ignoreCase = true) }
        .sorted()
    Panel("OPERATOR ACTIONS", modifier) {
        Text(
            if (connected) "Named map controls use the resolved DASH Compose files."
            else "Connect first. No server actions are available while disconnected.",
            color = MochaColors.TextSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(12.dp))
        ExposedDropdownMenuBox(
            expanded = mapMenuExpanded && mapOptions.isNotEmpty(),
            onExpandedChange = { mapMenuExpanded = !mapMenuExpanded },
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = selectedService,
                onValueChange = { onServiceChanged(it); mapMenuExpanded = true },
                enabled = connected && !actionBusy,
                modifier = Modifier
                    .menuAnchor(
                        type = ExposedDropdownMenuAnchorType.PrimaryEditable,
                        enabled = connected && !actionBusy,
                    )
                    .fillMaxWidth(),
                singleLine = true,
                label = { Text("Map service") },
                placeholder = { Text("Choose or filter a map") },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = mapMenuExpanded) },
            )
            ExposedDropdownMenu(
                expanded = mapMenuExpanded && mapOptions.isNotEmpty(),
                onDismissRequest = { mapMenuExpanded = false },
            ) {
                mapOptions.forEach { service ->
                    androidx.compose.material3.DropdownMenuItem(
                        text = { Text(service) },
                        onClick = { onServiceChanged(service); mapMenuExpanded = false },
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onMapAction(true) }, enabled = connected && selectedService in knownMapServices && !actionBusy, modifier = Modifier.weight(1f)) { Text("START") }
            Button(onClick = { onMapAction(false) }, enabled = connected && selectedService in knownMapServices && !actionBusy, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = MochaColors.SurfaceHighest)) { Text("STOP") }
        }
        if (actionText.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(actionText, color = MochaColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ItemGrantsPage(
    connected: Boolean,
    items: List<ItemCatalogEntry>,
    players: List<OnlinePlayerEntry>,
    adminLoggedIn: Boolean,
    authText: String,
    authBusy: Boolean,
    username: String,
    onUsernameChanged: (String) -> Unit,
    password: String,
    onPasswordChanged: (String) -> Unit,
    onLogin: () -> Unit,
    playerId: String,
    onPlayerIdChanged: (String) -> Unit,
    template: String,
    onTemplateChanged: (String) -> Unit,
    quantity: String,
    onQuantityChanged: (String) -> Unit,
    quality: String,
    onQualityChanged: (String) -> Unit,
    confirmation: String,
    onConfirmationChanged: (String) -> Unit,
    resultText: String,
    busy: Boolean,
    onGrant: () -> Unit,
    modifier: Modifier,
) {
    var playerExpanded by remember { mutableStateOf(false) }
    var itemExpanded by remember { mutableStateOf(false) }
    val playerOptions = players.filter {
        playerId.isBlank() || it.name.contains(playerId, ignoreCase = true) || it.id.toString().contains(playerId)
    }
    val itemOptions = items.filter {
        template.isBlank() || it.templateId.contains(template, ignoreCase = true) || it.displayName.contains(template, ignoreCase = true)
    }
    Panel("ITEM GRANTS", modifier) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Verified Dune Admin grant path", style = MaterialTheme.typography.titleLarge)
            Text(
                "The server performs its normal inventory-capacity checks and chooses the supported online RMQ or offline database path. This client never retries an uncertain mutation.",
                color = MochaColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            Text("DUNE ADMIN SESSION", color = MochaColors.Warning, style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(username, onUsernameChanged, Modifier.weight(1f), singleLine = true, label = { Text("Username") }, enabled = connected && !adminLoggedIn && !authBusy)
                OutlinedTextField(password, onPasswordChanged, Modifier.weight(1f), singleLine = true, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), enabled = connected && !adminLoggedIn && !authBusy)
                Button(onClick = onLogin, enabled = connected && !adminLoggedIn && !authBusy) { Text(if (authBusy) "SIGNING IN" else "SIGN IN") }
            }
            Text(authText.ifBlank { if (connected) "Sign in before granting items." else "Connect the SSH tunnel first." }, color = MochaColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Text("GRANT PREVIEW", color = MochaColors.Warning, style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ExposedDropdownMenuBox(
                    expanded = playerExpanded && playerOptions.isNotEmpty(),
                    onExpandedChange = { playerExpanded = !playerExpanded },
                    modifier = Modifier.weight(1f),
                ) {
                    OutlinedTextField(
                        value = playerId,
                        onValueChange = { onPlayerIdChanged(it); playerExpanded = true },
                        modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = adminLoggedIn && !busy).fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Online player") },
                        placeholder = { Text("Search name or ID") },
                        enabled = adminLoggedIn && !busy,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = playerExpanded) },
                    )
                    ExposedDropdownMenu(expanded = playerExpanded && playerOptions.isNotEmpty(), onDismissRequest = { playerExpanded = false }) {
                        playerOptions.forEach { player ->
                            DropdownMenuItem(
                                text = { Text("${player.name} (#${player.id})") },
                                onClick = { onPlayerIdChanged(player.id.toString()); playerExpanded = false },
                            )
                        }
                    }
                }
                ExposedDropdownMenuBox(
                    expanded = itemExpanded && itemOptions.isNotEmpty(),
                    onExpandedChange = { itemExpanded = !itemExpanded },
                    modifier = Modifier.weight(1.5f),
                ) {
                    OutlinedTextField(
                        value = template,
                        onValueChange = { onTemplateChanged(it); itemExpanded = true },
                        modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = adminLoggedIn && !busy).fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Item template") },
                        placeholder = { Text("Search name or template ID") },
                        enabled = adminLoggedIn && !busy,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = itemExpanded) },
                    )
                    ExposedDropdownMenu(expanded = itemExpanded && itemOptions.isNotEmpty(), onDismissRequest = { itemExpanded = false }) {
                        itemOptions.forEach { item ->
                            DropdownMenuItem(
                                text = { Text("${item.displayName} (${item.templateId})") },
                                onClick = { onTemplateChanged(item.templateId); itemExpanded = false },
                            )
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(quantity, onQuantityChanged, Modifier.weight(1f), singleLine = true, label = { Text("Quantity") }, enabled = adminLoggedIn && !busy)
                OutlinedTextField(quality, onQualityChanged, Modifier.weight(1f), singleLine = true, label = { Text("Quality") }, enabled = adminLoggedIn && !busy)
                OutlinedTextField(confirmation, onConfirmationChanged, Modifier.weight(1.5f), singleLine = true, label = { Text("Type GRANT") }, enabled = adminLoggedIn && !busy)
            }
            Button(onClick = onGrant, enabled = adminLoggedIn && !busy && connected, modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "GRANTING..." else "GRANT ITEM")
            }
            if (resultText.isNotBlank()) Text(resultText, color = MochaColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TeleportPage(
    connected: Boolean,
    players: List<OnlinePlayerEntry>,
    adminLoggedIn: Boolean,
    sourceId: String,
    onSourceChanged: (String) -> Unit,
    destination: String,
    onDestinationChanged: (String) -> Unit,
    targetId: String,
    onTargetChanged: (String) -> Unit,
    location: String,
    onLocationChanged: (String) -> Unit,
    locations: List<TeleportLocation>,
    confirmation: String,
    onConfirmationChanged: (String) -> Unit,
    resultText: String,
    onPreview: () -> Unit,
    previewReady: Boolean,
    busy: Boolean,
    onExecute: () -> Unit,
    modifier: Modifier,
) {
    var sourceExpanded by remember { mutableStateOf(false) }
    var targetExpanded by remember { mutableStateOf(false) }
    var locationExpanded by remember { mutableStateOf(false) }
    val sourceOptions = players.filter {
        sourceId.isBlank() || it.name.contains(sourceId, true) || it.id.toString().contains(sourceId) || it.flsId.contains(sourceId, true)
    }
    val sourcePlayer = players.firstOrNull { it.flsId == sourceId }
    val sourceMap = sourcePlayer?.map.orEmpty()
    val targetOptions = players.filter {
        it.flsId != sourceId &&
            sameMap(sourceMap, it.map) &&
            (targetId.isBlank() || it.name.contains(targetId, true) || it.id.toString().contains(targetId))
    }
    val locationOptions = locations.filter { isHaggaBasin(it.map) }.map { it.name }
    val selectionComplete = sourcePlayer != null &&
        if (destination == "PLAYER") {
            targetId.toLongOrNull()?.let { targetIdValue ->
                targetOptions.any { it.id == targetIdValue }
            } == true
        } else {
            isHaggaBasin(sourceMap) && location in locationOptions
        }

    Panel("TELEPORT", modifier) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Move a player safely", style = MaterialTheme.typography.titleLarge)
            Text(
                "Select the player to move, then choose another online player or a named Hagga Basin location. Teleports will require a preview and explicit confirmation.",
                color = MochaColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
            Text("SOURCE PLAYER", color = MochaColors.Warning, style = MaterialTheme.typography.labelLarge)
            ExposedDropdownMenuBox(
                expanded = sourceExpanded && sourceOptions.isNotEmpty(),
                onExpandedChange = { sourceExpanded = !sourceExpanded },
            ) {
                OutlinedTextField(
                    value = sourceId,
                    onValueChange = { onSourceChanged(it); sourceExpanded = true },
                    modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryEditable).fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Player to move") },
                    placeholder = { Text("Search online player by name or ID") },
                    enabled = connected && adminLoggedIn,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sourceExpanded) },
                )
                ExposedDropdownMenu(expanded = sourceExpanded && sourceOptions.isNotEmpty(), onDismissRequest = { sourceExpanded = false }) {
                    sourceOptions.forEach { player ->
                        DropdownMenuItem(
                            text = { Text("${player.name} (#${player.id})") },
                            onClick = { onSourceChanged(player.flsId); sourceExpanded = false },
                        )
                    }
                }
            }
            Text("DESTINATION", color = MochaColors.Warning, style = MaterialTheme.typography.labelLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onDestinationChanged("PLAYER") },
                    enabled = connected && adminLoggedIn,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (destination == "PLAYER") MochaColors.Accent else MochaColors.SurfaceHighest,
                    ),
                    modifier = Modifier.weight(1f),
                ) { Text("TO PLAYER") }
                Button(
                    onClick = { onDestinationChanged("LOCATION") },
                    enabled = connected && adminLoggedIn,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (destination == "LOCATION") MochaColors.Accent else MochaColors.SurfaceHighest,
                    ),
                    modifier = Modifier.weight(1f),
                ) { Text("TO LOCATION") }
            }
            if (destination == "PLAYER") {
                Text(
                    if (sourceMap.isBlank()) {
                        "Select a player with a known map before choosing a destination."
                    } else {
                        "Only players on the same map (${friendlyMapName(sourceMap)}) are available. Cross-map teleports are blocked."
                    },
                    color = MochaColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
                ExposedDropdownMenuBox(
                    expanded = targetExpanded && targetOptions.isNotEmpty(),
                    onExpandedChange = { targetExpanded = !targetExpanded },
                ) {
                    OutlinedTextField(
                        value = targetId,
                        onValueChange = { onTargetChanged(it); targetExpanded = true },
                        modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryEditable).fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Destination player") },
                        placeholder = { Text("Search another online player") },
                        enabled = connected && adminLoggedIn,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = targetExpanded) },
                    )
                    ExposedDropdownMenu(expanded = targetExpanded && targetOptions.isNotEmpty(), onDismissRequest = { targetExpanded = false }) {
                        targetOptions.forEach { player ->
                            DropdownMenuItem(
                                text = { Text("${player.name} (#${player.id})") },
                            onClick = { onTargetChanged(player.id.toString()); targetExpanded = false },
                            )
                        }
                    }
                }
            } else {
                ExposedDropdownMenuBox(
                    expanded = locationExpanded,
                    onExpandedChange = { locationExpanded = !locationExpanded },
                ) {
                    OutlinedTextField(
                        value = location,
                        onValueChange = onLocationChanged,
                        modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryEditable).fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Named location") },
                        placeholder = { Text("Choose a configured destination") },
                        enabled = connected && adminLoggedIn,
                    )
                    ExposedDropdownMenu(expanded = locationExpanded && locationOptions.isNotEmpty(), onDismissRequest = { locationExpanded = false }) {
                        locationOptions.forEach { option ->
                            DropdownMenuItem(text = { Text(option) }, onClick = { onLocationChanged(option); locationExpanded = false })
                        }
                    }
                }
                Text(
                    if (locationOptions.isEmpty()) {
                        "No verified Hagga Basin location presets are available. Raw/internal presets are hidden until the server labels them with map metadata."
                    } else {
                        "Only server presets explicitly marked Hagga Basin are shown."
                    },
                    color = MochaColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedTextField(
                value = confirmation,
                onValueChange = onConfirmationChanged,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Type TELEPORT to confirm") },
                enabled = connected && adminLoggedIn,
            )
            if (!previewReady) {
                Button(
                    onClick = onPreview,
                    enabled = connected && adminLoggedIn && selectionComplete && confirmation == "TELEPORT" && !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("PREVIEW TELEPORT") }
            } else {
                Button(
                    onClick = onExecute,
                    enabled = connected && adminLoggedIn && selectionComplete && confirmation == "TELEPORT" && !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (busy) "TELEPORTING..." else "EXECUTE TELEPORT") }
            }
            Text(
                resultText.ifBlank {
                    when {
                        !connected -> "Connect the SSH tunnel first."
                        !adminLoggedIn -> "Sign in to Dune Admin first."
                        else -> "Choose a source and destination, then type TELEPORT."
                    }
                },
                color = MochaColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun sameMap(sourceMap: String?, destinationMap: String): Boolean =
    !sourceMap.isNullOrBlank() && destinationMap.isNotBlank() &&
        sourceMap.trim().equals(destinationMap.trim(), ignoreCase = true)

private fun isHaggaBasin(map: String): Boolean =
    map.trim().equals("HaggaBasin", ignoreCase = true) ||
        map.trim().equals("Hagga Basin", ignoreCase = true)

private fun friendlyMapName(map: String): String =
    if (isHaggaBasin(map)) "Hagga Basin" else map

@Composable
private fun FeaturePage(title: String, subtitle: String, detail: String, modifier: Modifier) {
    Panel(title, modifier) {
        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(subtitle, style = MaterialTheme.typography.titleLarge)
            Text(detail, color = MochaColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
            Text("NOT ENABLED", color = MochaColors.Warning, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun EmptyState(title: String, detail: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text(detail, color = MochaColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun Panel(title: String, modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = MochaColors.Surface), border = BorderStroke(1.dp, MochaColors.Border), shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.fillMaxSize().padding(14.dp), content = content)
    }
}
