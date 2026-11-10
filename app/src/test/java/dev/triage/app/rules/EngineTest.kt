package dev.triage.app.rules

import dev.triage.app.model.Artifact
import dev.triage.app.model.Confidence
import dev.triage.app.model.Evidence
import dev.triage.app.model.InstalledApp
import dev.triage.app.model.IocHit
import dev.triage.app.model.PrivateDns
import dev.triage.app.model.ScreenLock
import dev.triage.app.model.Severity
import dev.triage.app.model.WifiSecurity
import dev.triage.app.model.WifiState
import dev.triage.app.ioc.IndicatorStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class EngineTest {

	private fun app(
		pkg: String,
		label: String = pkg,
		system: Boolean = false,
		installer: String? = InstalledApp.PLAY_STORE,
		targetSdk: Int = 35,
		perms: List<String> = emptyList(),
	) = InstalledApp(pkg, label, system, installer, targetSdk, perms, true, 0, 0)

	private fun evidence(
		patch: String = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE),
		lock: ScreenLock = ScreenLock.STRONG,
		apps: List<InstalledApp> = emptyList(),
		accessibility: List<InstalledApp> = emptyList(),
		sideloaded: List<InstalledApp> = emptyList(),
		userCa: Int = 0,
		verifier: Boolean = true,
		unknownSources: Boolean = false,
		usb: Boolean = false,
		wifi: WifiState? = null,
		privateDns: PrivateDns = PrivateDns.HOSTNAME,
		sdk: Int = 35,
		iocHits: List<IocHit> = emptyList(),
	) = Evidence(
		sdk = sdk,
		release = "15",
		securityPatch = patch,
		deviceModel = "Pixel test",
		buildTags = "release-keys",
		screenLock = lock,
		encrypted = true,
		developerOptions = usb,
		usbDebugging = usb,
		unknownSources = unknownSources,
		verifierEnabled = verifier,
		sideloaded = sideloaded,
		apps = apps,
		accessibility = accessibility,
		deviceAdmins = emptyList(),
		notificationListeners = emptyList(),
		overlayApps = emptyList(),
		usageAccess = emptyList(),
		vpns = emptyList(),
		userCaCount = userCa,
		proxyHost = null,
		privateDns = privateDns,
		wifi = wifi,
		hotspotOn = false,
		collectorErrors = emptyList(),
		iocHits = iocHits,
		indicatorAsOf = "2026-11-02",
		indicatorCount = 1037,
	)

	@Test
	fun `clean device scores 100 and has no actionable findings`() {
		val report = Engine.evaluate(evidence())
		assertEquals(100, report.score)
		assertTrue(report.findings.isEmpty())
	}

	@Test
	fun `stale patch escalates with age`() {
		val recent = LocalDate.now().minusDays(30).format(DateTimeFormatter.ISO_LOCAL_DATE)
		val old = LocalDate.now().minusDays(200).format(DateTimeFormatter.ISO_LOCAL_DATE)

		assertFalse(Rules.all.any { r -> r.evaluate(evidence(patch = recent)).any { it.checkId == "os.patch_level" } })
		val hit = Rules.all.flatMap { it.evaluate(evidence(patch = old)) }.single { it.checkId == "os.patch_level" }
		assertEquals(Severity.HIGH, hit.severity)
	}

	@Test
	fun `missing lock is high and confirmed`() {
		val hit = Engine.evaluate(evidence(lock = ScreenLock.NONE)).findings.single { it.checkId == "lock.missing" }
		assertEquals(Severity.HIGH, hit.severity)
		assertEquals(Confidence.CONFIRMED, hit.confidence)
	}

	@Test
	fun `known stalkerware is flagged`() {
		val report = Engine.evaluate(evidence(apps = listOf(app("com.mspy.android", "mSpy"))))
		assertTrue(report.findings.any { it.checkId.startsWith("spy.fingerprint") })
	}

	@Test
	fun `sideloaded stalkerware outranks store install`() {
		val store = Engine.evaluate(evidence(apps = listOf(app("com.mspy.android", "mSpy"))))
			.findings.single { it.checkId.startsWith("spy.fingerprint") }
		val side = Engine.evaluate(
			evidence(
				apps = listOf(app("com.mspy.android", "mSpy", installer = null)),
				sideloaded = listOf(app("com.mspy.android", "mSpy", installer = null)),
			),
		).findings.single { it.checkId.startsWith("spy.fingerprint") }
		assertTrue(side.severity.ordinal > store.severity.ordinal)
	}

	@Test
	fun `trusted accessibility apps stay quiet`() {
		val report = Engine.evaluate(evidence(accessibility = listOf(app("com.bitwarden.app", "Bitwarden"))))
		assertFalse(report.findings.any { it.checkId == "access.accessibility" && it.severity >= Severity.LOW })
	}

	@Test
	fun `unknown accessibility app is high`() {
		val report = Engine.evaluate(evidence(accessibility = listOf(app("com.weird.screen", "Screen Helper"))))
		val hit = report.findings.single { it.checkId == "access.accessibility" }
		assertEquals(Severity.HIGH, hit.severity)
	}

	@Test
	fun `sms plus accessibility combination is high`() {
		val reader = app(
			"com.weird.reader", "Reader",
			perms = listOf(android.Manifest.permission.RECEIVE_SMS),
		)
		val report = Engine.evaluate(
			evidence(apps = listOf(reader), accessibility = listOf(reader)),
		)
		assertTrue(report.findings.any { it.checkId == "access.combo" && it.severity == Severity.HIGH })
	}

	@Test
	fun `open wifi is high but wpa3 is silent`() {
		assertTrue(
			Engine.evaluate(evidence(wifi = WifiState("Cafe", WifiSecurity.OPEN)))
				.findings.any { it.checkId == "net.wifi" && it.severity == Severity.HIGH },
		)
		assertFalse(
			Engine.evaluate(evidence(wifi = WifiState("Home", WifiSecurity.WPA3)))
				.findings.any { it.checkId == "net.wifi" },
		)
	}

	@Test
	fun `score never goes below zero`() {
		val report = Engine.evaluate(
			evidence(
				patch = "2020-01-01",
				lock = ScreenLock.NONE,
				verifier = false,
				userCa = 3,
				wifi = WifiState("Cafe", WifiSecurity.OPEN),
				apps = listOf(
					app("com.mspy.android", "mSpy", installer = null),
					app("com.flexispy.app", "FlexiSPY", installer = null),
					app("com.hoverwatch.app", "Hoverwatch", installer = null),
				),
				sideloaded = listOf(
					app("com.mspy.android", "mSpy", installer = null),
					app("com.flexispy.app", "FlexiSPY", installer = null),
					app("com.hoverwatch.app", "Hoverwatch", installer = null),
				),
			),
		)
		assertTrue(report.score in 0..100)
		assertEquals(0, report.score)
	}

	@Test
	fun `findings are sorted by cost`() {
		val report = Engine.evaluate(evidence(lock = ScreenLock.NONE, usb = true, verifier = false))
		val costs = report.findings.map { it.cost }
		assertEquals(costs.sortedDescending(), costs)
	}

	@Test
	fun `a published package name alone is medium`() {
		val app = app("com.android.henbox", "HenBox")
		val hit = IocHit(app.packageName, Artifact.PACKAGE, IndicatorStore.Kind.PACKAGE, "HenBox", "unit42-henbox", app.packageName)
		val finding = Engine.evaluate(evidence(apps = listOf(app), iocHits = listOf(hit)))
			.findings.single { it.checkId.startsWith("spy.ioc") }
		assertEquals(Severity.MEDIUM, finding.severity)
		assertEquals(Confidence.LIKELY, finding.confidence)
	}

	@Test
	fun `exact file plus certificate match is critical and confirmed`() {
		val app = app("com.android.henbox", "HenBox")
		val hits = listOf(
			IocHit(app.packageName, Artifact.CERTIFICATE, IndicatorStore.Kind.SHA256, "HenBox", "unit42-henbox", "cert"),
			IocHit(app.packageName, Artifact.FILE, IndicatorStore.Kind.SHA256, "HenBox", "unit42-henbox", "file"),
		)
		val finding = Engine.evaluate(evidence(apps = listOf(app), iocHits = hits))
			.findings.single { it.checkId.startsWith("spy.ioc") }
		assertEquals(Severity.CRITICAL, finding.severity)
		assertEquals(Confidence.CONFIRMED, finding.confidence)
	}

	@Test
	fun `every report states its permanent gaps`() {
		val report = Engine.evaluate(evidence())
		assertTrue(report.gaps.any { it.id == "gap.intrusion" })
		assertTrue(report.gaps.any { it.id == "gap.cellebrite" })
	}

	@Test
	fun `indicator corpus metadata is carried into the report`() {
		val report = Engine.evaluate(evidence())
		assertEquals(1037, report.indicatorCount)
		assertEquals("2026-11-02", report.indicatorAsOf)
	}
}
