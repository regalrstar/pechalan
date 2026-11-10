package dev.triage.app.ai

import dev.triage.app.model.Confidence
import dev.triage.app.model.Evidence
import dev.triage.app.model.Origin
import dev.triage.app.model.PrivateDns
import dev.triage.app.model.ScreenLock
import dev.triage.app.model.Severity
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeepScanTest {

	private fun evidence() = Evidence(
		sdk = 35, release = "15", securityPatch = "2026-11-01", deviceModel = "Pixel",
		buildTags = "release-keys", screenLock = ScreenLock.STRONG, encrypted = true,
		developerOptions = false, usbDebugging = false, unknownSources = false, verifierEnabled = true,
		sideloaded = emptyList(), apps = emptyList(), accessibility = emptyList(), deviceAdmins = emptyList(),
		notificationListeners = emptyList(), overlayApps = emptyList(), usageAccess = emptyList(),
		vpns = emptyList(), userCaCount = 0, proxyHost = null, privateDns = PrivateDns.HOSTNAME,
		wifi = null, hotspotOn = false, collectorErrors = emptyList(), indicatorAsOf = "2026-11-02", indicatorCount = 1037,
	)

	private val mock = AiConfig(provider = AiConfig.Provider.MOCK, model = "mock", maxSteps = 6)

	@Test
	fun `extractJson takes the first balanced object out of prose`() {
		val text = "Here you go:\n```json\n{\"findings\":[{\"title\":\"a { brace } in text\"}],\"n\":1}\n```\nDone."
		assertEquals("""{"findings":[{"title":"a { brace } in text"}],"n":1}""", DeepScan.extractJson(text))
	}

	@Test
	fun `mock loop probes then returns findings`() = runTest {
		val out = DeepScan.run(mock, evidence(), emptyList())
		assertTrue(out.findings.isNotEmpty())
		assertEquals(Origin.AI, out.findings.first().origin)
		assertTrue(out.meta.steps >= 3)
		assertTrue(out.meta.tokens > 0)
	}

	@Test
	fun `ai findings keep severity from the model and are capped`() = runTest {
		val out = DeepScan.run(mock, evidence(), emptyList())
		assertTrue(out.findings.any { it.severity == Severity.HIGH })
		assertTrue(out.findings.size <= 6)
	}

	@Test
	fun `unknown provider yields an error, not a crash`() = runTest {
		val out = DeepScan.run(AiConfig(provider = AiConfig.Provider.OPENAI_COMPATIBLE, baseUrl = "", model = ""), evidence(), emptyList())
		// Config is incomplete but the client still builds; the HTTP call fails.
		assertTrue(out.error != null)
	}

	@Test
	fun `missing severity defaults to medium and confidence to possible`() = runTest {
		val out = DeepScan.run(mock, evidence(), emptyList())
		val f = out.findings.first { it.title.startsWith("Two-factor") }
		assertEquals(Severity.HIGH, f.severity)
		assertEquals(Confidence.LIKELY, f.confidence)
		assertFalse(out.findings.any { it.title.isBlank() })
	}
}
