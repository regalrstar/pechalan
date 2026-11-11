package dev.triage.app.scan

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

/** Android 15 blocks boot receivers from starting a dataSync foreground service. */
class BootReceiver : BroadcastReceiver() {
	override fun onReceive(ctx: Context, intent: Intent) {
		if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
		WorkManager.getInstance(ctx).enqueueUniqueWork(
			"boot-scan", ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<ScanWorker>().build(),
		)
	}
}
