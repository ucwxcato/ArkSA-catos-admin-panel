package com.cato.duneadmin.connection

import com.cato.duneadmin.model.knownMapServices
import org.apache.sshd.client.SshClient
import org.apache.sshd.client.channel.ClientChannelEvent
import org.apache.sshd.client.session.ClientSession
import org.apache.sshd.common.util.net.SshdSocketAddress
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.util.EnumSet
import java.util.concurrent.TimeUnit

data class TunnelConfig(
    val user: String = System.getenv("CATOS_DUNE_SSH_USER") ?: "root",
    val host: String = System.getenv("CATOS_DUNE_SSH_HOST") ?: "95.216.71.232",
    val localPort: Int = (System.getenv("CATOS_DUNE_LOCAL_PORT") ?: "18181").toIntOrNull() ?: 18181,
    val remotePort: Int = (System.getenv("CATOS_DUNE_REMOTE_PORT") ?: "18081").toIntOrNull() ?: 18081,
)

class SshTunnelManager(private val config: TunnelConfig = TunnelConfig()) {
    private var client: SshClient? = null
    private var session: ClientSession? = null
    private var forwarding: SshdSocketAddress? = null

    @Synchronized
    fun start(host: String, password: String): Result<Unit> {
        if (session?.isOpen == true && forwarding != null) return Result.success(Unit)
        stop()

        return runCatching {
            require(host.isNotBlank()) { "Enter the server IP or hostname." }
            require(password.isNotEmpty()) { "Enter the SSH password." }
            checkTcpReachable(host.trim())
            checkLocalPortAvailable()
            val newClient = SshClient.setUpDefaultClient()
            var newSession: ClientSession? = null
            try {
                newClient.start()
                newSession = runCatching {
                    newClient.connect(config.user, host.trim(), 22)
                        .verify(15, TimeUnit.SECONDS)
                        .session
                }.getOrElse { throw sshFailure("SSH connection", it) }
                newSession.addPasswordIdentity(password)
                runCatching { newSession.auth().verify(15, TimeUnit.SECONDS) }
                    .getOrElse { throw sshFailure("SSH authentication", it) }
                val newForwarding = runCatching {
                    newSession.startLocalPortForwarding(
                        SshdSocketAddress("127.0.0.1", config.localPort),
                        SshdSocketAddress("127.0.0.1", config.remotePort),
                    )
                }.getOrElse { throw sshFailure("SSH port forwarding", it) }
                client = newClient
                session = newSession
                forwarding = newForwarding
            } catch (error: Throwable) {
                runCatching { newSession?.close() }
                runCatching { newClient.stop() }
                throw error
            }
        }
    }

    @Synchronized
    fun stop() {
        runCatching { forwarding?.let { session?.stopLocalPortForwarding(it) } }
        runCatching { session?.close() }
        runCatching { client?.stop() }
        forwarding = null
        session = null
        client = null
    }

    fun isRunning(): Boolean = session?.isOpen == true && forwarding != null

    fun localUrl(): String = "http://127.0.0.1:${config.localPort}"

