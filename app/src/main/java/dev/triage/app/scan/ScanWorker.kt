package dev.triage.app.scan

import android.content.Context
import androidx.work.Worker
import androidx.work.WorkerParameters

class ScanWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
	override fun doWork(): Result {
		runCatching { ScanService.start(applicationContext) }
		return Result.success()
	}
}
