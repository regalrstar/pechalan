package dev.triage.app.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {

	private val vm: ScanViewModel by viewModels()

	override fun onCreate(savedInstanceState: Bundle?) {
		enableEdgeToEdge()
		super.onCreate(savedInstanceState)

		setContent {
			PecalangTheme {
				val state by vm.state.collectAsStateWithLifecycle()
				val aiState by vm.aiState.collectAsStateWithLifecycle()
				PecalangScreen(
					state = state,
					onScan = vm::scan,
					onExport = { report -> vm.export(report) { uri -> share(uri) } },
					onFix = { finding -> FixIntents.open(this, finding) },
					onTraining = vm::showTraining,
					onDeepScan = {
						vm.deepScan { startActivity(Intent(this, AiSettingsActivity::class.java)) }
					},
					onAiSettings = { startActivity(Intent(this, AiSettingsActivity::class.java)) },
					aiState = aiState,
				)
			}
		}
	}

	private fun share(uri: android.net.Uri) {
		val send = Intent(Intent.ACTION_SEND).apply {
			type = "application/json"
			putExtra(Intent.EXTRA_STREAM, uri)
			addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
		}
		startActivity(Intent.createChooser(send, "Share report"))
	}
}
