package org.parkjw.apps.oot

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Guards issue #111: sign-in runs through device flow and a pasted code, so the app must not
 * expose a custom-scheme redirect entry point again.
 */
class ExportedEntryPointsTest {

    @Test
    fun manifestDeclaresNoOauthDeepLink() {
        listOf("app/src/main/AndroidManifest.xml", "app/src/debug/AndroidManifest.xml")
            .map { File(projectRoot(), it) }
            .filter { it.isFile }
            .forEach { file ->
                val manifest = file.readText().replace('\'', '"')

                assertFalse("no <data> scheme filter allowed in $file", manifest.contains("<data"))
                assertFalse("no oot:// scheme allowed in $file", manifest.contains("android:scheme=\"oot\""))
                assertFalse("no BROWSABLE category allowed in $file", manifest.contains("category.BROWSABLE"))
            }
    }

    @Test
    fun sourcesDoNotReferenceOotRedirect() {
        val sources = File(projectRoot(), "app/src/main/java").walkTopDown().filter { it.extension == "kt" }
        sources.forEach { file ->
            val text = file.readText()
            listOf("oot://", "handleOAuthCallback", "onNewIntent(").forEach {
                assertFalse("$it found in $file", text.contains(it))
            }
        }
    }

    private fun projectRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").isFile) dir = dir.parentFile
        return checkNotNull(dir) { "project root not found" }
    }
}
