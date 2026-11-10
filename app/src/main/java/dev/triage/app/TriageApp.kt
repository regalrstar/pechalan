package dev.triage.app

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dev.triage.app.data.ScanStore
import dev.triage.app.scan.ScanWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class TriageApp : Application() {

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

	override fun onCreate() {
		super.onCreate()
		scope.launch { ScanStore.load(this@TriageApp) }
		scheduleDaily()
	}

	/** Daily re-scan. WorkManager survives reboots and Doze; no push infrastructure needed. */
	private fun scheduleDaily() {
		val request = PeriodicWorkRequestBuilder<ScanWorker>(1, TimeUnit.DAYS)
			.setInitialDelay(15, TimeUnit.MINUTES)
			.build()
		runCatching {
			WorkManager.getInstance(this)
				.enqueueUniquePeriodicWork("daily-scan", ExistingPeriodicWorkPolicy.KEEP, request)
		}
	}
}
