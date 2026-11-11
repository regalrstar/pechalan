package dev.triage.app.scan

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.triage.app.collect.EvidenceCollector
import dev.triage.app.data.ScanState
import dev.triage.app.data.ScanStore
import dev.triage.app.model.Severity
import dev.triage.app.monitor.Watch
import dev.triage.app.rules.Engine
import kotlinx.coroutines.CancellationException

/** Scheduled checks run within WorkManager, not a background-started foreground service. */
class ScanWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
	override suspend fun doWork(): Result {
		if (ScanStore.state.value is ScanState.Scanning) return Result.retry()
		ScanStore.scanning()
		return try {
			val evidence = EvidenceCollector(applicationContext).collect()
			ScanStore.lastEvidence = evidence
			val report = Engine.evaluate(evidence)
			ScanStore.save(applicationContext, report)
			val actionable = report.findings.count { it.severity >= Severity.MEDIUM }
			if (actionable > 0) runCatching {
				Watch.notify(applicationContext, "Pecalang check complete", "$actionable findings are worth a review. Open your report for details.")
			}
			Result.success()
		} catch (cancelled: CancellationException) {
			ScanStore.fail("The scheduled check was interrupted. You can run a new check.")
			throw cancelled
		} catch (error: Exception) {
			ScanStore.fail(error.message ?: "The scheduled check could not finish.")
			if (runAttemptCount < 3) Result.retry() else Result.failure()
		}
	}
}
