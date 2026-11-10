# On-device test plan

Supervised first run on a real phone. The emulator cannot show OEM behaviour:
permission prompts, battery killers, Settings forks, or real scan cost.

## Before you connect

- Phone on **Android 10+** (`minSdk 29`). Android 15 is the highest tested target.
- Developer options on, **USB debugging** on. Xiaomi/MIUI additionally needs
  *USB debugging (Security settings)* to allow installs; Samsung *Auto Blocker* can
  refuse sideloads — disable it for the test.
- Data cable, not a charge-only one. Battery above 40 %, screen unlocked.
- **Watch mode and AI off for the first run.** Add one variable at a time.

## Install

```bash
adb devices -l                     # must show your phone as "device", not "unauthorized"
adb install -r app/build/outputs/apk/deviceTest/app-deviceTest.apk
adb shell am start -n dev.triage.app.devicetest/dev.triage.app.ui.MainActivity
adb logcat -c
```

If `adb devices` says `unauthorized`, unlock the phone and accept the RSA prompt.

## What to watch, in order

1. **Cold launch** — no crash, first scan starts on its own.
2. **Notification permission** (Android 13+) — allow and deny both behave.
3. **Scan duration** — time it. Budget target is 4 s; expect more on first run
   because every sideloaded/recent APK gets hashed (400 MiB / 45 s cap).
4. **Finding correctness** — for each finding: is it true? Note false positives with
   the package name.
5. **Deep links** — every *Open settings* must land somewhere sensible on this OEM.
6. **Gaps** — the list must show 5 permanent gaps; note any collector error count.
7. **Persistence** — force-stop, relaunch, report still there and identical.
8. **Export** — share JSON, confirm it opens and has no unexpected fields.
9. **Watch mode** — enable, install an app, wait for the notification; then uninstall
   that app. Turn it off afterwards.
10. **Battery** — note level before/after; watch the device temperature.

```bash
adb logcat -d | grep -E "AndroidRuntime|FATAL|dev.triage" | tail -40
adb shell run-as dev.triage.app.devicetest cat files/last-report.json > report.json
```

## Known OEM pitfalls

| Symptom | Cause | Fix |
|---|---|---|
| `INSTALL_FAILED_USER_RESTRICTED` | MIUI install guard | Enable *Install via USB* + *USB debugging (Security settings)*, sign in to Mi account |
| Install blocked | Samsung Auto Blocker | Settings → Security and privacy → Auto Blocker → off for the test |
| Accessibility fix opens a different screen | OEM Settings fork | Record the destination; the text instructions must still work |
| Watch checks never run | Battery optimisation kills WorkManager | Exempt Triage from battery optimisation |
| Scan takes > 60 s | Large APK set / slow flash | Expected on first run; report the number, the budget may need tuning |
| `run-as` denied | OEM disables debuggable app data | Use the in-app share sheet instead |

## Reset

- Settings → Apps → Triage → Storage → **Clear storage** (drops report, baseline, key).
- Uninstall removes everything; nothing leaves the device.

## Do not test yet

- Real API keys against a paid provider until the deterministic scan looks right.
- Watch mode while the phone is in Doze for hours — first confirm a manual check works.
