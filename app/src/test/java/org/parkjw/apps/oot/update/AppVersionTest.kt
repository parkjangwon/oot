package org.parkjw.apps.oot.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVersionTest {
    @Test
    fun tagWithPrefixEqualsPlainVersionName() {
        val tag = AppVersion.parse("v0.3.0")
        val name = AppVersion.parse("0.3.0")
        assertEquals(AppVersion(0, 3, 0), tag)
        assertEquals(tag, name)
        assertEquals(0, tag!!.compareTo(name!!))
        assertEquals(AppVersion(0, 3, 0), AppVersion.parse("  v0.3.0\n"))
    }

    @Test
    fun parsesPreReleaseIdentifiers() {
        val rc = AppVersion.parse("v0.4.0-rc.1")!!
        assertEquals(listOf("rc", "1"), rc.preRelease)
        assertTrue(rc.isPreRelease)
        assertEquals("0.4.0-rc.1", rc.toString())
        assertEquals("0.3.0", AppVersion.parse("v0.3.0").toString())
    }

    @Test
    fun rejectsNonVersions() {
        listOf(
            null, "", "latest", "v", "1", "v0.3", "1.2.3.4", "v01.2.3", "1.x.0", "release-0.3.0",
            "1.2.3-", "1.2.3-rc..1", "1.2.3+45", "V0.3.0", "-1.0.0", "1.-2.3", "1.2.9999999999",
        )
            .forEach { assertNull(it, AppVersion.parse(it)) }
    }

    @Test
    fun ordersNumericallyNotLexically() {
        val chain = listOf("0.3.0", "0.3.1", "0.10.0").map { AppVersion.parse(it)!! }
        assertEquals(chain, chain.reversed().sorted())
        assertTrue(AppVersion.parse("0.10.0")!! > AppVersion.parse("0.9.9")!!)
        assertTrue(AppVersion.parse("1.0.0")!! > AppVersion.parse("0.99.99")!!)
        assertTrue(AppVersion.parse("0.3.10")!! > AppVersion.parse("0.3.2")!!)
        assertTrue(AppVersion.parse("v0.3.1")!! > AppVersion.parse("0.3.0")!!)
    }

    @Test
    fun preReleaseSortsBelowReleaseAndBySemverRules() {
        val ordered = listOf(
            "0.4.0-alpha",
            "0.4.0-alpha.1",
            "0.4.0-alpha.beta",
            "0.4.0-beta",
            "0.4.0-beta.2",
            "0.4.0-beta.11",
            "0.4.0-rc.1",
            "0.4.0",
        ).map { AppVersion.parse(it)!! }
        assertEquals(ordered, ordered.shuffled(java.util.Random(7)).sorted())
        assertTrue(AppVersion.parse("0.4.0-rc.1")!! > AppVersion.parse("0.3.9")!!)
    }
}
