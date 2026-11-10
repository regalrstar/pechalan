package dev.triage.app.rules

import dev.triage.app.model.Confidence
import dev.triage.app.model.Artifact
import dev.triage.app.model.Evidence
import dev.triage.app.model.Finding
import dev.triage.app.model.FixTarget
import dev.triage.app.model.Impact
import dev.triage.app.model.InstalledApp
import dev.triage.app.model.PrivateDns
import dev.triage.app.model.ScreenLock
import dev.triage.app.model.Severity
import dev.triage.app.model.WifiSecurity
import dev.triage.app.stalkerware.Fingerprints
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * A rule is a pure function Evidence -> findings. No IO, no Android framework
 * types other than what the collector already normalised. Every rule must be
 * able to return an empty list: "not applicable" is a valid verdict.
 */
fun interface Rule {
	fun evaluate(e: Evidence): List<Finding>
}

object Rules {

	private fun f(
		checkId: String,
		title: String,
		observed: String,
		why: String,
		fix: String,
		severity: Severity,
		confidence: Confidence,
		impact: Impact = Impact.THIS_DEVICE,
		evidence: Map<String, String> = emptyMap(),
		app: InstalledApp? = null,
		target: FixTarget? = null,
	) = Finding(
		checkId = checkId,
		title = title,
		observed = observed,
		why = why,
		fix = fix,
		severity = severity,
		confidence = confidence,
		impact = impact,
		evidence = evidence,
		appLabel = app?.label,
		appPackage = app?.packageName,
		fixTarget = target,
	)

