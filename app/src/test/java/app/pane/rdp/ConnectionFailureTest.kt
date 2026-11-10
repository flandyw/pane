package app.pane.rdp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import javax.net.ssl.SSLPeerUnverifiedException

class ConnectionFailureTest {
    @Test fun retryDelayDoublesThenCaps() {
        listOf(0 to 1000L, 1 to 2000L, 4 to 16000L, 5 to 30000L, 6 to 30000L, 40 to 30000L, Int.MAX_VALUE to 30000L, -1 to 1000L)
            .forEach { (attempt, ms) -> assertEquals("attempt $attempt", ms, retryDelayMs(attempt)) }
    }

    @Test fun failureMessages() {
        val cases = listOf(
            connectionFailure(null, 404) to "404",
            connectionFailure(IOException(), 500) to "500",
            connectionFailure(UnknownHostException("x"), null) to "find that server",
            connectionFailure(SSLHandshakeException("x"), null) to "secure connection",
            connectionFailure(SSLPeerUnverifiedException("x"), null) to "secure connection",
            connectionFailure(ConnectException("x"), null) to "refused",
            connectionFailure(SocketTimeoutException("x"), null) to "didn't respond",
            connectionFailure(IOException("x"), null) to "Couldn't reach the Voxl server",
            connectionFailure(null, null) to "Couldn't reach the Voxl server",
        )
        cases.forEach { (msg, part) ->
            assertTrue("$msg should contain $part", msg.contains(part))
            assertNotEquals("Connecting…", msg)
            assertNotEquals("Reconnecting…", msg)
        }
    }
}
