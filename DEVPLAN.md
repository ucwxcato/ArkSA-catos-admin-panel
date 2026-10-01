# Catos Dune Admin Panel — Development Plan

> **Status (2026-10-01):** Phase 1 desktop shell is partially implemented and the project builds successfully. The client already has an in-process SSH tunnel, live read-only Docker/map polling, host metrics, approved-map filtering, and named map start/stop commands. Preview/recheck/occupancy/audit gates, the server-side adapter, and the exact deployed dune-admin API contract remain unimplemented or unverified.
>
> **Purpose:** Provide a private Windows Kotlin/Compose operator dashboard for the Hetzner Dune server, using the visual language of CatosResourceCalc while keeping dune-admin as the primary Dune management authority.
>
> **Authority:** This document owns the proposed Kotlin desktop client, its read model, its narrow control contract, and its operator-safety rules. Icehunter/dune-admin owns Dune player/admin operations; DASH owns the underlying Compose deployment and map infrastructure. Related plans: [`duneawakening/DEVPLAN.md`](../DEVPLAN.md), [`duneawakening/admin-panel-details.md`](../admin-panel-details.md), and the CatosResourceCalc repository at `C:\Users\magni\Documents\BotsnCoding\Valheim\CatosResourceCalc`.
>
> **Target:** Windows desktop client built with Kotlin/JVM and Compose Desktop, initially based on CatosResourceCalc's Kotlin 2.4.20, Compose 1.12.0, Material 3, JVM 25, Mocha theme, and MSI/EXE packaging. Hetzner target is the existing shared Ubuntu host at `95.216.71.232`; management services remain private.

## 0. Outcome

The owner opens a Catos-styled desktop app, sees the Dune server and host health, searches maps or players, and performs carefully confirmed admin actions without manually copying SSH commands or exposing Docker, PostgreSQL, RabbitMQ, or root credentials.

Complete when:

```text
private authenticated connection -> current status and resource snapshot ->
operator searches a map/player/item -> app previews and validates the action ->
approved server-side operation executes through the existing Dune authority ->
result, audit receipt, and refreshed state are shown without interrupting unrelated games
```

The first production-capable release must support read-only status plus safe per-map lifecycle actions. Inventory grants, teleport, automap control, and broader admin operations require separate gates and verified backend support.

## 1. Locked decisions

- **Reuse the CatosResourceCalc visual system.** Keep its Compose Desktop foundation, Mocha colors, Minecraft font, card/panel layout, search-first interaction, and Windows packaging conventions. Do not copy unrelated Valheim calculation logic into the admin client.
- **Use the copied Minecraft font asset.** The app bundles `src/main/resources/fonts/Minecraft.otf`, copied from `C:\Users\magni\Documents\BotsnCoding\Valheim\Minecraft.otf`, and the Compose theme loads it through `Font("fonts/Minecraft.otf")` for the same typography as CatosResourceCalc.
- **The Kotlin app is a client, not the privileged control plane.** It must not contain the root SSH password, Funcom token, database password, RabbitMQ credentials, Docker socket access, or a broad sudo capability.
- **dune-admin remains the primary Dune management authority.** The Kotlin app is a convenience client over verified dune-admin APIs or a deliberately narrow adapter. It must not silently become a second source of truth.
- **DASH remains supporting infrastructure.** Compose operations, map autoload configuration, and infrastructure actions use DASH's validated control path through a server-side adapter only when dune-admin does not own the operation.
- **All mutations are preview-first where possible.** Item grants, player movement, configuration changes, and bulk actions require a dry-run/preview, explicit confirmation, server-side authorization, and an audit record.
- **Map identity uses stable service and partition identifiers.** Display names are search labels only. Occupancy checks use `partition_id` and the live `dune.world_partition` catalog; unknown or null identity fails closed.
- **No public management endpoint.** The client connects through a local SSH tunnel, private VPN, or another authenticated private path. Do not add a public Caddy route for the new client API.
- **Use one external connection.** The first implementation uses one SSH connection/tunnel from the Windows app to a loopback-only control service on Hetzner. The control service calls dune-admin, DASH, Docker, host metrics, and local configuration internally; the desktop app does not maintain four separate subsystem connections.
- **Initial credential UX may use an in-memory masked password field.** The password is never persisted, logged, placed in process arguments, or committed. The SSH implementation uses an in-process library, clears the field after the connection attempt, and still requires host-key verification. A key/agent path remains the preferred later option.
- **No unqualified Compose lifecycle commands.** Starting or stopping one map must name the exact service and use the active resolved Compose files. A full-stack `up -d` is prohibited from the client.

