package dev.triage.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import dev.triage.app.model.Finding
import dev.triage.app.model.FixTarget

/** Best-effort deep links. Every one of them falls back to the general settings app. */
object FixIntents {

	fun open(ctx: Context, finding: Finding) {
		val intent = build(ctx, finding)
		runCatching { ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
			.onFailure {
				runCatching {
					ctx.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
				}
			}
	}

	private fun build(ctx: Context, f: Finding): Intent {
		val pkgUri = f.appPackage?.let { Uri.parse("package:$it") }
		return when (f.fixTarget) {
			FixTarget.APP_DETAILS ->
				if (pkgUri != null) Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, pkgUri)
				else Intent(Settings.ACTION_APPLICATION_SETTINGS)
			FixTarget.DEVELOPER -> Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
			FixTarget.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
			FixTarget.NOTIFICATION_ACCESS -> Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
			FixTarget.USAGE_ACCESS -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
			FixTarget.OVERLAY -> Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, pkgUri)
			FixTarget.VPN -> Intent(Settings.ACTION_VPN_SETTINGS)
			FixTarget.WIFI -> Intent(Settings.ACTION_WIFI_SETTINGS)
			FixTarget.PLAY_PROTECT -> Intent(Settings.ACTION_SECURITY_SETTINGS)
			FixTarget.CERTIFICATES -> Intent(Settings.ACTION_SECURITY_SETTINGS)
			FixTarget.SCREEN_LOCK -> Intent(Settings.ACTION_SECURITY_SETTINGS)
			FixTarget.DEVICE_ADMIN -> Intent(Settings.ACTION_SECURITY_SETTINGS)
			FixTarget.SECURITY -> Intent(Settings.ACTION_SECURITY_SETTINGS)
			null -> Intent(Settings.ACTION_SETTINGS)
		}
	}
}
