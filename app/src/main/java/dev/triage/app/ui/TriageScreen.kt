package dev.triage.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Scaffold
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.triage.app.data.ScanState
import dev.triage.app.model.Confidence
import dev.triage.app.model.Finding
import dev.triage.app.model.Origin
import dev.triage.app.model.Report
import dev.triage.app.model.Severity
import dev.triage.app.rules.Engine

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PecalangScreen(
	state: ScanState,
	onScan: () -> Unit,
	onExport: (Report) -> Unit,
	onFix: (Finding) -> Unit,
	onTraining: () -> Unit,
	onDeepScan: () -> Unit,
	onAiSettings: () -> Unit,
	aiState: dev.triage.app.ai.AiRunState,
) {
	val ctx = LocalContext.current
	var menu by remember { mutableStateOf(false) }
	var sheet by remember { mutableStateOf<Sheet>(Sheet.None) }
	var showNotes by rememberSaveable { mutableStateOf(false) }
	var showAll by rememberSaveable { mutableStateOf(false) }
	var showGaps by rememberSaveable { mutableStateOf(false) }
	LaunchedEffect(state) { if (state is ScanState.Idle) onScan() }
	val report = (state as? ScanState.Done)?.report

	Scaffold(
		containerColor = Color.Transparent,
		topBar = {
			Box(Modifier.fillMaxWidth().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
				Row(Modifier.widthIn(max = 660.dp).fillMaxWidth().padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
					Wordmark(Modifier.weight(1f))
					IconButton(onAiSettings) { Icon(Icons.Outlined.Tune, "Settings", tint = TextDim, modifier = Modifier.size(21.dp)) }
					Box {
						IconButton({ menu = true }) { Icon(Icons.Filled.MoreVert, "More options", tint = TextDim, modifier = Modifier.size(21.dp)) }
						DropdownMenu(menu, { menu = false }) {
							DropdownMenuItem({ Text("Share report") }, { menu = false; report?.let(onExport) }, enabled = report != null)
							DropdownMenuItem({ Text("How scoring works") }, { menu = false; sheet = Sheet.Scoring })
							DropdownMenuItem({ Text("Check limitations") }, { menu = false; sheet = Sheet.Limits })
							DropdownMenuItem({ Text("Indicator sources") }, { menu = false; sheet = Sheet.Sources })
							DropdownMenuItem({ Text("Training example") }, { menu = false; onTraining() })
							DropdownMenuItem({ Text("Privacy") }, { menu = false; sheet = Sheet.Privacy })
						}
					}
				}
			}
		},
		bottomBar = {
			Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
				GlassPanel(Modifier.widthIn(max = 628.dp).fillMaxWidth()) {
					Column(Modifier.padding(10.dp)) {
						PrimaryAction(
							label = if (state is ScanState.Scanning) "Checking device…" else if (report == null) "Check this phone" else "Run a new check",
							onClick = onScan, enabled = state !is ScanState.Scanning, busy = state is ScanState.Scanning,
						)
						Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 2.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
							Icon(Icons.Outlined.Lock, null, Modifier.size(11.dp), tint = TextDim)
							Spacer(Modifier.width(6.dp))
							Text("On-device checks. No account required.", color = TextDim, style = MaterialTheme.typography.labelMedium)
						}
					}
				}
			}
		},
	) { insets ->
		Box(Modifier.fillMaxSize().padding(insets), contentAlignment = Alignment.TopCenter) {
			Column(Modifier.widthIn(max = 660.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
				Spacer(Modifier.height(12.dp))
				Eyebrow("Device security")
				Text("Overview", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(top = 6.dp, bottom = 6.dp).semantics { heading() })
				Text("Know what needs your attention.", color = TextDim, style = MaterialTheme.typography.bodyMedium)
				Spacer(Modifier.height(24.dp))

				if (report?.training == true) {
					Surface(color = Severity.MEDIUM.color().copy(alpha = 0.12f), shape = RoundedCornerShape(8.dp)) {
						Text("Training example · not a real device check", color = Severity.MEDIUM.color(), style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(12.dp))
					}
					Spacer(Modifier.height(12.dp))
				}
				when (state) {
					ScanState.Idle, is ScanState.Scanning -> Scanning()
					is ScanState.Failed -> Failed(state.message, onScan)
					is ScanState.Done -> {
						val result = state.report
						Score(result) { sheet = Sheet.Scoring }
						ReportMetrics(result)
						Spacer(Modifier.height(28.dp))
						val actionable = result.findings.filter { it.severity >= Severity.LOW }.sortedByDescending { it.severity }
						val notes = result.findings.filter { it.severity == Severity.INFO }
						SectionHeading("Review priorities", "${actionable.size} findings")
						if (actionable.isEmpty()) {
							Card {
								Icon(Icons.Outlined.VerifiedUser, null, Modifier.size(24.dp), tint = TextDim)
								Spacer(Modifier.height(12.dp))
								Text("No known issues found", style = MaterialTheme.typography.titleMedium)
								Text("No actionable risks were found. A clean check does not prove this device is free of spyware.", color = TextDim, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
							}
						} else {
							ContentPanel(Modifier.fillMaxWidth()) {
								Column {
									val shown = if (showAll) actionable else actionable.take(3)
									shown.forEachIndexed { index, finding ->
										if (index > 0) HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = Hairline)
										FindingRow(finding) { sheet = Sheet.Detail(finding) }
									}
									if (actionable.size > 3) {
										HorizontalDivider(color = Hairline)
										TextButton({ showAll = !showAll }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(if (showAll) "Show fewer findings" else "View all ${actionable.size} findings") }
									}
								}
							}
						}
						if (notes.isNotEmpty()) {
							TextButton({ showNotes = !showNotes }, Modifier.padding(top = 4.dp)) { Text(if (showNotes) "Hide informational notes" else "${notes.size} informational notes") }
							if (showNotes) ContentPanel(Modifier.fillMaxWidth()) {
								Column { notes.forEach { finding -> FindingRow(finding) { sheet = Sheet.Detail(finding) } } }
							}
						}
						Spacer(Modifier.height(28.dp))
						SectionHeading("A closer look")
						AiBlock(aiState, result, onDeepScan) { sheet = Sheet.Detail(it) }
						Spacer(Modifier.height(28.dp))
						SectionHeading("Check coverage", "${result.checksRun} checks")
						ContentPanel(Modifier.fillMaxWidth()) {
							Column(Modifier.padding(18.dp)) {
								Text(if (result.gaps.isEmpty() && result.collectorErrors.isEmpty()) "Available checks completed" else "Some areas could not be checked", style = MaterialTheme.typography.titleMedium)
								Text("${java.text.NumberFormat.getIntegerInstance().format(result.indicatorCount)} published indicators · updated ${result.indicatorAsOf.ifBlank { "date unavailable" }}", color = TextDim, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
								if (result.gaps.isNotEmpty()) {
									TextButton({ showGaps = !showGaps }) {
										Text(if (showGaps) "Hide coverage gaps" else "Review ${result.gaps.size} coverage gaps")
										Icon(if (showGaps) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, Modifier.size(18.dp))
									}
									if (showGaps) result.gaps.forEach { Text("• ${it.text}", color = TextDim, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 10.dp)) }
								}
								if (result.collectorErrors.isNotEmpty()) Text("${result.collectorErrors.size} collection errors: ${result.collectorErrors.joinToString(" · ")}", color = Severity.MEDIUM.color(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
							}
						}
						Spacer(Modifier.height(24.dp))
						Eyebrow("This device")
						Text("${result.deviceLabel}\nAndroid ${result.androidRelease} · Security patch ${result.patchLevel}", color = TextDim, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
					}
				}
				Spacer(Modifier.height(28.dp))
			}
		}
	}

	val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
	if (sheet != Sheet.None) {
		ModalBottomSheet(
			onDismissRequest = { sheet = Sheet.None },
			sheetState = sheetState,
			containerColor = Surface,
		) {
			Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) {
				TextButton({ sheet = Sheet.None }) { Text("Done") }
			}
			when (val s = sheet) {
				is Sheet.Detail -> Detail(s.finding, { finding -> sheet = Sheet.None; onFix(finding) }) {
					ctx.copy(s.finding)
					android.widget.Toast.makeText(ctx, "Finding copied", android.widget.Toast.LENGTH_SHORT).show()
					sheet = Sheet.None
				}
				Sheet.Scoring -> StaticSheet(
					"Scoring",
					"Start at 100 and subtract. A critical finding with confirmed evidence costs 25 points, a high one 15, a medium 8, a low 3 and a note 1. Each finding is scaled down when evidence is weaker (likely ×0.85, possible ×0.6).\n\nThe score only exists to show movement over time. The list is the truth: two phones with the same score can need very different work.",
				)
				Sheet.Limits -> StaticSheet(
					"What this can't see",
					"• A fresh, unknown spyware implant. Package fingerprints, signing certificates and file hashes only match samples somebody published — usually after an attack, and often only for a few weeks.\n• Anything inside an app's private storage without a backup or root.\n• DNS lookups, connection attempts and process starts; system logs need a USB collection.\n• Apps that were installed and then removed; Android keeps that history in a shell-only record.\n• Cellebrite unlock traces; those live in the phone's logs and expire.\n• Network traffic contents, or other devices on the network.\n\nTreat a clean scan as \"no known problem\", not \"proven clean\".",
				)
				Sheet.Sources -> StaticSheet(
					"Indicator sources",
					"Pecalang bundles the Android-relevant part of the Zero Breach Toolkit corpus (CC0), as of the date shown on the report.\n\nValues come from published research by ESET, Cisco Talos, SentinelLabs, Meta, Volexity, Unit 42, Lookout, Amnesty International's Security Lab, the Citizen Lab and the Mobile Verification Toolkit project. Each entry keeps its source tag. The file header carries the full citations.\n\nMatched artifacts: package name (weakest — a real app's name can be reused), signing certificate (strong), APK file hash (strongest).\n\nNo network is used: the corpus ships inside the app and goes stale between releases. Check the as-of date.",
				)
				Sheet.Privacy -> StaticSheet(
					"Privacy",
					"All collection runs locally. Optional AI sends the evidence bundle to the provider you configure when you enable it.\n\nWhat is read: Android build and patch level, lock-screen state, developer flags, installed package names and their granted permissions, accessibility/admin/notification/overlay/usage access lists, VPN and proxy configuration, Wi-Fi security type and installed user certificates.\n\nWhat is not read: contacts, messages, photos, files, accounts, location history, or traffic contents.\n\nThe report stays in app-private storage until you share it yourself.",
				)
				Sheet.None -> Unit
			}
		}
	}
}

private sealed interface Sheet {
	data object None : Sheet
	data object Scoring : Sheet
	data object Limits : Sheet
	data object Sources : Sheet
	data object Privacy : Sheet
	data class Detail(val finding: Finding) : Sheet
}

@Composable
private fun Scanning() {
	Card {
		Row(verticalAlignment = Alignment.CenterVertically) {
			CircularProgressIndicator(Modifier.size(20.dp), color = Accent, strokeWidth = 2.dp)
			Spacer(Modifier.width(12.dp))
			Text("Checking installed apps and access settings…", fontWeight = FontWeight.Medium)
		}
		Spacer(Modifier.height(14.dp))
		LinearProgressIndicator(Modifier.fillMaxWidth(), color = Accent, trackColor = SurfaceHigh)
		Spacer(Modifier.height(10.dp))
		Text("Takes a few seconds. You can leave the app.", color = TextDim, style = MaterialTheme.typography.bodySmall)
	}
}

@Composable
private fun Failed(message: String, onRetry: () -> Unit) {
	Card {
		Text("The check stopped early", fontWeight = FontWeight.SemiBold, color = Severity.HIGH.color())
		Spacer(Modifier.height(6.dp))
		Text(message, color = TextDim)
		Spacer(Modifier.height(10.dp))
		OutlinedButton(onRetry) { Text("Try again") }
	}
}

@Composable
private fun AiBlock(
	state: dev.triage.app.ai.AiRunState,
	report: Report,
	onRun: () -> Unit,
	onOpen: (Finding) -> Unit,
) {
	ContentPanel(modifier = Modifier.fillMaxWidth()) {
		Column(Modifier.padding(18.dp)) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				Icon(Icons.Outlined.AutoAwesome, null, tint = TextDim, modifier = Modifier.size(18.dp))
				Spacer(Modifier.width(8.dp))
				Text("AI review", fontWeight = FontWeight.SemiBold)
				Spacer(Modifier.weight(1f))
				Text("Optional", color = TextDim, style = MaterialTheme.typography.labelSmall)
			}
			Spacer(Modifier.height(4.dp))
			Text(
				"Connect your own model for a second perspective. Advisory results never change your score.",
				color = TextDim, style = MaterialTheme.typography.bodySmall,
			)

			when (state) {
				is dev.triage.app.ai.AiRunState.Running -> {
					Spacer(Modifier.height(10.dp))
					Row(verticalAlignment = Alignment.CenterVertically) {
						CircularProgressIndicator(Modifier.size(16.dp), color = Accent, strokeWidth = 2.dp)
						Spacer(Modifier.width(8.dp))
						Text(
							if (state.step == 0) state.label else "step ${state.step}: ${state.label}",
							color = TextDim, style = MaterialTheme.typography.bodySmall,
						)
					}
				}
				is dev.triage.app.ai.AiRunState.Failed -> {
					Spacer(Modifier.height(8.dp))
					Text(state.message, color = Severity.HIGH.color(), style = MaterialTheme.typography.bodySmall)
				}
				else -> Unit
			}

			if (report.aiError != null && state !is dev.triage.app.ai.AiRunState.Running) {
				Spacer(Modifier.height(8.dp))
				Text("Last run: ${report.aiError}", color = TextDim, style = MaterialTheme.typography.bodySmall)
			}
			report.aiMeta?.let { m ->
				Spacer(Modifier.height(8.dp))
				Text(
					listOfNotNull(
						m.provider,
						m.model.takeIf { it.isNotBlank() && it != "-" },
						"${m.steps} step(s)",
						"${m.tokens} tokens",
					).joinToString(" · "),
					color = TextDim, style = MaterialTheme.typography.bodySmall,
				)
			}

			if (report.aiFindings.isNotEmpty()) {
				HorizontalDivider(Modifier.padding(top = 14.dp), color = Hairline)
				report.aiFindings.forEach { finding -> FindingRow(finding) { onOpen(finding) } }
			}

			Spacer(Modifier.height(8.dp))
			OutlinedButton(
				onClick = onRun,
				enabled = state !is dev.triage.app.ai.AiRunState.Running,
				modifier = Modifier.fillMaxWidth(),
			) {
				Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(16.dp))
				Spacer(Modifier.width(6.dp))
				Text(if (report.aiFindings.isEmpty() && report.aiMeta == null) "Start AI review" else "Review again")
			}
		}
	}
}

