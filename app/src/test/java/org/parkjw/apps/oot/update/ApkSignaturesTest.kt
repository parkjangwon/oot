package org.parkjw.apps.oot.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkSignaturesTest {
    private val pkg = "org.parkjw.apps.oot"
    private val release = sha256Hex("release-cert".toByteArray())
    private val debug = sha256Hex("debug-cert".toByteArray())

    private fun snap(vararg signers: String, name: String = pkg) = SignerSnapshot(name, signers.toSet())

    @Test
    fun sha256IsLowercaseHex() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            sha256Hex(ByteArray(0)),
        )
    }

    @Test
    fun sameSignerMatches() {
        assertTrue(signaturesMatch(snap(release), snap(release)))
        assertTrue(signaturesMatch(snap(release, debug), snap(debug, release)))
    }

    @Test
    fun anyDifferenceOrMissingInfoIsRejected() {
        assertFalse(signaturesMatch(snap(release), snap(debug)))
        assertFalse(signaturesMatch(snap(release), snap(release, debug)))
        assertFalse(signaturesMatch(snap(release, debug), snap(release)))
        assertFalse(signaturesMatch(snap(release), snap(release, name = "com.example.other")))
        assertFalse(signaturesMatch(null, snap(release)))
        assertFalse(signaturesMatch(snap(release), null))
        assertFalse(signaturesMatch(snap(), snap()))
    }
}
