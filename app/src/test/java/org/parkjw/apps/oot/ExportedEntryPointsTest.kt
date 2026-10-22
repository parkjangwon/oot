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
        val manifest = File(projectRoot(), "app/src/main/AndroidManifest.xml").readText()

        assertFalse("no <data> scheme filter allowed", manifest.contains("<data"))
        assertFalse("no oot:// scheme allowed", manifest.contains("android:scheme=\"oot\""))
        assertFalse("no BROWSABLE category allowed", manifest.contains("category.BROWSABLE"))
    }

    @Test
    fun sourcesDoNotReferenceOotRedirect() {
        val sources = File(projectRoot(), "app/src/main/java").walkTopDown().filter { it.extension == "kt" }
        sources.forEach {
            assertFalse("oot:// redirect found in $it", it.readText().contains("oot://"))
        }
    }

    private fun projectRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").isFile) dir = dir.parentFile
        return checkNotNull(dir) { "project root not found" }
    }
}