## 2. Goals and non-goals

### Goals

- Show Dune map status, readiness, ports, service identity, occupancy, and recent lifecycle errors.
- Show host CPU, RAM, swap, disk, load, Docker usage, and Dune-specific resource usage with timestamps and stale-data indicators.
- Search maps by service name, display name, partition, region, and status.
- Start, gracefully stop, and restart one approved map with occupancy and dependency checks.
- Show and toggle the map autoload/controller policy through a narrow server-side operation, including `never_stop_services` and always-on maps such as Overmap.
- Search players and display safe identity/location/status information.
- Search the dune-admin item catalog by friendly name/template ID/category and show stack/quality warnings.
- Grant an item quantity through the same verified live-RMQ plus database-fallback path used by dune-admin's manual/welcome-kit item grants.
- Provide player-to-player teleport only through a verified supported dune-admin/game command path; support offline teleport separately if that is the only safe supported mode.
- Provide an auditable foundation for additional admin controls such as broadcast, XP, currency, permissions, settings, backups, and logs.
- Preserve the existing Dune Admin browser panel as a fallback and keep Nitrado data untouched.

### Explicitly out of scope

- Replacing or forking dune-admin's core player/inventory implementation in the first release.
- Direct database mutation from the Kotlin client.
- Direct Docker socket access from a Windows machine or broad root SSH execution.
- Automatic full-map farming, automatic player travel orchestration, or automatic restart policies.
- Moving valuable player progression, deleting worlds, resetting inventories, or destructive database tools without a separately approved plan.
- Public Internet exposure of the Kotlin API, DASH admin UI, PostgreSQL, RabbitMQ, or Docker.
- Mobile/web client support before the desktop/private-control model is proven.

## 3. User experience / operational flow

### 3.0 Navigation and page boundaries

The app uses separate top-level pages rather than placing every operation in one dashboard. The initial navigation shell is implemented; the operational workflows remain gated until their backend contracts are verified:

- **Dashboard:** connection state, host/Dune health, resource cards, running-map summary, and recent warnings. *(Shell implemented.)*
- **Maps:** searchable map catalog, partition/occupancy details, autoload policy, and guarded start/stop/restart actions. *(Initial map page implemented; safety gates remain.)*
- **Teleport:** player search, source/target selection, named Hagga Basin destination presets, preview, confirmation, and operation receipt. *(Player-to-player and named-location client calls are wired to the verified Dune Admin routes. Player targets are restricted to the source map; location presets must carry explicit Hagga Basin metadata, so raw/internal presets are hidden. Live throwaway-player verification remains.)*
- **Item Grants:** player search, item catalog, quantity/quality/inventory selection, dry-run warnings, confirmation, and grant receipt. *(Page and authenticated player/item autocomplete implemented; server-side dry-run and receipt remain gated.)*
- **Operations/Audit:** recent operation status, uncertain outcomes, receipts, and sanitized action history. *(Page shell implemented; data flow remains.)*

Teleport and Item Grants must not share a cramped generic action panel. Each page owns its target selection and preview state, while the app-wide operation model handles confirmation, execution, refresh, and errors consistently.

### 3.1 Connection and dashboard

1. The app opens to a connection profile named `Hetzner Dune` with no secret values stored in the repository.
2. The operator starts or verifies the private tunnel, or the app uses a configured private endpoint protected by the selected auth mechanism.
3. The app authenticates and displays connection state, backend version, last successful refresh, and a clear stale/error state.
4. The dashboard shows host resource cards, Dune stack health, running maps, player count, and recent warnings.

### 3.2 Map lifecycle

