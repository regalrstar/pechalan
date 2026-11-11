package dev.triage.app.ui

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Neutral canvas; a subtle tonal falloff gives depth without decorative color fields. */
@Composable
fun AtmosphericBackground(content: @Composable BoxScope.() -> Unit) {
	Box(
		Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Ink, Ink, Surface))),
		contentAlignment = Alignment.TopCenter, content = content,
	)
}

@Composable
fun GlassPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
	val dark = isSystemInDarkTheme()
	Surface(
		modifier = modifier,
		color = Surface.copy(alpha = if (reducedTransparency()) 1f else 0.94f),
		shape = RoundedCornerShape(22.dp),
		border = BorderStroke(1.dp, Brush.verticalGradient(listOf(
			if (dark) Color(0xFF55534E) else Color.White,
			Hairline.copy(alpha = 0.7f),
		))),
		shadowElevation = if (dark) 0.dp else 6.dp,
		content = content,
	)
}

@Composable
fun ContentPanel(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
	Surface(modifier = modifier, color = Surface, border = BorderStroke(1.dp, Hairline), shape = RoundedCornerShape(18.dp), content = content)
}

@Composable
fun PrimaryAction(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, busy: Boolean = false) {
	Button(
		onClick = onClick, enabled = enabled,
		modifier = modifier.fillMaxWidth().heightIn(min = 52.dp),
		shape = RoundedCornerShape(12.dp),
		contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
	) {
		Text(label, modifier = Modifier.weight(1f))
		Spacer(Modifier.width(12.dp))
		if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
		else Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, Modifier.size(18.dp))
	}
}

@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
	Text(text.uppercase(), modifier = modifier, style = MaterialTheme.typography.labelSmall, color = TextDim)
}

@Composable
fun SectionHeading(title: String, trailing: String? = null) {
	Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
		Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
		if (trailing != null) Text(trailing, color = TextDim, style = MaterialTheme.typography.labelMedium)
	}
}

@Composable
fun Wordmark(modifier: Modifier = Modifier) {
	Row(modifier, verticalAlignment = Alignment.CenterVertically) {
		Box(Modifier.width(3.dp).height(18.dp).background(Brand, RoundedCornerShape(1.dp)))
		Spacer(Modifier.width(10.dp))
		Text("Pecalang", fontFamily = Mars, fontSize = (21f / androidx.compose.ui.platform.LocalDensity.current.fontScale).sp, lineHeight = (28f / androidx.compose.ui.platform.LocalDensity.current.fontScale).sp, color = TextPrimary)
	}
}

/** Android has no consistent system-wide Reduce Transparency preference. */
@Composable
fun reducedTransparency(): Boolean {
	val context = LocalContext.current
	val preferences = remember(context) { context.getSharedPreferences("appearance", Context.MODE_PRIVATE) }
	var reduced by remember(preferences) { mutableStateOf(preferences.getBoolean("reduce_transparency", false)) }
	DisposableEffect(preferences) {
		val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
			if (key == "reduce_transparency") reduced = prefs.getBoolean(key, false)
		}
		preferences.registerOnSharedPreferenceChangeListener(listener)
		onDispose { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
	}
	return reduced
}
