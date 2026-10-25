package com.music.bitchord.data.innertube

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import java.io.IOException
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Every signed-in request asks for the account's session first. When that read failed it
 * used to be forgotten, so each request repeated it, one at a time, and the app crawled
 * until it was restarted.
 */
class SessionScopeTest {
    private val signedIn = """{"LOGGED_IN":true,"INNERTUBE_CLIENT_VERSION":"1.20260101.01.00"}"""

    @AfterTest
    fun reset() {
        Innertube.shellPageForTest = null
        Innertube.cookie = null
    }

    @Test
    fun aFailedReadIsNotRepeatedByEveryRequest() = runBlocking {
        var reads = 0
        Innertube.shellPageForTest = { reads++; throw IOException("connection reset") }
        Innertube.cookie = "SAPISID=a; test=1"
        repeat(5) { Innertube.ensureSessionScope() }
        assertEquals(1, reads)
        assertFalse(Innertube.hasSessionScope)
    }

    @Test
    fun aNetworkChangeAllowsAnotherReadAtOnce() = runBlocking {
        var reads = 0
        Innertube.shellPageForTest = { reads++; throw IOException("connection reset") }
        Innertube.cookie = "SAPISID=a; test=2"
        Innertube.ensureSessionScope()
        Innertube.shellPageForTest = { reads++; signedIn }
        Innertube.onNetworkChanged()
        Innertube.ensureSessionScope()
        assertEquals(2, reads)
        assertTrue(Innertube.hasSessionScope)
    }

    @Test
    fun aHangingReadDoesNotHoldRequestsUp() = runBlocking {
        Innertube.shellPageForTest = { awaitCancellation() }
        Innertube.cookie = "SAPISID=a; test=3"
        val started = System.currentTimeMillis()
        Innertube.ensureSessionScope()
        Innertube.ensureSessionScope()
        val took = System.currentTimeMillis() - started
        // One bounded wait, then requests go ahead without it.
        assertTrue(took < 10_000, "waited $took ms")
        assertFalse(Innertube.hasSessionScope)
    }

    @Test
    fun aSignedInPageScopesRequests() = runBlocking {
        Innertube.shellPageForTest = { signedIn }
        Innertube.cookie = "SAPISID=a; test=4"
        Innertube.ensureSessionScope()
        assertTrue(Innertube.hasSessionScope)
    }
}

class SharedHttpClientTest {
    /** Without pings a connection that died with a network change is reused until restart. */
    @Test
    fun pooledConnectionsAreCheckedAndRefreshed() {
        assertEquals(15_000, com.music.bitchord.data.Http.client.pingIntervalMillis)
    }
}
