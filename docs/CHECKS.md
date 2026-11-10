# Check catalog (v0.1)

Every check is deterministic and read-only. `Confidence` is part of the output so
the UI can be honest: `CONFIRMED` = an API fact, `LIKELY` = a strong convention,
`POSSIBLE` = a heuristic.

| id | Signal | Sev | Conf | Platform | False positives / notes |
|---|---|---|---|---|---|
| `os.patch_level` | `Build.VERSION.SECURITY_PATCH` older than 60/120 days | MED/HIGH | CONFIRMED | Android | Grey imports and some OEMs publish stale dates despite shipping fixes. Unknown date is reported separately as LOW. |
| `os.end_of_life` | API ≤ 29 | HIGH | LIKELY | Android | Backported ROMs may still patch; the base claim (vendor support ended) holds. |
| `os.test_keys` | `Build.TAGS` contains `test-keys` | INFO | LIKELY | Android | Normal on custom ROMs; not a problem by itself. |
| `lock.missing` | `KeyguardManager.isDeviceSecure == false` | HIGH | CONFIRMED | Android | A paired smartwatch can keep the phone unlocked; still worth a lock. |
| `lock.swipe` | reserved, currently folded into `lock.missing` | MED | LIKELY | Android | `isDeviceSecure` cannot distinguish swipe from none on all OEMs. |
| `storage.unencrypted` | `StorageManager.isEncrypted` | HIGH | CONFIRMED | Android | Always true on API 29+ (FBE mandatory); kept for completeness. |
| `install.unknown_sources` | install-unknown-apps enabled | MED | POSSIBLE | Android | Modern Android grants this per source app; the global secure flag is legacy. |
| `dev.usb_debugging` | `adb_enabled` | LOW | CONFIRMED | Android | Developers keep it on deliberately. |
| `dev.enabled` | `development_settings_enabled`, ADB off | INFO | CONFIRMED | Android | Purely informational. |
| `install.verifier` | `package_verifier_enable == 0` | HIGH | LIKELY | Android | Some OEMs and custom ROMs ship it off; Play Protect UI is the real control. |
| `apps.sideloaded` | non-system app with null/unknown installer | MED/HIGH | CONFIRMED | Android | Includes apps installed by ADB (e.g. our own debug build) and by file managers. |
| `apps.old_target_sdk` | non-system app targeting API ≤ 29 | LOW | LIKELY | Android | Old but maintained apps still exist; the sandbox claim is factual. |
| `spy.fingerprint` | package/label substring match on the stalkerware corpus | MED/HIGH | CONFIRMED/LIKELY | Android | Renamed or white-labelled builds miss. Unknown installer raises severity. |
| `spy.ioc` (package) | app id equals a published spyware package name | MED | LIKELY | Android | A real app's package name can be reused by the imitator. |
| `spy.ioc` (certificate) | signing-certificate SHA-256/SHA-1 is published | HIGH | CONFIRMED | Android | Strong: the attacker's signing key. One app can legitimately carry several signers. |
| `spy.ioc` (file) | APK SHA-256/SHA-1 equals a published sample | HIGH (CRITICAL with cert) | CONFIRMED | Android | Byte-identical. Split APKs are hashed individually. |
| `spy.recent_powerful_app` | non-system, first install < 30 d, ≥ 6 dangerous perms | LOW | POSSIBLE | Android | A fresh legitimate app trips it; that is the point. |
| `access.accessibility` | non-system accessibility service, not on the trusted list | HIGH | LIKELY | Android | Trusted list covers password managers, wearables, launchers, OEM tools. New accessibility apps are the top stalkerware signal. |
| `access.device_admin` | non-system active admin | MED | LIKELY | Android | Find-my-phone apps legitimately hold admin. |
| `access.notification_listener` | non-system listener, not trusted | MED | LIKELY | Android | Watch/Auto/2FA apps are on the trusted list. |
| `access.overlay` | AppOps `system_alert_window` allowed, not trusted | LOW | LIKELY | Android | Chat heads and floating widgets are common and benign. |
| `access.usage` | AppOps `get_usage_stats` allowed, not trusted | LOW | LIKELY | Android | Digital-wellbeing tools overlap. |
| `access.combo` | app has RECEIVE_SMS *and* is accessibility-capable or a stalkerware fingerprint | HIGH | LIKELY | Android | The single strongest capability combination for account takeover. |
| `net.user_ca` | any `user:` certificate in the Android CA store | MED | LIKELY | Android | Corporate MDM, Charles/mitmproxy, some VPNs. |
| `net.proxy` | global `http_proxy` set | MED | CONFIRMED | Android | Dev setups and corporate profiles. |
| `net.private_dns` | `private_dns_mode == off` | LOW | CONFIRMED | Android | Some carriers break Private DNS; "Automatic" is a safe middle. |
| `net.wifi` | connected Wi-Fi is OPEN or WEP | HIGH | CONFIRMED | Android | Captive portals behind "open" networks are normal; the risk is real. |
| `net.vpn` | active non-system VPN | INFO | CONFIRMED | Android | Usually desired; reported for completeness and for "who chose this?". |
| `net.hotspot` | reserved, collected but not scored | INFO | — | Android | Reflection-based; noisy across OEMs. |

## Coverage gaps (always reported)

`gap.intrusion`, `gap.install_history`, `gap.cellebrite`, `gap.sms`, `gap.network`,
plus dynamic `ioc.budget` / `ioc.unreadable`. See `Engine.permanentGaps()`. These
are not findings and never change the score; they bound what a clean result means.

## Rule vs AI findings

Everything above is deterministic and decides the score. The optional deep scan
adds **separate** `aiFindings` (`origin = AI`); they are never scored and can be
disabled entirely. See [AI.md](AI.md).

## Not checked yet (ranked by value)

1. Play Protect real status (needs Play Integrity / GMS APIs).
2. Hidden launcher icons.
3. Accessibility service *capabilities* (`canRetrieveWindowContent`).
4. Battery-optimisation exemption on monitoring packages.
5. Profile / MDM / supervision state (iOS first, Android device-owner mode later).
6. Local open ports and mDNS exposure.
7. Stalkerware account activity via vendor consoles (needs network).
8. AndroidQF collection import — closes the four shell-only gaps without root.
