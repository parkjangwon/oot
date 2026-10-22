package org.parkjw.apps.oot.data.remote

import org.junit.Assert.assertFalse
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
    fun `http endpoint is rejected`() {
        assertFalse(isAllowedCustomEndpoint("http://api.example.com"))
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
}
