package dev.triage.app.ai

import dev.triage.app.model.Evidence
import dev.triage.app.model.InstalledApp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * The only things the model may ask the phone for. All read-only, all already in
 * memory, all bounded. The model cannot reach the shell or the network.
 */
object Tools {

	val catalog: String = """
tool: overview {} -> counts per group
tool: permission_combos {"permissions":["RECEIVE_SMS",...]} -> apps granted ALL of them
tool: sideloaded_apps {} -> apps installed outside a store
tool: access_grants {} -> accessibility, admin, notification, overlay, usage lists
tool: network_config {} -> proxy, private DNS, Wi-Fi security, VPN, user CA count
tool: os_posture {} -> patch level, release, developer flags, verifier
tool: app_detail {"package":"..."} -> one app's permissions, installer, dates, indicator hits
tool: indicator_hits {} -> published-indicator matches
""".trimIndent()

	private val json = Json { ignoreUnknownKeys = true }

	fun run(name: String, args: JsonObject?, e: Evidence, shareNames: Boolean): String = when (name) {
		"overview" -> overview(e)
		"permission_combos" -> combos(e, args, shareNames)
		"sideloaded_apps" -> apps(e.sideloaded, shareNames)
		"access_grants" -> grants(e, shareNames)
		"network_config" -> network(e, shareNames)
		"os_posture" -> posture(e)
		"app_detail" -> detail(e, args?.get("package")?.jsonPrimitive?.content, shareNames)
		"indicator_hits" -> indicators(e, shareNames)
		else -> """{"error":"unknown tool"}"""
	}

	/** Redact local identities in watch context and rule titles as well as structured tools. */
	internal fun redactPackageNames(text: String, e: Evidence): String {
		val apps = e.apps + e.accessibility + e.deviceAdmins + e.notificationListeners + e.overlayApps + e.usageAccess + e.vpns
		// Removed apps no longer exist in the current inventory, so no local label can be resolved.
		val withoutRemovedIdentities = text.replace(Regex("(?m)(^app removed: )[^\\r\\n]+"), "$1App")
		return apps.distinctBy { it.packageName }.sortedByDescending { it.packageName.length }
			.fold(withoutRemovedIdentities) { redacted, app ->
				val label = app.label.takeIf { it.isNotBlank() && it != app.packageName } ?: "App"
				redacted.replace(app.packageName, label)
			}
	}

	private fun app(a: InstalledApp, shareNames: Boolean) = buildJsonObject {
		val label = a.label.take(60).replace(Regex("[\\r\\n\\t]+"), " ")
		if (shareNames) {
			put("package", a.packageName)
			put("label", label)
		} else {
			put("label", if (label == a.packageName) "App" else label)
		}
		put("system", a.system)
		if (shareNames) put("installer", a.installer ?: "unknown")
		else put("installerKnown", a.installer != null)
		put("targetSdk", a.targetSdk)
		put("perms", buildJsonArray { a.dangerousPermissions.take(20).forEach { add(it.substringAfterLast('.')) } })
		if (a.firstInstall > 0) put("firstInstallDays", (System.currentTimeMillis() - a.firstInstall) / 86_400_000)
	}

	private fun apps(list: List<InstalledApp>, shareNames: Boolean) = buildJsonObject {
		put("count", list.size)
		put("apps", buildJsonArray { list.take(25).forEach { add(app(it, shareNames)) } })
	}.toString()

	private fun overview(e: Evidence) = buildJsonObject {
		put("apps", e.apps.size)
		put("sideloaded", e.sideloaded.size)
		put("accessibility", e.accessibility.size)
		put("deviceAdmins", e.deviceAdmins.size)
		put("notificationListeners", e.notificationListeners.size)
		put("overlays", e.overlayApps.size)
		put("usageAccess", e.usageAccess.size)
		put("userCAs", e.userCaCount)
		put("indicatorHits", e.iocHits.size)
	}.toString()

	private fun combos(e: Evidence, args: JsonObject?, shareNames: Boolean): String {
		val wanted = args?.get("permissions")?.let { p ->
			runCatching { p.toString().replace(Regex("[\\[\\]\" ]"), "").split(',').filter { it.isNotBlank() } }
				.getOrDefault(emptyList())
		}.orEmpty()
		val hits = e.apps.filter { a ->
			!a.system && wanted.all { w ->
				a.dangerousPermissions.any { it.endsWith(w) || it == w }
			}
		}
		return apps(hits, shareNames)
	}

	private fun grants(e: Evidence, shareNames: Boolean) = buildJsonObject {
		put("accessibility", buildJsonArray { e.accessibility.take(15).forEach { add(app(it, shareNames)) } })
		put("deviceAdmins", buildJsonArray { e.deviceAdmins.take(15).forEach { add(app(it, shareNames)) } })
		put("notificationListeners", buildJsonArray { e.notificationListeners.take(15).forEach { add(app(it, shareNames)) } })
		put("overlays", buildJsonArray { e.overlayApps.take(15).forEach { add(app(it, shareNames)) } })
		put("usageAccess", buildJsonArray { e.usageAccess.take(15).forEach { add(app(it, shareNames)) } })
	}.toString()

	private fun network(e: Evidence, shareNames: Boolean) = buildJsonObject {
		put("wifi", e.wifi?.let { "${it.ssid ?: "unknown"} (${it.security})" } ?: "not connected")
		put("privateDns", e.privateDns.name)
		put("proxy", e.proxyHost ?: "none")
		put("vpn", buildJsonArray { e.vpns.take(10).forEach { add(if (!shareNames && it.label == it.packageName) "VPN app" else it.label) } })
		put("userCAs", e.userCaCount)
	}.toString()

	private fun posture(e: Evidence) = buildJsonObject {
		put("android", e.release)
		put("sdk", e.sdk)
		put("patch", e.securityPatch)
		put("screenLock", e.screenLock.name)
		put("developerOptions", e.developerOptions)
		put("usbDebugging", e.usbDebugging)
		put("unknownSources", e.unknownSources)
		put("verifier", e.verifierEnabled)
	}.toString()

	private fun detail(e: Evidence, pkg: String?, shareNames: Boolean): String {
		val a = e.apps.firstOrNull { it.packageName == pkg } ?: return """{"error":"not found"}"""
		return buildJsonObject {
			put("app", app(a, shareNames))
			put("hits", buildJsonArray {
				e.iocHits.filter { it.packageName == a.packageName }.forEach {
					add(buildJsonObject { put("artifact", it.artifact.name); put("family", it.family); put("source", it.source) })
				}
			})
		}.toString()
	}

	private fun indicators(e: Evidence, shareNames: Boolean) = buildJsonObject {
		put("asOf", e.indicatorAsOf)
		put("hits", buildJsonArray {
			e.iocHits.take(20).forEach { h ->
				add(buildJsonObject {
					if (shareNames) put("package", h.packageName)
					put("artifact", h.artifact.name)
					put("family", h.family)
					put("source", h.source)
				})
			}
		})
	}.toString()
}
