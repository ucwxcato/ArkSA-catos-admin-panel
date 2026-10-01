# Catos Dune Admin Release and Update Flow

This is the repeatable process for publishing a Windows release and testing the in-app updater.

## Update behavior

The installed app checks the public GitHub Releases API after startup. A manual **CHECK** button is also available in the footer.

The updater reads:

```text
https://api.github.com/repos/ucwxcato/ArkSA-catos-admin-panel/releases/latest
```

It only accepts a newer stable SemVer release containing exactly one MSI matching:

```text
CatosDuneAdmin-<version>.msi
```

and one `SHA256SUMS.txt` asset. The MSI is downloaded, verified, and installed only after explicit operator confirmation. The EXE is for manual installation and is not used by automatic updates.

## Before publishing

1. Confirm the working tree contains the intended changes and no secrets.
2. Set the same version in both locations:

   - `build.gradle.kts` → `releaseVersion`
   - `src/main/kotlin/com/cato/duneadmin/update/UpdateConfig.kt` → `INSTALLED_VERSION`

3. Use a stable version such as `0.1.1`. The MSI package version must match the release version.
4. Run the build and tests:

```powershell
.\gradlew.bat compileKotlin test packageDistributionForCurrentOS
```

## Create release assets

The installers are generated under:

```text
build/compose/binaries/main/msi/CatosDuneAdmin-<version>.msi
build/compose/binaries/main/exe/CatosDuneAdmin-<version>.exe
```

Generate the checksum manifest from the exact files being uploaded:

```powershell
Get-FileHash `
  "build/compose/binaries/main/msi/CatosDuneAdmin-<version>.msi", `
  "build/compose/binaries/main/exe/CatosDuneAdmin-<version>.exe" `
  -Algorithm SHA256
```

Create `SHA256SUMS.txt` with two lines in this format:

```text
<64-character SHA-256 hash>  CatosDuneAdmin-<version>.msi
<64-character SHA-256 hash>  CatosDuneAdmin-<version>.exe
```

Do not reuse a manifest from another build. The checksum must be recalculated whenever an installer is rebuilt.

## Publish on GitHub

Create a release in `ucwxcato/ArkSA-catos-admin-panel` using a tag such as `v0.1.1`. Upload exactly:

```text
CatosDuneAdmin-<version>.msi
CatosDuneAdmin-<version>.exe
SHA256SUMS.txt
```

Do not mark the release as a draft or prerelease for the stable client channel. Keep the release notes short and include visible user-facing changes.

## Test an update

1. Publish the baseline release, for example `v0.1.0`.
2. Install the `v0.1.0` MSI on a disposable Windows profile.
3. Build and publish the newer release, for example `v0.1.1`.
4. Start the installed `v0.1.0` application.
5. Wait for the launch check or select **CHECK** in the footer.
6. Confirm that `v0.1.1` is shown.
7. Select **DOWNLOAD MSI**.
8. Confirm the verified state appears.
9. Select **INSTALL & RESTART** and approve the visible Windows Installer flow.
10. Confirm the application relaunches as `v0.1.1` and the saved SSH host remains available.

## Failure checks

- Missing or ambiguous MSI: no update must be offered.
- Missing checksum or checksum mismatch: installation must be refused.
- GitHub unavailable: the panel must remain usable.
- Non-GitHub download host: the download must be refused.
- Cancelled or failed install: the current panel must remain usable.

Never upload credentials, SSH keys, cookies, player data, or private diagnostics as release assets.
