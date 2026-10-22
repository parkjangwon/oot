package org.parkjw.apps.oot.i18n

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LanguageStoreTest {

    @Test
    fun defaultsToKoreanAndSurvivesARestart() = runBlocking {
        val file = File(Files.createTempDirectory("lang").toFile(), "language.preferences_pb")

        val firstJob = SupervisorJob()
        val first = LanguageStore(PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + firstJob)) { file })
        assertEquals(AppLanguage.KOREAN, first.current())
        first.set(AppLanguage.ENGLISH)
        assertEquals(AppLanguage.ENGLISH, first.current())
        firstJob.cancelAndJoin() // "process death": a new DataStore reads the file again

        val secondJob = SupervisorJob()
        val second = LanguageStore(PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + secondJob)) { file })
        assertEquals(AppLanguage.ENGLISH, second.current())
        second.set(AppLanguage.KOREAN)
        assertEquals(AppLanguage.KOREAN, second.current())
        secondJob.cancelAndJoin()
    }

    @Test
    fun languageTagsMapToSupportedLanguages() {
        assertEquals(AppLanguage.KOREAN, AppLanguage.fromTag("ko"))
        assertEquals(AppLanguage.KOREAN, AppLanguage.fromTag("ko-KR"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en_US"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("EN"))
        assertNull(AppLanguage.fromTag("ja"))
        assertNull(AppLanguage.fromTag(null))
        assertEquals(AppLanguage.KOREAN, AppLanguage.DEFAULT)
    }
}
