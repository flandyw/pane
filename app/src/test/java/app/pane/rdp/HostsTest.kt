package app.pane.rdp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HostsTest {
    @Test fun rejectsMalformedEndpoints() {
        listOf("wss://h:0", "wss://h:65536", "wss://h:-2", "wss://h/ws#secret", "wss://h/w\ns", "wss://user@h").forEach {
            assertNull(it, Hosts.normalize(it))
        }
        assertEquals("ws://[::1]:6901/ws", Hosts.normalize("ws://[::1]:6901"))
    }
    @Test fun normalize() {
        assertEquals("wss://mac.ts.net:6901/ws", Hosts.normalize(" mac.ts.net:6901 "))
        assertEquals("wss://mac.ts.net/ws", Hosts.normalize("https://mac.ts.net/"))
        assertEquals("ws://10.0.2.2:6902/ws", Hosts.normalize("http://10.0.2.2:6902"))
        assertEquals("wss://h/other?x=1", Hosts.normalize("wss://h/other?x=1"))
        assertNull(Hosts.normalize("wss://user:pw@h/ws"))
        assertNull(Hosts.normalize("ftp://h"))
        assertNull(Hosts.normalize(""))
    }
}
