package dev.triage.app.data

import android.content.Context
import dev.triage.app.model.Report
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

sealed interface ScanState {
	data object Idle : ScanState
	data object Scanning : ScanState
	data class Done(val report: Report) : ScanState
	data class Failed(val message: String) : ScanState
}

/**
 * Last scan only. Everything stays in app-private storage; nothing is uploaded.
 * The store is a singleton so the foreground service and the UI share one truth.
 */
object ScanStore {

	private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
	private val _state = MutableStateFlow<ScanState>(ScanState.Idle)
	val state: StateFlow<ScanState> = _state.asStateFlow()

	/** Last collected evidence. Memory only; refreshed by every scan. */
	@Volatile
	var lastEvidence: dev.triage.app.model.Evidence? = null

	private fun file(ctx: Context) = File(ctx.filesDir, "last-report.json")

	suspend fun load(ctx: Context) = withContext(Dispatchers.IO) {
		val f = file(ctx)
		if (!f.exists()) return@withContext
		runCatching {
			val report = json.decodeFromString<Report>(f.readText())
			if (_state.value !is ScanState.Scanning) _state.value = ScanState.Done(report)
		}
	}

	fun scanning() {
		_state.value = ScanState.Scanning
	}

	suspend fun save(ctx: Context, report: Report) = withContext(Dispatchers.IO) {
		runCatching { file(ctx).writeText(json.encodeToString(Report.serializer(), report)) }
		_state.value = ScanState.Done(report)
	}

	fun fail(message: String) {
		_state.value = ScanState.Failed(message)
	}
}
