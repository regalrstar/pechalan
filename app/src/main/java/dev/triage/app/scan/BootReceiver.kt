package dev.triage.app.scan

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** One scan shortly after boot so a returning user sees a fresh score. */
class BootReceiver : BroadcastReceiver() {
	override fun onReceive(ctx: Context, intent: Intent) {
		if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
		runCatching { ScanService.start(ctx) }
	}
}
