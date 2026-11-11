package dev.triage.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.triage.app.R
import dev.triage.app.model.Severity

val Mars = FontFamily(Font(R.font.mars))
private val Suse = FontFamily(Font(R.font.suse_bold, FontWeight.Bold))

val Ink: Color @Composable get() = MaterialTheme.colorScheme.background
val Surface: Color @Composable get() = MaterialTheme.colorScheme.surface
val SurfaceHigh: Color @Composable get() = MaterialTheme.colorScheme.surfaceVariant
val Accent: Color @Composable get() = MaterialTheme.colorScheme.primary
val TextPrimary: Color @Composable get() = MaterialTheme.colorScheme.onSurface
val TextDim: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val Hairline: Color @Composable get() = MaterialTheme.colorScheme.outlineVariant
val Brand = Color(0xFFD31510)

// Zenos semantic roles, adapted for native Compose and checked in both appearances.
private val light = lightColorScheme(
	primary = Color(0xFF191919), onPrimary = Color(0xFFFAFAF8),
	primaryContainer = Color(0xFFE5E4DF), onPrimaryContainer = Color(0xFF191919),
	tertiary = Color(0xFF55534F), tertiaryContainer = Color(0xFFEAE9E5), onTertiaryContainer = Color(0xFF191919),
	surfaceTint = Color.Transparent,
	surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF6F5F2),
	surfaceContainer = Color(0xFFF1F0EC), surfaceContainerHigh = Color(0xFFECEBE7), surfaceContainerHighest = Color(0xFFE5E4DF),
	secondary = Color(0xFF55534F), onSecondary = Color.White,
	secondaryContainer = Color(0xFFEAE9E5), onSecondaryContainer = Color(0xFF191919),
	background = Color(0xFFF6F5F2), onBackground = Color(0xFF191919),
	surface = Color(0xFFFEFDFC), onSurface = Color(0xFF191919),
	surfaceVariant = Color(0xFFECEBE7), onSurfaceVariant = Color(0xFF686761),
	outline = Color(0xFF85847D), outlineVariant = Color(0xFFDDDCD6),
	error = Color(0xFFB33A32),
)
private val dark = darkColorScheme(
	primary = Color(0xFFF0EFEB), onPrimary = Color(0xFF181818),
	primaryContainer = Color(0xFF33332F), onPrimaryContainer = Color(0xFFF0EFEB),
	tertiary = Color(0xFFCAC7BE), tertiaryContainer = Color(0xFF2A2A28), onTertiaryContainer = Color(0xFFF0EFEB),
	surfaceTint = Color.Transparent,
	surfaceContainerLowest = Color(0xFF0B0B0B), surfaceContainerLow = Color(0xFF141414),
	surfaceContainer = Color(0xFF1A1A19), surfaceContainerHigh = Color(0xFF222221), surfaceContainerHighest = Color(0xFF2D2D2A),
	secondary = Color(0xFFCAC7BE), onSecondary = Color(0xFF191919),
	secondaryContainer = Color(0xFF2A2A28), onSecondaryContainer = Color(0xFFF0EFEB),
	background = Color(0xFF0B0B0B), onBackground = Color(0xFFF0EFEB),
	surface = Color(0xFF141414), onSurface = Color(0xFFF0EFEB),
	surfaceVariant = Color(0xFF222221), onSurfaceVariant = Color(0xFFAAA8A1),
	outline = Color(0xFF77756F), outlineVariant = Color(0xFF30302D),
	error = Color(0xFFF08B81),
)

@Composable
fun Severity.color(): Color = when (this) {
	Severity.CRITICAL -> MaterialTheme.colorScheme.error
	Severity.HIGH -> if (isSystemInDarkTheme()) Color(0xFFE5B57D) else Color(0xFF87551B)
	Severity.MEDIUM -> if (isSystemInDarkTheme()) Color(0xFFD9C589) else Color(0xFF756021)
	Severity.LOW -> TextPrimary
	Severity.INFO -> TextDim
}

private fun text(size: Int, height: Int, weight: FontWeight = FontWeight.Normal, tracking: Float = 0f) =
	TextStyle(fontFamily = if (weight >= FontWeight.Bold) Suse else FontFamily.SansSerif, fontSize = size.sp, lineHeight = height.sp, fontWeight = weight, letterSpacing = tracking.sp)

private val typography = Typography(
	displayLarge = text(86, 94, FontWeight.Normal, -4f),
	headlineLarge = text(34, 39, FontWeight.Bold, -1.1f),
	headlineMedium = text(28, 34, FontWeight.Bold, -0.7f),
	titleLarge = text(22, 28, FontWeight.Bold, -0.4f),
	titleMedium = text(16, 22, FontWeight.Bold, -0.1f),
	titleSmall = text(14, 20, FontWeight.Bold),
	bodyLarge = text(16, 25), bodyMedium = text(14, 21), bodySmall = text(12, 18),
	labelLarge = text(15, 21, FontWeight.Bold), labelMedium = text(12, 16, FontWeight.Medium),
	labelSmall = text(10, 14, FontWeight.Medium, 1f),
)

@Composable
fun PecalangTheme(content: @Composable () -> Unit) {
	MaterialTheme(
		colorScheme = if (isSystemInDarkTheme()) dark else light,
		typography = typography,
		shapes = Shapes(small = RoundedCornerShape(8.dp), medium = RoundedCornerShape(12.dp), large = RoundedCornerShape(18.dp), extraLarge = RoundedCornerShape(24.dp)),
	) {
		AtmosphericBackground {
			CompositionLocalProvider(LocalContentColor provides TextPrimary, content = content)
		}
	}
}
