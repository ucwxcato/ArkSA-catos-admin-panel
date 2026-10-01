# Catos Dune Admin

**Catos Dune Admin** is a private Windows desktop panel for operating the Dune Awakening deployment hosted on Hetzner.

It provides a focused operator interface for viewing server and map status, connecting to the private Dune Admin service, reviewing online players, browsing the item catalog, and using approved administration workflows.

> This is an early-release operations tool. Use it only with an authorized server account and test administrative actions carefully before using them on live players.

## What it does

- Connects to the private server through an authenticated SSH tunnel.
- Displays host, map, service, partition, and player-count information.
- Loads online players from Dune Admin.
- Loads the Dune item catalog.
- Provides guarded item-grant and teleport workflows where the backend contract is enabled.
- Keeps sensitive server services and credentials behind the server-side control path.

The panel is an operator client, not the Dune control plane. Docker, PostgreSQL, RabbitMQ, root-level operations, and other privileged actions remain server-side.

## Requirements

- Windows 10 or later.
- Network access to the Hetzner host over SSH.
- An authorized SSH account and password or key-based login, depending on the deployment configuration.
- A Dune Admin account for player and catalog operations.

## Installation

Download the latest Windows installer from the project's [Releases](../../releases) page.

The MSI installer is the recommended option. Run it, follow the installation prompts, and launch **Catos Dune Admin** from the Start menu or desktop shortcut.

For development builds, the project can also be built locally with the Gradle wrapper; see [Development](#development).

## Connecting

1. Launch the application.
2. Enter the SSH host, SSH username, and SSH password when prompted.
3. Connect to the server and wait for the tunnel and dashboard data to become available.
4. Sign in to Dune Admin when using player, catalog, item-grant, or teleport features.

The panel clears the SSH password after the connection attempt. Passwords, cookies, private keys, and other credentials should never be saved in the repository, screenshots, logs, or issue reports.

## Main areas

### Dashboard

Shows host health, map services, partition occupancy, and online-player counts while the SSH tunnel is connected. Dashboard data refreshes periodically.

### Maps

Shows the approved map services and allows permitted start or graceful-stop operations. Only explicitly allowlisted services can be controlled from the panel.

### Teleport

Provides a preview-first workflow for moving an online player to another online player or to a reviewed server-side location preset. Actions require explicit confirmation and depend on the deployed backend contract being enabled.

### Item Grants

Loads the server-side item catalog and provides a guarded workflow for selecting a player, item, quantity, and quality before confirmation.

### Operations and audit

Displays operation results and status messages returned by the control service. Keep the existing browser-based Dune Admin panel available as a fallback during this early release.

## Configuration

The default connection values are:

| Setting | Default | Environment variable |
| --- | --- | --- |
| SSH host | `95.216.71.232` | `CATOS_DUNE_SSH_HOST` |
| SSH user | `root` | `CATOS_DUNE_SSH_USER` |
| Local forwarded port | `18181` | `CATOS_DUNE_LOCAL_PORT` |
| Remote loopback port | `18081` | `CATOS_DUNE_REMOTE_PORT` |

The SSH tunnel forwards the local port to the server-side Dune Admin service. Do not expose the forwarded port or the Dune Admin service publicly.

## Safety notes

- Confirm the target host before operating on production services.
- Treat player names, IDs, inventory data, locations, and operation results as sensitive information.
- Do not share screenshots or diagnostic files containing credentials, cookies, private coordinates, or player identities.
- Mutating operations should remain preview-first and require explicit confirmation.
- Do not use the panel to run unrestricted Docker commands or direct database and message-queue operations.
- If data is stale, missing, or uncertain, stop and verify the server state before retrying an action.
- Do not automatically retry uncertain item grants or teleports.

## Current limitations

This is an alpha release. Some workflows are intentionally restricted while the server-side control adapter and safety checks are completed.

- Teleport execution may be disabled until the live backend contract is verified.
- Full operation receipts, audit history, preview fingerprints, and stale-data guards are still being completed.
- SSH key or agent-based authentication is preferred for a future hardening pass.
- The default host points to the main Hetzner server; always confirm the target before connecting.

## Development

The project uses Kotlin/JVM and Compose Desktop. The pinned toolchain is defined in `build.gradle.kts`.

From the project directory, use the checked-in Gradle wrapper:

```powershell
.\gradlew.bat compileKotlin
.\gradlew.bat test
.\gradlew.bat packageDistributionForCurrentOS
```

Generated files under `build/`, local Gradle caches, credentials, SSH keys, and sensitive diagnostics must not be committed.

More detailed architecture and contributor guidance is available in [AGENTS.md](AGENTS.md). The project plan and authority boundaries are documented in [DEVPLAN.md](DEVPLAN.md).

## Releases

Releases are currently published manually. A release should include the Windows MSI/EXE installers, a short summary of user-visible changes, and a `SHA256SUMS.txt` manifest. The canonical repository is `ucwxcato/ArkSA-catos-admin-panel`.

Recommended tag format:

```text
v0.1.0
```

Automatic update checks are not enabled yet. Users should install new versions manually from the project's [Releases](../../releases) page. The planned update flow will use the MSI and verify its SHA-256 digest before installation.