1. Operator searches or selects a map.
2. The app shows stable service name, partition IDs, assigned ports, current container state, health, occupancy, and last transition.
3. Start/stop/restart opens a preview dialog showing affected services, player impact, dependency changes, and the exact safe operation class.
4. The server rechecks identity, occupancy, current state, and authorization immediately before execution.
5. The app displays the operation ID, result, sanitized logs, and refreshed status.

### 3.3 Item grant

1. Operator selects a player and searches the bundled/server-provided item catalog.
2. The app displays template ID, friendly name, quantity, stack limit, quality options, target inventory, and warnings.
3. The server performs a dry-run resolving inventory capacity, free slot, template identity, and current player state.
4. Operator confirms the exact grant phrase in the UI.
5. The server performs the guarded grant, records before/after evidence and operation ID, and refreshes the player's inventory.

### 3.4 Teleport

1. Operator selects source and target players or an approved destination.
2. The app indicates whether the operation is online/native-command or offline/database-backed.
3. The server refuses unsupported or ambiguous modes; it does not silently fall back to raw SQL.
4. A preview displays target identity, current map/partition, destination, online state, and expected disconnect/relog behavior.
5. After explicit confirmation, the operation executes and the app shows the receipt and any required relog instruction.

## 4. Architecture and ownership

```text
Windows Kotlin/Compose client
  ├─ Catos visual system and navigation
  ├─ typed API client, polling, cache, action dialogs
  ├─ one SSH connection/tunnel
  └─ no root/Docker/database secrets
          │ one forwarded loopback control endpoint
          ▼
Server-side narrow control adapter (loopback-only)
  ├─ authenticates and authorizes requests
  ├─ calls dune-admin's verified REST API where available
  ├─ calls allowlisted DASH/map helper operations where required
  ├─ reads sanitized metrics and Docker/map state
  ├─ performs preview/recheck/audit/timeout logic
  └─ never exposes Docker socket or raw credentials to the client
          │
          ├─ Icehunter/dune-admin: players, inventory, grants, teleport,
          │  permissions, audit, supported player/admin operations
          └─ DASH/Compose: map services, autoload policy, infrastructure,
             resource and container state
```

### Proposed client modules

```text
kotlin-admin-panel/
  build.gradle.kts
  settings.gradle.kts
  src/main/kotlin/.../
    Main.kt
    model/                 // serialized API DTOs and stable IDs
    api/                   // authenticated HTTP client and error mapping
    state/                 // refresh jobs, cache, operation state
    ui/
      CatosDuneTheme.kt    // adapted MochaTheme
      DashboardScreen.kt
      MapsScreen.kt
      TeleportScreen.kt
      ItemGrantsScreen.kt
      OperationsScreen.kt
      components/
    security/              // no secret persistence; tunnel/profile policy
```

The server-side adapter is a separate deployable component or a narrowly scoped extension of dune-admin, not a privileged Kotlin desktop process. It listens only on loopback and is reached externally through one SSH tunnel. Its exact language and location are TBD after the installed dune-admin release/API is inspected. Prefer extending dune-admin only when its ownership and upgrade path remain clear; otherwise use a small root-owned local service with an allowlisted helper.

### Existing facts and discovery boundaries

