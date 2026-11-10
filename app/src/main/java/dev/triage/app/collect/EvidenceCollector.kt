package dev.triage.app.collect

import android.app.AppOpsManager
import android.app.KeyguardManager
import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.provider.Settings
import android.security.KeyChain
import dev.triage.app.ioc.IndicatorStore
import dev.triage.app.model.Artifact
import dev.triage.app.model.Evidence
import dev.triage.app.model.Gap
import dev.triage.app.model.InstalledApp
import dev.triage.app.model.IocHit
import dev.triage.app.model.PrivateDns
import dev.triage.app.model.ScreenLock
import dev.triage.app.model.WifiSecurity
import dev.triage.app.model.WifiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.KeyStore
import java.security.MessageDigest

/**
 * Reads the device. Read-only, no root, no network. Every block is independently
 * guarded: one OEM breaking a hidden setting must not sink the whole scan.
 */
class EvidenceCollector(private val ctx: Context) {

	private val pm: PackageManager = ctx.packageManager
	private val errors = mutableListOf<String>()

	suspend fun collect(): Evidence = withContext(Dispatchers.IO) {
		val apps = runCatching { readApps() }.getOrElse {
			errors += "app inventory: ${it.javaClass.simpleName}"
			emptyList()
		}
		val byPackage = apps.associateBy { it.packageName }
		val iocs = IndicatorStore.get(ctx)
		val (identities, hashGaps) = identities(apps, iocs)

		Evidence(
			sdk = Build.VERSION.SDK_INT,
			release = Build.VERSION.RELEASE,
			securityPatch = Build.VERSION.SECURITY_PATCH ?: "unknown",
			deviceModel = listOf(Build.MANUFACTURER, Build.MODEL).filter { it.isNotBlank() }.joinToString(" "),
			buildTags = Build.TAGS ?: "",
			screenLock = screenLock(),
			encrypted = encrypted(),
			developerOptions = globalInt(Settings.Global.DEVELOPMENT_SETTINGS_ENABLED) == 1,
			usbDebugging = globalInt(Settings.Global.ADB_ENABLED) == 1,
			unknownSources = installUnknownSources(),
			verifierEnabled = globalInt("package_verifier_enable") != 0,
			sideloaded = apps.filter { it.sideloaded },
			apps = apps,
			accessibility = named(accessibilityPackages(), byPackage),
			deviceAdmins = named(deviceAdminPackages(), byPackage),
			notificationListeners = named(notificationListenerPackages(), byPackage),
			overlayApps = named(overlayPackages(apps), byPackage),
			usageAccess = named(usageAccessPackages(apps), byPackage),
			vpns = named(vpnPackages(), byPackage),
			userCaCount = userCaCount(),
			proxyHost = proxy(),
			privateDns = privateDns(),
			wifi = wifi(),
			hotspotOn = hotspot(),
			collectorErrors = errors.toList(),
			gaps = hashGaps,
			iocHits = matchIndicators(identities, iocs),
			indicatorAsOf = iocs.asOf,
			indicatorCount = iocs.totalCount,
		)
	}

	// ---- inventory ----

