package app.pane.rdp

import android.view.KeyEvent
import org.junit.Assert.*
import org.junit.Test

class ProfileTest {
    @Test fun cpuProfileUsesPinnedTlsWithoutVideoOrEffects() {
        val args = Profile().arguments("test-only")
        assertTrue(args.contains("/sec:tls"))
        assertTrue(args.contains("/size:1600x1000"))
        assertTrue(args.contains("/bpp:24"))
        assertTrue(args.contains("-aero"))
        assertTrue(args.contains("/audio-mode:2"))
        assertTrue(args.any { it.startsWith("/cert:deny,fingerprint:sha256:") })
        assertFalse(args.any { it.contains("ignore") || it.contains("tofu") || it.contains("gfx") || it.contains("rfx") })
    }
    @Test fun rejectsMalformedEndpointsAndPins() {
        listOf("", "host:3389", "https://host", "host/path", "a\n/log-level:TRACE").forEach {
            assertNotNull(Profile(host=it).error())
        }
        assertNotNull(Profile(fingerprint="AB").error())
        assertNotNull(Profile(fingerprint="Z".repeat(64)).error())
        assertNotNull(Profile(username="ubuntu\n").error())
        assertNull(Profile(host="armvps.tail123.ts.net").error())
        assertNull(Profile(fingerprint=Profile().fingerprint.chunked(2).joinToString(":")).error())
    }
    @Test fun resolutionPresetsStayWithinServerBudget() {
        assertEquals(1280*800,Quality.FAST.width*Quality.FAST.height)
        assertTrue(Quality.entries.all { it.width<=1920 && it.height<=1200 })
        assertTrue(Quality.entries.zipWithNext().all { (a,b) -> a.width*a.height < b.width*b.height })
    }
    @Test fun argumentsKeepPasswordAndUserAsSeparateValues() {
        val password="space : slash / and ü"
        val args=Profile().arguments(password)
        assertEquals(1,args.count { it.startsWith("/p:") })
        assertEquals("/p:$password",args.first { it.startsWith("/p:") })
    }
    @Test fun shortcutsUseWindowsVirtualKeys() {
        assertEquals(0xa2,virtualKey(KeyEvent.KEYCODE_CTRL_LEFT))
        assertEquals(0x41,virtualKey(KeyEvent.KEYCODE_A))
        assertEquals(0x7b,virtualKey(KeyEvent.KEYCODE_F12))
        assertEquals(0x25,virtualKey(KeyEvent.KEYCODE_DPAD_LEFT))
        assertEquals(0x2e,virtualKey(KeyEvent.KEYCODE_FORWARD_DEL))
        assertNull(virtualKey(KeyEvent.KEYCODE_VOLUME_UP))
    }
}
