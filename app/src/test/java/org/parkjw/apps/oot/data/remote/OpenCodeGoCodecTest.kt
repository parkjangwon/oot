package org.parkjw.apps.oot.data.remote

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.parkjw.apps.oot.data.model.Remaining
import org.parkjw.apps.oot.data.model.VendorError
import java.time.Instant

class OpenCodeGoCodecTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val successBody = """
        {
          "usage": {
            "rolling": { "status": "ok", "percent": 11, "resetsAt": "2026-10-21T18:00:00Z" },
            "monthly": { "status": "ok", "percent": 3, "resetsAt": "2026-11-01T00:00:00Z" }
          }
        }
    """.trimIndent()

    @Test
    fun `success json parses windows with remaining 100 - used percent`() {
        val windows = OpenCodeGoCodec.parse(successBody, json)!!
        assertEquals(2, windows.size)

        // rolling uses the same id as Claude's 5-hour window
        assertEquals("five_hour", windows[0].id)
        assertEquals("5h", windows[0].label)
        assertEquals(89.0, (windows[0].remaining as Remaining.Percent).percentLeft, 0.001)
        assertEquals(Instant.parse("2026-10-21T18:00:00Z"), windows[0].resetsAt)

        assertEquals("monthly", windows[1].id)
        assertEquals("monthly", windows[1].label)
        assertEquals(97.0, (windows[1].remaining as Remaining.Percent).percentLeft, 0.001)
    }

    @Test
    fun `any window may be missing`() {
        val windows = OpenCodeGoCodec.parse(
            """{"usage": {"weekly": {"percent": 0}}}""",
            json
        )!!
        assertEquals(1, windows.size)
        assertEquals("weekly", windows[0].id)
        assertEquals("weekly", windows[0].label)
        assertEquals(100.0, (windows[0].remaining as Remaining.Percent).percentLeft, 0.001)
    }

    @Test
    fun `remaining is clamped to the 0-100 range`() {
        val windows = OpenCodeGoCodec.parse(
            """{"usage": {"rolling": {"percent": 120}, "weekly": {"percent": -5}}}""",
            json
        )!!
        assertEquals(0.0, (windows[0].remaining as Remaining.Percent).percentLeft, 0.001)
        assertEquals(100.0, (windows[1].remaining as Remaining.Percent).percentLeft, 0.001)
    }

    @Test
    fun `windows without percent are skipped`() {
        assertEquals(
            emptyList<Nothing>(),
            OpenCodeGoCodec.parse("""{"usage": {"rolling": {"status": "ok"}}}""", json)
        )
        assertEquals(emptyList<Nothing>(), OpenCodeGoCodec.parse("""{"usage": {}}""", json))
    }

    @Test
    fun `null usage is treated as missing`() {
        assertNull(OpenCodeGoCodec.parse("""{"usage": null}""", json))
        assertNull(OpenCodeGoCodec.parse("""{"usage": "oops"}""", json))
    }

    @Test
    fun `null or non-object windows are skipped`() {
        assertEquals(
            emptyList<Nothing>(),
            OpenCodeGoCodec.parse(
                """{"usage": {"rolling": null, "weekly": "oops", "monthly": []}}""",
                json
            )
        )
        val windows = OpenCodeGoCodec.parse(
            """{"usage": {"rolling": {"percent": null}, "weekly": {"percent": 10}}}""",
            json
        )!!
        assertEquals(1, windows.size)
        assertEquals("weekly", windows.single().id)
    }

    @Test
    fun `non-primitive percent is treated as missing`() {
        assertEquals(
            emptyList<Nothing>(),
            OpenCodeGoCodec.parse("""{"usage": {"rolling": {"percent": {}}}}""", json)
        )
    }

    @Test
    fun `malformed body fails to parse`() {
        assertNull(OpenCodeGoCodec.parse("not json", json))
        assertNull(OpenCodeGoCodec.parse("""{"error": "boom"}""", json))
    }

    // -- 401 fixture --

    @Test
    fun `401 maps to Unauthorized`() {
        assertEquals(VendorError.Unauthorized, OpenCodeGoCodec.httpError(401))
    }

    @Test
    fun `403 maps to an unknown error that OpenCodeGoApi words as no subscription`() {
        assertEquals(
            VendorError.Unknown(null),
            OpenCodeGoCodec.httpError(403)
        )
        assertTrue(OpenCodeGoCodec.httpError(500) is VendorError.Http)
    }
}
