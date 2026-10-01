# Catos Dune Admin — GitHub Release Auto-Update Development Plan

> **Status:** Planned. The application currently has Windows MSI/EXE packaging, but no release checker, download flow, installer handoff, or update verification.
>
> **Purpose:** Let an installed Catos Dune Admin client detect a newer public GitHub Release, download the correct Windows installer, verify it, and offer a safe in-place update.
>
> **Authority:** This focused plan owns the client-side release-check and update workflow. The parent [`DEVPLAN.md`](../DEVPLAN.md) remains authoritative for the application architecture, security boundaries, and release hardening. Manual GitHub Release creation remains the release owner’s responsibility.
>
> **Target:** Windows Compose Desktop application packaged as MSI and EXE through the existing Gradle Compose Desktop configuration.

## 0. Outcome

After the operator starts the installed panel, it checks the public GitHub Releases endpoint without blocking the UI. If a newer compatible release exists, the panel shows the version and release notes. After explicit confirmation, it downloads the MSI, verifies its SHA-256 checksum, launches the Windows installer, and exits so the installer can upgrade the existing installation.

Complete when:

```text
installed version -> background release check -> newer compatible GitHub release ->
operator reviews release details -> installer downloads and verifies ->
operator confirms restart -> MSI upgrades the app -> app launches at the new version
```

## 1. Locked decisions

- **Manual releases remain in scope.** A human creates and publishes GitHub Releases and uploads the MSI, EXE, and checksum manifest. This plan does not add GitHub Actions release automation.
- **Use the public GitHub Releases API.** The client reads `GET /repos/{owner}/{repo}/releases/latest`; no GitHub token is embedded in the application.
- **Use the MSI as the update artifact.** The MSI is the primary in-place upgrade path. The EXE remains available for manual installation but is not selected for automatic updates in the MVP.
- **Require user confirmation before installation.** The app may check and download only after the user accepts the update prompt; it must not silently install or restart the panel.
- **Verify the installer before launch.** The client must compare the downloaded MSI SHA-256 digest with a checksum published in the release assets. A mismatch or missing checksum fails closed.
- **Do not self-replace the running executable.** The panel launches `msiexec.exe`, then exits cleanly. The installer owns file replacement and elevation.
- **Use semantic version tags.** Release tags use a `v` prefix, such as `v0.2.0` or `v0.2.0-alpha.2`; comparison strips the prefix and follows SemVer precedence.
- **Centralize the application version.** Gradle project version and Windows package version must derive from one release version so the running client and published installer cannot silently disagree.

## 2. Goals and non-goals

### Goals

- Detect a newer public release without blocking Compose rendering or SSH/Dune operations.
- Display the current version, available version, release name, publication date, and sanitized release notes.
- Select the Windows MSI asset using a stable naming convention and reject unrelated assets.
- Download to an application-owned temporary directory, not directly over the installed application.
- Verify the downloaded installer with SHA-256 before execution.
- Launch the installer through the normal Windows installer flow and close the app only after the user confirms.
- Handle offline GitHub, malformed JSON, rate limits, missing assets, checksum mismatch, cancellation, and failed installer launch without breaking the panel.
- Provide a manual “Check for updates” action in addition to the startup check.
- Avoid repeatedly checking GitHub during one day or repeatedly prompting for the same release.

### Explicitly out of scope

- Silent/background installation or automatic application restart.
- Automatic release creation, GitHub Actions publishing, signing, or certificate acquisition.
- GitHub authentication for private repositories.
- Updating the app by running `git pull`, replacing JARs in place, or downloading arbitrary executable URLs.
- Linux, macOS, Microsoft Store, MSIX, or mobile update flows.
- Delta patches, rollback orchestration, or multi-version migration logic beyond the MSI installer’s normal upgrade behavior.

## 3. User experience / operational flow

### 3.1 Startup check

