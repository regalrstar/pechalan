package dev.triage.app.rules

import dev.triage.app.model.Evidence
import dev.triage.app.model.Finding
import dev.triage.app.model.Gap
import dev.triage.app.model.Report
import dev.triage.app.model.Severity

/**
 * Runs every rule, drops cross-rule duplicates (same check prefix + same app),
 * and turns the remainder into a 0-100 score.
 *
 * Scoring is linear deduction from 100. A CONFIRMED critical costs 25, a
 * POSSIBLE note costs 1. The score exists to make progress legible, not to be
 * scientifically exact; the findings list is the source of truth.
 */
object Engine {

	fun evaluate(e: Evidence): Report {
		val started = System.currentTimeMillis()
		val findings = Rules.all
			.flatMap { rule -> runCatching { rule.evaluate(e) }.getOrElse { emptyList() } }
			.distinctBy { "${it.checkId}|${it.appPackage ?: ""}" }
			.sortedWith(compareByDescending<Finding> { it.cost }.thenBy { it.title })

		val score = (100 - findings.sumOf { it.cost }).coerceIn(0, 100)
		return Report(
			startedAt = started,
			finishedAt = System.currentTimeMillis(),
			score = score,
			findings = findings,
			checksRun = Rules.all.size,
			deviceLabel = e.deviceModel.ifBlank { "Android device" },
			patchLevel = e.securityPatch,
			androidRelease = e.release,
			collectorErrors = e.collectorErrors,
			gaps = (e.gaps + permanentGaps()).distinctBy { it.id },
			indicatorAsOf = e.indicatorAsOf,
			indicatorCount = e.indicatorCount,
		)
	}

	/**
	 * Places a sandboxed app cannot look, stated on every report. They do not change
	 * with the device; hiding them would let a clean score imply more than it means.
	 */
	private fun permanentGaps(): List<Gap> = listOf(
		Gap("gap.intrusion", "DNS lookups, connection attempts and process starts — system logs need a USB collection (AndroidQF)."),
		Gap("gap.install_history", "Apps that were installed and later removed — Android keeps the install history in a shell-only record."),
		Gap("gap.cellebrite", "Cellebrite unlock traces — only in the phone's own logs, and only for a short time after the phone was accessed."),
		Gap("gap.sms", "Links inside SMS and MMS — needs the SMS permission, which this build does not request."),
		Gap("gap.network", "Traffic contents and other devices on the network — configuration is read, packets are not."),
	)

	fun grade(score: Int): String = when {		score >= 90 -> "Good"
		score >= 75 -> "Fair"
		score >= 55 -> "Needs work"
		else -> "At risk"
	}

	fun topActions(findings: List<Finding>, limit: Int = 3): List<Finding> =
		findings.filter { it.severity >= Severity.MEDIUM }.take(limit)

	fun allCheckCount(): Int = Rules.all.size

	fun permanentGapsForTraining(): List<Gap> = permanentGaps()
}
