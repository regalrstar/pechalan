package dev.triage.app.model

import kotlinx.serialization.Serializable
import dev.triage.app.ioc.IndicatorStore

/**
 * Deterministic severity. Weights are the score cost of a CONFIRMED finding;
 * [Confidence] scales them down.
 */
@Serializable
enum class Severity(val weight: Int, val label: String) {
	INFO(1, "Note"),
	LOW(3, "Low"),
	MEDIUM(8, "Medium"),
	HIGH(15, "High"),
	CRITICAL(25, "Critical"),
}

/**
 * How sure we are. A package-name match is CONFIRMED; a heuristic such as
 * "has accessibility + SMS" is LIKELY; "looks odd" is POSSIBLE.
 */
@Serializable
enum class Confidence(val factor: Float) {
	CONFIRMED(1.0f),
	LIKELY(0.85f),
	POSSIBLE(0.6f),
}

/** What the finding is actually about, so the UI can say "this phone" vs "an app you installed". */
@Serializable
enum class Impact(val label: String) {
	THIS_DEVICE("This device"),
	INSTALLED_APP("An app you installed"),
	ACCOUNT("An account"),
	NETWORK("Your network"),
}

/** A settings page we can deep-link to. Never required: every fix also has text steps. */
@Serializable
enum class FixTarget {
	APP_DETAILS,
	SECURITY,
	DEVELOPER,
	ACCESSIBILITY,
	DEVICE_ADMIN,
	NOTIFICATION_ACCESS,
	USAGE_ACCESS,
	OVERLAY,
	VPN,
	WIFI,
	PLAY_PROTECT,
	CERTIFICATES,
	SCREEN_LOCK,
}

/** Who produced a finding. AI findings never overwrite rule findings or the score. */
@Serializable
enum class Origin { RULES, AI }

@Serializable
data class Finding(
	val checkId: String,
	val title: String,
	/** What we literally observed, in plain language. */
	val observed: String,
	/** Why a normal person should care. */
	val why: String,
	/** Ordered, always-available instructions. */
	val fix: String,
	val severity: Severity,
	val confidence: Confidence,
	val impact: Impact,
	val evidence: Map<String, String> = emptyMap(),
	val appLabel: String? = null,
	val appPackage: String? = null,
	val fixTarget: FixTarget? = null,
	val origin: Origin = Origin.RULES,
) {
	val cost: Int get() = (severity.weight * confidence.factor).toInt().coerceAtLeast(1)
}

@Serializable
data class Report(
	val schema: Int = 1,
	val startedAt: Long,
	val finishedAt: Long,
	val score: Int,
	val findings: List<Finding>,
	val checksRun: Int,
	val deviceLabel: String,
	val patchLevel: String,
	val androidRelease: String,
	val collectorErrors: List<String> = emptyList(),
	val gaps: List<Gap> = emptyList(),
	val indicatorAsOf: String = "",
	val indicatorCount: Int = 0,
	val training: Boolean = false,
	val aiFindings: List<Finding> = emptyList(),
	val aiMeta: AiMeta? = null,
	val aiError: String? = null,
) {
	val criticalCount: Int get() = findings.count { it.severity == Severity.CRITICAL }
	val highCount: Int get() = findings.count { it.severity == Severity.HIGH }
}

/** A place the scan could not look. Always shown; a clean result is only as good as its gaps. */
@Serializable
data class Gap(val id: String, val text: String)

/** Provenance of an AI deep scan. Advisory only: the rule score is unchanged. */
@Serializable
data class AiMeta(
	val provider: String,
	val model: String,
	val steps: Int,
	val tokens: Int,
	val at: Long,
	val note: String = "",
)

/** One published indicator matched against an installed app. */
data class IocHit(
	val packageName: String,
	val artifact: Artifact,
	val kind: IndicatorStore.Kind,
	val family: String,
	val source: String,
	val value: String,
)

enum class Artifact { PACKAGE, CERTIFICATE, FILE }

/** Everything the rules are allowed to see. Nothing here leaves the device by default. */
data class Evidence(
	val sdk: Int,
	val release: String,
	val securityPatch: String,
	val deviceModel: String,
	val buildTags: String,
	val screenLock: ScreenLock,
	val encrypted: Boolean,
	val developerOptions: Boolean,
	val usbDebugging: Boolean,
	val unknownSources: Boolean,
	val verifierEnabled: Boolean,
	val sideloaded: List<InstalledApp>,
	val apps: List<InstalledApp>,
	val accessibility: List<InstalledApp>,
	val deviceAdmins: List<InstalledApp>,
	val notificationListeners: List<InstalledApp>,
	val overlayApps: List<InstalledApp>,
	val usageAccess: List<InstalledApp>,
	val vpns: List<InstalledApp>,
	val userCaCount: Int,
	val proxyHost: String?,
	val privateDns: PrivateDns,
	val wifi: WifiState?,
	val hotspotOn: Boolean,
	val collectorErrors: List<String>,
	val gaps: List<Gap> = emptyList(),
	val iocHits: List<IocHit> = emptyList(),
	val indicatorAsOf: String = "",
	val indicatorCount: Int = 0,
)

enum class ScreenLock { NONE, SWIPE, WEAK, STRONG, BIOMETRIC }
enum class PrivateDns { OFF, OPPORTUNISTIC, HOSTNAME }
enum class WifiSecurity { OPEN, WEP, WPA2, WPA3, ENTERPRISE, UNKNOWN }

data class WifiState(val ssid: String?, val security: WifiSecurity)

/**
 * Minimal app record. Deliberately no paths, no account names, no file lists
 * unless a rule asks for them.
 */
data class InstalledApp(
	val packageName: String,
	val label: String,
	val system: Boolean,
	val installer: String?,
	val targetSdk: Int,
	val dangerousPermissions: List<String>,
	val hasLauncher: Boolean,
	val firstInstall: Long,
	val updated: Long,
) {
	val sideloaded: Boolean
		get() = !system && installer !in KNOWN_STORES

	val unknownInstaller: Boolean
		get() = !system && (installer == null || installer !in KNOWN_INSTALLERS)

	companion object {
		const val PLAY_STORE = "com.android.vending"
		val KNOWN_STORES = setOf(
			PLAY_STORE,
			"com.samsung.android.galaxyapps",
			"com.amazon.venezia",
			"com.huawei.appmarket",
			"com.xiaomi.mipicks",
			"com.oppo.market",
			"com.heytap.market",
			"com.bbk.appstore",
		)
		val KNOWN_INSTALLERS = KNOWN_STORES + setOf("com.google.android.packageinstaller", "com.android.packageinstaller")
	}
}
