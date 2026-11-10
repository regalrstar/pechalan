package dev.triage.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Typography
import dev.triage.app.model.Severity

val Ink = Color(0xFF0B0F14)
val Surface = Color(0xFF141A21)
val SurfaceHigh = Color(0xFF1C242D)
val Accent = Color(0xFF39D3A0)
val TextPrimary = Color(0xFFE8EEF4)
val TextDim = Color(0xFF93A1AE)

private val scheme = darkColorScheme(
	primary = Accent,
	onPrimary = Ink,
	background = Ink,
	onBackground = TextPrimary,
	surface = Surface,
	onSurface = TextPrimary,
	surfaceVariant = SurfaceHigh,
	onSurfaceVariant = TextDim,
	outline = Color(0xFF2C3742),
	error = Color(0xFFFF5C5C),
)

fun Severity.color(): Color = when (this) {
	Severity.CRITICAL -> Color(0xFFFF5C5C)
	Severity.HIGH -> Color(0xFFFF9F43)
	Severity.MEDIUM -> Color(0xFFFFD166)
	Severity.LOW -> Color(0xFF4FB3FF)
	Severity.INFO -> Color(0xFF93A1AE)
}

private val type = Typography().let { base ->
	base.copy(
		headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 34.sp),
		titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
		bodyLarge = base.bodyLarge.copy(lineHeight = 23.sp),
		bodyMedium = base.bodyMedium.copy(lineHeight = 21.sp),
		labelLarge = base.labelLarge.copy(fontWeight = FontWeight.SemiBold),
	)
}

@Composable
fun TriageTheme(content: @Composable () -> Unit) {
	MaterialTheme(colorScheme = scheme, typography = type, content = content)
}