@Composable
private fun Score(report: Report, onExplain: () -> Unit) {
	val statusColor = when {
		report.score >= 90 -> TextPrimary
		report.score >= 75 -> Severity.MEDIUM.color()
		report.score >= 55 -> Severity.HIGH.color()
		else -> Severity.CRITICAL.color()
	}
	val description = if (report.collectorErrors.isNotEmpty()) "Partial check · review coverage" else when {
		report.score >= 90 -> "No major risks identified"
		report.score >= 75 -> "A few things to review"
		else -> "Review recommended"
	}
	val track = Hairline
	val dialColor = TextPrimary
	val wide = LocalDensity.current.fontScale <= 1.3f && androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 380
	Surface(
		onClick = onExplain, shape = RoundedCornerShape(20.dp), color = Surface,
		border = BorderStroke(1.dp, Hairline), modifier = Modifier.fillMaxWidth(),
	) {
		Column(Modifier.background(Brush.linearGradient(listOf(SurfaceHigh.copy(alpha = 0.4f), Color.Transparent))).padding(22.dp)) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				Text("Security posture", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
				Icon(Icons.Outlined.Info, "About this score", Modifier.size(17.dp), tint = TextDim)
			}
			Spacer(Modifier.height(12.dp))
			Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) { contentDescription = "Security score ${report.score} out of 100. ${Engine.grade(report.score)}. $description" }, verticalAlignment = Alignment.CenterVertically) {
				Column(Modifier.weight(1f)) {
					Row(verticalAlignment = Alignment.Bottom) {
						Text("${report.score}", style = MaterialTheme.typography.displayLarge)
						Text("/ 100", color = TextDim, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp, bottom = 16.dp))
					}
				}
				if (wide) Box(Modifier.padding(start = 12.dp).size(94.dp), contentAlignment = Alignment.Center) {
					Canvas(Modifier.fillMaxSize()) {
						val width = 2.5.dp.toPx()
						val inset = width / 2
						val arc = Size(size.width - width, size.height - width)
						repeat(40) { index ->
							drawArc(if (index < report.score * 0.4f) dialColor else track, -90f + index * 9, 4f, false, Offset(inset, inset), arc, style = Stroke(width, cap = StrokeCap.Round))
						}
					}
					Icon(Icons.Outlined.VerifiedUser, null, Modifier.size(27.dp), tint = TextDim)
				}
			}
			Row(Modifier.padding(top = 4.dp, bottom = 20.dp), verticalAlignment = Alignment.CenterVertically) {
				Box(Modifier.size(5.dp).clip(RoundedCornerShape(3.dp)).background(statusColor))
				Spacer(Modifier.width(8.dp))
				Text(description, color = statusColor, style = MaterialTheme.typography.labelMedium)
			}
			HorizontalDivider(color = Hairline)
			Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
				Text("${report.highCount + report.criticalCount} high-priority findings", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), color = TextDim)
				Text(java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(java.util.Date(report.finishedAt)), color = TextDim, style = MaterialTheme.typography.labelMedium)
			}
			Text(java.text.DateFormat.getDateInstance(java.text.DateFormat.MEDIUM).format(java.util.Date(report.finishedAt)), color = TextDim, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 6.dp))
		}
	}
}

