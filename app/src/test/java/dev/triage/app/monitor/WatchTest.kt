package dev.triage.app.monitor

import dev.triage.app.model.Severity
import org.junit.Assert.*
import org.junit.Test

class WatchTest {
	@Test fun `added CA is notified at medium severity`() {
		val lines = Watch.diff(Baseline(caCount = 0), Baseline(caCount = 2))
		assertEquals(listOf("2 new user CA certificate(s)"), lines)
		assertEquals(Severity.MEDIUM, Watch.hardSeverity(lines))
	}

	@Test fun `new notification access is not silently treated as a routine install`() {
		val lines = Watch.diff(Baseline(), Baseline(listeners = setOf("com.example.listener")))
		assertEquals(Severity.MEDIUM, Watch.hardSeverity(lines))
	}

	@Test fun `ordinary install remains quiet without higher risk changes`() {
		assertEquals(Severity.LOW, Watch.hardSeverity(Watch.diff(Baseline(), Baseline(packages = setOf("com.example.app")))))
	}

	@Test fun `accessibility change outranks routine installs and certificates`() {
		val lines = Watch.diff(Baseline(), Baseline(packages = setOf("com.example.app"), accessibility = setOf("com.example.reader"), caCount = 1))
		assertEquals(Severity.HIGH, Watch.hardSeverity(lines))
	}
}
