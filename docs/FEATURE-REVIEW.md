# Pecalang feature review

Review scope: collection, posture scoring, published-indicator matching, optional AI,
change watching, scheduling, report persistence/export and permission behavior.
The app remains a read-only check-up, not proof that a device is clean.

## Corrected defects

| Area | Previous behavior | Updated behavior |
|---|---|---|
| Android 10 inventory | API 30 install-source calls failed and made installer information unknown | Use Android 10's supported installer API; retain the newer API on Android 11+ |
| VPN coverage | Unsupported/missing owner UID could silently hide an active VPN | Guard the API level and include an explicit coverage gap when Android cannot name the active VPN |
| Indicator matching | An unreadable signing certificate skipped package-name matching too | Keep package-name and readable APK checks even when certificate lookup fails |
| APK hashing | Budgets were checked only between packages, so one large APK could exceed the byte/time cap | Check the shared byte budget before each file and the deadline between chunks; report skipped coverage |
| Sideload classification | Legitimate OEM stores were labeled as sideloads, while Android's file installer was excluded | Distinguish recognized stores from package installers; keep system apps excluded |
| Change watching | Network constraints blocked local checks while offline | Local detection works offline; AI remains optional and deterministic alerts still work when the model fails |
| Certificate alerts | “2 new user CA certificates” never matched the severity test | Newly added CA certificates trigger medium-severity review |
| Notification access | A new listener could stay silent as a low-priority routine change | Newly granted notification access triggers a medium-severity alert |
| Watch baseline | Failed app inventory could replace a healthy baseline with an empty one | Retry without replacing the baseline when the inventory is incomplete |
| Watch manual helper | A helper tried to launch an Activity as a foreground Service | Enqueue the normal watch worker instead |
| AI package privacy | Disabling package names still exposed fallback labels, installer IDs and watch delta text | Hide package fallback labels/installer IDs and redact local package names from watch context and rule titles, including removed apps no longer present in the inventory |
| Anthropic configuration | A base URL ending in `/v1` produced `/v1/v1/messages` | Accept origin or `/v1` base URLs without duplicating the API path |
| Anthropic usage | Token usage always displayed zero because only `total_tokens` was read | Sum Anthropic input/output usage while retaining OpenAI-compatible total-token handling |
| Automatic checks | Daily and boot jobs attempted a background dataSync service, then swallowed start failures | Execute scheduled collection inside CoroutineWorker; boot enqueues the same supported worker; failures retry and remain visible |
| AI run bounds | Direct callers could supply an out-of-range step budget | Clamp to 2–10 steps at execution as well as storage; propagate cancellation |

## Regression verification

Fourteen focused tests were added: three hash-budget/digest cases, four watch-alert
cases, and seven AI/privacy/provider/installer cases. Existing rule and mock-AI
regressions remain in place. The final integrated debug APK builds successfully and
all 34 tests pass after the scheduled-worker and removed-app privacy fixes.
Lint reports one existing QUERY_ALL_PACKAGES distribution-review error, with no
remaining UI or Android API compatibility errors.

## Retained constraints and follow-up

- App identity and existing preference/report filenames stay compatible with current
  installations. User-facing branding and new shared export names use Pecalang.
- `QUERY_ALL_PACKAGES` remains necessary for this inventory feature. The app needs
  the relevant Play distribution declaration/review; removing it would hide apps.
- No live paid-provider request was made. Connection tests need the user's configured
  endpoint, model and key.
- AI key storage can fall back to app-private plain preferences when Android's
  keystore fails. UI copy must not promise encryption unconditionally. A future
  fail-closed storage migration needs explicit product treatment and device tests.
- Real-device Android 10/15, OEM permission surfaces, Doze/reboot, denied-notification,
  full TalkBack and long-running watch verification remain release QA work.
- Wi-Fi/overlay/usage-access visibility depends on Android/OEM restrictions; this
  review does not turn sandboxed configuration checks into a full forensic scan.

Reference for background-work review:
[Android foreground-service start restrictions](https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start) and [Android 15 boot restrictions](https://developer.android.com/about/versions/15/behavior-changes-15#fgs-boot-completed).
