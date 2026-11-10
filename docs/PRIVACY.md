# Privacy

## Position

The phone already holds everything. A security app is only useful if it reads some
of it, so the question is: what is the minimum, and where does it go?

**v1 answer: nothing leaves the phone, and the app has no internet permission.**

## Data inventory

Read during a scan, held only in memory until the report is written:

| Field | Why | Example |
|---|---|---|
| Android release / SDK / security patch | patch-age and EOL checks | `15`, `35`, `2025-11-01` |
| Manufacturer + model | report header only | `Google Pixel 8` |
| Build tags | test-key check | `release-keys` |
| Lock-screen state, biometric hardware | lock checks | `secure` |
| Developer / ADB / unknown-sources / verifier flags | posture checks | `adb=on` |
| Installed package name + label | the core inventory | `com.example.app`, `Example` |
| Installer package, first-install and update time | provenance and recency | `com.android.vending` |
| Granted dangerous permissions (names only) | capability checks | `RECEIVE_SMS` |
| Signing-certificate SHA-256/SHA-1 | published-indicator match | `a1b2…` |
| APK file SHA-256/SHA-1 (candidate apps only) | published-indicator match | `f6e5…` |
| Target SDK | legacy-sandbox check | `28` |
| Accessibility / admin / notification / overlay / usage access membership | access-surface checks | `true` |
| Active VPN package, proxy host, Private DNS mode | network checks | `one.one.one.one` |
| Wi-Fi SSID + security type | Wi-Fi check | `Cafe`, `OPEN` |
| Count of user-installed CA certificates | interception check | `2` |

## Not read

- Contacts, SMS bodies, call logs, photos, files, calendars.
- Accounts, email addresses, phone number.
- Location, BSSID, IP addresses, DNS history.
- Traffic contents, app-private data.
- App usage durations.

APK files are read (not copied) only to compute a hash, and only for apps that
could plausibly match an indicator: sideloaded, published package name, or
installed in the last 180 days. Hashing is local and the files never leave the
device.

## Stored

One report file, app-private: `files/last-report.json`. Backup is disabled
(`allowBackup=false`), so it is not copied to Google Drive. If AI is enabled, the
API key lives in a separate Keystore-encrypted preferences file; the watch mode
adds `watch-baseline.json` and `watch-log.json` in the same private directory.

## Shared

Only when the user taps **Share report**. The shared file is the same minimal
record. A package list is a fingerprint of the device and can identify a person
with effort; the privacy screen says so.

## Deleted

The next scan overwrites the file. Uninstalling removes it.

## Future cloud mode

Non-negotiable rules if a backend is added:

1. Off by default, enabled per scan, not buried in settings.
2. The payload is the evidence bundle above — no raw dumps.
3. Hosted mode publishes modified Artex source (AGPL-3.0) and a retention period.
4. A "delete my data" path that actually deletes, including backups.

## Optional AI

Off by default. When enabled, the connected model receives the **evidence bundle**:
build, patch, flags, package inventory (labels only if package names are turned
off), granted permission names, access lists, network configuration and indicator
matches. It never receives messages, photos, files, accounts or location.

The API key is stored in `EncryptedSharedPreferences` (Keystore-backed AES-256-GCM)
and sent only to the base URL you type — a local server keeps everything on your
network. Transcripts are not stored; only findings, steps and token counts. The
provider's retention policy applies to what it receives. See [AI.md](AI.md).

## Permissions and their justification

| Permission | Justification |
|---|---|
| `QUERY_ALL_PACKAGES` | The app is an inventory app; without it the finding list is empty. |
| `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `NEARBY_WIFI_DEVICES` | Security type of the connected network. `neverForLocation` so no location permission is needed. |
| `POST_NOTIFICATIONS` | Scan result notification. Optional. |
| `FOREGROUND_SERVICE(_DATA_SYNC)` | A package walk should survive screen-off. |
| `RECEIVE_BOOT_COMPLETED` | Refresh the score after reboot. |
| `INTERNET` | Only for the optional AI layer: the user's own model endpoint. |

No runtime dangerous permission is requested in v1.
