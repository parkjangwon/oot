package org.parkjw.apps.oot

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

/**
 * Guards issue #93: the credential store is bound to this device's Keystore and must never
 * leave it via cloud backup or device transfer, on any Android version.
 */
class BackupRulesTest {

    private val credentialFiles = setOf(
        "sharedpref/oot_secure_prefs.xml",
        "sharedpref/oot_fallback_prefs.xml",
        "sharedpref/oot_tink_prefs.xml",
        "sharedpref/oot_tink_keyset.xml",
    )
    private val updateStateFile = "file/datastore/update.preferences_pb"
    private val languageFile = "file/datastore/language.preferences_pb"

    @Test
    fun android12RulesExcludeCredentialsFromCloudBackupAndTransfer() {
        val root = parseRuleFile("data_extraction_rules.xml")

        assertExcluded(excludes(root, "cloud-backup"), "cloud-backup")
        assertExcluded(excludes(root, "device-transfer"), "device-transfer")
    }

    @Test
    fun legacyRulesExcludeCredentialsFromFullBackup() {
        val root = parseRuleFile("backup_rules.xml")

        assertExcluded(excludes(root, null), "full-backup-content")
    }

    @Test
    fun languagePreferenceStaysIncluded() {
        (excludes(parseRuleFile("data_extraction_rules.xml"), "cloud-backup") +
            excludes(parseRuleFile("data_extraction_rules.xml"), "device-transfer") +
            excludes(parseRuleFile("backup_rules.xml"), null))
            .forEach { assertFalse("language datastore must stay backed up: $it", it == languageFile) }
    }

    @Test
    fun manifestReferencesBothRuleFiles() {
        val manifest = File(projectRoot(), "app/src/main/AndroidManifest.xml").readText()

        assertTrue(
            "manifest must set android:dataExtractionRules",
            manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\""),
        )
        assertTrue(
            "manifest must set android:fullBackupContent",
            manifest.contains("android:fullBackupContent=\"@xml/backup_rules\""),
        )
        assertTrue("allowBackup stays true", manifest.contains("android:allowBackup=\"true\""))
    }

    private fun assertExcluded(excluded: Set<String>, section: String) {
        credentialFiles.forEach {
            assertTrue("$section must exclude $it", it in excluded)
        }
        assertTrue("$section must exclude $updateStateFile", updateStateFile in excluded)
    }

    /** Pairs of `domain/path` for every `<exclude>` under [section], or under the root when null. */
    private fun excludes(root: Element, section: String?): Set<String> {
        val scope = if (section == null) root else root.getElementsByTagName(section).item(0) as Element
        val nodes = scope.getElementsByTagName("exclude")
        return (0 until nodes.length).map { index ->
            val element = nodes.item(index) as Element
            "${element.getAttribute("domain")}/${element.getAttribute("path")}"
        }.toSet()
    }

    private fun parseRuleFile(name: String): Element {
        val file = File(projectRoot(), "app/src/main/res/xml/$name")
        assertTrue("missing $file", file.isFile)
        return DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(file)
            .documentElement
    }

    private fun projectRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").isFile) dir = dir.parentFile
        return checkNotNull(dir) { "project root not found" }
    }
}
