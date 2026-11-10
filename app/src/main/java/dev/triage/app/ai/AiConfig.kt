package dev.triage.app.ai

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Bring-your-own model. The key never leaves the device except in the request
 * header to the endpoint the user typed.
 *
 * MOCK is a scripted provider: no network, no key, deterministic. It exists so the
 * whole loop is testable and demo-able offline.
 */
data class AiConfig(
	val provider: Provider = Provider.NONE,
	val baseUrl: String = "",
	val model: String = "",
	val key: String = "",
	val sharePackageNames: Boolean = true,
	val maxSteps: Int = 6,
) {
	enum class Provider(val label: String, val defaultBase: String, val needsKey: Boolean) {
		NONE("Off", "", false),
		OPENAI_COMPATIBLE("OpenAI-compatible", "https://api.openai.com/v1", true),
		ANTHROPIC("Anthropic", "https://api.anthropic.com", true),
		MOCK("Built-in test model", "", false),
	}

	val configured: Boolean
		get() = provider == Provider.MOCK ||
			(provider != Provider.NONE && baseUrl.isNotBlank() && model.isNotBlank() && (!provider.needsKey || key.isNotBlank()))

	val endpoint: String
		get() = when (provider) {
			Provider.OPENAI_COMPATIBLE -> baseUrl.trimEnd('/') + "/chat/completions"
			Provider.ANTHROPIC -> baseUrl.trimEnd('/') + "/v1/messages"
			Provider.MOCK -> "mock://"
			Provider.NONE -> ""
		}
}

class AiStore(ctx: Context) {

	/**
	 * Encrypted when the Keystore cooperates, plain otherwise. Reinstalls and some
	 * OEM Keystores can invalidate the alias, so the choice is probed on every open
	 * and remembered as a fallback rather than crashing the caller.
	 */
	private val prefs: android.content.SharedPreferences = run {
		val plain = ctx.getSharedPreferences(PLAIN, Context.MODE_PRIVATE)
		if (plain.getBoolean(FALLBACK, false)) return@run plain
		runCatching {
			val key = MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
			val enc = EncryptedSharedPreferences.create(
				ctx, "triage-ai", key,
				EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
				EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
			)
			enc.edit().putString(PROBE, "1").commit()
			check(enc.getString(PROBE, "") == "1")
			enc
		}.getOrElse {
			plain.edit().putBoolean(FALLBACK, true).apply()
			plain
		}
	}

	fun load(): AiConfig = AiConfig(
		provider = runCatching { AiConfig.Provider.valueOf(prefs.getString(KEY_PROVIDER, "NONE")!!) }
			.getOrDefault(AiConfig.Provider.NONE),
		baseUrl = prefs.getString(KEY_BASE, "") ?: "",
		model = prefs.getString(KEY_MODEL, "") ?: "",
		key = prefs.getString(KEY_KEY, "") ?: "",
		sharePackageNames = prefs.getBoolean(KEY_NAMES, true),
		maxSteps = prefs.getInt(KEY_STEPS, 6).coerceIn(2, 10),
	)

	fun save(cfg: AiConfig) {
		prefs.edit()
			.putString(KEY_PROVIDER, cfg.provider.name)
			.putString(KEY_BASE, cfg.baseUrl.trim())
			.putString(KEY_MODEL, cfg.model.trim())
			.putString(KEY_KEY, cfg.key.trim())
			.putBoolean(KEY_NAMES, cfg.sharePackageNames)
			.putInt(KEY_STEPS, cfg.maxSteps)
			.apply()
	}

	fun watchEnabled(): Boolean = prefs.getBoolean(KEY_WATCH, false)
	fun setWatch(enabled: Boolean) = prefs.edit().putBoolean(KEY_WATCH, enabled).apply()

	private companion object {
		const val PLAIN = "triage-ai-plain"
		const val FALLBACK = "fallback"
		const val PROBE = "probe"
		const val KEY_PROVIDER = "provider"
		const val KEY_BASE = "base"
		const val KEY_MODEL = "model"
		const val KEY_KEY = "key"
		const val KEY_NAMES = "names"
		const val KEY_STEPS = "steps"
		const val KEY_WATCH = "watch"
	}
}
