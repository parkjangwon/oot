package org.parkjw.apps.oot.i18n

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** Korean (values) and English (values-en) carry the same keys with the same format arguments (#74). */
class StringResourcesParityTest {

    private val placeholder = Regex("""%(\d+\$)?[-#+ 0,(]*\d*(\.\d+)?[sdfx]""")

    @Test
    fun englishHasEveryTranslatableKoreanStringWithSameArguments() {
        val ko = load("values").filterValues { it.second }.mapValues { it.value.first }
        val en = load("values-en").mapValues { it.value.first }
        assertTrue("values-en is empty", en.isNotEmpty())
        assertEquals("keys missing in values-en", emptySet<String>(), ko.keys - en.keys)
        assertEquals("keys only in values-en", emptySet<String>(), en.keys - ko.keys)
        ko.forEach { (key, value) ->
            assertEquals("format args of $key", args(value), args(en.getValue(key)))
        }
    }

    @Test
    fun englishCopyHasNoHangul() {
        val hangul = Regex("[\\uAC00-\\uD7A3]")
        val offenders = load("values-en").filterValues { hangul.containsMatchIn(it.first) }.keys
        assertEquals(emptySet<String>(), offenders)
    }

    private fun args(text: String): List<String> =
        placeholder.findAll(text).map { it.value.replace(Regex("""[-#+ 0,(]*\d*(\.\d+)?(?=[sdfx])"""), "") }.sorted().toList()

    /** key -> (text, translatable) from every values-dir/strings*.xml. */
    private fun load(dir: String): Map<String, Pair<String, Boolean>> {
        val res = File(projectRoot(), "app/src/main/res/$dir")
        val files = res.listFiles { f -> f.name.startsWith("strings") && f.extension == "xml" }.orEmpty()
        val factory = DocumentBuilderFactory.newInstance()
        return files.flatMap { file ->
            val nodes = factory.newDocumentBuilder().parse(file).getElementsByTagName("string")
            (0 until nodes.length).map { nodes.item(it) as Element }.map {
                it.getAttribute("name") to (it.textContent to (it.getAttribute("translatable") != "false"))
            }
        }.toMap()
    }

    private fun projectRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").isFile) dir = dir.parentFile
        return checkNotNull(dir) { "project root not found" }
    }
}
