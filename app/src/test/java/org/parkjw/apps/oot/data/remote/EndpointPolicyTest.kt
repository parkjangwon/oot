package org.parkjw.apps.oot.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointPolicyTest {

    @Test
    fun `blank means provider default`() {
        assertTrue(isAllowedCustomEndpoint(""))
        assertTrue(isAllowedCustomEndpoint("   "))
    }

    @Test
    fun `https endpoint is allowed`() {
        assertTrue(isAllowedCustomEndpoint("https://api.example.com"))
        assertTrue(isAllowedCustomEndpoint("https://api.example.com/v1/usage"))
        assertTrue(isAllowedCustomEndpoint("  https://api.example.com  "))
    }

    @Test
    fun `uppercase scheme is allowed`() {
        assertTrue(isAllowedCustomEndpoint("HTTPS://api.example.com"))
    }

    @Test
    fun `ipv6 host is allowed`() {
        assertTrue(isAllowedCustomEndpoint("https://[2001:db8::1]/v1"))
    }

    @Test
    fun `non standard port and trailing slash are allowed`() {
        assertTrue(isAllowedCustomEndpoint("https://api.example.com:8443"))
        assertTrue(isAllowedCustomEndpoint("https://api.example.com/"))
    }

    @Test
    fun `http endpoint is rejected`() {
        assertFalse(isAllowedCustomEndpoint("http://api.example.com"))
        assertFalse(isAllowedCustomEndpoint("HTTP://api.example.com"))
    }

    @Test
    fun `endpoint with query or fragment is rejected`() {
        assertFalse(isAllowedCustomEndpoint("https://api.example.com?key=abc"))
        assertFalse(isAllowedCustomEndpoint("https://api.example.com/v1?key=abc"))
        assertFalse(isAllowedCustomEndpoint("https://api.example.com/#frag"))
    }

    @Test
    fun `non http schemes and garbage are rejected`() {
        assertFalse(isAllowedCustomEndpoint("ftp://api.example.com"))
        assertFalse(isAllowedCustomEndpoint("not a url"))
        assertFalse(isAllowedCustomEndpoint("api.example.com"))
    }

    @Test
    fun `endpoint with user info is rejected`() {
        assertFalse(isAllowedCustomEndpoint("https://user:pass@api.example.com"))
        assertFalse(isAllowedCustomEndpoint("https://user@api.example.com"))
    }

    @Test
    fun `resolve base url blank falls back to default`() {
        assertEquals("https://default.example.com", resolveBaseUrl(null, "https://default.example.com"))
        assertEquals("https://default.example.com", resolveBaseUrl("", "https://default.example.com"))
        assertEquals("https://default.example.com", resolveBaseUrl("   ", "https://default.example.com"))
    }

    @Test
    fun `resolve base url trims allowed custom endpoint`() {
        assertEquals(
            "https://api.example.com",
            resolveBaseUrl("  https://api.example.com  ", "https://default.example.com")
        )
    }

    @Test
    fun `resolve base url rejects disallowed custom endpoint`() {
        assertNull(resolveBaseUrl("http://api.example.com", "https://default.example.com"))
    }
}
