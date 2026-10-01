# Catos Dune Admin — Agent and Developer Notes

This directory contains the private Windows desktop administration client for the Dune Awakening deployment hosted on Hetzner. The application is a Kotlin/JVM Compose Desktop client; it is not the Dune control plane and must not become a second source of truth.

The authoritative planning and safety document is [DEVPLAN.md](DEVPLAN.md). Read it before adding a new admin mutation. The surrounding repository contains host runbooks and deployment notes, but this file is the quick technical reference for work inside this project.

## Project purpose

The panel provides one operator-facing application for:

- opening an authenticated SSH tunnel to the private Dune host;
- reading host metrics, running Compose map services, and partition occupancy;
- starting or gracefully stopping one approved Dune map service;
- authenticating to Dune Admin and loading online players and the item catalog;
- previewing guarded item-grant and teleport workflows.

The client must not directly expose Docker, PostgreSQL, RabbitMQ, root credentials, or a broad privileged API to the Windows machine. Mutations must use a verified Dune Admin or narrow server-side adapter contract, be preview-first where possible, require explicit confirmation, and produce an operation/audit result.

## Toolchain and dependencies

The versions are pinned in `build.gradle.kts` and should not be changed casually:

- Kotlin/JVM `2.4.20`;
- Kotlin serialization plugin `2.4.20`;
- Compose Desktop `1.12.0`;
- Compose Material 3 `1.12.0-alpha03`;
- JVM toolchain `25`;
- kotlinx serialization JSON `1.9.0`;
- Apache SSHD `2.19.0` for the in-process SSH client and local port forwarding;
- SLF4J NOP `2.0.17` to keep SSH library logging quiet by default;
- JNA and JNA platform `5.19.1` for desktop/native integration;
- Kotlin test for tests.

The application bundles `src/main/resources/fonts/Minecraft.otf` for the Catos-style typography. Windows packaging targets MSI and EXE through Compose Desktop; the package name is `CatosDuneAdmin`.

### Local build requirements

Install a JDK capable of running JVM 25 and use the checked-in Gradle wrapper. From this directory:

```powershell
.\gradlew.bat compileKotlin
.\gradlew.bat test
.\gradlew.bat packageDistributionForCurrentOS
```

The first command is the minimum verification after a Kotlin source change. Do not commit `build/`, generated installers, local Gradle caches, passwords, SSH keys, or diagnostic dumps containing player identities.

## Runtime architecture

```text
Windows Compose Desktop app
        |
        | Apache SSHD: local 127.0.0.1 forward
        v
Hetzner loopback control service / Dune Admin API
        |
        +-- Dune Admin player, inventory, and admin operations
        +-- DASH/Compose map control where explicitly allowed
        +-- PostgreSQL/RabbitMQ/host commands remain server-side
```

The main entry point is `src/main/kotlin/com/cato/duneadmin/Main.kt`. It owns the Compose window, page navigation, polling, UI state, authentication form, and page-level callbacks.

Important classes:

- `connection/SshTunnelManager.kt` — starts/stops the SSH session, opens the local forward, reads fixed read-only host/map/player-count commands, and controls only services in `knownMapServices`.
- `connection/DuneAdminClient.kt` — maintains an in-memory cookie session and calls the verified Dune Admin HTTP routes.
- `model/DashboardModels.kt` — player, item, map, host metric, service allowlist, and partition mapping models.
- `ui/CatosDuneTheme.kt` — Catos/Mocha visual theme and bundled font usage.
- `platform/WindowsTitleBar.kt` — Windows title-bar customization.

The SSH tunnel defaults are defined by `TunnelConfig`:

| Setting | Default | Environment override |
|---|---|---|
| SSH user | `root` | `CATOS_DUNE_SSH_USER` |
| SSH host | `95.216.71.232` | `CATOS_DUNE_SSH_HOST` |
| Local forwarded port | `18181` | `CATOS_DUNE_LOCAL_PORT` |
| Remote loopback port | `18081` | `CATOS_DUNE_REMOTE_PORT` |

The UI also lets the operator enter the SSH host and password. The password is cleared after the connection attempt and must never be persisted or logged. Host-key verification is still required before using this operationally.

## Current connection and API behavior

After the SSH forward is active, `DuneAdminClient` uses the local URL `http://127.0.0.1:18181` unless the local-port environment override is set. The currently verified Dune Admin routes are:

- `POST /api/v1/auth/login` — creates the Dune Admin session;
- `GET /api/v1/players` — loads players after authentication;
- `GET /api/v1/market/catalog` — loads item templates;
- `GET /api/v1/locations` — loads saved named teleport/location presets;
- `POST /api/v1/players/give-item` — executes the existing item-grant operation after the UI validates player, template, quantity, and quality.
- `POST /api/v1/players/teleport-to-player` — accepts `{ "source_fls_id": "...", "target_id": 123 }` and moves the source to the target actor's current position;
- `POST /api/v1/players/teleport` — accepts `{ "fls_id": "...", "partition_label": "..." }` for a named saved location.

