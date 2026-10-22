package org.parkjw.apps.oot.i18n

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/** Korean copy uses friendly 해요체, not formal 합니다체 endings (#85). */
class KoreanToneTest {

    private val formalTone = Regex("""니다[.!]?|해주세요|하세요\.""")

    @Test
    fun regexCatchesOnlyFormalEndings() {
        assertTrue(formalTone.containsMatchIn("등록해주세요"))
        assertTrue(formalTone.containsMatchIn("확인합니다."))
        assertFalse(formalTone.containsMatchIn("등록해 주세요"))
        assertFalse(formalTone.containsMatchIn("확인해요."))
    }

    @Test
    fun koreanStringsUseFriendlyTone() {
        val offenders = load().filterValues { formalTone.containsMatchIn(it) }.keys
        assertEquals("strings with formal tone", emptySet<String>(), offenders)
    }

    /** name -> text from every values/strings*.xml. */
    private fun load(): Map<String, String> {
        val res = File(projectRoot(), "app/src/main/res/values")
        val files = res.listFiles { f -> f.name.startsWith("strings") && f.extension == "xml" }.orEmpty()
        val factory = DocumentBuilderFactory.newInstance()
        return files.flatMap { file ->
            val nodes = factory.newDocumentBuilder().parse(file).getElementsByTagName("string")
            (0 until nodes.length).map { nodes.item(it) as Element }.map {
                it.getAttribute("name") to it.textContent
            }
        }.toMap()
    }

    private fun projectRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").isFile) dir = dir.parentFile
        return checkNotNull(dir) { "project root not found" }
    }
}