    /** Fixed read-only status command; user input is never interpolated. */
    @Synchronized
    fun readRunningDuneContainers(): Result<String> {
        val activeSession = session ?: return Result.failure(IllegalStateException("SSH tunnel is not connected."))
        return runCatching {
            val stdout = ByteArrayOutputStream()
            val channel = activeSession.createExecChannel(
                "docker ps --filter label=com.docker.compose.project=dune_server --format '{{.Label \"com.docker.compose.service\"}}\\t{{.Names}}\\t{{.State}}\\t{{.Ports}}'",
            )
            channel.out = stdout
            channel.err = stdout
            channel.open().verify(10, TimeUnit.SECONDS)
            channel.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), 10_000)
            check(channel.exitStatus == 0) { stdout.toString(StandardCharsets.UTF_8).trim().ifBlank { "Docker status command failed." } }
            stdout.toString(StandardCharsets.UTF_8)
        }
    }

    /** Read-only host metrics in a small key/value format. */
    @Synchronized
    fun readHostMetrics(): Result<String> {
        val activeSession = session ?: return Result.failure(IllegalStateException("SSH tunnel is not connected."))
        return runCatching {
            val stdout = ByteArrayOutputStream()
            val channel = activeSession.createExecChannel(
                "printf 'memory_used_mb\\t%s\\n' \"$(free -m | awk '/^Mem:/ {print ${'$'}3}')\"; printf 'memory_total_mb\\t%s\\n' \"$(free -m | awk '/^Mem:/ {print ${'$'}2}')\"; printf 'cpu_load_1m\\t%s\\n' \"$(awk '{print ${'$'}1}' /proc/loadavg)\"; printf 'disk_used_pct\\t%s\\n' \"$(df -P / | awk 'NR==2 {print ${'$'}5}')\"; printf 'uptime\\t%s\\n' \"$(uptime -p)\"",
            )
            channel.out = stdout
            channel.err = stdout
            channel.open().verify(10, TimeUnit.SECONDS)
            channel.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), 10_000)
            check(channel.exitStatus == 0) { stdout.toString(StandardCharsets.UTF_8).trim().ifBlank { "Host metrics command failed." } }
            stdout.toString(StandardCharsets.UTF_8)
        }
    }

    /** Read-only online-player counts grouped by the stable world partition ID. */
    @Synchronized
    fun readPartitionPlayerCounts(): Result<String> {
        val activeSession = session ?: return Result.failure(IllegalStateException("SSH tunnel is not connected."))
        return runCatching {
            val stdout = ByteArrayOutputStream()
            val channel = activeSession.createExecChannel(
                "docker exec dune_server-postgres-1 psql -U dune -d dune_sb_1_5_3_0 -Atc " +
                    "\"WITH online AS (SELECT (server_info).partition_id AS partition_id " +
                    "FROM dune.get_all_online_or_recently_disconnected_player_online_state()) " +
                    "SELECT partition_id, count(*)::int FROM online WHERE partition_id IS NOT NULL " +
                    "GROUP BY partition_id ORDER BY partition_id;\"",
            )
            channel.out = stdout
            channel.err = stdout
            channel.open().verify(10, TimeUnit.SECONDS)
            channel.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), 10_000)
            check(channel.exitStatus == 0) { stdout.toString(StandardCharsets.UTF_8).trim().ifBlank { "Player-count query failed." } }
            stdout.toString(StandardCharsets.UTF_8)
        }
    }

    /** Start or gracefully stop one approved map service only. */
    @Synchronized
    fun controlMap(service: String, start: Boolean): Result<String> {
        require(service in knownMapServices) { "Unknown or non-map service." }
        val action = if (start) "up -d --no-deps" else "stop"
        val script = """
            cd /opt/dash/current &&
            IFS=: read -ra compose_file_list <<< "$(./scripts/compose-files.sh /var/lib/dash/.env)" &&
            compose_args=() &&
            for file in "${'$'}{compose_file_list[@]}"; do compose_args+=(--file "${'$'}file"); done &&
            docker compose -p dune_server --env-file /var/lib/dash/.env "${'$'}{compose_args[@]}" $action $service &&
            docker compose -p dune_server --env-file /var/lib/dash/.env "${'$'}{compose_args[@]}" ps $service
        """.trimIndent()
        return execCommand("bash -lc ${shellQuote(script)}")
    }

    @Synchronized
    private fun execCommand(command: String): Result<String> {
        val activeSession = session ?: return Result.failure(IllegalStateException("SSH tunnel is not connected."))
        return runCatching {
            val stdout = ByteArrayOutputStream()
            val channel = activeSession.createExecChannel(command)
            channel.out = stdout
            channel.err = stdout
            channel.open().verify(10, TimeUnit.SECONDS)
            channel.waitFor(EnumSet.of(ClientChannelEvent.CLOSED), 10_000)
            check(channel.exitStatus == 0) { stdout.toString(StandardCharsets.UTF_8).trim().ifBlank { "Remote map command failed." } }
            stdout.toString(StandardCharsets.UTF_8)
        }
    }

    private fun shellQuote(value: String): String = "'${value.replace("'", "'\\''")}'"

    private fun checkTcpReachable(host: String) {
        runCatching {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, 22), 5_000)
            }
        }.getOrElse { error ->
            throw IllegalStateException("Cannot reach SSH host $host:22: ${error.message ?: "connection refused or timed out"}")
        }
    }

    private fun checkLocalPortAvailable() {
        runCatching {
            ServerSocket().use { socket ->
                socket.reuseAddress = false
                socket.bind(InetSocketAddress("127.0.0.1", config.localPort))
            }
        }.getOrElse { error ->
            throw IllegalStateException("Local tunnel port ${config.localPort} is unavailable: ${error.message ?: "it may already be in use"}")
        }
    }

    private fun sshFailure(stage: String, error: Throwable): IllegalStateException {
        val cause = generateSequence(error) { it.cause }.last()
        val detail = cause.message?.takeIf { it.isNotBlank() } ?: cause::class.simpleName.orEmpty()
        return IllegalStateException("$stage failed: $detail", error)
    }
}
