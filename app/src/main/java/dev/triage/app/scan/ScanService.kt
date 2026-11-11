package dev.triage.app.scan

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import dev.triage.app.R
import dev.triage.app.collect.EvidenceCollector
import dev.triage.app.data.ScanStore
import dev.triage.app.model.Severity
import dev.triage.app.rules.Engine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * A scan takes seconds, not minutes, but it walks every installed package, so it
 * runs in a dataSync foreground service to survive a screen-off.
 */
class ScanService : Service() {

	private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
	private var job: Job? = null

	override fun onBind(intent: Intent?): IBinder? = null

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		startForeground(NOTIFICATION_ID, buildNotification("Checking this device…"))
		if (job?.isActive == true) return START_NOT_STICKY

		job = scope.launch {
			try {
				ScanStore.scanning()
				val evidence = EvidenceCollector(this@ScanService).collect()
				ScanStore.lastEvidence = evidence
				val report = Engine.evaluate(evidence)
				ScanStore.save(this@ScanService, report)
				notifyDone(report.findings.count { it.severity >= Severity.MEDIUM })
			} catch (t: Throwable) {
				ScanStore.fail(t.message ?: t.javaClass.simpleName)
			} finally {
				stopSelf(startId)
			}
		}
		return START_NOT_STICKY
	}

	private fun notifyDone(actionable: Int) {
		val nm = getSystemService(NotificationManager::class.java) ?: return
		val text = if (actionable == 0) "No urgent issues found" else "$actionable thing(s) worth a look"
		nm.notify(NOTIFICATION_ID, buildNotification(text))
	}

	private fun buildNotification(text: String) = NotificationCompat.Builder(this, CHANNEL)
		.setSmallIcon(R.drawable.ic_stat_scan)
		.setContentTitle(getString(R.string.app_name))
		.setContentText(text)
		.setOngoing(true)
		.setSilent(true)
		.setContentIntent(
			android.app.PendingIntent.getActivity(
				this, 0,
				Intent(this, dev.triage.app.ui.MainActivity::class.java),
				android.app.PendingIntent.FLAG_IMMUTABLE,
			),
		)
		.build()

	override fun onDestroy() {
		scope.cancel()
		super.onDestroy()
	}

	companion object {
		private const val CHANNEL = "scan"
		private const val NOTIFICATION_ID = 41

		fun start(ctx: Context) {
			ensureChannel(ctx)
			ContextCompat.startForegroundService(ctx, Intent(ctx, ScanService::class.java))
		}

		private fun ensureChannel(ctx: Context) {
			val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
			if (nm.getNotificationChannel(CHANNEL) != null) return
			nm.createNotificationChannel(
				NotificationChannel(CHANNEL, "Scan progress", NotificationManager.IMPORTANCE_LOW),
			)
		}
	}
}