@Composable
private fun ReportMetrics(report: Report) {
	Row(Modifier.fillMaxWidth().padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
		listOf("${report.checksRun}" to "Checks run", java.text.NumberFormat.getIntegerInstance().format(report.indicatorCount) to "Indicators", "${report.gaps.size}" to "Coverage gaps").forEachIndexed { index, (value, label) ->
			if (index > 0) Box(Modifier.width(1.dp).height(38.dp).background(Hairline))
			Column(Modifier.weight(1f).padding(start = if (index == 0) 0.dp else 8.dp)) {
				Text(value, style = MaterialTheme.typography.titleLarge)
				Text(label, color = TextDim, style = MaterialTheme.typography.bodySmall)
			}
		}
	}
}

@Composable
private fun FindingRow(finding: Finding, onClick: () -> Unit) {
	Surface(color = Color.Transparent, onClick = onClick, modifier = Modifier.fillMaxWidth()) {
		Row(Modifier.heightIn(min = 88.dp).padding(horizontal = 16.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
			Box(Modifier.size(32.dp).background(SurfaceHigh, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
				Icon(
					when (finding.impact) {
						dev.triage.app.model.Impact.INSTALLED_APP -> Icons.Outlined.Apps
						dev.triage.app.model.Impact.NETWORK -> Icons.Outlined.Wifi
						dev.triage.app.model.Impact.ACCOUNT -> Icons.Outlined.PersonOutline
						else -> Icons.Outlined.Security
					}, null, Modifier.size(17.dp), tint = TextDim,
				)
			}
			Spacer(Modifier.width(12.dp))
			Column(Modifier.weight(1f)) {
				if (finding.origin == Origin.AI) Eyebrow("AI advisory", Modifier.padding(bottom = 4.dp))
				Text(finding.title, style = MaterialTheme.typography.titleSmall)
				Row(Modifier.padding(top = 5.dp), verticalAlignment = Alignment.CenterVertically) {
					Text(finding.severity.label, color = finding.severity.color(), style = MaterialTheme.typography.labelMedium)
					Text("  ·  ${confidenceLabel(finding.confidence)}", color = TextDim, style = MaterialTheme.typography.bodySmall)
				}
				finding.appLabel?.let { Text(it, color = TextDim, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 3.dp)) }
			}
			Spacer(Modifier.width(8.dp))
			Icon(Icons.Rounded.ChevronRight, null, Modifier.size(18.dp), tint = TextDim)
		}
	}
}

@Composable
private fun Detail(finding: Finding, onFix: (Finding) -> Unit, onCopy: () -> Unit) {
	Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).imePadding().navigationBarsPadding()) {
		Row(verticalAlignment = Alignment.CenterVertically) {
			Surface(color = finding.severity.color().copy(alpha = 0.16f), shape = RoundedCornerShape(6.dp)) {
				Text(
					finding.severity.label, color = finding.severity.color(),
					style = MaterialTheme.typography.labelMedium,
					modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
				)
			}
			if (finding.origin == Origin.AI) {
				Spacer(Modifier.width(6.dp))
				Surface(color = Accent.copy(alpha = 0.16f), shape = RoundedCornerShape(6.dp)) {
					Text(
						"AI", color = Accent, style = MaterialTheme.typography.labelMedium,
						modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
					)
				}
			}
			Spacer(Modifier.width(8.dp))
			Text(
				confidenceLabel(finding.confidence),
				color = TextDim, style = MaterialTheme.typography.labelMedium,
			)
		}
		Spacer(Modifier.height(10.dp))
		Text(finding.title, style = MaterialTheme.typography.headlineMedium, color = TextPrimary)
		Spacer(Modifier.height(6.dp))
		Text(finding.impact.label, color = TextDim, style = MaterialTheme.typography.bodySmall)

		Spacer(Modifier.height(16.dp))
		Eyebrow("Observation")
		Spacer(Modifier.height(4.dp))
		Text(finding.observed, color = TextDim)

		Spacer(Modifier.height(16.dp))
		Eyebrow("Why it matters")
		Spacer(Modifier.height(4.dp))
		Text(finding.why, color = TextDim)

		Spacer(Modifier.height(16.dp))
		Eyebrow("Recommended action")
		Spacer(Modifier.height(4.dp))
		Text(finding.fix, color = TextPrimary)

		if (finding.appPackage != null) {
			Spacer(Modifier.height(12.dp))
			Text(finding.appPackage, color = TextDim, style = MaterialTheme.typography.bodySmall)
		}

		Spacer(Modifier.height(20.dp))
		Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
			PrimaryAction("Open device settings", { onFix(finding) })
			OutlinedButton(onClick = onCopy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Copy finding") }
		}
		Spacer(Modifier.height(24.dp))
	}
}

@Composable
private fun StaticSheet(title: String, body: String) {
	Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).navigationBarsPadding()) {
		Text(title, style = MaterialTheme.typography.titleLarge)
		Spacer(Modifier.height(12.dp))
		SelectionContainer { Text(body, color = TextDim) }
		Spacer(Modifier.height(28.dp))
	}
}

@Composable
private fun Card(content: @Composable () -> Unit) {
	ContentPanel(modifier = Modifier.fillMaxWidth()) {
		Column(Modifier.padding(16.dp)) { content() }
	}
}

private fun confidenceLabel(c: Confidence) = when (c) {
	Confidence.CONFIRMED -> "Confirmed"
	Confidence.LIKELY -> "Likely"
	Confidence.POSSIBLE -> "Possible"
}

private fun Context.copy(f: Finding) {
	val text = "${f.title}\n\n${f.observed}\n\nWhy: ${f.why}\n\nFix: ${f.fix}"
	val cm = getSystemService(ClipboardManager::class.java) ?: return
	cm.setPrimaryClip(ClipData.newPlainText("Pecalang finding", text))
}
