package org.parkjw.apps.oot.update

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateStringsTest {
    private val ko = mapOf(
        "settings_section_update" to "업데이트",
        "update_check" to "업데이트 확인",
        "update_auto" to "자동으로 업데이트 확인",
        "update_auto_desc" to "앱을 켤 때 하루 한 번 확인해요",
        "update_checking" to "확인하는 중…",
        "update_latest" to "최신 버전이에요",
        "update_available" to "새 버전이 있어요 (%1\$s)",
        "update_download" to "받고 설치하기",
        "update_later" to "나중에",
        "update_downloading" to "받는 중 %1\$d%%",
        "update_need_permission" to "설치하려면 oot의 앱 설치를 허용해 주세요",
        "update_open_settings" to "설정 열기",
        "update_check_failed" to "업데이트를 확인하지 못했어요. 인터넷 연결을 확인해 주세요.",
        "update_download_failed" to "파일을 받지 못했어요",
        "update_verify_failed" to "파일을 확인하지 못해 설치하지 않았어요. 릴리스 페이지에서 받아 주세요.",
        "update_open_release" to "릴리스 페이지 열기",
        "update_install_failed" to "설치 화면을 열지 못했어요. 릴리스 페이지에서 받아 주세요.",
        "update_ok" to "확인",
        "update_current_version" to "지금 버전 %1\$s",
        "update_download_hint" to "다 받으면 설치 화면으로 넘어가요",
    )

    @Test
    fun updateCopyMatchesScopeDocument() {
        assertEquals(20, ko.size)
        val root = projectRoot()
        val values = loadStrings(File(root, "app/src/main/res/values/strings.xml"))
        ko.forEach { (key, value) -> assertEquals("values/$key", value, values[key]) }
    }

    private fun loadStrings(file: File): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("string")
        return (0 until nodes.length).associate { i ->
            val node = nodes.item(i)
            node.attributes.getNamedItem("name").nodeValue to node.textContent.replace("\\'", "'")
        }
    }
}

internal fun projectRoot(): File {
    var dir: File? = File(System.getProperty("user.dir") ?: ".").canonicalFile
    while (dir != null) {
        if (File(dir, "settings.gradle.kts").isFile) return dir
        dir = dir.parentFile
    }
    throw AssertionError("project root not found")
}