1. The app loads its local version from the centralized build/package version.
2. A background coroutine starts an update check after the main window is usable; it does not delay the SSH connection screen or dashboard.
3. The client requests the latest public release with a short connection timeout and a descriptive `User-Agent`.
4. The response is parsed and validated. Draft, prerelease, or unsupported releases are ignored unless the configured channel explicitly allows them.
5. If the release is newer, the UI displays an unobtrusive update notice. If there is no update, the result remains quiet.

### 3.2 Confirmed update

1. The operator opens the update notice and sees the target version, release notes, installer size, and release URL.
2. The operator selects **Download update**.
3. The client downloads the MSI to a unique temporary file while showing progress and a cancel action.
4. The client downloads or reads the release checksum manifest and calculates the MSI SHA-256 digest.
5. The client refuses to continue unless the expected and calculated digests match exactly.
6. The UI asks the operator to confirm closing the panel and starting the Windows installer.
7. The client starts `msiexec.exe /i <verified-msi-path> /passive` or an equivalent visible installer command, then exits.
8. The MSI performs the upgrade. The operator can launch the updated panel after installation.

### 3.3 Failure and recovery

- GitHub unavailable: keep the current app usable and show no blocking error during automatic checks.
- Manual check failure: show a concise retryable message without exposing raw HTTP responses or tokens.
- Invalid release metadata: ignore the release and record a sanitized diagnostic.
- Missing or ambiguous MSI asset: do not offer installation; link to the release page for manual review.
- Checksum mismatch: delete the temporary installer, refuse execution, and require a fresh download.
- Cancelled download: delete the partial file and leave the app running.
- Installer launch failure: keep the app open where possible and provide the release-page link for manual installation.

## 4. Architecture and ownership

### 4.1 Proposed project structure

```text
src/main/kotlin/com/cato/duneadmin/
  update/
    UpdateConfig.kt          # proposed repository/asset/channel constants
    UpdateModels.kt          # proposed release, asset, and update-state models
    GitHubReleaseClient.kt   # proposed GitHub API and asset-download client
    UpdateService.kt         # proposed orchestration, version comparison, cache
    WindowsInstaller.kt      # proposed checksum verification and msiexec launch
```

`Main.kt` owns Compose state and routes update events into the existing UI. Update networking and file work run on `Dispatchers.IO`; no network, hashing, or process launch work runs on the Compose UI thread.

### 4.2 External contract

The client uses the public GitHub REST response from:

```text
GET https://api.github.com/repos/{owner}/{repository}/releases/latest
```

The release must expose:

```text
v0.2.0/
  CatosDuneAdmin-0.2.0.msi
  CatosDuneAdmin-0.2.0.exe
  SHA256SUMS.txt
```

`SHA256SUMS.txt` uses one SHA-256 digest and filename per line, for example:

```text
<64 lowercase hexadecimal characters>  CatosDuneAdmin-0.2.0.msi
<64 lowercase hexadecimal characters>  CatosDuneAdmin-0.2.0.exe
```

The exact GitHub owner/repository is a release configuration value. The current local Git remote is `ucwxcato/ArkSA-catos-admin-panel`; confirm the canonical public repository before implementation and do not hardcode a temporary fork or mirror.

### 4.3 Ownership boundaries

- **GitHub:** release metadata, notes, assets, availability, and download transport.
- **Release owner:** builds the artifacts, checks the files, creates the release, and publishes the checksum manifest.
- **Kotlin client:** validates metadata, compares versions, downloads the expected MSI, verifies its digest, and starts the installer.
- **Windows Installer:** performs elevation, file replacement, shortcuts, and installation lifecycle.
- **Dune server/SSH/API:** unrelated to update checks; an update failure must not tear down or alter the operational connection.

## 5. Data, lifecycle, and failure handling

### 5.1 Update states

```text
Idle
  -> Checking
  -> NoUpdate
  -> UpdateAvailable
  -> Downloading
  -> Verifying
  -> ReadyToInstall
  -> Installing
  -> Failed
```

