package dev.triage.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.triage.app.ai.AiConfig
import dev.triage.app.ai.AiStore
import dev.triage.app.ai.Clients
import dev.triage.app.ai.Tools
import dev.triage.app.model.Severity
import dev.triage.app.monitor.Watch
import kotlinx.coroutines.launch

/**
 * Bring-your-own model. Nothing here is mandatory; without it the app is the same
 * deterministic scanner as before.
 */
class AiSettingsActivity : ComponentActivity() {

	private val store by lazy { AiStore(this) }

	override fun onCreate(savedInstanceState: Bundle?) {
		enableEdgeToEdge()
		super.onCreate(savedInstanceState)
		setContent {
			TriageTheme {
				AiSettingsScreen(
					initial = store.load(),
					watchInitial = store.watchEnabled(),
					onBack = { finish() },
					onSave = { cfg, watch ->
						store.save(cfg)
						store.setWatch(watch)
						Watch.schedule(this, watch)
						if (watch) Watch.recheck(this)
					},
					onTest = { cfg -> Clients.test(cfg) },
					onCheckNow = { Watch.recheck(this) },
				)
			}
		}
	}
}

@Composable
private fun AiSettingsScreen(
	initial: AiConfig,
	watchInitial: Boolean,
	onBack: () -> Unit,
	onSave: (AiConfig, Boolean) -> Unit,
	onTest: suspend (AiConfig) -> Unit,
	onCheckNow: () -> Unit,
) {
	var provider by remember { mutableStateOf(initial.provider) }
	var base by remember { mutableStateOf(initial.baseUrl.ifBlank { provider.defaultBase }) }
	var model by remember { mutableStateOf(initial.model) }
	var key by remember { mutableStateOf(initial.key) }
	var names by remember { mutableStateOf(initial.sharePackageNames) }
	var steps by remember { mutableStateOf(initial.maxSteps.toFloat()) }
	var watch by remember { mutableStateOf(watchInitial) }
	var status by remember { mutableStateOf("") }
	var busy by remember { mutableStateOf(false) }
	val scope = rememberCoroutineScope()

	fun cfg() = AiConfig(
		provider = provider,
		baseUrl = if (base.isBlank()) provider.defaultBase else base,
		model = model,
		key = key,
		sharePackageNames = names,
		maxSteps = steps.toInt(),
	)

	Column(
		Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()
			.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
	) {
		Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
			IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
			Text("AI & protection", style = MaterialTheme.typography.titleLarge)
		}

		Text(
			"Triage stays rule-based. A model you connect is used only for the extra deep scan and for watching changes. Your key is stored encrypted on this phone.",
			color = TextDim, style = MaterialTheme.typography.bodySmall,
		)

		Spacer(Modifier.height(18.dp))
		Section("Model provider")
		AiConfig.Provider.entries.forEach { p ->
			Row(
				Modifier.fillMaxWidth().padding(vertical = 2.dp),
				verticalAlignment = Alignment.CenterVertically,
			) {
				RadioButton(
					selected = provider == p,
					onClick = {
						provider = p
						if (p.defaultBase.isNotBlank()) base = p.defaultBase
					},
				)
				Column {
					Text(p.label)
					if (p == AiConfig.Provider.MOCK) {
						Text("No key, no network, scripted answers", color = TextDim, style = MaterialTheme.typography.bodySmall)
					}
				}
			}
		}

		if (provider != AiConfig.Provider.NONE && provider != AiConfig.Provider.MOCK) {
			Spacer(Modifier.height(10.dp))
			OutlinedTextField(
				value = base, onValueChange = { base = it },
				label = { Text("Base URL") },
				singleLine = true, modifier = Modifier.fillMaxWidth(),
				keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
			)
			Spacer(Modifier.height(8.dp))
			OutlinedTextField(
				value = model, onValueChange = { model = it },
				label = { Text("Model") }, placeholder = { Text("e.g. gpt-4.1-mini, claude-sonnet-4-5") },
				singleLine = true, modifier = Modifier.fillMaxWidth(),
			)
			Spacer(Modifier.height(8.dp))
			OutlinedTextField(
				value = key, onValueChange = { key = it },
				label = { Text("API key") }, singleLine = true,
				visualTransformation = PasswordVisualTransformation(),
				modifier = Modifier.fillMaxWidth(),
			)
			Text(
				"The key is sent only to that base URL. An OpenAI-compatible endpoint can be a local server.",
				color = TextDim, style = MaterialTheme.typography.bodySmall,
			)
		}

		Spacer(Modifier.height(18.dp))
		Section("What may leave the phone")
		Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
			Column(Modifier.weight(1f)) {
				Text("Include package names")
				Text(
					"Off sends app labels only — still useful, slightly weaker.",
					color = TextDim, style = MaterialTheme.typography.bodySmall,
				)
			}
			Switch(names, { names = it })
		}

		Spacer(Modifier.height(14.dp))
		Text("Probe budget: ${steps.toInt()} steps", fontWeight = FontWeight.Medium)
		Slider(steps, { steps = it }, valueRange = 2f..10f, steps = 7)

		Spacer(Modifier.height(12.dp))
		Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
			Button(
				onClick = {
					onSave(cfg(), watch)
					status = "Saved"
				},
				colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink),
			) { Text("Save") }
			OutlinedButton(
				enabled = !busy && cfg().configured,
				onClick = {
					busy = true
					status = "Testing…"
					scope.launch {
						runCatching { onTest(cfg()) }
							.onSuccess { status = "Connection OK" }
							.onFailure { status = "Failed: ${it.message?.take(160)}" }
						busy = false
					}
				},
			) { Text("Test connection") }
		}
		if (status.isNotBlank()) {
			Spacer(Modifier.height(8.dp))
			Text(status, color = if (status.startsWith("Failed")) Severity.HIGH.color() else TextDim)
		}

		Spacer(Modifier.height(22.dp))
		Section("Dynamic protection")
		Text(
			"Watch for changes even when you don't open the app: new installs, new device admins, new accessibility access, new certificates, VPN or developer-flag changes. One notification when something actually changed.",
			color = TextDim, style = MaterialTheme.typography.bodySmall,
		)
		Spacer(Modifier.height(8.dp))
		Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
			Column(Modifier.weight(1f)) {
				Text("Watch this phone")
				Text(
					"Checks at most every 15 minutes and on install events. Needs the model above to explain a change; without it you still get a plain alert.",
					color = TextDim, style = MaterialTheme.typography.bodySmall,
				)
			}
			Switch(watch, {
				watch = it
				onSave(cfg(), it)
			})
		}
		if (watch) {
			Spacer(Modifier.height(8.dp))
			OutlinedButton(
				onClick = { onCheckNow() },
				modifier = Modifier.fillMaxWidth(),
			) { Text("Check for changes now") }
		}

		Spacer(Modifier.height(10.dp))
		Text("Tool catalogue the model may call", fontWeight = FontWeight.Medium)
		Spacer(Modifier.height(4.dp))
		Surface(color = SurfaceHigh, shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp)) {
			Text(
				Tools.catalog, color = TextDim, fontSize = 11.sp,
				modifier = Modifier.padding(10.dp),
			)
		}

		Spacer(Modifier.height(12.dp))
		Text(
			"Only the evidence bundle above is sent: build, flags, package/permission inventory, access lists, network configuration and indicator matches. Never messages, photos, files, accounts or location.",
			color = TextDim, style = MaterialTheme.typography.bodySmall,
		)

		Spacer(Modifier.height(16.dp))
		OutlinedButton(
			onClick = {
				provider = AiConfig.Provider.NONE
				onSave(cfg().copy(provider = AiConfig.Provider.NONE), false)
				status = "AI off"
			},
			modifier = Modifier.fillMaxWidth(),
		) { Text("Turn AI off") }

		Spacer(Modifier.height(30.dp))
	}
}

@Composable
private fun Section(text: String) {
	Text(text, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 4.dp))
}