- **Verified:** CatosResourceCalc is Kotlin/JVM Compose Desktop with Material 3, a Mocha palette, Minecraft font, search panels, cards, coroutines, and Windows MSI/EXE packaging.
- **Verified:** `src/main/resources/fonts/Minecraft.otf` has been copied into this project from the CatosResourceCalc source location; the Kotlin theme must use that bundled resource rather than an OS-installed font.
- **Verified from dune-admin documentation/source description:** dune-admin is a Go single binary exposing a REST API over PostgreSQL and RabbitMQ; it supports Docker/local control planes, player inventory/admin features, teleport, audit logging, and Welcome Kits.
- **Verified from dune-admin documentation:** Welcome Kits use item template IDs, quantity, and quality and deliver through the same live-RMQ plus database-fallback path as manual item grants.
- **Verified from the local deployment notes:** Icehunter/dune-admin is primary and loopback-bound on port `18081`; DASH is supporting tooling; map occupancy must use partition IDs; the automap policy is stored in `/etc/dune-map-autoload.json` with a related systemd start helper.
- **Verified in the Phase 1 shell:** The Windows client opens one in-process SSH tunnel to `95.216.71.232` as the configured SSH user, forwarding local `127.0.0.1:18181` to the server's loopback `127.0.0.1:18081`. The password field is masked and ephemeral; successful live authentication was tested by the owner.
- **Verified in the live-status shell:** Once the SSH session is authenticated, the client uses the same session's read-only fixed Docker status command to enumerate only running containers in Compose project `dune_server`; no separate Docker, PostgreSQL, RabbitMQ, or dune-admin connection is created. Host metrics (memory, CPU load, disk, uptime) and online-player counts grouped by stable `partition_id` also refresh over that session. Occupancy uses the same `dune.get_all_online_or_recently_disconnected_player_online_state()` source as the map autoloader; failed occupancy reads are displayed as unknown rather than zero.
- **Verified in the map-control shell:** The client offers an approved-map autocomplete list and named start/stop operations. Starts resolve `/opt/dash/current/scripts/compose-files.sh` and use `up -d --no-deps`; stops use graceful `stop`. Unknown services and infrastructure names are rejected client-side and server-side. Occupancy/partition rechecks, previews, audit receipts, and autoload controls remain outstanding.
- **Unknown/TBD:** Exact installed dune-admin version, online teleport support, and whether the deployed binary exposes all documented current features. The Kotlin shell now calls the verified Dune Admin login and item-grant endpoints through the SSH-forwarded loopback API; item catalog search and live throwaway-player verification remain outstanding.

## 5. Data, lifecycle, and failure handling

### Read model

The client keeps a short-lived in-memory cache only. Suggested DTOs:

| Model | Required identity | Important state |
|---|---|---|
| `MapSummary` | `serviceName`, `partitionIds` | display name, running, healthy, ports, occupancy, last transition |
| `PlayerSummary` | account/player/FLS-safe ID | character name, online state, partition ID, map label, last seen |
| `ItemDefinition` | `templateId` | display name, category, stack limit, quality support |
| `HostMetrics` | sample timestamp | CPU, load, memory, swap, disk, Docker/Dune usage |
| `OperationReceipt` | server-generated operation ID | actor, action, target, preview fingerprint, result, warnings, audit location |

Every response carries `observed_at`, backend version, and a stale/error indicator. The client never treats stale status as permission to stop a map or mutate a player.

### Operation states

```text
requested -> previewing -> awaiting_confirmation -> executing -> succeeded
                                                └──────> refused/failed
```

Operations must be idempotent where possible. A duplicate start of an already-running map returns current state rather than recreating dependencies. A duplicate item grant is never silently retried after an uncertain timeout; the operator must inspect the operation receipt and inventory before retrying.

### Failure behavior

- API timeout: show unknown/stale state; do not infer stopped or failed.
- Lost connection during mutation: mark outcome uncertain and require server-side operation lookup.
- Unknown partition or player identity: refuse the action.
- Occupancy scan failure: refuse map stop/restart.
- Compose or dependency recreation detected: refuse the action unless the operation explicitly allows it and the preview names the affected dependencies.
- Player/item dry-run mismatch at execution: abort; do not execute the stale preview.
- Server restart: reconnect, invalidate cached mutation previews, and require a new preview.

## 6. Configuration, permissions, and integrations

### Proposed server contract

These names are design contracts, not claimed existing dune-admin endpoints. The adapter should map them to the deployed API only after route/schema inspection.

```text
GET  /control/v1/health
GET  /control/v1/metrics
GET  /control/v1/maps
POST /control/v1/maps/{service}/preview-start
POST /control/v1/maps/{service}/preview-stop
POST /control/v1/maps/{service}/execute
GET  /control/v1/players?query=...
GET  /control/v1/items?query=...
POST /control/v1/items/grant/preview
POST /control/v1/items/grant/execute
POST /control/v1/teleport/preview
POST /control/v1/teleport/execute
GET  /control/v1/autoload
POST /control/v1/autoload/preview
POST /control/v1/autoload/execute
GET  /control/v1/operations/{operation_id}
```