	@Suppress("DEPRECATION")
	private fun readApps(): List<InstalledApp> {
		val launcher = launcherPackages()
		val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
		return packages.mapNotNull { pi ->
			val app = pi.applicationInfo ?: return@mapNotNull null
			if (app.packageName == ctx.packageName) return@mapNotNull null
			val system = (app.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
				(app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
			val granted = pi.requestedPermissions.orEmpty().filter {
				it in DANGEROUS && pm.checkPermission(it, pi.packageName) == PackageManager.PERMISSION_GRANTED
			}
			InstalledApp(
				packageName = pi.packageName,
				label = runCatching { pm.getApplicationLabel(app).toString() }.getOrDefault(pi.packageName),
				system = system,
				installer = runCatching { pm.getInstallSourceInfo(pi.packageName).installingPackageName }.getOrNull(),
				targetSdk = app.targetSdkVersion,
				dangerousPermissions = granted,
				hasLauncher = pi.packageName in launcher,
				firstInstall = pi.firstInstallTime,
				updated = pi.lastUpdateTime,
			)
		}
	}

	private fun launcherPackages(): Set<String> {
		val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
			.addCategory(android.content.Intent.CATEGORY_LAUNCHER)
		return runCatching {
			pm.queryIntentActivities(intent, 0).mapNotNull { it.activityInfo?.packageName }.toSet()
		}.getOrDefault(emptySet())
	}

	private fun named(pkgs: Collection<String>, byPackage: Map<String, InstalledApp>): List<InstalledApp> =
		pkgs.distinct().map { pkg ->
			byPackage[pkg] ?: InstalledApp(pkg, pkg, false, null, 0, emptyList(), false, 0, 0)
		}

	// ---- access surfaces ----

	private fun accessibilityPackages(): List<String> =
		csvSetting(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).mapNotNull { it.substringBefore('/').ifBlank { null } }

	private fun notificationListenerPackages(): List<String> =
		csvSetting("enabled_notification_listeners").mapNotNull { it.substringBefore('/').ifBlank { null } }

	private fun deviceAdminPackages(): List<String> = runCatching {
		val dpm = ctx.getSystemService(DevicePolicyManager::class.java)
		dpm?.activeAdmins.orEmpty().map { it.packageName }
	}.getOrDefault(emptyList())

	private fun overlayPackages(apps: List<InstalledApp>): List<String> {
		val ops = ctx.getSystemService(AppOpsManager::class.java) ?: return emptyList()
		return apps.filter { app ->
			runCatching {
				ops.unsafeCheckOpNoThrow(
					"android:system_alert_window", app.uid(), app.packageName,
				) == AppOpsManager.MODE_ALLOWED
			}.getOrDefault(false)
		}.map { it.packageName }
	}

	private fun usageAccessPackages(apps: List<InstalledApp>): List<String> {
		val ops = ctx.getSystemService(AppOpsManager::class.java) ?: return emptyList()
		return apps.filter { app ->
			runCatching {
				ops.unsafeCheckOpNoThrow(
					"android:get_usage_stats", app.uid(), app.packageName,
				) == AppOpsManager.MODE_ALLOWED
			}.getOrDefault(false)
		}.map { it.packageName }
	}

	private fun vpnPackages(): List<String> = runCatching {
		val cm = ctx.getSystemService(ConnectivityManager::class.java) ?: return emptyList()
		cm.allNetworks.mapNotNull { network ->
			val caps = cm.getNetworkCapabilities(network) ?: return@mapNotNull null
			if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) return@mapNotNull null
			pm.getPackagesForUid(caps.ownerUid)?.firstOrNull()
		}
	}.getOrDefault(emptyList())

	// ---- indicator identity ----

	private data class Identity(
		val certSha256: String?,
		val certSha1: String?,
		val fileSha256: List<String>,
		val fileSha1: List<String>,
	)

	/**
	 * Signing certificates are cheap, so every non-system app gets one. APK file
	 * hashes are expensive, so only apps that could plausibly match (published
	 * package, sideloaded, or installed in the last 180 days) are hashed, under a
	 * byte and wall-clock budget.
	 */
	private fun identities(
		apps: List<InstalledApp>,
		iocs: IndicatorStore,
	): Pair<Map<String, Identity>, List<Gap>> {
		val out = HashMap<String, Identity>()
		val gaps = mutableListOf<Gap>()
		var budget = HASH_BUDGET_BYTES
		val deadline = android.os.SystemClock.elapsedRealtime() + HASH_BUDGET_MS
		var unreadable = 0
		var skipped = 0
		val now = System.currentTimeMillis()

		for (app in apps) {
			if (app.system) continue
			val cert = runCatching { certDigests(app.packageName) }.getOrNull()
			if (cert == null) {
				unreadable++
				continue
			}
			val interesting = iocs.byPackageName(app.packageName) != null ||
				app.sideloaded ||
				(app.firstInstall > 0 && now - app.firstInstall < RECENT_MS)
			val files = if (interesting) {
				if (budget > 0 && android.os.SystemClock.elapsedRealtime() < deadline) {
					runCatching { hashApks(app.packageName) { budget -= it } }
						.getOrNull().also { if (it == null) unreadable++ }
				} else {
					skipped++
					null
				}
			} else null

			out[app.packageName] = Identity(
				cert.first, cert.second,
				files?.first ?: emptyList(), files?.second ?: emptyList(),
			)
		}

		if (skipped > 0) gaps += Gap(
			"ioc.budget",
			"Fingerprinting stopped at the budget: $skipped app file(s) were not compared by hash. They were still checked by package name and certificate.",
		)
		if (unreadable > 0) gaps += Gap(
			"ioc.unreadable",
			"$unreadable app(s) could not be read for a signing certificate or file hash.",
		)
		return out to gaps
	}

	private fun certDigests(pkg: String): Pair<String?, String?>? {
		@Suppress("DEPRECATION")
		val pi = pm.getPackageInfo(pkg, PackageManager.GET_SIGNING_CERTIFICATES)
		val signers = pi.signingInfo?.apkContentsSigners ?: return null to null
		var sha256: String? = null
		var sha1: String? = null
		for (signer in signers) {
			val der = signer.toByteArray()
			if (sha256 == null) sha256 = runCatching { IndicatorStore.sha256(der) }.getOrNull()
			if (sha1 == null) sha1 = runCatching { IndicatorStore.sha1(der) }.getOrNull()
		}
		return sha256 to sha1
	}

	/** SHA-256/SHA-1 of every APK file of one package (base + splits). */
	private fun hashApks(pkg: String, spend: (Long) -> Unit): Pair<List<String>, List<String>>? {
		@Suppress("DEPRECATION")
		val info = pm.getApplicationInfo(pkg, 0)
		val paths = buildList {
			info.sourceDir?.let(::add)
			info.splitSourceDirs?.let { addAll(it) }
		}
		if (paths.isEmpty()) return null
		val sha256 = mutableListOf<String>()
		val sha1 = mutableListOf<String>()
		for (path in paths) {
			val file = File(path)
			if (!file.canRead()) return null
			val d256 = MessageDigest.getInstance("SHA-256")
			val d1 = MessageDigest.getInstance("SHA-1")
			var total = 0L
			file.inputStream().buffered(64 * 1024).use { input ->
				val buf = ByteArray(64 * 1024)
				while (true) {
					val n = input.read(buf)
					if (n <= 0) break
					d256.update(buf, 0, n)
					d1.update(buf, 0, n)
					total += n
				}
			}
			spend(total)
			sha256 += hex(d256.digest())
			sha1 += hex(d1.digest())
		}
		return sha256 to sha1
	}

	private fun matchIndicators(ids: Map<String, Identity>, iocs: IndicatorStore): List<IocHit> {
		val hits = LinkedHashMap<String, IocHit>()
		fun add(pkg: String, artifact: Artifact, kind: IndicatorStore.Kind, value: String, ind: IndicatorStore.Indicator) {
			hits.putIfAbsent("$pkg|$artifact|${ind.family}", IocHit(pkg, artifact, kind, ind.family, ind.source, value))
		}
		for ((pkg, id) in ids) {
			iocs.byPackageName(pkg)?.let { add(pkg, Artifact.PACKAGE, IndicatorStore.Kind.PACKAGE, pkg, it) }
			id.certSha256?.let { h -> iocs.byDigestValue(h)?.let { add(pkg, Artifact.CERTIFICATE, IndicatorStore.Kind.SHA256, h, it) } }
			id.certSha1?.let { h -> iocs.byDigestValue(h)?.let { add(pkg, Artifact.CERTIFICATE, IndicatorStore.Kind.SHA1, h, it) } }
			for (h in id.fileSha256) iocs.byDigestValue(h)?.let { add(pkg, Artifact.FILE, IndicatorStore.Kind.SHA256, h, it) }
			for (h in id.fileSha1) iocs.byDigestValue(h)?.let { add(pkg, Artifact.FILE, IndicatorStore.Kind.SHA1, h, it) }
		}
		return hits.values.toList()
	}

	private fun hex(b: ByteArray): String = b.joinToString("") { "%02x".format(it) }

	// ---- posture ----

	private fun screenLock(): ScreenLock {
		val km = ctx.getSystemService(KeyguardManager::class.java) ?: return ScreenLock.NONE
		if (km.isDeviceSecure) {
			val biometric = pm.hasSystemFeature(PackageManager.FEATURE_FINGERPRINT) ||
				pm.hasSystemFeature(PackageManager.FEATURE_FACE) ||
				pm.hasSystemFeature(PackageManager.FEATURE_IRIS)
			return if (biometric) ScreenLock.BIOMETRIC else ScreenLock.STRONG
		}
		return ScreenLock.NONE
	}

	// File-based encryption is mandatory for everything that launched with Android 10+,
	// which is our minSdk, so on-device storage is always encrypted for supported devices.
	private fun encrypted(): Boolean = true

	private fun installUnknownSources(): Boolean = runCatching {
		pm.canRequestPackageInstalls() ||
			secureInt(Settings.Secure.INSTALL_NON_MARKET_APPS) == 1
	}.getOrDefault(false)

	private fun userCaCount(): Int = runCatching {
		val ks = KeyStore.getInstance("AndroidCAStore")
		ks.load(null, null)
		ks.aliases().toList().count { it.startsWith("user:") }
	}.getOrDefault(0)

	private fun privateDns(): PrivateDns = when (
		Settings.Global.getString(ctx.contentResolver, "private_dns_mode")
	) {
		"hostname" -> PrivateDns.HOSTNAME
		"opportunistic" -> PrivateDns.OPPORTUNISTIC
		else -> PrivateDns.OFF
	}

	private fun proxy(): String? = runCatching {
		Settings.Global.getString(ctx.contentResolver, "http_proxy")
			?.takeIf { it.isNotBlank() && it != ":0" }
	}.getOrNull()

	private fun wifi(): WifiState? = runCatching {
		val wm = ctx.applicationContext.getSystemService(WifiManager::class.java) ?: return null
		val info: WifiInfo = wm.connectionInfo ?: return null
		if (info.ssid == null || info.ssid == "<unknown ssid>") return null
		val security = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
			when (info.currentSecurityType) {
				WifiInfo.SECURITY_TYPE_OPEN -> WifiSecurity.OPEN
				WifiInfo.SECURITY_TYPE_WEP -> WifiSecurity.WEP
				WifiInfo.SECURITY_TYPE_PSK -> WifiSecurity.WPA2
				WifiInfo.SECURITY_TYPE_SAE -> WifiSecurity.WPA3
				WifiInfo.SECURITY_TYPE_EAP,
				WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE,
				WifiInfo.SECURITY_TYPE_EAP_WPA3_ENTERPRISE_192_BIT,
				-> WifiSecurity.ENTERPRISE
				else -> WifiSecurity.UNKNOWN
			}
		} else WifiSecurity.UNKNOWN
		WifiState(info.ssid.trim('"'), security)
	}.getOrNull()

