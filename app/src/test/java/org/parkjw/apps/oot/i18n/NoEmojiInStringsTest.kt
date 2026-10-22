package org.parkjw.apps.oot.i18n

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** String resources must not contain emoji code points (#86). */
class NoEmojiInStringsTest {

    private val emoji = Regex("[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{2B00}-\\x{2BFF}\\x{FE0F}\\x{1F1E6}-\\x{1F1FF}]")

    @Test
    fun noStringResourceContainsEmoji() {
        val offenders = allValuesDirs().flatMap { dir ->
            load(dir).filterValues { emoji.containsMatchIn(it) }.map { (key, _) -> "${dir}:$key" }
        }
        assertEquals("emoji found in string resources", emptyList<String>(), offenders)
    }

    @Test
    fun detectorFlagsEmojiOnly() {
        assertTrue(emoji.containsMatchIn("💡 팁"))
        assertFalse(emoji.containsMatchIn("팁: 프로바이더 연결"))
        assertFalse(emoji.containsMatchIn("Tip: 5h · weekly … \$100 %1\$s"))
    }

    /** dir -> key -> text from every values* strings*.xml. */
    private fun allValuesDirs(): List<String> =
        File(projectRoot(), "app/src/main/res")
            .listFiles { f -> f.isDirectory && f.name.startsWith("values") }.orEmpty()
            .map { it.name }
            .sorted()

    private fun load(dir: String): Map<String, String> {
        val res = File(projectRoot(), "app/src/main/res/$dir")
        val files = res.listFiles { f -> f.name.startsWith("strings") && f.extension == "xml" }.orEmpty()
        val factory = DocumentBuilderFactory.newInstance()
        return files.flatMap { file ->
            val nodes = factory.newDocumentBuilder().parse(file).getElementsByTagName("string")
            (0 until nodes.length).map { nodes.item(it) as Element }.map { it.getAttribute("name") to it.textContent }
        }.toMap()
    }

    private fun projectRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").isFile) dir = dir.parentFile
        return checkNotNull(dir) { "project root not found" }
    }
}
