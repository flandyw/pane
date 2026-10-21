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
        assertEquals(1280,Quality.FAST.width(1.6f))
        listOf(1.0f,1.4133f,1.6f,2.4f).forEach { aspect ->
            assertTrue(Quality.entries.all { it.width(aspect)<=1920 && it.height<=1200 && it.width(aspect)%8==0 })
            assertTrue(Quality.entries.zipWithNext().all { (a,b) -> a.width(aspect)*a.height < b.width(aspect)*b.height })
        }
    }
    @Test fun sharpIsExactlyHalfAOnePlusPad2Pro() {
        assertEquals(1696,Quality.SHARP.width(3392f/2400f))
        assertTrue(Profile(quality=Quality.SHARP).arguments("x",3392f/2400f).contains("/size:1696x1200"))
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
        assertEquals(0x2d,virtualKey(KeyEvent.KEYCODE_INSERT))
        assertEquals(0x65,virtualKey(KeyEvent.KEYCODE_NUMPAD_5))
        assertNull(virtualKey(KeyEvent.KEYCODE_VOLUME_UP))
    }
    private fun feed(code: Int, url: String = "https://pane.flandolf.me/releases/v0.2.0/pane-0.2.0.apk", digest: String = "sha256:"+"a".repeat(64), size: Long = 1000) =
        """{"version_code":$code,"version_name":"0.2.0","assets":[{"name":"pane-0.2.0.apk","browser_download_url":"$url","size":$size,"digest":"$digest"},{"name":"SHA256SUMS","browser_download_url":"x","size":1,"digest":"x"}]}"""
    @Test fun updateFeedOnlyAcceptsNewerReleasesFromOurOrigin() {
        assertEquals(Release(2,"0.2.0","https://pane.flandolf.me/releases/v0.2.0/pane-0.2.0.apk","a".repeat(64),1000),Release.parse(feed(2),1))
        assertNull(Release.parse(feed(1),1))
        listOf(feed(2,url="https://evil.example/pane.apk"),feed(2,url="http://pane.flandolf.me/releases/a.apk"),
            feed(2,url="https://pane.flandolf.me/releases/../x.apk"),feed(2,digest="sha256:XYZ"),feed(2,size=0)).forEach {
            assertThrows(IllegalArgumentException::class.java) { Release.parse(it,1) }
        }
    }
}
