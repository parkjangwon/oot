package org.parkjw.apps.oot.update

data class AppVersion(
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: List<String> = emptyList(),
) : Comparable<AppVersion> {
    val isPreRelease: Boolean get() = preRelease.isNotEmpty()

    override fun compareTo(other: AppVersion): Int {
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch }).let { if (it != 0) return it }
        if (preRelease.isEmpty() || other.preRelease.isEmpty()) {
            return other.preRelease.size.coerceAtMost(1) - preRelease.size.coerceAtMost(1)
        }
        for (i in 0 until minOf(preRelease.size, other.preRelease.size)) {
            val cmp = comparePreReleaseId(preRelease[i], other.preRelease[i])
            if (cmp != 0) return cmp
        }
        return preRelease.size.compareTo(other.preRelease.size)
    }

    override fun toString(): String =
        "$major.$minor.$patch" + if (isPreRelease) "-" + preRelease.joinToString(".") else ""

    companion object {
        private val pattern = Regex(
            """^v?(0|[1-9]\d{0,8})\.(0|[1-9]\d{0,8})\.(0|[1-9]\d{0,8})(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?$""",
        )

        fun parse(text: String?): AppVersion? {
            val match = pattern.matchEntire(text?.trim() ?: return null) ?: return null
            val (major, minor, patch, pre) = match.destructured
            return AppVersion(
                major = major.toInt(),
                minor = minor.toInt(),
                patch = patch.toInt(),
                preRelease = if (pre.isEmpty()) emptyList() else pre.split('.'),
            )
        }

        private fun comparePreReleaseId(a: String, b: String): Int {
            val an = a.toLongOrNull()?.takeIf { a.all(Char::isDigit) }
            val bn = b.toLongOrNull()?.takeIf { b.all(Char::isDigit) }
            return when {
                an != null && bn != null -> an.compareTo(bn)
                an != null -> -1
                bn != null -> 1
                else -> a.compareTo(b)
            }
        }
    }
}