The deployed dune-admin API should be preferred locally for player/inventory operations if it already provides the required authenticated route. The Kotlin client should not bypass its permission matrix or audit log. Any adapter endpoint must preserve actor identity and forward the operation to dune-admin's supported path. The Kotlin client reaches all endpoints through the single SSH-forwarded adapter endpoint; it does not separately connect to Docker, PostgreSQL, RabbitMQ, or dune-admin.

### Authorization roles

- `viewer`: metrics, status, maps, players, logs.
- `map_operator`: start/stop/restart approved maps; cannot change infrastructure or autoload policy.
- `player_operator`: dry-run and execute item grants/teleports after explicit authorization.
- `owner`: configuration, autoload policy, backup, and emergency actions.

The initial deployment should use one owner account and read-only defaults. Any desktop profile is a locator/auth configuration, not a secret vault.

### CatosResourceCalc reuse

- Copy or extract only reusable visual components and theme values, respecting the existing project license files and font attribution.
- Keep the admin panel as a separate project under this directory rather than coupling it to the Valheim calculator's data model.
- Use Kotlin serialization DTOs, coroutine-backed refresh jobs, Compose `LazyColumn` search results, cards, dialogs, and the existing Windows title-bar/packaging approach.

## 7. Safety, security, and product constraints

- Bind the server adapter to loopback or a private VPN. A local SSH tunnel may forward it to the desktop.
- Use TLS and authenticated sessions for any non-loopback path. Do not put the Hetzner root password in Kotlin resources, Gradle properties, Git, logs, process arguments, or crash reports.
- Keep dune-admin's capability checks and audit log authoritative. Every client mutation must show an actor, target, action, confirmation, and result.
- Keep item grants disabled until the exact deployed endpoint and the same live-RMQ/DB-fallback behavior are verified with a throwaway player.
- Prefer server-side dry-run fingerprints, exact confirmation phrases, stack/capacity validation, and full backup/receipt behavior.
- Online teleport must use a supported command/API path with a tested player-targeting model. Offline teleport must use the reviewed offline-only path and must not move an online player.
- Never issue raw SQL from a text box in the Kotlin app. A read-only diagnostic query feature, if later needed, must be separately allowlisted and role-gated.
- Map controls must preserve `never_stop_services`, always-on Overmap policy, partition occupancy checks, and the warning that map names and service names are not interchangeable.
- Do not restart PufferPanel from this app. Do not expose or grant the Docker socket to PufferPanel or the desktop client.
- Rate-limit mutation buttons, disable while an operation is in flight, and require explicit confirmation for quantity, destination, map, and bulk scope.
- Redact tokens, passwords, database credentials, RabbitMQ headers, player FLS identities, and private coordinates from UI diagnostics and logs.
- Preserve Nitrado and existing Dune backups as rollback until the new client has passed operational and restore tests.

## 8. Verification matrix

| Scenario | Expected result | Evidence required |
|---|---|---|
| App starts with no server connection | Clear offline state; no crash; no secrets requested in logs | UI test and sanitized log |
| Authenticated read-only connection | Health, maps, players, metrics load with timestamps | API integration test and screenshot |
| Search maps with duplicate display names | Stable service/partition identity remains visible | Fixture test using conflicting names |
| Start an already-running map | No recreate; current state returned | Adapter test and Compose/container evidence |
| Stop an occupied map | Refused with player/map identity shown | Occupancy fixture and live safe test |
| Stop with unknown occupancy | Refused closed | Failure-injection test |
| Overmap always-on policy | Stop action disabled/refused unless owner override is explicitly approved | Live policy check |
| Automap toggle | Preview shows affected policy/systemd behavior; no unrelated maps start | Config diff, service status, audit receipt |
| Item search | Friendly name maps to correct template ID and displays warnings | Catalog fixture and deployed catalog comparison |
| Item dry-run | No inventory mutation; capacity/slot/warnings returned | Before/after inventory evidence |
| Item grant | Exactly one requested grant; audit receipt; refresh shows result | Throwaway-player live test |
| Duplicate/timeout item request | No blind retry or duplicate grant | Operation lookup and audit evidence |
| Online teleport | Supported path only; player arrives or action refuses clearly | Two-player throwaway test and logs |
| Offline teleport | Only Offline player is moved; backup/receipt exists | Preview fingerprint, DB verification, restore evidence |
| Restart backend | Client reconnects; old previews invalidated | Restart test |
| Existing games online | No PufferPanel restart, port collision, or resource starvation | Host service/resource snapshot |
| Unauthorized mutation | Server rejects action regardless of client UI state | Role test and audit/log evidence |
| Public probe | Adapter and admin ports are not Internet-exposed | `ss`, firewall/provider check, external probe |

