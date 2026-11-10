package dev.triage.app.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL

data class Turn(val role: String, val content: String)

data class Completion(val text: String, val tokens: Int)

class AiException(message: String) : Exception(message)

interface ModelClient {
	suspend fun complete(system: String, turns: List<Turn>, maxTokens: Int): Completion
}

object Clients {

	fun build(cfg: AiConfig): ModelClient = when (cfg.provider) {
		AiConfig.Provider.MOCK -> MockClient()
		AiConfig.Provider.OPENAI_COMPATIBLE -> HttpModelClient(cfg, anthropic = false)
		AiConfig.Provider.ANTHROPIC -> HttpModelClient(cfg, anthropic = true)
		AiConfig.Provider.NONE -> throw AiException("No AI provider configured")
	}

	/** One cheap round-trip the settings screen can use to prove the key works. */
	suspend fun test(cfg: AiConfig) {
		val client = build(cfg)
		val out = client.complete("Reply with the single word OK.", listOf(Turn("user", "Ping")), 16)
		if (out.text.isBlank()) throw AiException("Empty reply")
	}
}

private class HttpModelClient(private val cfg: AiConfig, private val anthropic: Boolean) : ModelClient {

	private val json = Json { ignoreUnknownKeys = true }

	override suspend fun complete(system: String, turns: List<Turn>, maxTokens: Int): Completion =
		withContext(Dispatchers.IO) {
			val body = if (anthropic) {
				buildJsonObject {
					put("model", cfg.model)
					put("system", system)
					put("max_tokens", maxTokens)
					put("temperature", 0.1)
					put("messages", messages(turns))
				}
			} else {
				buildJsonObject {
					put("model", cfg.model)
					put("max_tokens", maxTokens)
					put("temperature", 0.1)
					put("messages", messages(listOf(Turn("system", system)) + turns))
				}
			}

			val conn = (URL(cfg.endpoint).openConnection() as HttpURLConnection).apply {
				requestMethod = "POST"
				connectTimeout = 20_000
				readTimeout = 90_000
				doOutput = true
				setRequestProperty("Content-Type", "application/json")
				if (anthropic) {
					setRequestProperty("x-api-key", cfg.key)
					setRequestProperty("anthropic-version", "2023-06-01")
				} else {
					setRequestProperty("Authorization", "Bearer ${cfg.key}")
				}
			}
			try {
				conn.outputStream.use { it.write(body.toString().toByteArray()) }
				val code = conn.responseCode
				val text = (if (code in 200..299) conn.inputStream else conn.errorStream)
					?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
				if (code !in 200..299) {
					throw AiException("HTTP $code: ${text.take(300).replace(Regex("\\s+"), " ")}")
				}
				parse(text)
			} catch (e: AiException) {
				throw e
			} catch (e: Exception) {
				throw AiException(e.message ?: e.javaClass.simpleName)
			} finally {
				conn.disconnect()
			}
		}

	private fun messages(turns: List<Turn>): JsonArray = buildJsonArray {
		turns.forEach { t ->
			add(buildJsonObject {
				put("role", t.role)
				put("content", t.content)
			})
		}
	}

	private fun parse(body: String): Completion {
		val root = json.parseToJsonElement(body).jsonObject
		val text = if (anthropic) {
			root["content"]?.let { c ->
				(c as? JsonArray)?.joinToString("") { part ->
					part.jsonObject["text"]?.jsonPrimitive?.content.orEmpty()
				}
			}.orEmpty()
		} else {
			root["choices"]?.let { c ->
				(c as? JsonArray)?.firstOrNull()
					?.jsonObject?.get("message")?.jsonObject?.get("content")
					?.jsonPrimitive?.content
			}.orEmpty()
		}
		val tokens = root["usage"]?.jsonObject?.get("total_tokens")?.jsonPrimitive?.content?.toIntOrNull() ?: 0
		if (text.isBlank()) throw AiException("The model returned no text")
		return Completion(text, tokens)
	}
}

/**
 * Scripted model: asks for two probes, then reports what a human reviewer would
 * likely say about the emulator-class fixture. Deterministic, zero network.
 */
private class MockClient : ModelClient {

	private var step = 0

	override suspend fun complete(system: String, turns: List<Turn>, maxTokens: Int): Completion {
		val call = step++
		val text = when (call) {
			0 -> """{"tool":"permission_combos","args":{"permissions":["RECEIVE_SMS","READ_SMS"]}}"""
			1 -> """{"tool":"access_grants","args":{}}"""
			2 -> """{"findings":[
{"title":"Two-factor codes are readable by a non-system app","observed":"An app you installed can receive SMS and is also on an accessibility surface, the classic code-capture combination.","why":"Together these cover one-time codes and everything on screen, which is enough to take over an account.","fix":"Open the app's details and confirm you still use it; revoke access if not.","severity":"HIGH","confidence":"LIKELY","package":"com.example.reader"},
{"title":"Review the sideloaded app set","observed":"The phone has apps installed outside an app store.","why":"Sideloaded apps skip store review and update silently.","fix":"Keep the ones you recognise, remove the rest.","severity":"MEDIUM","confidence":"CONFIRMED","package":null}
]}"""
			else -> """{"findings":[]}"""
		}
		return Completion(text, 40)
	}
}
