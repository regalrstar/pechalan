package dev.triage.app.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
	private val askNotifications = registerForActivityResult(
		androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
	) { }

	private fun requestWatchNotifications() {
		if (android.os.Build.VERSION.SDK_INT >= 33 &&
			androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
		) askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
	}

	override fun onCreate(savedInstanceState: Bundle?) {
		enableEdgeToEdge()
		super.onCreate(savedInstanceState)
		setContent {
			PecalangTheme {
				AiSettingsScreen(
					initial = store.load(),
					watchInitial = store.watchEnabled(),
					onBack = { finish() },
					onSave = { cfg, watch ->
						store.save(cfg)
						store.setWatch(watch)
						Watch.schedule(this, watch)
						if (watch) {
							requestWatchNotifications()
							Watch.recheck(this)
						}
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
	var provider by rememberSaveable { mutableStateOf(initial.provider) }
	var base by rememberSaveable { mutableStateOf(initial.baseUrl.ifBlank { provider.defaultBase }) }
	var model by rememberSaveable { mutableStateOf(initial.model) }
	var key by remember { mutableStateOf(initial.key) }
	var names by rememberSaveable { mutableStateOf(initial.sharePackageNames) }
	var steps by rememberSaveable { mutableStateOf(initial.maxSteps.toFloat()) }
	var watch by rememberSaveable { mutableStateOf(watchInitial) }
	var status by remember { mutableStateOf("") }
	var busy by remember { mutableStateOf(false) }
	var showKey by remember { mutableStateOf(false) }
	var showTools by rememberSaveable { mutableStateOf(false) }
	val scope = rememberCoroutineScope()
	val context = LocalContext.current
	val reduceTransparency = reducedTransparency()
	fun cfg() = AiConfig(provider, base.ifBlank { provider.defaultBase }, model.trim(), key.trim(), names, steps.toInt())

	Scaffold(
		containerColor = Color.Transparent,
		topBar = {
			Box(Modifier.fillMaxWidth().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
				Row(Modifier.widthIn(max = 660.dp).fillMaxWidth().padding(start = 10.dp, end = 24.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
					IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
					Spacer(Modifier.width(8.dp))
					Wordmark()
				}
			}
		},
		bottomBar = {
			Box(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
				GlassPanel(Modifier.widthIn(max = 628.dp).fillMaxWidth()) {
					Column(Modifier.padding(10.dp)) {
						PrimaryAction("Save settings", {
							onSave(cfg(), watch)
							status = "Settings saved"
						})
						if (status.isNotBlank()) Text(status, color = if (status.startsWith("Failed")) Severity.HIGH.color() else TextDim, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp).semantics { liveRegion = LiveRegionMode.Polite })
					}
				}
			}
		},
	) { insets ->
		Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
			Column(Modifier.widthIn(max = 660.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
				Spacer(Modifier.height(12.dp))
				Eyebrow("Make it yours")
				Text("Settings", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp).semantics { heading() })
				Text("Your device. Your model. Your control.", color = TextDim, style = MaterialTheme.typography.bodyMedium)
				Spacer(Modifier.height(28.dp))
				SectionHeading("Appearance", "Follows system")
				ContentPanel(Modifier.fillMaxWidth()) {
					PreferenceSwitch("Reduce transparency", "Use opaque controls for a clearer view.", reduceTransparency) {
						context.getSharedPreferences("appearance", android.content.Context.MODE_PRIVATE).edit().putBoolean("reduce_transparency", it).apply()
					}
				}
				Spacer(Modifier.height(28.dp))
				SectionHeading("AI model", "Optional")
				Text("Device checks work without AI. Connect your own model only if you want an additional review.", color = TextDim, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 12.dp))
				ContentPanel(Modifier.fillMaxWidth()) {
					Column(Modifier.selectableGroup()) {
						AiConfig.Provider.entries.forEachIndexed { index, p ->
							if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Hairline)
							Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).selectable(selected = provider == p, role = Role.RadioButton, onClick = {
								provider = p
								if (p.defaultBase.isNotBlank()) base = p.defaultBase
								status = ""
							}).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
								Column(Modifier.weight(1f)) {
									Text(p.label, style = MaterialTheme.typography.titleSmall)
									if (p == AiConfig.Provider.MOCK) Text("Offline example · no data leaves this phone", color = TextDim, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
								}
								RadioButton(provider == p, onClick = null)
							}
						}
					}
				}
				if (provider != AiConfig.Provider.NONE && provider != AiConfig.Provider.MOCK) {
					Spacer(Modifier.height(16.dp))
					OutlinedTextField(base, { base = it }, label = { Text("Provider URL") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
					Spacer(Modifier.height(12.dp))
					OutlinedTextField(model, { model = it }, label = { Text("Model name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
					Spacer(Modifier.height(12.dp))
					OutlinedTextField(key, { key = it }, label = { Text("API key") }, singleLine = true, modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(), trailingIcon = {
						IconButton({ showKey = !showKey }) { Icon(if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (showKey) "Hide API key" else "Show API key", Modifier.size(20.dp)) }
					})
					Text("Your key stays in this app’s storage on this phone. Requests go to the provider URL above.", color = TextDim, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
				}
				if (provider != AiConfig.Provider.NONE) {
					OutlinedButton(onClick = {
						val testConfig = cfg()
						busy = true
						status = "Testing ${testConfig.provider.label}…"
						scope.launch {
							try { onTest(testConfig); status = "${testConfig.provider.label}: connection confirmed" }
							catch (e: kotlinx.coroutines.CancellationException) { throw e }
							catch (e: Exception) { status = "Failed: ${e.message?.take(160)}" }
							finally { busy = false }
						}
					}, enabled = !busy && cfg().configured, modifier = Modifier.fillMaxWidth().padding(top = 14.dp).heightIn(min = 48.dp), shape = RoundedCornerShape(12.dp)) { Text(if (busy) "Testing connection…" else "Test connection") }
				}
				Spacer(Modifier.height(28.dp))
				SectionHeading("Privacy & sharing")
				ContentPanel(Modifier.fillMaxWidth()) {
					Column {
						PreferenceSwitch("Include package names", "When using AI. App labels and other scan evidence are still shared when this is off.", names) { names = it }
						HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Hairline)
						Column(Modifier.padding(18.dp)) {
							Row { Text("AI review limit", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f)); Text("${steps.toInt()} steps", color = TextDim, style = MaterialTheme.typography.bodySmall) }
							Slider(steps, { steps = it }, valueRange = 2f..10f, steps = 7, modifier = Modifier.semantics { contentDescription = "Maximum AI review steps" })
							Text("A bounded, read-only review. AI results never change your security score.", color = TextDim, style = MaterialTheme.typography.bodySmall)
						}
					}
				}
				Spacer(Modifier.height(28.dp))
				SectionHeading("Change watching")
				ContentPanel(Modifier.fillMaxWidth()) {
					Column {
						PreferenceSwitch("Watch this phone", "Look for changed app access, certificates and network settings. Checks run periodically and on install events.", watch) { watch = it }
						if (watch) {
							HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Hairline)
							Text("Save settings to apply change watching. Notifications are requested when you enable it.", color = TextDim, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(18.dp))
							OutlinedButton({ onCheckNow(); status = "Change check requested" }, Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 18.dp)) { Text("Check for changes now") }
						}
					}
				}
				Spacer(Modifier.height(20.dp))
				Text("Checks are read-only. AI can receive device and app evidence, never messages, photos or location history.", color = TextDim, style = MaterialTheme.typography.bodySmall)
				TextButton({ showTools = !showTools }) { Text(if (showTools) "Hide available AI tools" else "View available AI tools") }
				if (showTools) SelectionContainer { Text(Tools.catalog, color = TextDim, style = MaterialTheme.typography.bodySmall) }
				if (provider != AiConfig.Provider.NONE || watch) {
					TextButton({
						provider = AiConfig.Provider.NONE
						watch = false
						onSave(cfg().copy(provider = AiConfig.Provider.NONE), false)
						status = "AI and change watching turned off"
					}, Modifier.fillMaxWidth().padding(top = 12.dp)) { Text("Turn off AI & watching", color = MaterialTheme.colorScheme.error) }
				}
				Spacer(Modifier.height(32.dp))
			}
		}
	}
}

@Composable
private fun PreferenceSwitch(title: String, description: String, checked: Boolean, onChange: (Boolean) -> Unit) {
	Row(Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChange).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
		Column(Modifier.weight(1f)) {
			Text(title, style = MaterialTheme.typography.titleSmall)
			Text(description, color = TextDim, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 5.dp))
		}
		Spacer(Modifier.width(14.dp))
		Switch(checked, onCheckedChange = null)
	}
}
