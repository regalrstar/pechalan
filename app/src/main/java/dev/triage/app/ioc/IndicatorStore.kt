package dev.triage.app.ioc

import android.content.Context
import java.security.MessageDigest

/**
 * Published spyware indicators, bundled as TSV under assets/iocs.
 *
 * Format (from the upstream corpus): `value \t tier \t confidence \t source \t family [\t kind]`.
 * Values are package names (kind `app-id`), SHA-256 or SHA-1 digests. Other kinds
 * (domains, IPs, paths) are parsed but not matched yet.
 *
 * The data is CC0; attribution to the publishers is kept in the file header and in
 * docs/INDICATORS.md. Loaded once, read-only.
 */
class IndicatorStore private constructor(
	val asOf: String,
	private val byPackage: Map<String, Indicator>,
	private val byDigest: Map<String, Indicator>,
) {

	data class Indicator(val value: String, val family: String, val source: String, val kind: Kind)

	enum class Kind { PACKAGE, SHA256, SHA1 }

	val digestCount: Int get() = byDigest.size
	val packageCount: Int get() = byPackage.size
	val totalCount: Int get() = digestCount + packageCount

	/** Package-name hit. Weakest identity: a publisher's app id can be reused by the real app. */
	fun byPackageName(value: String): Indicator? = byPackage[value.lowercase()]

	/** File or signing-certificate digest hit. Both are hex, lowercased. */
	fun byDigestValue(hex: String): Indicator? = byDigest[hex.lowercase()]

	companion object {
		private const val ASSET_DIR = "iocs"
		private const val TARGETED = "$ASSET_DIR/curated_targeted.txt"
		private const val MERCENARY = "$ASSET_DIR/curated_mercenary.txt"

		@Volatile
		private var cached: IndicatorStore? = null

		fun get(ctx: Context): IndicatorStore =
			cached ?: synchronized(this) { cached ?: load(ctx).also { cached = it } }

		private fun load(ctx: Context): IndicatorStore {
			val pkgs = HashMap<String, Indicator>()
			val digests = HashMap<String, Indicator>()
			var asOf = "unknown"

			for (asset in listOf(TARGETED, MERCENARY)) {
				val text = runCatching {
					ctx.assets.open(asset).bufferedReader().use { it.readText() }
				}.getOrNull() ?: continue

				for (line in text.lineSequence()) {
					if (line.isBlank()) continue
					if (line.startsWith("#")) {
						line.removePrefix("# ").removePrefix("as-of: ").let {
							if (line.contains("as-of:")) asOf = it.substringAfter("as-of:").trim()
						}
						continue
					}
					val f = line.split('\t')
					if (f.size < 5) continue
					val value = f[0].trim().lowercase()
					if (value.isEmpty()) continue
					val ind = Indicator(value, f[4].trim(), f[3].trim(), Kind.PACKAGE)
					when {
						f.getOrNull(5)?.trim() == "app-id" -> pkgs.putIfAbsent(value, ind)
						SHA256.matches(value) -> digests.putIfAbsent(value, ind.copy(kind = Kind.SHA256))
						SHA1.matches(value) -> digests.putIfAbsent(value, ind.copy(kind = Kind.SHA1))
					}
				}
			}
			return IndicatorStore(asOf, pkgs, digests)
		}

		private val SHA256 = Regex("^[0-9a-f]{64}$")
		private val SHA1 = Regex("^[0-9a-f]{40}$")

		fun sha256(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))
		fun sha1(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-1").digest(bytes))

		private fun hex(b: ByteArray): String {
			val out = CharArray(b.size * 2)
			val digits = "0123456789abcdef"
			for (i in b.indices) {
				val v = b[i].toInt() and 0xFF
				out[i * 2] = digits[v ushr 4]
				out[i * 2 + 1] = digits[v and 0x0F]
			}
			return String(out)
		}
	}
}
