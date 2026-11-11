package dev.triage.app.ui

import android.app.Application
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.triage.app.ai.AiRunner
import dev.triage.app.ai.AiStore
import dev.triage.app.collect.EvidenceCollector
import dev.triage.app.data.ScanState
import dev.triage.app.data.ScanStore
import dev.triage.app.model.Report
import dev.triage.app.rules.Engine
import dev.triage.app.scan.ScanService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File

class ScanViewModel(app: Application) : AndroidViewModel(app) {

	val state = ScanStore.state
	val aiState = AiRunner.state

	fun scan() {
		ScanService.start(getApplication())
	}

	fun showTraining() {
		viewModelScope.launch { ScanStore.save(getApplication(), dev.triage.app.training.Training.report()) }
	}

	/**
	 * Deep scan: reuses the last evidence when available, otherwise collects fresh,
	 * then runs the bounded AI loop. Rules keep their score; AI results are extra.
	 */
	fun deepScan(onNeedConfig: () -> Unit) {
		val cfg = AiStore(getApplication()).load()
		if (!cfg.configured) {
			onNeedConfig()
			return
		}
		if (aiState.value is dev.triage.app.ai.AiRunState.Running) return

		viewModelScope.launch {
			val app = getApplication<Application>()
			val evidence = ScanStore.lastEvidence ?: runCatching {
				EvidenceCollector(app).collect().also { ScanStore.lastEvidence = it }
			}.getOrElse {
				AiRunner.reset()
				return@launch
			}
			val base = (ScanStore.state.value as? ScanState.Done)?.report ?: Engine.evaluate(evidence)
			val outcome = AiRunner.run(app, cfg, evidence, base.findings.map { it.title })
			ScanStore.save(
				app,
				base.copy(
					aiFindings = outcome.findings,
					aiMeta = outcome.meta,
					aiError = outcome.error,
				),
			)
		}
	}

	/** Writes the exact report JSON to a shareable file. Nothing else is included. */
	fun export(report: Report, onReady: (Uri) -> Unit) {
		viewModelScope.launch(Dispatchers.IO) {
			runCatching {
				val dir = File(getApplication<Application>().cacheDir, "exports").apply { mkdirs() }
				val file = File(dir, "pecalang-${report.finishedAt}.json")
				file.writeText(Json { prettyPrint = true }.encodeToString(Report.serializer(), report))
				return@runCatching FileProvider.getUriForFile(
					getApplication(), "${getApplication<Application>().packageName}.files", file,
				)
			}.onSuccess { uri -> launch(Dispatchers.Main) { onReady(uri) } }
		}
	}
}
