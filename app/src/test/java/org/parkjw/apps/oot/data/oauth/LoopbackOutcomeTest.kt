package org.parkjw.apps.oot.data.oauth

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config


// Robolectric: error texts come from string resources (AppText), Korean by default.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class LoopbackOutcomeTest {

    private val state = "state-test-000"

    @Test
    fun `callback with the expected state yields the decoded code`() {
        val line = "GET /?state=$state&code=4%2F0Abc-test&scope=email%20profile HTTP/1.1"
        assertEquals(LoopbackOutcome.Code("4/0Abc-test"), loopbackOutcome(line, state))
    }

    @Test
    fun `callback with another or missing state is not this login`() {
        assertEquals(LoopbackOutcome.Unexpected, loopbackOutcome("GET /?state=stale&code=evil HTTP/1.1", state))
        assertEquals(LoopbackOutcome.Unexpected, loopbackOutcome("GET /?code=evil HTTP/1.1", state))
    }

    @Test
    fun `probes and malformed lines are ignored`() {
        assertEquals(LoopbackOutcome.Unexpected, loopbackOutcome("GET /favicon.ico HTTP/1.1", state))
        assertEquals(LoopbackOutcome.Unexpected, loopbackOutcome("GET / HTTP/1.1", state))
        assertEquals(LoopbackOutcome.Unexpected, loopbackOutcome("", state))
        assertEquals(LoopbackOutcome.Unexpected, loopbackOutcome("GET /?state=$state&code=%zz HTTP/1.1", state))
        assertEquals(LoopbackOutcome.Unexpected, loopbackOutcome("GET /?state=$state&code= HTTP/1.1", state))
    }

    @Test
    fun `denied consent is reported with the matching state only`() {
        assertEquals(
            LoopbackOutcome.Denied("Google 로그인이 취소되었어요."),
            loopbackOutcome("GET /?state=$state&error=access_denied HTTP/1.1", state)
        )
        assertEquals(
            LoopbackOutcome.Denied("Google 로그인에 실패했어요 (invalid_scope)."),
            loopbackOutcome("GET /?state=$state&error=invalid_scope HTTP/1.1", state)
        )
        assertEquals(LoopbackOutcome.Unexpected, loopbackOutcome("GET /?state=stale&error=access_denied HTTP/1.1", state))
    }
}
