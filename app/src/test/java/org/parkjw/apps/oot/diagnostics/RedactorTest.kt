package org.parkjw.apps.oot.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RedactorTest {

    @Test
    fun masksBearerAndAuthorizationValues() {
        val redacted = Redactor.redact(
            "Authorization: Bearer eyJhbGciOiJIUzI1NiJ9.payload.signature\nBearer abcdefgh12345678",
        )
        assertFalse(redacted.contains("eyJhbGciOiJIUzI1NiJ9"))
        assertFalse(redacted.contains("abcdefgh12345678"))
        assertTrue(redacted.contains("[REDACTED]"))
    }

    @Test
    fun masksLabeledTokensAndApiKeys() {
        val redacted = Redactor.redact(
            "api_key=sk-abcdefgh12345678 access_token: tok_live_1234567890 secret=\"my-secret-value\" password=hunter2",
        )
        assertFalse(redacted.contains("sk-abcdefgh12345678"))
        assertFalse(redacted.contains("tok_live_1234567890"))
        assertFalse(redacted.contains("my-secret-value"))
        assertFalse(redacted.contains("hunter2"))
    }

    @Test
    fun masksEmails() {
        assertEquals("user [EMAIL] failed", Redactor.redact("user demo@example.com failed"))
    }

    @Test
    fun masksLongHexAndBase64Strings() {
        val hex = "a1b2c3d4e5f60718293a4b5c6d7e8f90"
        val base64 = "QWxhZGRpbjpvcGVuIHNlc2FtZQ=="
        val redacted = Redactor.redact("hex=$hex base=$base64")

        assertFalse(redacted.contains(hex))
        assertFalse(redacted.contains(base64))
        assertEquals("hex=[REDACTED] base=[REDACTED]", redacted)
    }

    @Test
    fun masksTokenQueryParameters() {
        val redacted = Redactor.redact("https://example.com/cb?access_token=abcdef123456&state=ok")
        assertFalse(redacted.contains("abcdef123456"))
        assertTrue(redacted.contains("state=ok"))
    }

    @Test
    fun keepsOrdinaryStackTracesReadable() {
        val stack = """
            java.lang.NullPointerException: demo
                at org.parkjw.apps.oot.ui.MainActivity.onCreate(MainActivity.kt:42)
                at android.app.Activity.performCreate(Activity.java:8000)
        """.trimIndent()
        assertEquals(stack, Redactor.redact(stack))
    }

    @Test
    fun keepsShortIdentifiers() {
        val text = "mode=plainword retry=2"
        assertEquals(text, Redactor.redact(text))
    }
}
