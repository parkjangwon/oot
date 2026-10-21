package org.parkjw.apps.oot.update

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class UpdateStoreTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun persistsLastCheckDayAndSnooze() = runTest {
        val store = DataStoreUpdateStore(
            PreferenceDataStoreFactory.create(
                scope = backgroundScope,
                produceFile = { File(tmp.root, "update.preferences_pb") },
            ),
        )
        assertNull(store.lastAutoCheckDay())
        assertNull(store.snooze())
        store.setLastAutoCheckDay(20_000)
        store.setSnooze(UpdateSnooze("1.1.0", 20_000))
        assertEquals(20_000L, store.lastAutoCheckDay())
        assertEquals(UpdateSnooze("1.1.0", 20_000), store.snooze())
    }
}
