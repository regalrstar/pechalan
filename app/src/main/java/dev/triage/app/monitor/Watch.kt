package dev.triage.app.monitor

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.triage.app.R
import dev.triage.app.ai.AiConfig
import dev.triage.app.ai.AiRunner
import dev.triage.app.ai.AiStore
import dev.triage.app.ai.DeepScan
import dev.triage.app.collect.EvidenceCollector
import dev.triage.app.model.Evidence
import dev.triage.app.model.Severity
import dev.triage.app.ui.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.TimeUnit

/** The durable shape of "what the phone looked like last time". */
@Serializable
data class Baseline(
	val packages: Set<String> = emptySet(),
	val admins: Set<String> = emptySet(),
	val accessibility: Set<String> = emptySet(),
	val listeners: Set<String> = emptySet(),
	val caCount: Int = 0,
	val vpn: Set<String> = emptySet(),
	val developer: Boolean = false,
	val usb: Boolean = false,
	val verifier: Boolean = true,
) {
	companion object {
		fun from(e: Evidence) = Baseline(
			packages = e.apps.filter { !it.system }.map { it.packageName }.toSet(),
			admins = e.deviceAdmins.map { it.packageName }.toSet(),
			accessibility = e.accessibility.map { it.packageName }.toSet(),
			listeners = e.notificationListeners.map { it.packageName }.toSet(),
			caCount = e.userCaCount,
			vpn = e.vpns.map { it.packageName }.toSet(),
			developer = e.developerOptions,
			usb = e.usbDebugging,
			verifier = e.verifierEnabled,
		)
	}
}

@Serializable
data class WatchEvent(
	val at: Long,
	val lines: List<String>,
	val headline: String,
	val aiCount: Int = 0,
	val severity: String = "INFO",
)

object Watch {

	private val json = Json { ignoreUnknownKeys = true }

	fun enabled(ctx: Context) = AiStore(ctx).watchEnabled()

	fun baselineFile(ctx: Context) = File(ctx.filesDir, "watch-baseline.json")
	fun logFile(ctx: Context) = File(ctx.filesDir, "watch-log.json")

	suspend fun loadBaseline(ctx: Context): Baseline? = withContext(Dispatchers.IO) {
		val f = baselineFile(ctx)
		if (!f.exists()) return@withContext null
		runCatching { json.decodeFromString<Baseline>(f.readText()) }.getOrNull()
	}

	suspend fun saveBaseline(ctx: Context, b: Baseline) = withContext(Dispatchers.IO) {
		runCatching { baselineFile(ctx).writeText(json.encodeToString(Baseline.serializer(), b)) }
	}

	suspend fun appendLog(ctx: Context, e: WatchEvent) = withContext(Dispatchers.IO) {
		val old = runCatching {
			json.decodeFromString<List<WatchEvent>>(logFile(ctx).readText())
		}.getOrDefault(emptyList())
		val next = (listOf(e) + old).take(30)
		runCatching { logFile(ctx).writeText(json.encodeToString(kotlinx.serialization.builtins.ListSerializer(WatchEvent.serializer()), next)) }
	}

	suspend fun loadLog(ctx: Context): List<WatchEvent> = withContext(Dispatchers.IO) {
		runCatching { json.decodeFromString<List<WatchEvent>>(logFile(ctx).readText()) }
			.getOrDefault(emptyList())
	}

	/** Human-readable deltas; the AI gets the same list. */
	fun diff(old: Baseline, new: Baseline): List<String> = buildList {
		(new.packages - old.packages).forEach { add("new app installed: $it") }
		(old.packages - new.packages).forEach { add("app removed: $it") }
		(new.admins - old.admins).forEach { add("new device admin: $it") }
		(new.accessibility - old.accessibility).forEach { add("new accessibility service: $it") }
		(new.listeners - old.listeners).forEach { add("new notification listener: $it") }
		(new.vpn - old.vpn).forEach { add("VPN app now active: $it") }
		if (new.caCount > old.caCount) add("${new.caCount - old.caCount} new user CA certificate(s)")
		if (new.developer != old.developer) add("developer options ${if (new.developer) "enabled" else "disabled"}")
		if (new.usb != old.usb) add("USB debugging ${if (new.usb) "enabled" else "disabled"}")
		if (new.verifier != old.verifier) add("install-time scanning ${if (new.verifier) "enabled" else "disabled"}")
	}

	/** Changes worth a notification even without AI. */
	fun hardSeverity(lines: List<String>): Severity = when {
		lines.any { it.startsWith("new device admin") || it.startsWith("new accessibility") } -> Severity.HIGH
		lines.any { it.startsWith("new user CA") || it.contains("scanning disabled") } -> Severity.MEDIUM
		else -> Severity.LOW
	}

