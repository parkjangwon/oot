package org.parkjw.apps.oot.data.remote

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.parkjw.apps.oot.data.model.Remaining
import org.parkjw.apps.oot.data.model.VendorError

class DeepSeekCodecTest {

    private val json = Json { ignoreUnknownKeys = true }

    // -- success fixtures --

    private val successBody = """
        {
          "is_available": true,
          "balance_infos": [
            {
              "currency": "CNY",
              "total_balance": "110.00",
              "granted_balance": "10.00",
              "topped_up_balance": "100.00"
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `success json parses one balance window per entry`() {
        val windows = DeepSeekCodec.parse(successBody, json)!!
        assertEquals(1, windows.size)
        val balance = windows.single().remaining as Remaining.Balance
        assertEquals(110.0, balance.amountLeft, 0.001)
        assertEquals("CNY", balance.currency)
    }

    @Test
    fun `multiple balance entries become multiple windows`() {
        val windows = DeepSeekCodec.parse(
            """
            {"is_available": true, "balance_infos": [
              {"currency": "CNY", "total_balance": "110.00"},
              {"currency": "USD", "total_balance": "5.50"}
            ]}
            """.trimIndent(),
            json
        )!!
        assertEquals(2, windows.size)
        val usd = windows[1].remaining as Remaining.Balance
        assertEquals(5.5, usd.amountLeft, 0.001)
        assertEquals("USD", usd.currency)
    }

    // -- null or wrong-typed nested values --

    @Test
    fun `null balance_infos is treated as missing`() {
        assertNull(DeepSeekCodec.parse("""{"balance_infos": null}""", json))
        assertNull(DeepSeekCodec.parse("""{"balance_infos": {}}""", json))
    }

    @Test
    fun `null or non-object balance entries are skipped`() {
        val windows = DeepSeekCodec.parse(
            """{"balance_infos": [null, "oops", {"total_balance": "5.00"}]}""",
            json
        )!!
        assertEquals(1, windows.size)
        assertEquals(5.0, (windows.single().remaining as Remaining.Balance).amountLeft, 0.001)
    }

    @Test
    fun `non-primitive balance fields are treated as missing`() {
        assertEquals(
            emptyList<Nothing>(),
            DeepSeekCodec.parse("""{"balance_infos": [{"total_balance": {}}]}""", json)
        )
    }

    @Test
    fun `null currency falls back to USD instead of the text null`() {
        val windows = DeepSeekCodec.parse(
            """{"balance_infos": [{"currency": null, "total_balance": "5.00"}]}""",
            json
        )!!
        assertEquals("USD", (windows.single().remaining as Remaining.Balance).currency)
    }

    // -- empty fixture --

    @Test
    fun `empty balance_infos parses to an empty list`() {
        val windows = DeepSeekCodec.parse(
            """{"is_available": true, "balance_infos": []}""",
            json
        )
        assertEquals(emptyList<Nothing>(), windows)
    }

    // -- 401 fixture --

    @Test
    fun `401 maps to Unauthorized`() {
        assertEquals(VendorError.Unauthorized, DeepSeekCodec.httpError(401))
    }

    @Test
    fun `401 error body is not parseable usage`() {
        assertNull(
            DeepSeekCodec.parse(
                """{"error":{"message":"Invalid credential","code":"invalid_request_error"}}""",
                json
            )
        )
    }

    @Test
    fun `other http codes map`() {
        assertEquals(VendorError.RateLimited, DeepSeekCodec.httpError(429))
        assertTrue(DeepSeekCodec.httpError(500) is VendorError.Http)
    }
}
