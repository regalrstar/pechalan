package dev.triage.app.ai

import dev.triage.app.model.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import java.io.Closeable
import java.net.InetAddress
import java.net.ServerSocket
import kotlin.concurrent.thread
import java.util.concurrent.CopyOnWriteArrayList

/** Real HTTP requests against a local provider stub; no keys or scan data leave this process. */
class AnthropicConnectionTest {
	@Test fun `connection test works with models that reject temperature`() = runTest {
		AnthropicServer("OK").use { server ->
			Clients.test(server.config)
			val request = server.requests.single()
			assertEquals("claude-opus-5-5", request["model"]?.jsonPrimitive?.content)
			assertFalse(request.containsKey("temperature"))
			assertTrue(request["max_tokens"]!!.jsonPrimitive.int > 0)
			assertEquals("user", request["messages"]!!.jsonArray.first().jsonObject["role"]!!.jsonPrimitive.content)
			assertTrue(request["system"]!!.jsonPrimitive.content.contains("OK"))
			assertEquals("test-only-key", server.apiKeys.single())
			assertEquals("2023-06-01", server.versions.single())
		}
	}

	@Test fun `AI review works with models that reject temperature`() = runTest {
		AnthropicServer("""{"findings":[]}""").use { server ->
			val outcome = DeepScan.run(server.config, evidence(), emptyList())
			assertNull(outcome.error)
			assertEquals(1, outcome.meta.steps)
			assertEquals(12, outcome.meta.tokens)
			assertFalse(server.requests.single().containsKey("temperature"))
		}
	}

	@Test fun `provider errors expose the useful message without raw JSON or credentials`() = runTest {
		AnthropicServer("unused", error = "Invalid\nAPI\tkey test-only-key").use { server ->
			val failure = runCatching { Clients.test(server.config) }.exceptionOrNull()
			assertNotNull(failure)
			assertEquals("HTTP 401: Invalid API key [redacted]", failure!!.message)
		}
	}

	private fun evidence() = Evidence(
		sdk = 35, release = "15", securityPatch = "2026-11-01", deviceModel = "Test device",
		buildTags = "release-keys", screenLock = ScreenLock.STRONG, encrypted = true,
		developerOptions = false, usbDebugging = false, unknownSources = false, verifierEnabled = true,
		sideloaded = emptyList(), apps = emptyList(), accessibility = emptyList(), deviceAdmins = emptyList(),
		notificationListeners = emptyList(), overlayApps = emptyList(), usageAccess = emptyList(),
		vpns = emptyList(), userCaCount = 0, proxyHost = null, privateDns = PrivateDns.HOSTNAME,
		wifi = null, hotspotOn = false, collectorErrors = emptyList(),
	)

	private class AnthropicServer(reply: String, error: String? = null) : Closeable {
		val requests = CopyOnWriteArrayList<JsonObject>()
		val apiKeys = CopyOnWriteArrayList<String>()
		val versions = CopyOnWriteArrayList<String>()
		private val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
		val config = AiConfig(
			provider = AiConfig.Provider.ANTHROPIC, baseUrl = "http://127.0.0.1:${server.localPort}",
			model = "claude-opus-5-5", key = "test-only-key",
		)
		private val worker = thread(isDaemon = true, name = "anthropic-test-server") {
			server.accept().use { socket ->
				socket.soTimeout = 5_000
				val input = socket.getInputStream().buffered()
				fun line(): String = buildString {
					while (true) {
						val next = input.read()
						if (next == -1 || next == 10) break
						if (next != 13) append(next.toChar())
					}
				}
				assertEquals("POST /v1/messages HTTP/1.1", line())
				val headers = mutableMapOf<String, String>()
				while (true) {
					val header = line()
					if (header.isEmpty()) break
					headers[header.substringBefore(':').lowercase()] = header.substringAfter(':').trim()
				}
				val data = ByteArray(headers.getValue("content-length").toInt())
				var read = 0
				while (read < data.size) {
					val count = input.read(data, read, data.size - read)
					check(count > 0)
					read += count
				}
				val request = Json.parseToJsonElement(data.toString(Charsets.UTF_8)).jsonObject
				requests += request
				apiKeys += headers["x-api-key"].orEmpty()
				versions += headers["anthropic-version"].orEmpty()
				val message = error ?: if (request.containsKey("temperature")) "temperature is deprecated for this model." else null
				val response = if (message != null) buildJsonObject {
					put("type", "error")
					putJsonObject("error") { put("type", "invalid_request_error"); put("message", message) }
				} else buildJsonObject {
					putJsonArray("content") { addJsonObject { put("type", "text"); put("text", reply) } }
					putJsonObject("usage") { put("input_tokens", 10); put("output_tokens", 2) }
				}
				val bytes = response.toString().toByteArray(Charsets.UTF_8)
				val status = if (error != null) "401 Unauthorized" else if (message != null) "400 Bad Request" else "200 OK"
				socket.getOutputStream().use { output ->
					output.write("HTTP/1.1 $status\r\nContent-Type: application/json\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n".toByteArray(Charsets.UTF_8))
					output.write(bytes)
				}
			}
		}
		override fun close() { server.close(); worker.join(2_000) }

	}
}
