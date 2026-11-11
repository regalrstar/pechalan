package dev.triage.app.collect

import java.io.InputStream
import java.io.EOFException
import java.security.MessageDigest

internal class HashBudgetExceeded : Exception()

/** Shared across base APKs, splits and apps; checked before every chunk. */
internal class HashBudget(var remaining: Long, private val deadline: Long, private val now: () -> Long) {
	fun check(bytes: Long) {
		if (bytes > remaining || now() >= deadline) throw HashBudgetExceeded()
	}
}

internal object ApkDigests {
	fun hash(input: InputStream, expectedBytes: Long, budget: HashBudget): Pair<String, String> {
		budget.check(expectedBytes)
		val sha256 = MessageDigest.getInstance("SHA-256")
		val sha1 = MessageDigest.getInstance("SHA-1")
		val buffer = ByteArray(64 * 1024)
		var read = 0L
		while (read < expectedBytes) {
			budget.check(expectedBytes - read)
			val size = input.read(buffer, 0, minOf(buffer.size.toLong(), expectedBytes - read).toInt())
			if (size < 0) throw EOFException("APK changed while fingerprinting")
			if (size == 0) continue
			budget.remaining -= size
			read += size
			sha256.update(buffer, 0, size)
			sha1.update(buffer, 0, size)
		}
		return hex(sha256.digest()) to hex(sha1.digest())
	}

	private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
}
