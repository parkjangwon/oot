package org.parkjw.apps.oot.i18n

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keeps user-facing text in string resources (#74). Fails when app code (main + debug sources):
 *  - has a string literal containing Hangul, or
 *  - passes a literal with letters straight to a UI text sink (Compose Text / text= / contentDescription=,
 *    Toast, notification title/text/ticker/action, error callbacks).
 * Comments are ignored. Use stringResource / getString / AppText.get instead.
 */
class HardcodedUiStringTest {

    private val hangul = Regex("[\\uAC00-\\uD7A3\\u3131-\\u318E]")

    // A UI sink immediately followed by a string literal (after comment stripping, literals keep their quotes).
    private val uiSink = Regex(
        """(?:\bText\(\s*(?:text\s*=\s*)?|\btext\s*=\s*|\bcontentDescription\s*=\s*|\bplaceholder\s*=\s*\{\s*Text\(|""" +
            """\bCustomAccessibilityAction\(\s*|\bsetContentTitle\(\s*|\bsetContentText\(\s*|\bsetTicker\(\s*|""" +
            """\bonError\(\s*|\bToast\.makeText\([^,]+,\s*|""" +
            // `text = if (x) "A" else "B"`: both branches
            """\b(?:text|contentDescription)\s*=\s*if\s*\([^)]*\)\s*(?:"[^"]*"\s*else\s*)?)"((?:[^"\\]|\\.)*)""""
    )
    private val letters = Regex("[A-Za-z\\uAC00-\\uD7A3]")

    // Literals that are not prose: the lowercase "oot" wordmark and brand names shown as-is in every language.
    private val allowedUiLiterals = setOf("oot", "GitHub", "OOT")

    @Test
    fun noHangulStringLiteralsInAppCode() {
        val offenders = sourceFiles().flatMap { file ->
            stringLiterals(stripComments(file.readText()))
                .filter { hangul.containsMatchIn(it.second) }
                .map { "${relative(file)}:${it.first}: \"${it.second}\"" }
        }
        assertTrue("Move these to res/values*/strings*.xml:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    @Test
    fun noLiteralTextPassedToUiSinks() {
        val offenders = sourceFiles().flatMap { file ->
            val code = stripComments(file.readText())
            uiSink.findAll(code)
                .filter { letters.containsMatchIn(it.groupValues[1]) && it.groupValues[1] !in allowedUiLiterals }
                .map { "${relative(file)}:${lineOf(code, it.range.first)}: ${it.value}" }
                .toList()
        }
        assertTrue("Use a string resource for UI text:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }

    @Test
    fun scannerCatchesHardcodedText() {
        val code = stripComments(
            """
            // Text("주석은 괜찮아요")
            Text("Hello")
            Text(text = "설정", style = x)
            Icon(contentDescription = "Back")
            Icon(contentDescription = if (shown) "Hide" else "Show")
            val ok = stringResource(R.string.settings_title)
            """.trimIndent()
        )
        val sinks = uiSink.findAll(code).map { it.groupValues[1] }.toList()
        assertTrue(sinks.toString(), sinks == listOf("Hello", "설정", "Back", "Show"))
        assertTrue(stringLiterals(code).map { it.second }.count { hangul.containsMatchIn(it) } == 1)
    }

    private fun sourceFiles(): List<File> {
        val root = projectRoot()
        return listOf("app/src/main/java", "app/src/debug/java")
            .map { File(root, it) }
            .filter { it.isDirectory }
            .flatMap { dir -> dir.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList() }
            .also { assertTrue("no sources found under $root", it.isNotEmpty()) }
    }

    private fun projectRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").isFile) dir = dir.parentFile
        return checkNotNull(dir) { "project root not found" }
    }

    private fun relative(file: File): String = file.relativeTo(projectRoot()).path

    private fun lineOf(code: String, index: Int): Int = code.substring(0, index).count { it == '\n' } + 1

    /** Replaces comments with spaces (newlines kept so line numbers stay right); string literals untouched. */
    internal fun stripComments(src: String): String {
        val out = StringBuilder(src.length)
        var i = 0
        while (i < src.length) {
            val c = src[i]
            when {
                src.startsWith("\"\"\"", i) -> {
                    val end = src.indexOf("\"\"\"", i + 3).let { if (it < 0) src.length else it + 3 }
                    out.append(src, i, end); i = end
                }
                c == '"' || c == '\'' -> {
                    var j = i + 1
                    while (j < src.length && src[j] != c && src[j] != '\n') j += if (src[j] == '\\') 2 else 1
                    val end = minOf(j + 1, src.length)
                    out.append(src, i, end); i = end
                }
                src.startsWith("//", i) -> {
                    while (i < src.length && src[i] != '\n') { out.append(' '); i++ }
                }
                src.startsWith("/*", i) -> {
                    val end = src.indexOf("*/", i + 2).let { if (it < 0) src.length else it + 2 }
                    for (k in i until end) out.append(if (src[k] == '\n') '\n' else ' ')
                    i = end
                }
                else -> { out.append(c); i++ }
            }
        }
        return out.toString()
    }

    /** (line, content) of every "…" and """…""" literal in comment-free code. */
    internal fun stringLiterals(code: String): List<Pair<Int, String>> {
        val result = mutableListOf<Pair<Int, String>>()
        var i = 0
        while (i < code.length) {
            when {
                code.startsWith("\"\"\"", i) -> {
                    val end = code.indexOf("\"\"\"", i + 3).let { if (it < 0) code.length else it }
                    result += lineOf(code, i) to code.substring(i + 3, end)
                    i = end + 3
                }
                code[i] == '"' -> {
                    var j = i + 1
                    while (j < code.length && code[j] != '"' && code[j] != '\n') j += if (code[j] == '\\') 2 else 1
                    result += lineOf(code, i) to code.substring(i + 1, minOf(j, code.length))
                    i = j + 1
                }
                code[i] == '\'' -> {
                    var j = i + 1
                    while (j < code.length && code[j] != '\'' && code[j] != '\n') j += if (code[j] == '\\') 2 else 1
                    i = j + 1
                }
                else -> i++
            }
        }
        return result
    }
}