## 9. Phased checklist

### Phase 0 — Contract and source discovery

- [ ] Record the deployed dune-admin version, commit/release, provider mode, auth mode, and API route inventory without printing secrets.
- [ ] Inspect dune-admin source or generated route/schema documentation for player list, inventory, item catalog, welcome-kit/manual grant, teleport, map lifecycle, audit, and metrics paths.
- [ ] Trace the care-package/manual item-grant implementation and document the exact live-RMQ envelope, DB fallback, template/quantity/quality model, dry-run behavior, and execution gates.
- [ ] Confirm whether online teleport is supported separately from offline teleport; document refusal behavior if it is not.
- [ ] Decide whether the server adapter is an extension of dune-admin or a separate narrow service; record upgrade/rollback ownership.
- [ ] Create a non-secret test contract with redacted fixtures for maps, players, items, metrics, previews, and receipts.
- [ ] **Verify:** The exact deployed routes and operation owners are known, and a throwaway test can perform a read-only status request without changing Dune.

### Phase 1 — Catos-styled read-only desktop shell

- [x] Scaffold `kotlin-admin-panel` as a separate Kotlin/JVM Compose Desktop project using the verified CatosResourceCalc baseline.
- [x] Recreate the Mocha theme, Minecraft typography, panel/card components, and Windows title-bar behavior without copying Valheim-specific state. MSI/EXE packaging remains to be verified.
- [x] Load `src/main/resources/fonts/Minecraft.otf` through Compose `Font("fonts/Minecraft.otf")` and apply it to the Dune app typography. Packaged-release resource verification remains outstanding.
- [x] Implement the initial connection profile and private SSH tunnel path with masked ephemeral password handling, authentication, bounded command timeouts, polling, and disconnect/error handling. Reconnect policy and full stale-state presentation remain to be hardened.
- [ ] Complete dashboard cards for host metrics, Dune health, map counts, player counts, disk/swap, and last refresh. The current shell displays live host memory/load/disk/uptime, running-map data, and partition-backed player counts; swap and a full Dune health model are not yet wired.
- [x] Implement the initial maps list/search with approved service names, stable service identifiers, running-state badges, partition/port display, and the four repaired story-map services. Full catalog/state coverage for stopped maps remains outstanding.
- [ ] **Verify:** Windows build/package succeeds; fixture-backed UI tests cover loading, empty, stale, error, and disconnected states; live read-only status works through the private path.

### Phase 2 — Map operations and autoload control

- [ ] Implement server-side map inventory from the resolved Compose/service catalog and `dune.world_partition` mapping.
- [ ] Implement preview/recheck/execute for one-map start, graceful stop, and restart; prohibit unqualified full-stack actions.
- [ ] Add occupancy guard using partition IDs and fail closed on unknown/null results.
- [ ] Add operation polling, uncertain-outcome recovery, sanitized log snippets, and audit receipt display.
- [ ] Add autoload policy read/preview/execute through a narrow allowlisted server operation; preserve Overmap and `never_stop_services` semantics.
- [ ] **Verify:** Start/stop a non-production or approved map, refuse an occupied/unknown map stop, demonstrate no dependency recreation for a simple map operation, and verify no PufferPanel restart.

### Phase 3 — Player and item administration