Cancellation returns to `Idle`. A checksum mismatch returns to `Failed` and removes the temporary file. The service must not report `ReadyToInstall` until metadata, asset selection, download completion, and checksum verification all succeed.

### 5.2 Version rules

- Parse only valid SemVer-compatible release tags after removing a leading `v`.
- Ignore releases older than or equal to the installed version.
- Treat prereleases according to an explicit channel setting; the stable channel ignores prereleases by default.
- Do not compare version strings lexicographically (`0.10.0` must be newer than `0.9.0`).
- The installed version shown to users must be the same value used to select the package version.

### 5.3 Temporary files and cleanup

- Use a unique file under the Windows temporary directory.
- Do not overwrite an existing installer or any installed application file.
- Delete partial downloads on cancellation and failed verification.
- Delete verified temporary installers after installation handoff when possible; retain a failed installer only if needed for a user-visible diagnostic, and never include it in logs.
- Cache only the last successful check time and last-notified release identifier. Do not persist credentials or release contents containing sensitive operational data.

## 6. Configuration, permissions, and integrations

Proposed constants/configuration:

```kotlin
data class UpdateConfig(
    val owner: String,
    val repository: String,
    val stableOnly: Boolean = true,
    val checkInterval: Duration = 24.hours,
    val msiAssetPattern: Regex = Regex("^CatosDuneAdmin-[0-9A-Za-z.+-]+\\\\.msi$")
)
```

The repository and asset naming values should be easy to change for a repository transfer without changing update logic. Avoid user-editable update URLs in the MVP; arbitrary URLs would expand the trust boundary.

Required HTTP behavior:

- `Accept: application/vnd.github+json`.
- A stable descriptive `User-Agent`.
- HTTPS only.
- Bounded connect, request, and download timeouts.
- Maximum download size suitable for the published installer.
- No authorization header for the public repository.

The update UI must remain available if SSH is disconnected, and update checks must not require Dune Admin authentication.

## 7. Safety, security, and product constraints

- A GitHub release is an executable supply-chain boundary. The app must allowlist the configured repository and expected MSI filename pattern.
- Require HTTPS and reject redirects to non-HTTPS or unexpected hosts where the HTTP client exposes redirect handling.
- Verify the checksum before starting `msiexec.exe`; never execute a partially downloaded or unverified file.
- Do not treat a checksum hosted separately from GitHub as stronger authentication. Until code signing exists, the release repository account and GitHub access controls remain critical trust boundaries.
- Do not log full download URLs if they can contain query tokens, and never add GitHub credentials to the app.
- Show release notes as plain text or safely rendered text; do not execute HTML, Markdown links, or embedded content automatically.
- Use a visible installer invocation requiring normal Windows consent. Do not attempt hidden elevation or silent replacement.
- Keep the current application usable if the update system is unavailable.
- Document that unsigned releases may produce Windows SmartScreen or unknown-publisher warnings until a publicly trusted signing path is available.

## 8. Verification matrix

| Scenario | Expected result | Evidence required |
|---|---|---|
| Installed version equals latest release | No update prompt; app remains usable | Unit test and manual startup check |
| Latest stable release is newer | Update notice shows version, notes, and release link | Unit test with fixture JSON and manual UI check |
| Newer prerelease in stable channel | Prerelease is ignored | Version/channel test |
| `0.10.0` versus `0.9.0` | `0.10.0` is correctly newer | SemVer unit test |
| Public GitHub API unavailable | App continues normally; manual check gives concise retryable error | Network-failure test/manual offline check |
| Repository response is malformed | Update is rejected without crash | Parser test |
| MSI asset missing or ambiguous | No install action is offered; release page remains available | Asset-selection test |
| MSI download cancelled | Partial file is removed; app remains open | Manual cancellation check |
| MSI checksum matches | Installer becomes eligible for confirmation | Hash verification test |
| MSI checksum mismatches | Installer is deleted and never launched | Negative hash test |
| Installer launch confirmed | `msiexec.exe` starts with the verified path and app exits | Manual test on disposable installation |
| Installer launch fails | App remains or returns with a manual-install link | Process-launch failure test/manual check |
| Update check while SSH is disconnected | Update check still works and does not alter connection state | Manual UI check |
| Release notes contain links/HTML | Text is safely displayed without automatic execution | UI rendering test |
| Repeated launches on same day | GitHub is not queried each time after successful cache policy | Cache/throttling test |