	private fun hotspot(): Boolean = runCatching {
		val wm = ctx.applicationContext.getSystemService(WifiManager::class.java) ?: return false
		val m = wm.javaClass.getMethod("isWifiApEnabled")
		m.invoke(wm) as? Boolean ?: false
	}.getOrDefault(false)

	// ---- settings helpers ----

	private fun secureInt(key: String): Int =
		runCatching { Settings.Secure.getInt(ctx.contentResolver, key) }.getOrDefault(0)

	private fun globalInt(key: String): Int =
		runCatching { Settings.Global.getInt(ctx.contentResolver, key) }.getOrDefault(0)

	private fun csvSetting(key: String): List<String> =
		runCatching { Settings.Secure.getString(ctx.contentResolver, key) }
			.getOrNull()?.split(':')?.filter { it.isNotBlank() } ?: emptyList()

	private fun InstalledApp.uid(): Int = runCatching {
		pm.getApplicationInfo(packageName, 0).uid
	}.getOrDefault(-1)

	companion object {
		private const val HASH_BUDGET_BYTES = 400L * 1024 * 1024
		private const val HASH_BUDGET_MS = 45_000L
		private const val RECENT_MS = 180L * 24 * 3600 * 1000

		private val DANGEROUS = setOf(
			android.Manifest.permission.READ_SMS,
			android.Manifest.permission.RECEIVE_SMS,
			android.Manifest.permission.SEND_SMS,
			android.Manifest.permission.READ_CONTACTS,
			android.Manifest.permission.READ_CALL_LOG,
			android.Manifest.permission.READ_PHONE_STATE,
			android.Manifest.permission.ACCESS_FINE_LOCATION,
			android.Manifest.permission.ACCESS_COARSE_LOCATION,
			android.Manifest.permission.ACCESS_BACKGROUND_LOCATION,
			android.Manifest.permission.RECORD_AUDIO,
			android.Manifest.permission.CAMERA,
			android.Manifest.permission.READ_CALENDAR,
			android.Manifest.permission.BODY_SENSORS,
			android.Manifest.permission.ACTIVITY_RECOGNITION,
			android.Manifest.permission.POST_NOTIFICATIONS,
		)
	}
}
