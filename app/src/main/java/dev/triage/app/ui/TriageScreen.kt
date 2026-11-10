package dev.triage.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.OpenInNew
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
fun TriageScreen(
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
	var showNotes by remember { mutableStateOf(false) }
	var showAll by remember { mutableStateOf(false) }
	var showGaps by remember { mutableStateOf(false) }

	// First open: there is nothing to show yet, so run the first check.
	LaunchedEffect(state) {
		if (state is ScanState.Idle) onScan()
	}

	val report = (state as? ScanState.Done)?.report

	Column(
		Modifier
			.fillMaxSize()
			.background(Ink)
			.statusBarsPadding()
			.navigationBarsPadding()
			.verticalScroll(rememberScrollState())
			.padding(horizontal = 20.dp),
	) {
		Row(
			Modifier.fillMaxWidth().padding(top = 8.dp),
			verticalAlignment = Alignment.CenterVertically,
		) {
			Icon(Icons.Rounded.Shield, null, tint = Accent, modifier = Modifier.size(22.dp))
			Spacer(Modifier.width(8.dp))
			Text("TRIAGE", fontWeight = FontWeight.Bold, letterSpacing = 2.sp, color = TextPrimary)
			Spacer(Modifier.weight(1f))
			Box {
				IconButton({ menu = true }) {
					Icon(Icons.Filled.MoreVert, "Menu", tint = TextDim)
				}
				DropdownMenu(menu, { menu = false }) {
					DropdownMenuItem(
						text = { Text("Share report (JSON)") },
						onClick = { menu = false; report?.let(onExport) },
						enabled = report != null,
					)
					DropdownMenuItem({ Text("How scoring works") }, { menu = false; sheet = Sheet.Scoring })
					DropdownMenuItem({ Text("What this can't see") }, { menu = false; sheet = Sheet.Limits })
					DropdownMenuItem({ Text("Indicator sources") }, { menu = false; sheet = Sheet.Sources })
					DropdownMenuItem({ Text("AI & protection") }, { menu = false; onAiSettings() })
					DropdownMenuItem({ Text("Training example") }, { menu = false; onTraining() })
					DropdownMenuItem({ Text("Privacy") }, { menu = false; sheet = Sheet.Privacy })
				}
			}
		}

		Text(
			report?.let { "${it.deviceLabel} · Android ${it.androidRelease} · patch ${it.patchLevel}" }
				?: "Read-only check of this phone",
			color = TextDim,
			style = MaterialTheme.typography.bodySmall,
		)

		if (report?.training == true) {
			Spacer(Modifier.height(8.dp))
			Surface(color = Severity.MEDIUM.color().copy(alpha = 0.18f), shape = RoundedCornerShape(6.dp)) {
				Text(
					"TRAINING EXAMPLE — NOT A REAL CHECK",
					color = Severity.MEDIUM.color(),
					style = MaterialTheme.typography.labelSmall,
					fontWeight = FontWeight.Bold,
					modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
				)
			}
		}

		Spacer(Modifier.height(24.dp))

		when (state) {
			is ScanState.Scanning -> Scanning()
			is ScanState.Failed -> Failed(state.message, onScan)
			is ScanState.Done -> {
				Score(report!!)
				Spacer(Modifier.height(14.dp))
				AiBlock(aiState, report, onDeepScan) { f -> sheet = Sheet.Detail(f) }
				Spacer(Modifier.height(16.dp))
				val actionable = report.findings.filter { it.severity >= Severity.LOW }
				val notes = report.findings.filter { it.severity == Severity.INFO }
				val shown = if (showAll) actionable else actionable.take(4)

				if (actionable.isEmpty()) {
					Card {
						Row(verticalAlignment = Alignment.CenterVertically) {
							Icon(Icons.Rounded.CheckCircle, null, tint = Accent)
							Spacer(Modifier.width(10.dp))
							Text("Nothing needs attention", fontWeight = FontWeight.SemiBold)
						}
						Spacer(Modifier.height(6.dp))
						Text(
							"The obvious boxes are ticked. Android changes weekly, so keep the daily check running.",
							color = TextDim, style = MaterialTheme.typography.bodyMedium,
						)
					}
				} else {
					Text("Fix first", fontWeight = FontWeight.SemiBold, color = TextPrimary)
					Spacer(Modifier.height(10.dp))
					shown.forEach { finding -> FindingRow(finding) { sheet = Sheet.Detail(finding) } }
					if (!showAll && actionable.size > shown.size) {
						TextButton({ showAll = true }) { Text("Show all ${actionable.size}") }
					}
				}

				if (notes.isNotEmpty()) {
					Spacer(Modifier.height(12.dp))
					TextButton({ showNotes = !showNotes }) {
						Text(if (showNotes) "Hide notes" else "Notes (${notes.size})")
					}
					if (showNotes) notes.forEach { finding -> FindingRow(finding) { sheet = Sheet.Detail(finding) } }
				}

				Spacer(Modifier.height(12.dp))
				Text(
					"${report.checksRun} checks · ${report.findings.size} results" +
						if (report.collectorErrors.isEmpty()) "" else " · ${report.collectorErrors.size} collector error(s)",
					color = TextDim, style = MaterialTheme.typography.bodySmall,
				)
				if (report.indicatorCount > 0) {
					Text(
						"${report.indicatorCount} bundled spyware indicators, as of ${report.indicatorAsOf}",
						color = TextDim, style = MaterialTheme.typography.bodySmall,
					)
				}
				if (report.collectorErrors.isNotEmpty()) {
					Text(report.collectorErrors.joinToString(" · "), color = TextDim, style = MaterialTheme.typography.bodySmall)
				}

				if (report.gaps.isNotEmpty()) {
					Spacer(Modifier.height(14.dp))
					TextButton({ showGaps = !showGaps }) {
						Text("Could not check (${report.gaps.size})", color = Severity.MEDIUM.color())
						Icon(
							if (showGaps) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
							null, tint = Severity.MEDIUM.color(), modifier = Modifier.size(18.dp),
						)
					}
					if (showGaps) {
						report.gaps.forEach { gap ->
							Row(Modifier.padding(vertical = 3.dp)) {
								Text("—", color = TextDim)
								Spacer(Modifier.width(8.dp))
								Text(gap.text, color = TextDim, style = MaterialTheme.typography.bodySmall)
							}
						}
					}
				}
			}
			ScanState.Idle -> Unit
		}

		Spacer(Modifier.height(24.dp))
		Button(
			onClick = onScan,
			enabled = state !is ScanState.Scanning,
			modifier = Modifier.fillMaxWidth().height(52.dp),
			shape = RoundedCornerShape(14.dp),
			colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink),
		) {
			Icon(Icons.Rounded.Refresh, null, modifier = Modifier.size(18.dp))
			Spacer(Modifier.width(8.dp))
			Text(if (report == null) "Check this phone" else "Check again")
		}
		Spacer(Modifier.height(10.dp))
		Text(
			"Everything is read on the phone. Nothing is uploaded unless you turn on AI.",
			color = TextDim, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center,
			modifier = Modifier.fillMaxWidth(),
		)
		Spacer(Modifier.height(28.dp))
	}

	val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
	if (sheet != Sheet.None) {
		ModalBottomSheet(
			onDismissRequest = { sheet = Sheet.None },
			sheetState = sheetState,
			containerColor = Surface,
		) {
			when (val s = sheet) {
				is Sheet.Detail -> Detail(s.finding, onFix) {
					ctx.copy(s.finding)
					sheet = Sheet.None
					onFix(s.finding)
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
					"Triage bundles the Android-relevant part of the Zero Breach Toolkit corpus (CC0), as of the date shown on the report.\n\nValues come from published research by ESET, Cisco Talos, SentinelLabs, Meta, Volexity, Unit 42, Lookout, Amnesty International's Security Lab, the Citizen Lab and the Mobile Verification Toolkit project. Each entry keeps its source tag. The file header carries the full citations.\n\nMatched artifacts: package name (weakest — a real app's name can be reused), signing certificate (strong), APK file hash (strongest).\n\nNo network is used: the corpus ships inside the app and goes stale between releases. Check the as-of date.",
				)
				Sheet.Privacy -> StaticSheet(
					"Privacy",
					"All collection runs locally. The app has no internet permission in this build.\n\nWhat is read: Android build and patch level, lock-screen state, developer flags, installed package names and their granted permissions, accessibility/admin/notification/overlay/usage access lists, VPN and proxy configuration, Wi-Fi security type and installed user certificates.\n\nWhat is not read: contacts, messages, photos, files, accounts, location history, or traffic contents.\n\nThe report stays in app-private storage until you share it yourself.",
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
	Surface(color = Surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
		Column(Modifier.padding(16.dp)) {
			Row(verticalAlignment = Alignment.CenterVertically) {
				Icon(Icons.Rounded.AutoAwesome, null, tint = Accent, modifier = Modifier.size(18.dp))
				Spacer(Modifier.width(8.dp))
				Text("AI deep scan", fontWeight = FontWeight.SemiBold)
				Spacer(Modifier.weight(1f))
				Text("advisory", color = TextDim, style = MaterialTheme.typography.labelSmall)
			}
			Spacer(Modifier.height(4.dp))
			Text(
				"Your own model looks for what the rules missed. Results never change the score.",
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
				Spacer(Modifier.height(10.dp))
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
				Text(if (report.aiFindings.isEmpty() && report.aiMeta == null) "Run deep scan" else "Run deep scan again")
			}
		}
	}
}

@Composable
private fun Score(report: Report) {	val color = when {
		report.score >= 90 -> Accent
		report.score >= 75 -> Severity.MEDIUM.color()
		report.score >= 55 -> Severity.HIGH.color()
		else -> Severity.CRITICAL.color()
	}
	Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
		Box(contentAlignment = Alignment.Center) {
			Canvas(Modifier.size(184.dp)) {
				val stroke = 14.dp.toPx()
				val inset = stroke / 2
				val arcSize = Size(size.width - stroke, size.height - stroke)
				drawArc(
					color = SurfaceHigh,
					startAngle = -90f, sweepAngle = 360f, useCenter = false,
					topLeft = Offset(inset, inset), size = arcSize,
					style = Stroke(stroke, cap = StrokeCap.Round),
				)
				drawArc(
					color = color,
					startAngle = -90f, sweepAngle = 360f * (report.score / 100f), useCenter = false,
					topLeft = Offset(inset, inset), size = arcSize,
					style = Stroke(stroke, cap = StrokeCap.Round),
				)
			}
			Column(horizontalAlignment = Alignment.CenterHorizontally) {
				Text("${report.score}", fontSize = 52.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
				Text(Engine.grade(report.score), color = color, fontWeight = FontWeight.SemiBold)
			}
		}
		Spacer(Modifier.height(10.dp))
		Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
			countChip("${report.criticalCount} critical", Severity.CRITICAL)
			countChip("${report.highCount} high", Severity.HIGH)
		}
	}
}

@Composable
private fun countChip(text: String, sev: Severity) {
	Surface(color = SurfaceHigh, shape = RoundedCornerShape(8.dp)) {
		Text(
			text, color = if (sev == Severity.CRITICAL && text.startsWith("0")) TextDim else sev.color(),
			style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
		)
	}
}

@Composable
private fun FindingRow(finding: Finding, onClick: () -> Unit) {
	Surface(
		color = Surface,
		shape = RoundedCornerShape(14.dp),
		modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
		onClick = onClick,
	) {
		Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
			Box(
				Modifier.size(width = 4.dp, height = 34.dp)
					.clip(RoundedCornerShape(2.dp))
					.background(finding.severity.color()),
			)
			Spacer(Modifier.width(12.dp))
			Column(Modifier.weight(1f)) {
				Row(verticalAlignment = Alignment.CenterVertically) {
					if (finding.origin == Origin.AI) {
						Surface(color = Accent.copy(alpha = 0.16f), shape = RoundedCornerShape(4.dp)) {
							Text(
								"AI", color = Accent, style = MaterialTheme.typography.labelSmall,
								fontWeight = FontWeight.Bold,
								modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
							)
						}
						Spacer(Modifier.width(6.dp))
					}
					Text(finding.title, fontWeight = FontWeight.Medium, color = TextPrimary)
				}
				Text(
					(finding.appLabel ?: finding.impact.label) +
						" · " + finding.severity.label + " · " + finding.confidence.name.lowercase(),
					color = TextDim, style = MaterialTheme.typography.bodySmall,
				)
			}
			Icon(Icons.Rounded.ChevronRight, null, tint = TextDim)
		}
	}
}

@Composable
private fun Detail(finding: Finding, onFix: (Finding) -> Unit, onCopy: () -> Unit) {
	Column(Modifier.padding(horizontal = 20.dp).imePadding()) {
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
		Text(finding.title, style = MaterialTheme.typography.titleLarge, color = TextPrimary)
		Spacer(Modifier.height(6.dp))
		Text(finding.impact.label, color = TextDim, style = MaterialTheme.typography.bodySmall)

		Spacer(Modifier.height(16.dp))
		Text("What we saw", fontWeight = FontWeight.SemiBold, color = TextPrimary)
		Spacer(Modifier.height(4.dp))
		Text(finding.observed, color = TextDim)

		Spacer(Modifier.height(16.dp))
		Text("Why it matters", fontWeight = FontWeight.SemiBold, color = TextPrimary)
		Spacer(Modifier.height(4.dp))
		Text(finding.why, color = TextDim)

		Spacer(Modifier.height(16.dp))
		Text("How to fix it", fontWeight = FontWeight.SemiBold, color = Accent)
		Spacer(Modifier.height(4.dp))
		Text(finding.fix, color = TextPrimary)

		if (finding.appPackage != null) {
			Spacer(Modifier.height(12.dp))
			Text(finding.appPackage, color = TextDim, style = MaterialTheme.typography.bodySmall)
		}

		Spacer(Modifier.height(20.dp))
		Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
			Button(
				onClick = { onFix(finding) },
				colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Ink),
				modifier = Modifier.weight(1f),
			) {
				Icon(Icons.Rounded.OpenInNew, null, modifier = Modifier.size(16.dp))
				Spacer(Modifier.width(6.dp))
				Text("Open settings")
			}
			OutlinedButton(onClick = onCopy) { Text("Copy") }
		}
		Spacer(Modifier.height(24.dp))
	}
}

@Composable
private fun StaticSheet(title: String, body: String) {
	Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding()) {
		Text(title, style = MaterialTheme.typography.titleLarge)
		Spacer(Modifier.height(12.dp))
		Text(body, color = TextDim)
		Spacer(Modifier.height(28.dp))
	}
}

@Composable
private fun Card(content: @Composable () -> Unit) {
	Surface(color = Surface, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
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
	cm.setPrimaryClip(ClipData.newPlainText("Triage finding", text))
}

private fun Uri.unused() = Unit