## 9. Phased checklist

### Phase 0 — Release contract and version lock

- [ ] Confirm the canonical public GitHub repository owner/name and whether releases are stable-only or include prereleases.
- [ ] Choose and document the single source of truth for the application/package version in Gradle.
- [ ] Establish the manual release asset names and `SHA256SUMS.txt` format.
- [ ] Confirm that the generated MSI performs an in-place upgrade for the existing package identity.
- [ ] **Verify:** Build a test distribution, inspect its version and filenames, and manually install it on a disposable Windows profile.

### Phase 1 — Release metadata and version comparison

- [ ] Add typed update models and a strict parser for GitHub release JSON.
- [ ] Add SemVer parsing/comparison with stable/prerelease channel rules.
- [ ] Add repository, asset-pattern, timeout, and check-interval configuration.
- [ ] Add unit fixtures for latest release, malformed release, missing assets, ambiguous assets, and version precedence.
- [ ] **Verify:** `compileKotlin`, `test`, and parser/version test coverage for all comparison branches.

### Phase 2 — Background checking and user-visible notice

- [ ] Implement `GitHubReleaseClient` using the JDK HTTP client already used by the project.
- [ ] Implement `UpdateService` with `Dispatchers.IO`, bounded timeouts, daily throttling, and sanitized failure states.
- [ ] Add a non-blocking startup check and a manual **Check for updates** action.
- [ ] Add a compact update notice/dialog showing current version, target version, release notes, release date, and release-page link.
- [ ] **Verify:** Manual checks while online/offline, with no SSH connection, and with a fixture or test release containing a newer version.

### Phase 3 — Verified installer download and handoff

- [ ] Implement stable MSI asset selection and checksum-manifest parsing.
- [ ] Implement cancellable temporary-file download with progress reporting and maximum-size enforcement.
- [ ] Implement SHA-256 verification and fail-closed cleanup.
- [ ] Implement Windows installer launch through a visible `msiexec.exe` process using the verified MSI path.
- [ ] Add confirmation, close/restart messaging, and installer-launch failure recovery.
- [ ] **Verify:** Successful update from an older disposable installation, cancelled download, checksum mismatch, missing asset, and failed process launch.

### Phase 4 — Release documentation and hardening

- [ ] Document manual release creation, artifact naming, checksum generation, and rollback to the previous release in `README.md` or a release-maintainer guide.
- [ ] Add the update behavior and unsigned-installer warning to the user-facing release documentation.
- [ ] Review logs and UI for credentials, raw URLs with secrets, player data, and unsafe release-note rendering.
- [ ] Retain the previous known-good installer for manual rollback.
- [ ] **Verify:** Perform a complete manual release rehearsal from build through GitHub upload, client detection, checksum verification, MSI upgrade, and relaunch.

## 10. Open tuning points

- **Canonical repository:** Confirm whether `ucwxcato/ArkSA-catos-admin-panel` is the final public repository or a temporary remote.
- **Release channel:** Decide whether alpha releases should be offered to friends automatically or only stable tags.
- **Installer mode:** Start with a visible/passive MSI installer; decide later whether a more interactive or quieter handoff is preferable.
- **Update cadence:** Default to one successful automatic check per 24 hours; adjust after observing GitHub rate-limit behavior.
- **Authenticity upgrade:** Revisit public code signing or an eligible open-source signing service before broader distribution.
