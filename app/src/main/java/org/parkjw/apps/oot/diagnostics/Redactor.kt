package org.parkjw.apps.oot.diagnostics

/**
 * Masks values that commonly carry credentials before a record is shown, shared or put in an issue.
 * The stored files stay untouched, so this runs on every read path that leaves the app.
 */
object Redactor {

    private const val MASK = "[REDACTED]"

    private val email = Regex("""(?i)\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b""")

    private val bearer = Regex("""(?i)\b(Bearer|Basic)\s+[A-Za-z0-9._~+/=-]{8,}""")

    private val labeledSecret = Regex(
        """(?i)\b(api[-_]?key|access[-_]?token|refresh[-_]?token|id[-_]?token|client[-_]?secret|secret|password|authorization|token)\b(\s*[:=]\s*)(["']?)([^\s"',;&]{4,})\3""",
    )

    private val knownPrefix = Regex(
        """(?i)\b(?:sk-(?:ant-)?[A-Za-z0-9_-]{8,}|gh[pousr]_[A-Za-z0-9]{8,}|github_pat_[A-Za-z0-9_]{8,}|xox[abp]-[A-Za-z0-9-]{8,}|AIza[0-9A-Za-z_-]{20,}|eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,})\b""",
    )

    private val querySecret = Regex(
        """(?i)([?&](?:api[-_]?key|access[-_]?token|token|key|secret)=)[^&\s#]+""",
    )

    private val longToken = Regex("""(?<![A-Za-z0-9+/_-])[A-Za-z0-9+/_-]{24,}={0,2}(?![A-Za-z0-9+/_=-])""")

    fun redact(text: String): String {
        var result = text
        result = email.replace(result, "[EMAIL]")
        result = bearer.replace(result, "$1 $MASK")
        result = labeledSecret.replace(result, "$1$2$3$MASK$3")
        result = knownPrefix.replace(result, MASK)
        result = querySecret.replace(result, "$1$MASK")
        result = longToken.replace(result, MASK)
        return result
    }
}
