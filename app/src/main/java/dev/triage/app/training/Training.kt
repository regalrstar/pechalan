package dev.triage.app.training

import dev.triage.app.model.Confidence
import dev.triage.app.model.Finding
import dev.triage.app.model.FixTarget
import dev.triage.app.model.Gap
import dev.triage.app.model.Impact
import dev.triage.app.model.Report
import dev.triage.app.model.Severity
import dev.triage.app.rules.Engine

/**
 * A made-up phone, so a first-time user can see what a report looks like before
 * trusting one. Every value is invented; the indicator family is labelled as such.
 */
object Training {

	fun report(now: Long = System.currentTimeMillis()): Report {
		val findings = listOf(
			Finding(
				checkId = "spy.ioc:com.example.henbox",
				title = "Published spyware match: HenBox (training)",
				observed = "ChatBox matches a published HenBox indicator — the installed file is byte-identical to a published sample and signed with the published key.",
				why = "This is not an app that merely looks odd. Researchers published it as spyware used against a specific group. A file or certificate match is strong.",
				fix = "Keep the app for now — do not delete the evidence. Share the report, then get a human to look at it.",
				severity = Severity.CRITICAL,
				confidence = Confidence.CONFIRMED,
				impact = Impact.INSTALLED_APP,
				evidence = mapOf("family" to "HenBox", "matched" to "certificate,file", "source" to "unit42-henbox-2018-03"),
				appLabel = "ChatBox",
				appPackage = "com.example.henbox",
				fixTarget = FixTarget.APP_DETAILS,
			),
			Finding(
				checkId = "apps.sideloaded",
				title = "Apps installed from outside an app store",
				observed = "2 app(s): ChatBox, File Rescue.",
				why = "Sideloaded apps skip store review and update silently. They are the single most common way stalkerware and scam apps arrive.",
				fix = "Open each app. Keep the ones you recognise and use; uninstall the rest.",
				severity = Severity.MEDIUM,
				confidence = Confidence.CONFIRMED,
				impact = Impact.INSTALLED_APP,
				fixTarget = FixTarget.APP_DETAILS,
			),
			Finding(
				checkId = "os.patch_level",
				title = "Security updates are 210 days old",
				observed = "The last Android security patch is dated 2026-04-14.",
				why = "Most Android attacks land on bugs that already have a patch.",
				fix = "Settings → System → System update.",
				severity = Severity.HIGH,
				confidence = Confidence.CONFIRMED,
				impact = Impact.THIS_DEVICE,
				fixTarget = FixTarget.SECURITY,
			),
			Finding(
				checkId = "access.accessibility",
				title = "Apps can read the screen",
				observed = "Accessibility access: ChatBox.",
				why = "Accessibility lets an app read every screen and tap buttons, including one-time codes.",
				fix = "Settings → Accessibility. Turn off anything you don't recognise.",
				severity = Severity.HIGH,
				confidence = Confidence.LIKELY,
				impact = Impact.INSTALLED_APP,
				fixTarget = FixTarget.ACCESSIBILITY,
			),
		).sortedByDescending { it.cost }

		return Report(
			startedAt = now,
			finishedAt = now,
			score = 100 - findings.sumOf { it.cost },
			findings = findings,
			checksRun = Engine.allCheckCount(),
			deviceLabel = "Training example (not a real check)",
			patchLevel = "2026-04-14",
			androidRelease = "14",
			gaps = Engine.permanentGapsForTraining(),
			indicatorAsOf = "2026-11-02",
			indicatorCount = 1037,
			training = true,
		)
	}
}
