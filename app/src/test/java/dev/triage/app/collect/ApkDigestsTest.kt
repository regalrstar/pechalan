package dev.triage.app.collect

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.security.MessageDigest

class ApkDigestsTest {
	@Test fun `digests match both published algorithms and spend shared budget`() {
		val bytes = "Pecalang fingerprint".toByteArray()
		val budget = HashBudget(bytes.size.toLong() * 2, 100) { 0 }
		val hashes = ApkDigests.hash(ByteArrayInputStream(bytes), bytes.size.toLong(), budget)
		fun digest(name: String) = MessageDigest.getInstance(name).digest(bytes).joinToString("") { "%02x".format(it) }
		assertEquals(digest("SHA-256"), hashes.first)
		assertEquals(digest("SHA-1"), hashes.second)
		assertEquals(bytes.size.toLong(), budget.remaining)
		ApkDigests.hash(ByteArrayInputStream(bytes), bytes.size.toLong(), budget)
		assertEquals(0L, budget.remaining)
	}

	@Test fun `oversize APK is skipped before any bytes are read`() {
		val input = ByteArrayInputStream(ByteArray(100))
		try {
			ApkDigests.hash(input, 100, HashBudget(99, 100) { 0 })
			fail("Budget should reject file")
		} catch (_: HashBudgetExceeded) { }
		assertEquals(100, input.available())
	}

	@Test fun `deadline interrupts a large APK between chunks`() {
		val input = ByteArrayInputStream(ByteArray(200_000))
		var clock = 0L
		val budget = HashBudget(200_000, 2) { clock++ }
		try {
			ApkDigests.hash(input, 200_000, budget)
			fail("Deadline should interrupt file")
		} catch (_: HashBudgetExceeded) { }
		assertEquals(200_000 - 65_536, input.available())
	}
}
