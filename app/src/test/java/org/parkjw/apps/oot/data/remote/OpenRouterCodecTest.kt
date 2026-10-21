package org.parkjw.apps.oot.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.parkjw.apps.oot.data.model.Remaining

// FAKE fixtures only — no real keys or balances.
class OpenRouterCodecTest {

    private val keyWithLimit = """
        {"data":{"label":"sk-or-v1-demo","limit":20.0,"limit_remaining":7.6,"usage":12.4,"limit_reset":null,"is_free_tier":false,"rate_limit":{"requests":600,"interval":"60s"}}}
    """.trimIndent()

    private val keyWithoutLimit = """
        {"data":{"label":"sk-or-v1-demo","limit":null,"limit_remaining":null,"usage":3.2,"limit_reset":null,"is_free_tier":false,"rate_limit":{"requests":600,"interval":"60s"}}}
    """.trimIndent()

    private val credits = """
        {"data":{"total_credits":50.0,"total_usage":37.6}}
    """.trimIndent()

    // GET /credits with a non-management key can return 401/403.
    private val creditsForbidden = """
        {"error":{"code":403,"message":"Insufficient permissions to access this endpoint"}}
    """.trimIndent()

    @Test
    fun `parse key with limit`() {
        val key = OpenRouterCodec.parseKey(keyWithLimit)
        requireNotNull(key)
        assertEquals("sk-or-v1-demo", key.label)
        assertEquals(20.0, key.limit!!, 0.001)
        assertEquals(7.6, key.limitRemaining!!, 0.001)
        assertEquals(12.4, key.usage, 0.001)
    }

    @Test
    fun `parse key without limit`() {
        val key = OpenRouterCodec.parseKey(keyWithoutLimit)
        requireNotNull(key)
        assertNull(key.limit)
        assertNull(key.limitRemaining)
        assertEquals(3.2, key.usage, 0.001)
    }

    @Test
    fun `parse credits`() {
        val credits = OpenRouterCodec.parseCredits(credits)
        requireNotNull(credits)
        assertEquals(50.0, credits.totalCredits, 0.001)
        assertEquals(37.6, credits.totalUsage, 0.001)
        assertEquals(12.4, OpenRouterCodec.accountBalance(credits), 0.001)
    }

    @Test
    fun `parse credits 403 error body returns null`() {
        assertNull(OpenRouterCodec.parseCredits(creditsForbidden))
        assertNull(OpenRouterCodec.parseCredits("not json"))
        assertNull(OpenRouterCodec.parseKey("not json"))
    }

    @Test
    fun `windows with credits and key limit`() {
        val credits = OpenRouterCodec.parseCredits(credits)!!
        val key = OpenRouterCodec.parseKey(keyWithLimit)!!
        val windows = OpenRouterCodec.windows(credits, key)
        assertEquals(listOf("credit", "key"), windows.map { it.id })
        val credit = windows[0].remaining as Remaining.Balance
        assertEquals(12.4, credit.amountLeft, 0.001)
        assertEquals("USD", credit.currency)
        val keyWindow = windows[1]
        assertEquals("키 한도 $20.00", keyWindow.label)
        assertEquals(7.6, (keyWindow.remaining as Remaining.Balance).amountLeft, 0.001)
    }

    @Test
    fun `windows without key limit has only credit window`() {
        val credits = OpenRouterCodec.parseCredits(credits)!!
        val key = OpenRouterCodec.parseKey(keyWithoutLimit)!!
        val windows = OpenRouterCodec.windows(credits, key)
        assertEquals(listOf("credit"), windows.map { it.id })
    }

    @Test
    fun `windows with credits 403 falls back to key window only`() {
        val key = OpenRouterCodec.parseKey(keyWithLimit)!!
        val windows = OpenRouterCodec.windows(credits = null, key = key)
        assertEquals(listOf("key"), windows.map { it.id })
    }

    @Test
    fun `windows empty when both unavailable`() {
        assertEquals(emptyList<String>(), OpenRouterCodec.windows(null, null).map { it.id })
    }
}
