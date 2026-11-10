package dev.triage.app.ai

import android.content.Context
import dev.triage.app.model.Evidence
import dev.triage.app.model.Finding
import dev.triage.app.model.Origin
import dev.triage.app.model.Severity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

sealed interface AiRunState {
	data object Idle : AiRunState
	data class Running(val step: Int, val label: String) : AiRunState
	data object Done : AiRunState
	data class Failed(val message: String) : AiRunState
}

/**
 * Orchestrates one deep scan. UI state lives here so the settings screen and the
 * report screen see the same run; persistence stays with the caller.
 */
object AiRunner {

	private val _state = MutableStateFlow<AiRunState>(AiRunState.Idle)
	val state: StateFlow<AiRunState> = _state.asStateFlow()

	suspend fun run(
		ctx: Context,
		cfg: AiConfig,
		evidence: Evidence,
		ruleTitles: List<String>,
		onStep: (Int, String) -> Unit = { _, _ -> },
		extraContext: String = "",
	): AiOutcome {
		_state.value = AiRunState.Running(0, "asking the model")
		val outcome = DeepScan.run(
			cfg = cfg,
			e = evidence,
			ruleTitles = ruleTitles,
			onStep = { step, label ->
				_state.value = AiRunState.Running(step, label)
				onStep(step, label)
			},
			extraContext = extraContext,
		)
		_state.value = if (outcome.error != null && outcome.findings.isEmpty()) {
			AiRunState.Failed(outcome.error)
		} else {
			AiRunState.Done
		}
		return outcome
	}

	fun reset() {
		_state.value = AiRunState.Idle
	}

	/** AI findings are advisory: they never change the rule score. */
	fun summarise(ai: List<Finding>): String {
		if (ai.isEmpty()) return "No additional findings"
		val top = ai.maxOf { it.severity }
		return "${ai.size} AI finding(s), highest ${top.label.lowercase()}"
	}

	fun needsAttention(ai: List<Finding>): Boolean = ai.any { it.severity >= Severity.MEDIUM }

	fun anyAi(findings: List<Finding>): Boolean = findings.any { it.origin == Origin.AI }
}