- [ ] Implement player search, identity, online state, current partition, location label, and safe inventory summary for the Teleport and Item Grants pages. The current grant page queries `/api/v1/players` after Dune Admin login and filters to online players for editable autocomplete; richer identity/location/inventory data remains.
- [x] Query dune-admin's `/api/v1/market/catalog` after login and provide case-insensitive editable search by display name or template ID. Category, quality, and stack warnings remain.
- [x] Implement the initial item-grant preview/confirmation and execution through dune-admin's verified `/api/v1/players/give-item` path, not a new direct SQL implementation. The client requires a Dune Admin session, validates bounds, requires typing `GRANT`, and never retries an uncertain mutation.
- [ ] Add exact target inventory selection only after the dry-run resolves capacity and slot behavior.
- [ ] Add item catalog search, server-side preview/operation receipt display, duplicate prevention evidence, and post-grant inventory refresh. Client-side quantity bounds and confirmation phrase are implemented.
- [ ] **Verify:** Use a throwaway character, prove dry-run makes no write, grant one known harmless item, verify exactly one result in-game/database, and test an invalid template/quantity refusal.

### Phase 4 — Teleport and general admin actions

- [ ] Implement the safest supported player-to-player/native teleport path after Phase 0 confirms its contract.
- [ ] Implement offline teleport only if the reviewed guarded endpoint is available and clearly labeled as offline; require preview fingerprint and backup/receipt.
- [ ] Add read-only logs, broadcasts, player kick/utility controls, and server settings only as individually scoped capabilities.
- [ ] Add owner-only backup/status actions; do not add destructive restore or database SQL to the first production release.
- [ ] **Verify:** Two-player throwaway teleport test, offline-state refusal for online players, permission matrix tests, audit verification, and rollback evidence.

### Phase 5 — Hardening and release

- [ ] Add unit tests for DTO validation, map identity, quantity bounds, stale previews, operation state, and error mapping.
- [ ] Add integration tests against a mock adapter and a controlled Dune test target; avoid testing mutations on valuable characters.
- [ ] Add structured client diagnostics with secret/FLS/coordinate redaction.
- [ ] Document tunnel setup, account roles, recovery, backend restart behavior, and fallback to the browser dune-admin panel.
- [ ] Package signed Windows MSI/EXE releases and retain the previous release for rollback.
- [ ] **Verify:** Full matrix passes, external management ports remain private, resource use is acceptable, and the owner confirms the browser panel remains usable as fallback.

## 10. Open tuning points

- Exact dune-admin release/API route schemas and whether the live installation is behind the repository's current documentation.
- Separate adapter versus dune-admin extension; decide after source and upgrade-boundary review.
- SSH tunnel ownership and reconnect behavior. Initial implementation should use one app-owned or operator-started tunnel with host-key verification, bounded reconnect, and no automatic mutation retry after disconnect.
- Whether the app should bundle `item-data.json` or query the server catalog, and how catalog revisions are identified.
- Which maps are eligible for normal operator start/stop, especially Survival and always-on Overmap.
- Whether online teleport is acceptable for routine use or only offline teleport should be exposed.
- Which additional admin controls are wanted after the read-only/map/item MVP passes live testing.

## References

- [Icehunter/dune-admin](https://github.com/Icehunter/dune-admin) — REST/API architecture, player/inventory/teleport capabilities, Welcome Kits, permissions, and audit behavior.
- [DASH admin-safe content API](https://github.com/snapetech/DuneAwakeningSelfHost/blob/main/docs/admin-safe-content-api.md) — dry-run, guarded item grant, bundle, and offline-teleport safety patterns used as supporting integration guidance.
- [DASH admin panel operations](https://github.com/snapetech/DuneAwakeningSelfHost/blob/main/docs/admin-panel.md) — guarded item grant and operational mutation gates.
- [`duneawakening/DEVPLAN.md`](../DEVPLAN.md) — live-host safety, ports, resource, map identity, and panel ownership constraints.
- `C:\Users\magni\Documents\BotsnCoding\Valheim\CatosResourceCalc` — existing Kotlin/Compose UI reference implementation.