	private fun daysSince(date: String): Long? = runCatching {
		val parsed = LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE)
		ChronoUnit.DAYS.between(parsed, LocalDate.now())
	}.getOrNull()

	private fun names(apps: List<InstalledApp>, limit: Int = 8): String =
		apps.take(limit).joinToString(", ") { it.label }
			.let { if (apps.size > limit) "$it and ${apps.size - limit} more" else it }

	val all: List<Rule> = listOf(

		// ---------- operating system ----------

		Rule { e ->
			val id = "os.patch_level"
			val age = daysSince(e.securityPatch)
			when {
				age == null -> listOf(
					f(
						id, "Security patch level unknown",
						"We couldn't read this Android build's security patch date.",
						"Without a patch date there is no way to tell how far behind the device is.",
						"Check Settings → About phone → Android version. If no patch date is shown, the build is old.",
						Severity.LOW, Confidence.POSSIBLE,
					),
				)
				age > 120 -> listOf(
					f(
						id, "Security updates are ${age} days old",
						"The last Android security patch is dated ${e.securityPatch} — ${age} days ago.",
						"Most Android attacks land on bugs that already have a patch. A four-month gap means the device is missing fixes for known issues.",
						"Settings → System → System update. If it says up to date, the manufacturer has stopped patching this model.",
						Severity.HIGH, Confidence.CONFIRMED,
						evidence = mapOf("patch" to e.securityPatch, "age_days" to age.toString(), "release" to e.release),
						target = FixTarget.SECURITY,
					),
				)
				age > 60 -> listOf(
					f(
						id, "Security updates are ${age} days old",
						"The last Android security patch is dated ${e.securityPatch}.",
						"Two months without a patch is where known, published bugs start to matter.",
						"Settings → System → System update.",
						Severity.MEDIUM, Confidence.CONFIRMED,
						evidence = mapOf("patch" to e.securityPatch, "age_days" to age.toString()),
						target = FixTarget.SECURITY,
					),
				)
				else -> emptyList()
			}
		},

		Rule { e ->
			val id = "os.end_of_life"
			if (e.sdk > 29) emptyList() else listOf(
				f(
					id, "This Android version no longer gets fixes",
					"Android ${e.release} (API ${e.sdk}) is past its support window.",
					"Apps increasingly refuse to run, and new vulnerabilities are not backported. Every month on this version widens the gap.",
					"If a system update is offered, take it. Otherwise the realistic fix is a newer device.",
					Severity.HIGH, Confidence.LIKELY,
					evidence = mapOf("sdk" to e.sdk.toString(), "release" to e.release),
					target = FixTarget.SECURITY,
				),
			)
		},

		Rule { e ->
			val id = "os.test_keys"
			if (!e.buildTags.contains("test-keys")) emptyList() else listOf(
				f(
					id, "Build is signed with test keys",
					"Build tags: ${e.buildTags}.",
					"Test-key builds (common on custom ROMs and some grey imports) cannot verify updates the same way retail builds do.",
					"Confirm the ROM's update channel. If you did not flash it yourself, ask who did.",
					Severity.INFO, Confidence.LIKELY,
					evidence = mapOf("tags" to e.buildTags),
				),
			)
		},

		// ---------- lock & storage ----------

		Rule { e ->
			val id = "lock.missing"
			when (e.screenLock) {
				ScreenLock.NONE -> listOf(
					f(
						id, "No screen lock",
						"Anyone holding the phone can unlock it.",
						"Without a lock, a thief gets the mail, the bank app and the saved passwords in one go. It also weakens encryption-at-rest.",
						"Settings → Security → Screen lock. Use a 6-digit PIN at minimum, ideally with fingerprint or face.",
						Severity.HIGH, Confidence.CONFIRMED, target = FixTarget.SCREEN_LOCK,
					),
				)
				ScreenLock.SWIPE -> listOf(
					f(
						id, "Screen lock is only a swipe",
						"A swipe pattern-less lock is configured.",
						"A swipe stops nothing; it only wakes the screen.",
						"Settings → Security → Screen lock → PIN or password.",
						Severity.MEDIUM, Confidence.LIKELY, target = FixTarget.SCREEN_LOCK,
					),
				)
				else -> emptyList()
			}
		},

		Rule { e ->
			val id = "storage.unencrypted"
			if (e.encrypted) emptyList() else listOf(
				f(
					id, "Storage is not encrypted",
					"The data partition reports as unencrypted.",
					"On an unencrypted device, pulling the flash chip reveals photos, tokens and app data even with a lock screen.",
					"Settings → Security → Encryption. On most modern phones this is on by default; if not, it can require a factory reset.",
					Severity.HIGH, Confidence.CONFIRMED, target = FixTarget.SECURITY,
				),
			)
		},

		// ---------- developer & install posture ----------

		Rule { e ->
			val id = "install.unknown_sources"
			if (!e.unknownSources) emptyList() else listOf(
				f(
					id, "Installing apps from outside the store is allowed",
					"\"Install unknown apps\" is enabled for at least one app.",
					"Every sideloaded app bypasses store review. That is fine when intentional and a common stalkerware route when not.",
					"Settings → Apps → Special access → Install unknown apps. Turn off anything you don't use as a file manager or browser.",
					Severity.MEDIUM, Confidence.POSSIBLE, target = FixTarget.APP_DETAILS,
				),
			)
		},

		Rule { e ->
			val id = "dev.usb_debugging"
			if (!e.usbDebugging) emptyList() else listOf(
				f(
					id, "USB debugging is on",
					"ADB is enabled in Developer options.",
					"Anything you plug into USB can run shell commands on the phone after you tap Allow. A hostile charging cable or laptop gets full access to app data.",
					"If you don't develop, turn it off: Settings → System → Developer options → USB debugging.",
					Severity.LOW, Confidence.CONFIRMED, target = FixTarget.DEVELOPER,
				),
			)
		},

		Rule { e ->
			val id = "dev.enabled"
			if (!e.developerOptions || e.usbDebugging) emptyList() else listOf(
				f(
					id, "Developer options are enabled",
					"The developer menu is switched on (USB debugging is currently off).",
					"Mostly harmless, but it is the switch you need for ADB, mock locations and OEM unlock.",
					"Leave it if you use it; otherwise Settings → System → Developer options → off.",
					Severity.INFO, Confidence.CONFIRMED, target = FixTarget.DEVELOPER,
				),
			)
		},

		Rule { e ->
			val id = "install.verifier"
			if (e.verifierEnabled) emptyList() else listOf(
				f(
					id, "Install-time app scanning is off",
					"Package verifier is disabled, so Play Protect cannot check apps as they install.",
					"Without install-time scanning, a malicious APK gets installed silently and is only caught (maybe) at the next manual scan.",
					"Open Play Store → profile → Play Protect → turn scanning back on.",
					Severity.HIGH, Confidence.LIKELY, target = FixTarget.PLAY_PROTECT,
				),
			)
		},

		Rule { e ->
			val id = "apps.sideloaded"
			if (e.sideloaded.isEmpty()) emptyList() else listOf(
				f(
					id, "Apps installed from outside an app store",
					"${e.sideloaded.size} app(s): ${names(e.sideloaded)}.",
					"Sideloaded apps skip store review and update silently. They are the single most common way stalkerware and scam apps arrive.",
					"Open each app. Keep the ones you recognise and use; uninstall the rest (long-press icon → App info → Uninstall).",
					if (e.sideloaded.size >= 4) Severity.HIGH else Severity.MEDIUM,
					Confidence.CONFIRMED,
					Impact.INSTALLED_APP,
					evidence = mapOf("packages" to e.sideloaded.joinToString(",") { it.packageName }),
					target = FixTarget.APP_DETAILS,
				),
			)
		},

		Rule { e ->
			val id = "apps.old_target_sdk"
			val old = e.apps.filter { !it.system && it.targetSdk in 1..29 }
			if (old.isEmpty()) emptyList() else listOf(
				f(
					id, "Apps built for old Android versions",
					"${old.size} app(s) target Android 10 or older: ${names(old, 5)}.",
					"Old target SDKs opt out of newer sandboxing and permission rules, so these apps keep access they would not be granted today.",
					"Update or replace them. If an app has not been updated in years, check whether you still need it.",
					Severity.LOW, Confidence.LIKELY, Impact.INSTALLED_APP,
					evidence = mapOf("packages" to old.joinToString(",") { it.packageName }),
					target = FixTarget.APP_DETAILS,
				),
			)
		},

		// ---------- known monitoring apps ----------

		Rule { e ->
			val id = "spy.fingerprint"
			e.apps.filter { Fingerprints.isStalkerware(it.packageName, it.label) }.map { app ->
				val family = Fingerprints.isFamily(app.packageName, app.label)
				f(
					"$id:${app.packageName}",
					"${app.label} is a monitoring app",
					"${app.label} (${app.packageName}) matches a known device-monitoring product.",
					"Commercial monitoring apps read messages, location and call logs. If you did not install it, someone else did — with their account, not yours.",
					"Decide whether you want it. To remove: long-press the icon → App info → Uninstall. To check the owner: open the app's web console.",
					when {
						app.unknownInstaller -> Severity.HIGH
						family -> Severity.LOW
						else -> Severity.MEDIUM
					},
					if (app.unknownInstaller) Confidence.CONFIRMED else Confidence.LIKELY,
					Impact.INSTALLED_APP,
					evidence = mapOf(
						"package" to app.packageName,
						"installer" to (app.installer ?: "unknown"),
						"first_install" to SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Date(app.firstInstall)),
					),
					app = app, target = FixTarget.APP_DETAILS,
				)
			}
		},

		Rule { e ->
			val id = "spy.recent_powerful_app"
			e.apps.filter {
				!it.system &&
					System.currentTimeMillis() - it.firstInstall < 30L * 24 * 3600 * 1000 &&
					it.dangerousPermissions.size >= 6
			}.map { app ->
				f(
					"$id:${app.packageName}",
					"${app.label} is new and asks for a lot",
					"Installed recently and granted ${app.dangerousPermissions.size} sensitive permissions.",
					"A fresh app with broad permissions has not earned trust yet. Stalkerware is usually installed this way and then never opened again.",
					"Open it once. If you don't remember installing it, check its Play listing and uninstall if unsure.",
					Severity.LOW, Confidence.POSSIBLE, Impact.INSTALLED_APP,
					app = app, target = FixTarget.APP_DETAILS,
				)
			}
		},

		// ---------- published indicators ----------

		Rule { e ->
			val id = "spy.ioc"
			e.iocHits.groupBy { it.packageName }.map { (pkg, hits) ->
				val app = e.apps.firstOrNull { it.packageName == pkg }
				val artifacts = hits.map { it.artifact }.toSet()
				val family = hits.first().family
				val exact = Artifact.FILE in artifacts
				val signed = Artifact.CERTIFICATE in artifacts
				val label = app?.label ?: pkg
				val observed = buildString {
					append(label)
					append(" matches a published ")
					append(family)
					append(" indicator")
					when {
						exact && signed -> append(" — the installed file is byte-identical to a published sample and signed with the published key")
						exact -> append(" — the installed file is byte-identical to a published sample")
						signed -> append(" — it is signed with the certificate used for published samples")
						else -> append(" by package name; the name can be reused by the real app")
					}
					append(".")
				}
				f(
					"$id:$pkg",
					"Published spyware match: $family",
					observed,
					"This is not an app that merely looks odd. Researchers published it as spyware used against journalists, activists or minorities. A file or certificate match is strong; a package-name match needs a human look.",
					"Keep the app for now — do not delete the evidence. Share the report. If this phone handles work, activism or journalism, send it to a digital-security helpline before changing anything.",
					when {
						exact && signed -> Severity.CRITICAL
						exact || signed -> Severity.HIGH
						else -> Severity.MEDIUM
					},
					if (exact || signed) Confidence.CONFIRMED else Confidence.LIKELY,
					Impact.INSTALLED_APP,
					evidence = mapOf(
						"package" to pkg,
						"family" to family,
						"matched" to artifacts.joinToString(","),
						"source" to hits.first().source,
						"indicators_as_of" to e.indicatorAsOf,
					),
					app = app,
					target = FixTarget.APP_DETAILS,
				)
			}
		},

		// ---------- access surfaces ----------

		Rule { e ->
			val id = "access.accessibility"
			val risky = e.accessibility.filter { !it.system && !Fingerprints.isTrustedAccess(it.packageName, it.label) }
			val trusted = e.accessibility.filter { !it.system && Fingerprints.isTrustedAccess(it.packageName, it.label) }
			buildList {
				if (risky.isNotEmpty()) add(
					f(
						id, "Apps can read the screen",
						"Accessibility access: ${names(risky)}.",
						"Accessibility was built for screen readers, but it lets an app read every screen, tap buttons and capture one-time codes. Nearly every banking trojan and a lot of stalkerware asks for it.",
						"Settings → Accessibility. Turn off anything you don't recognise. Password managers and launchers are usually fine.",
						Severity.HIGH, Confidence.LIKELY, Impact.INSTALLED_APP,
						evidence = mapOf("packages" to risky.joinToString(",") { it.packageName }),
						target = FixTarget.ACCESSIBILITY,
					),
				)
				if (trusted.isNotEmpty()) add(
					f(
						"$id.trusted", "Accessibility used by known apps",
						"${names(trusted)} — expected for this category.",
						"Listed so the list above is complete. No action unless you stopped using the app.",
						"Nothing to do.",
						Severity.INFO, Confidence.CONFIRMED, Impact.INSTALLED_APP,
						target = FixTarget.ACCESSIBILITY,
					),
				)
			}
		},

		Rule { e ->
			val id = "access.device_admin"
			val admins = e.deviceAdmins.filter { !it.system }
			if (admins.isEmpty()) emptyList() else listOf(
				f(
					id, "Apps can administer the device",
					"Device admin: ${names(admins)}.",
					"Device admins can lock or wipe the phone and change the screen lock. Useful for find-my-phone; dangerous when it is an app you don't know.",
					"Settings → Security → Device admin apps. Disable entries you don't recognise.",
					Severity.MEDIUM, Confidence.LIKELY, Impact.INSTALLED_APP,
					evidence = mapOf("packages" to admins.joinToString(",") { it.packageName }),
					target = FixTarget.DEVICE_ADMIN,
				),
			)
		},

		Rule { e ->
			val id = "access.notification_listener"
			val listeners = e.notificationListeners
				.filter { !it.system && !Fingerprints.isTrustedAccess(it.packageName, it.label) }
			if (listeners.isEmpty()) emptyList() else listOf(
				f(
					id, "Apps can read every notification",
					"Notification access: ${names(listeners)}.",
					"Notifications carry message previews, two-factor codes and bank alerts. Access survives reboots and is easy to forget.",
					"Settings → Notifications → Device & app notifications. Revoke anything unfamiliar.",
					Severity.MEDIUM, Confidence.LIKELY, Impact.INSTALLED_APP,
					evidence = mapOf("packages" to listeners.joinToString(",") { it.packageName }),
					target = FixTarget.NOTIFICATION_ACCESS,
				),
			)
		},

		Rule { e ->
			val id = "access.overlay"
			val overlays = e.overlayApps
				.filter { !it.system && !Fingerprints.isTrustedAccess(it.packageName, it.label) }
			if (overlays.isEmpty()) emptyList() else listOf(
				f(
					id, "Apps can draw over other apps",
					"Display-over-other-apps: ${names(overlays)}.",
					"An overlay can put a fake login box on top of a real app and collect what you type (tapjacking).",
					"Settings → Apps → Special access → Display over other apps. Revoke what you don't need.",
					Severity.LOW, Confidence.LIKELY, Impact.INSTALLED_APP,
					evidence = mapOf("packages" to overlays.joinToString(",") { it.packageName }),
					target = FixTarget.OVERLAY,
				),
			)
		},

		Rule { e ->
			val id = "access.usage"
			val usage = e.usageAccess.filter { !it.system && !Fingerprints.isTrustedAccess(it.packageName, it.label) }
			if (usage.isEmpty()) emptyList() else listOf(
				f(
					id, "Apps can see what you open and when",
					"Usage access: ${names(usage)}.",
					"Usage stats reveal your daily routine: which apps, what time, how long. Combined with location it is a decent behavioural profile.",
					"Settings → Apps → Special access → Usage access. Revoke anything unfamiliar.",
					Severity.LOW, Confidence.LIKELY, Impact.INSTALLED_APP,
					evidence = mapOf("packages" to usage.joinToString(",") { it.packageName }),
					target = FixTarget.USAGE_ACCESS,
				),
			)
		},

		Rule { e ->
			val id = "access.combo"
			val combos = e.apps.filter { app ->
				!app.system &&
					app.dangerousPermissions.contains(android.Manifest.permission.RECEIVE_SMS) &&
					(Fingerprints.isStalkerware(app.packageName, app.label) ||
						e.accessibility.any { it.packageName == app.packageName })
			}
			if (combos.isEmpty()) emptyList() else listOf(
				f(
					id, "Apps can read SMS and the screen at once",
					names(combos) + " can both receive texts and observe the screen.",
					"Together that covers two-factor codes, password resets and everything you type. This is the classic account-takeover combination.",
					"Re-check these apps: do you use them, and does the publisher still ship updates?",
					Severity.HIGH, Confidence.LIKELY, Impact.INSTALLED_APP,
					evidence = mapOf("packages" to combos.joinToString(",") { it.packageName }),
					target = FixTarget.APP_DETAILS,
				),
			)
		},

		// ---------- network & interception ----------

		Rule { e ->
			val id = "net.user_ca"
			if (e.userCaCount == 0) emptyList() else listOf(
				f(
					id, "Custom certificates are installed",
					"${e.userCaCount} user-installed CA certificate(s).",
					"A custom CA lets whoever owns the matching key read your TLS traffic. Expected on a work phone or with a debugging proxy; odd on a personal one.",
					"Settings → Security → Encryption & credentials → Trusted credentials → User. Remove anything you can't attribute.",
					Severity.MEDIUM, Confidence.LIKELY, target = FixTarget.CERTIFICATES,
				),
			)
		},

		Rule { e ->
			val id = "net.proxy"
			if (e.proxyHost == null) emptyList() else listOf(
				f(
					id, "A proxy is configured",
					"Global HTTP proxy: ${e.proxyHost}.",
					"All app traffic is routed through that host, which sees the destinations (and the contents of anything not pinned).",
					"Settings → Network → Private DNS/Proxy, or long-press the Wi-Fi network → Modify → Advanced → Proxy → None.",
					Severity.MEDIUM, Confidence.CONFIRMED, target = FixTarget.WIFI,
					evidence = mapOf("proxy" to e.proxyHost),
				),
			)
		},

		Rule { e ->
			val id = "net.private_dns"
			if (e.privateDns != PrivateDns.OFF) emptyList() else listOf(
				f(
					id, "Private DNS is off",
					"DNS lookups go out unencrypted to whatever the network hands you.",
					"The network — café, hotel, mobile carrier — sees every domain you visit, and can answer with a fake address.",
					"Settings → Network → Private DNS → Automatic (or a hostname such as one.one.one.one).",
					Severity.LOW, Confidence.CONFIRMED, target = FixTarget.WIFI,
				),
			)
		},

		Rule { e ->
			val id = "net.wifi"
			val w = e.wifi ?: return@Rule emptyList()
			val (sev, conf, text) = when (w.security) {
				WifiSecurity.OPEN ->
					Triple(Severity.HIGH, Confidence.CONFIRMED, "It is open: no password, so anyone nearby can join and sniff.")
				WifiSecurity.WEP ->
					Triple(Severity.HIGH, Confidence.CONFIRMED, "WEP is trivially broken; a neighbour can decrypt the traffic.")
				WifiSecurity.WPA2 ->
					Triple(Severity.LOW, Confidence.CONFIRMED, "WPA2 is acceptable but older than WPA3.")
				else -> return@Rule emptyList()
			}
			listOf(
				f(
					id, "Wi-Fi \"${w.ssid}\" is ${w.security.name}",
					"Connected to ${w.ssid} using ${w.security.name}.",
					text,
					"For open networks: use a VPN, or avoid banking on them. For WEP: change the router to WPA2/WPA3.",
					sev, conf, Impact.NETWORK, target = FixTarget.WIFI,
				),
			)
		},

		Rule { e ->
			val id = "net.vpn"
			val vpns = e.vpns.filter { !it.system }
			if (vpns.isEmpty()) emptyList() else listOf(
				f(
					id, "A VPN app is active",
					"${names(vpns)} holds an active VPN session.",
					"Everything leaves through that app. That is good if you chose it, and a full traffic view for someone else if you didn't.",
					"Settings → Network → VPN. Check which app is connected; remove profiles you don't recognise.",
					Severity.INFO, Confidence.CONFIRMED, Impact.NETWORK,
					evidence = mapOf("packages" to vpns.joinToString(",") { it.packageName }),
					target = FixTarget.VPN,
				),
			)
		},
	)
}