The teleport page uses the verified player-to-player and named-location routes with a preview → confirmation → execute flow. The source selector stores the player's `fls_id`; the destination player selector sends the target actor `id`. The upstream handler uses live RabbitMQ movement when the source is online and a guarded database position write when it is offline, so the UI currently exposes online players only until offline behavior is deliberately added and tested. Test with throwaway players before routine use.

The player list currently filters to players whose `online_status` is `online` for editable player selectors. The `OnlinePlayerEntry` model expects `id`, `name`, and `online_status` JSON fields.

## Map and host operations

The dashboard polls every five seconds while the SSH tunnel is connected. Map status comes from a fixed Docker Compose query filtered to project `dune_server`. Player counts come from the server-side PostgreSQL function `dune.get_all_online_or_recently_disconnected_player_online_state()`, grouped by stable `partition_id`.

`controlMap` refuses any service not in `knownMapServices`, then uses the resolved DASH Compose files and executes only the named service:

- start: `docker compose ... up -d --no-deps <service>`;
- stop: `docker compose ... stop <service>`.

Never replace this with unrestricted `docker compose up`, `down`, `restart`, shell interpolation of user input, or direct Docker socket access. Hagga Basin/Survival and other protected services must remain governed by the repository’s map-autoload and DASH safety rules.

## UI conventions

- Keep Dashboard, Maps, Teleport, Item Grants, and Operations/Audit as separate page boundaries.
- Use the existing Mocha colors, cards, spacing, typography, and bundled Minecraft font.
- Mutating controls should be disabled until the required connection, authentication, selection, validation, and confirmation state is complete.
- Show safe, short error messages in the UI; do not display passwords, cookies, database credentials, RabbitMQ headers, raw SSH commands, or private player coordinates.
- Avoid blocking the Compose UI thread. SSH and HTTP work belongs in `Dispatchers.IO` inside the existing coroutine pattern.
- Preserve stale/unknown state instead of treating a failed read as zero players or permission to perform a destructive action.
- Keep the default window size at least tall enough for the Item Grants workflow; the current default is `1280 x 840` and the minimum is `1080 x 600`.

## Teleport workflow requirements

The intended menu is:

1. Select the online player to move.
2. Choose `TO PLAYER` or `TO LOCATION`.
3. Select another online player, or a named server-side Hagga Basin location preset such as a tradepost/hub.
4. Preview the action and show source, destination, online state, map/partition, and any cross-map warning.
5. Require explicit confirmation, execute through the verified backend, display an operation receipt, and refresh player state.

Location names and coordinates must come from a reviewed server-side preset list. Do not hardcode guessed coordinates in the Kotlin client. Do not allow an online player to be moved through an offline-only database path.

## Security and operational rules

- Never commit credentials, private keys, tokens, cookies, admin passwords, RCON passwords, database passwords, or player FLS identities.
- Never put secrets in command-line arguments, logs, screenshots, exceptions, or generated installers.
- Keep the management path private behind SSH/VPN; do not expose the local API or Dune Admin port publicly.
- Treat all player IDs, names, locations, inventory data, and operation responses as sensitive diagnostics.
- Use stable IDs for actions; display names are labels and are not identity.
- Mutations must fail closed on stale data, unknown map/partition identity, failed preview fingerprints, uncertain responses, or missing permissions.
- Teleports are map-scoped: player-to-player targets must have the same resolved map as the source. Hagga Basin location teleports require explicit `HaggaBasin` map metadata from the location preset; raw names such as `PS5_*` are not treated as safe tradepost labels.
- Do not add automatic retries around uncertain mutations such as item grants or teleports.
- Preserve the existing browser Dune Admin panel as a fallback.

## Change and verification checklist

Before handing off a change:

1. Read `DEVPLAN.md` and confirm the change fits the authority boundaries.
2. Keep backend route assumptions explicit and verified against the deployed service or source.
3. Run `.\gradlew.bat compileKotlin`.
4. Run `.\gradlew.bat test` when tests exist for the changed behavior.
5. For UI changes, verify the connected, disconnected, unauthenticated, empty-player, stale-data, and backend-error states.
6. For a new mutation, test with throwaway data/players, verify server audit output, and confirm no unrelated map or player changed.
7. Review `git diff` for secrets and generated files before committing.

## Known current limitations

- The server-side control adapter described in `DEVPLAN.md` is not fully implemented.
- Teleport selection/preview UI exists, but execution is intentionally gated until the live teleport contract is verified.
- Full operation receipts, audit history, preview fingerprints, stale-data guards, and complete map occupancy safety checks remain work in progress.
- The SSH implementation currently accepts a password in memory; an SSH key/agent flow is preferred for a later hardening pass.
- The default host is the main Hetzner host. Confirm the target host before operating on production services.
