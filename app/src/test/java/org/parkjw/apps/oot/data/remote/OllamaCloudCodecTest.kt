package org.parkjw.apps.oot.data.remote

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.parkjw.apps.oot.data.model.Remaining
import org.parkjw.apps.oot.data.model.VendorError
import java.time.Instant

class OllamaCloudCodecTest {

    private val json = Json { ignoreUnknownKeys = true }

    // -- legacy shape fixture --

    private val legacyBody = """
        {
          "included": {
            "session": { "remaining_percent": 75, "resets_at": "2026-10-21T18:00:00Z" },
            "weekly": { "remaining_percent": 40, "resets_at": "2026-10-26T00:00:00Z" }
          },
          "purchased": { "balance_usd": 25 }
        }
    """.trimIndent()

    @Test
    fun `legacy json parses session first then weekly then purchased`() {
        val windows = OllamaCloudCodec.parse(legacyBody, json)!!
        assertEquals(3, windows.size)

        assertEquals("session", windows[0].id)
        assertEquals("session", windows[0].label)
        assertEquals(75.0, (windows[0].remaining as Remaining.Percent).percentLeft, 0.001)
        assertEquals(Instant.parse("2026-10-21T18:00:00Z"), windows[0].resetsAt)

        assertEquals("weekly", windows[1].id)
        assertEquals("weekly", windows[1].label)
        assertEquals(40.0, (windows[1].remaining as Remaining.Percent).percentLeft, 0.001)

        val purchased = windows[2].remaining as Remaining.Balance
        assertEquals(25.0, purchased.amountLeft, 0.001)
        assertEquals("USD", purchased.currency)
    }

    @Test
    fun `purchased balance of zero is skipped`() {
        val windows = OllamaCloudCodec.parse(
            """
            {"included": {"session": {"remaining_percent": 50}},
             "purchased": {"balance_usd": 0}}
            """.trimIndent(),
            json
        )!!
        assertEquals(1, windows.size)
        assertEquals("session", windows[0].id)
    }

    // -- credit plan shape fixture --

    private val creditBody = """
        {
          "included": {
            "balance_usd": 72.5,
            "allowance_usd": 100,
            "period": {
              "from": "2026-10-01T00:00:00Z",
              "until": "2026-11-01T00:00:00Z"
            }
          },
          "purchased": { "balance_usd": 25 }
        }
    """.trimIndent()

    @Test
    fun `credit plan json parses monthly percent window plus purchased`() {
        val windows = OllamaCloudCodec.parse(creditBody, json)!!
        assertEquals(2, windows.size)

        assertEquals("monthly", windows[0].id)
        assertEquals("monthly", windows[0].label)
        assertEquals(72.5, (windows[0].remaining as Remaining.Percent).percentLeft, 0.001)
        assertEquals(Instant.parse("2026-11-01T00:00:00Z"), windows[0].resetsAt)

        assertEquals(25.0, (windows[1].remaining as Remaining.Balance).amountLeft, 0.001)
    }

    // -- null or wrong-typed nested values --

    @Test
    fun `null included is treated as missing`() {
        assertNull(OllamaCloudCodec.parse("""{"included": null}""", json))
        assertNull(OllamaCloudCodec.parse("""{"included": "oops"}""", json))
    }

    @Test
    fun `null session and weekly are skipped`() {
        val windows = OllamaCloudCodec.parse(
            """
            {"included": {"session": null, "weekly": {"remaining_percent": 30}, "period": null},
             "purchased": null}
            """.trimIndent(),
            json
        )!!
        assertEquals(1, windows.size)
        assertEquals("weekly", windows.single().id)
    }

    @Test
    fun `wrong-typed nested values are treated as missing`() {
        assertEquals(
            emptyList<Nothing>(),
            OllamaCloudCodec.parse(
                """{"included": {"session": "oops", "weekly": []}, "purchased": "oops"}""",
                json
            )
        )
        val windows = OllamaCloudCodec.parse(
            """{"included": {"balance_usd": 50, "allowance_usd": 100, "period": {"until": null}}}""",
            json
        )!!
        assertEquals(1, windows.size)
        assertEquals("monthly", windows.single().id)
        assertNull(windows.single().resetsAt)
    }

    // -- empty fixture --

    @Test
    fun `empty included parses to an empty list`() {
        assertEquals(emptyList<Nothing>(), OllamaCloudCodec.parse("""{"included": {}}""", json))
    }

    @Test
    fun `malformed body fails to parse`() {
        assertNull(OllamaCloudCodec.parse("not json", json))
        assertNull(OllamaCloudCodec.parse("""{"purchased": {"balance_usd": 5}}""", json))
    }

    // -- 401 fixture --

    @Test
    fun `401 maps to Unauthorized`() {
        assertEquals(VendorError.Unauthorized, OllamaCloudCodec.httpError(401))
        assertTrue(OllamaCloudCodec.httpError(500) is VendorError.Http)
    }
}
