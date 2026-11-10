package dev.triage.app.stalkerware

/**
 * Substring fingerprints, matched against package name AND label, because
 * vendors rename packages between releases and localise labels. Matching is
 * case-insensitive.
 *
 * This is a starting corpus, not a feed. It is intentionally conservative:
 * a hit means "look here", the access-surface rules decide how loud to be.
 */
object Fingerprints {

	/** Commercial stalkerware / monitoring products. */
	val STALKERWARE = listOf(
		"mspy", "flexispy", "hoverwatch", "spyzie", "kidsguard", "eyezy",
		"umobix", "ikeymonitor", "spyera", "mobistealth", "thetruthspy",
		"xnspy", "cocospy", "spyic", "minspy", "spyngo", "spyfone",
	)

	/** Household-name parental controls and find-my tools: expected, mostly benign. */
	val FAMILY = listOf(
		"familylink", "family link", "life360", "find my device", "findmydevice",
		"smartthings find", "galaxy find", "parental", "qustodio",
		"norton family", "eset parental", "bitdefender parental", "kaspersky safe kids",
		"screen time", "screentime",
	)

	/** Apps routinely granted accessibility / notification access; not worth alarming about. */
	val TRUSTED_ACCESS = listOf(
		"bitwarden", "1password", "lastpass", "dashlane", "keepass", "enpass",
		"nordpass", "roboform", "authenticator", "authy", "microsoft authenticator",
		"android auto", "wear os", "galaxy wearable", "smartthings", "tasker",
		"macrodroid", "nova launcher", "lawnchair", "niagara",
		"google", "samsung", "xiaomi", "oneplus", "oppo", "vivo", "huawei",
	)

	fun matches(needles: List<String>, packageName: String, label: String): Boolean {
		val haystack = "$packageName $label".lowercase()
		return needles.any { it.isNotBlank() && haystack.contains(it) }
	}

	fun isStalkerware(packageName: String, label: String) = matches(STALKERWARE, packageName, label)
	fun isFamily(packageName: String, label: String) = matches(FAMILY, packageName, label)
	fun isTrustedAccess(packageName: String, label: String) = matches(TRUSTED_ACCESS, packageName, label)
}
