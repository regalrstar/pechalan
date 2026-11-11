package dev.triage.app.ai

import dev.triage.app.model.*
import org.junit.Assert.*
import org.junit.Test

class FeatureRegressionTest {
	private val hidden = InstalledApp("com.example.private", "com.example.private", false, "com.example.store", 35, listOf("android.permission.READ_SMS"), true, 0, 0)
	private fun evidence() = Evidence(
		sdk = 35, release = "15", securityPatch = "2026-11-01", deviceModel = "Pixel",
		buildTags = "release-keys", screenLock = ScreenLock.STRONG, encrypted = true,
		developerOptions = false, usbDebugging = false, unknownSources = false, verifierEnabled = true,
		sideloaded = listOf(hidden), apps = listOf(hidden), accessibility = listOf(hidden), deviceAdmins = emptyList(),
		notificationListeners = emptyList(), overlayApps = emptyList(), usageAccess = emptyList(),
		vpns = listOf(hidden), userCaCount = 0, proxyHost = null, privateDns = PrivateDns.HOSTNAME,
		wifi = null, hotspotOn = false, collectorErrors = emptyList(),
	)

	@Test fun `privacy mode suppresses fallback labels installers and VPN package identities`() {
		for (tool in listOf("sideloaded_apps", "access_grants", "network_config", "permission_combos")) {
			val output = Tools.run(tool, null, evidence(), false)
			assertFalse("$tool leaked an app package", output.contains(hidden.packageName))
			assertFalse("$tool leaked an installer package", output.contains(hidden.installer!!))
		}
		assertTrue(Tools.run("sideloaded_apps", null, evidence(), true).contains(hidden.packageName))
	}

	@Test fun `watch delta and rule text use labels instead of package names in privacy mode`() {
		val e = evidence().copy(apps = listOf(hidden.copy(label = "Reader")), vpns = emptyList(), accessibility = emptyList())
		assertEquals("new app installed: Reader", Tools.redactPackageNames("new app installed: ${hidden.packageName}", e))
		assertEquals("App has access", Tools.redactPackageNames("${hidden.packageName} has access", evidence()))
	}

	@Test fun `privacy mode hides removed app identities absent from the current inventory`() {
		val e = evidence().copy(apps = emptyList(), sideloaded = emptyList(), accessibility = emptyList(), vpns = emptyList())
		val delta = "Changes since the last check:\napp removed: com.example.removed\nUSB debugging enabled"
		assertEquals("Changes since the last check:\napp removed: App\nUSB debugging enabled", Tools.redactPackageNames(delta, e))
	}

	@Test fun `sideload classification recognises OEM stores and system package installer`() {
		assertFalse(hidden.copy(installer = "com.samsung.android.galaxyapps").sideloaded)
		assertFalse(hidden.copy(installer = InstalledApp.PLAY_STORE).sideloaded)
		assertTrue(hidden.copy(installer = "com.android.packageinstaller").sideloaded)
		assertTrue(hidden.copy(installer = null).sideloaded)
		assertFalse(hidden.copy(system = true, installer = null).sideloaded)
	}

	@Test fun `Anthropic accepts origin and v1 base URLs without duplicating path`() {
		val cfg = AiConfig(provider = AiConfig.Provider.ANTHROPIC, baseUrl = "https://api.anthropic.com")
		assertEquals("https://api.anthropic.com/v1/messages", cfg.endpoint)
		assertEquals(cfg.endpoint, cfg.copy(baseUrl = "https://api.anthropic.com/v1/").endpoint)
	}

	@Test fun `Anthropic usage counts input and output tokens`() {
		val result = ModelReplies.parse("""{"content":[{"type":"text","text":"OK"}],"usage":{"input_tokens":21,"output_tokens":4}}""", true)
		assertEquals("OK", result.text)
		assertEquals(25, result.tokens)
	}

	@Test fun `OpenAI-compatible usage still reads total tokens`() {
		val result = ModelReplies.parse("""{"choices":[{"message":{"content":"OK"}}],"usage":{"total_tokens":30}}""", false)
		assertEquals("OK", result.text)
		assertEquals(30, result.tokens)
	}
}
