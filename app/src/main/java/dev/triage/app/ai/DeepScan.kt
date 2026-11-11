package dev.triage.app.ai

import dev.triage.app.model.AiMeta
import dev.triage.app.model.Confidence
import dev.triage.app.model.Evidence
import dev.triage.app.model.Finding
import dev.triage.app.model.FixTarget
import dev.triage.app.model.Impact
import dev.triage.app.model.Origin
import dev.triage.app.model.Severity
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

data class AiOutcome(
	val findings: List<Finding>,
	val meta: AiMeta,
	val error: String? = null,
)

/**
 * A bounded, read-only agent loop.
 *
 * The model may only call [Tools] over evidence that is already in memory, and it
 * may only answer with findings — never with a score. Rules stay authoritative; an
 * AI finding is additive and separately labelled.
 */
object DeepScan {

	private val json = Json { ignoreUnknownKeys = true }

	@Serializable
	private data class Step(
		val tool: String? = null,
		val args: JsonObject? = null,
		val findings: List<AiFinding>? = null,
	)

	@Serializable
	private data class AiFinding(
		val title: String,
		val observed: String = "",
		val why: String = "",
		val fix: String = "",
		val severity: String = "MEDIUM",
		val confidence: String = "POSSIBLE",
		val packageName: String? = null,
		val `package`: String? = null,
	) {
		val pkg: String? get() = packageName ?: `package`
	}

	private fun system(maxSteps: Int) = """
You are the analysis layer of Pecalang, a consumer Android security app. The device
evidence below was collected read-only; posture rules have already run and are
shown to the user. Your job is to add what a careful human reviewer would add, not
to repeat the rules.

You may call read-only tools. Reply with exactly ONE JSON object and nothing else:
- to probe: {"tool":"<name>","args":{...}}
- to finish: {"findings":[...]}

Tools:
${Tools.catalog}

Finding shape:
{"title":"short","observed":"what the evidence shows","why":"consequence for this person","fix":"Settings path and action","severity":"INFO|LOW|MEDIUM|HIGH|CRITICAL","confidence":"POSSIBLE|LIKELY|CONFIRMED","package":"optional.package.name"}

Rules:
- Only claim what tool output or the evidence shows. Never invent CVEs or app names.
- Do not repeat a rule finding unless you add a concrete, evidence-backed detail.
- Maximum $maxSteps probes, then finish. Maximum 6 findings. Empty is a valid answer.
- Plain language, second person, no "you are infected".
""".trimIndent()

	suspend fun run(
		cfg: AiConfig,
		e: Evidence,
		ruleTitles: List<String>,
		onStep: (Int, String) -> Unit = { _, _ -> },
		extraContext: String = "",
	): AiOutcome {
		val client = runCatching { Clients.build(cfg) }.getOrElse {
			return AiOutcome(emptyList(), meta(cfg, 0, 0, it.message ?: "not configured"), it.message)
		}
		val maxSteps = cfg.maxSteps.coerceIn(2, 10)
		val turns = mutableListOf(
			Turn("user", buildString {
				append("Device: Android ").append(e.release).append(", patch ").append(e.securityPatch).append(".\n")
				if (extraContext.isNotBlank()) append(if (cfg.sharePackageNames) extraContext else Tools.redactPackageNames(extraContext, e)).append("\n")
				append("Existing rule findings: ")
				val titles = ruleTitles.take(12).joinToString("; ").ifBlank { "none" }
				append(if (cfg.sharePackageNames) titles else Tools.redactPackageNames(titles, e))
				append("\n\nEvidence overview: ")
				append(Tools.run("overview", null, e, cfg.sharePackageNames))
				append("\n\nStart with one probe.")
			}),
		)

		var tokens = 0
		var steps = 0
		val findings = mutableListOf<Finding>()
		var note = ""

		for (i in 0 until maxSteps) {
			steps = i + 1
			onStep(steps, "asking the model")
			val reply = try {
				client.complete(system(maxSteps), turns, MAX_TOKENS)
			} catch (err: CancellationException) {
				throw err
			} catch (err: Exception) {
				return AiOutcome(findings, meta(cfg, steps, tokens, err.message ?: "request failed"), err.message)
			}
			tokens += reply.tokens
			val parsed = runCatching { json.decodeFromString<Step>(extractJson(reply.text)) }.getOrNull()
			if (parsed == null) {
				turns += Turn("assistant", reply.text)
				turns += Turn("user", "That was not valid JSON. Reply with one JSON object only.")
				continue
			}
			if (parsed.findings != null) {
				findings += parsed.findings.mapNotNull(::toFinding).take(6)
				note = "model finished in $steps step(s)"
				return AiOutcome(findings, meta(cfg, steps, tokens, note))
			}
			val tool = parsed.tool
			if (tool == null) {
				note = "model returned neither a tool nor findings"
				return AiOutcome(findings, meta(cfg, steps, tokens, note), note)
			}
			onStep(steps, "probing $tool")
			val observation = Tools.run(tool, parsed.args, e, cfg.sharePackageNames)
			turns += Turn("assistant", reply.text)
			turns += Turn("user", "Observation for $tool: $observation")
		}

		note = "step limit reached"
		return AiOutcome(findings, meta(cfg, steps, tokens, note), note)
	}

	private const val MAX_TOKENS = 1200

	private fun meta(cfg: AiConfig, steps: Int, tokens: Int, note: String) = AiMeta(
		provider = cfg.provider.label,
		model = cfg.model.ifBlank { "-" },
		steps = steps,
		tokens = tokens,
		at = System.currentTimeMillis(),
		note = note,
	)

	private fun toFinding(a: AiFinding): Finding? {
		if (a.title.isBlank()) return null
		val severity = runCatching { Severity.valueOf(a.severity.uppercase()) }.getOrDefault(Severity.MEDIUM)
		val confidence = runCatching { Confidence.valueOf(a.confidence.uppercase()) }.getOrDefault(Confidence.POSSIBLE)
		val slug = a.title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(40)
		return Finding(
			checkId = "ai.$slug",
			title = a.title.take(90),
			observed = a.observed.ifBlank { "Reported by the model without a stated observation." },
			why = a.why.ifBlank { "Not explained." },
			fix = a.fix.ifBlank { "Review the app or setting named above." },
			severity = severity,
			confidence = confidence,
			impact = if (a.pkg != null) Impact.INSTALLED_APP else Impact.THIS_DEVICE,
			evidence = mapOf("ai" to "1"),
			appPackage = a.pkg,
			fixTarget = if (a.pkg != null) FixTarget.APP_DETAILS else null,
			origin = Origin.AI,
		)
	}

	/** Models wrap JSON in prose or fences; take the first balanced object. */
	internal fun extractJson(text: String): String {
		val start = text.indexOf('{')
		if (start < 0) return text
		var depth = 0
		var inString = false
		var escape = false
		for (i in start until text.length) {
			val c = text[i]
			if (inString) {
				when {
					escape -> escape = false
					c == '\\' -> escape = true
					c == '"' -> inString = false
				}
				continue
			}
			when (c) {
				'"' -> inString = true
				'{' -> depth++
				'}' -> {
					depth--
					if (depth == 0) return text.substring(start, i + 1)
				}
			}
		}
		return text.substring(start)
	}
}