	fun notify(ctx: Context, headline: String, body: String) {
		val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
		nm.createNotificationChannel(
			NotificationChannel(CHANNEL, "Protection", NotificationManager.IMPORTANCE_DEFAULT),
		)
		val pi = PendingIntent.getActivity(
			ctx, 1, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
		)
		nm.notify(
			42,
			NotificationCompat.Builder(ctx, CHANNEL)
				.setSmallIcon(R.drawable.ic_stat_scan)
				.setContentTitle(headline)
				.setContentText(body)
				.setStyle(NotificationCompat.BigTextStyle().bigText(body))
				.setContentIntent(pi)
				.setAutoCancel(true)
				.build(),
		)
	}

	const val CHANNEL = "watch"

	fun schedule(ctx: Context, on: Boolean) {
		val wm = WorkManager.getInstance(ctx)
		if (!on) {
			wm.cancelUniqueWork("watch-periodic")
			wm.cancelUniqueWork("watch-recheck")
			return
		}
		val periodic = PeriodicWorkRequestBuilder<WatchWorker>(15, TimeUnit.MINUTES)
			.setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
			.build()
		wm.enqueueUniquePeriodicWork("watch-periodic", ExistingPeriodicWorkPolicy.UPDATE, periodic)
	}

	fun recheck(ctx: Context) {
		val req = OneTimeWorkRequestBuilder<WatchWorker>()
			.setInitialDelay(4, TimeUnit.SECONDS)
			.setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
			.build()
		WorkManager.getInstance(ctx).enqueueUniqueWork("watch-recheck", ExistingWorkPolicy.REPLACE, req)
	}
}

class WatchReceiver : BroadcastReceiver() {
	override fun onReceive(ctx: Context, intent: Intent) {
		runCatching {
			val beat = java.io.File(ctx.filesDir, "watch-heartbeat")
			beat.appendText("hit ${intent.action}\n")
			val on = runCatching { Watch.enabled(ctx) }
			beat.appendText("enabled=${on.getOrNull()} err=${on.exceptionOrNull()?.javaClass?.simpleName}:${on.exceptionOrNull()?.message}\n")
			if (on.getOrDefault(false)) Watch.recheck(ctx)
		}
	}}

/**
 * Collects a fresh snapshot, diffs it against the stored baseline, and asks the
 * model about the delta only. Falls back to a deterministic severity if no AI is
 * configured.
 */
class WatchWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

	override suspend fun doWork(): Result {
		val ctx = applicationContext
		if (!Watch.enabled(ctx)) return Result.success()

		val cfg = AiStore(ctx).load()
		val evidence = runCatching { EvidenceCollector(ctx).collect() }.getOrNull() ?: return Result.success()
		val old = Watch.loadBaseline(ctx)
		val new = Baseline.from(evidence)
		if (old == null) {
			Watch.saveBaseline(ctx, new)
			return Result.success()
		}
		val lines = Watch.diff(old, new)
		if (lines.isEmpty()) return Result.success()

		var aiCount = 0
		var severity = Watch.hardSeverity(lines)
		var headline = lines.first()

		if (cfg.configured) {
			val context = "Changes since the last check:\n" + lines.joinToString("\n")
			val outcome = runCatching {
				DeepScan.run(cfg, evidence, emptyList(), extraContext = context)
			}.getOrNull()
			if (outcome != null) {
				aiCount = outcome.findings.size
				if (outcome.findings.isNotEmpty()) {
					headline = outcome.findings.first().title
					findingsSeverity(outcome.findings)?.let { if (it > severity) severity = it }
				}
			}
		}

		Watch.saveBaseline(ctx, new)
		Watch.appendLog(
			ctx,
			WatchEvent(
				at = System.currentTimeMillis(),
				lines = lines.take(10),
				headline = headline,
				aiCount = aiCount,
				severity = severity.name,
			),
		)

		// Notify for anything a person should see; stay quiet for routine installs.
		if (severity >= Severity.MEDIUM || aiCount > 0) {
			val body = buildString {
				append(lines.take(4).joinToString(" · "))
				if (aiCount > 0) append(" — AI added $aiCount note(s)")
			}
			runCatching { Watch.notify(ctx, "Change on your phone", body) }
		}
		return Result.success()
	}

	private fun findingsSeverity(list: List<dev.triage.app.model.Finding>): Severity? =
		list.maxOfOrNull { it.severity }

	companion object {
		fun oneOff(ctx: Context) {
			ContextCompat.startForegroundService(ctx, Intent(ctx, MainActivity::class.java))
		}
	}
}
