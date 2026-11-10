package dev.triage.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {

	private val vm: ScanViewModel by viewModels()

	private val askNotifications =
		registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

	override fun onCreate(savedInstanceState: Bundle?) {
		enableEdgeToEdge()
		super.onCreate(savedInstanceState)
		maybeAskNotifications()

		setContent {
			TriageTheme {
				val state by vm.state.collectAsStateWithLifecycle()
				val aiState by vm.aiState.collectAsStateWithLifecycle()
				TriageScreen(
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

	private fun maybeAskNotifications() {
		if (Build.VERSION.SDK_INT < 33) return
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
			PackageManager.PERMISSION_GRANTED
		) return
		askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
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
