package app.pane.rdp
import org.junit.Assert.*
import org.junit.Test
class ReleaseTest {
    private fun feed(code: Int, url: String = "https://pane.flandolf.me/releases/v0.2.0/pane-0.2.0.apk", digest: String = "sha256:"+"a".repeat(64), size: Long = 1000) =
        """{"version_code":$code,"version_name":"0.2.0","assets":[{"name":"pane-0.2.0.apk","browser_download_url":"$url","size":$size,"digest":"$digest"},{"name":"SHA256SUMS","browser_download_url":"x","size":1,"digest":"x"}]}"""
    @Test fun updateFeedOnlyAcceptsNewerReleasesFromOurOrigin() {
        assertEquals(Release(2,"0.2.0","https://pane.flandolf.me/releases/v0.2.0/pane-0.2.0.apk","a".repeat(64),1000),Release.parse(feed(2),1))
        assertNull(Release.parse(feed(1),1))
        listOf(feed(2,url="https://evil.example/pane.apk"),feed(2,url="https://pane.flandolf.me/releases/%2e%2e/x.apk"),
            feed(2,url="https://pane.flandolf.me:8080/releases/a.apk"),feed(2,url="https://user@pane.flandolf.me/releases/a.apk"),feed(2,url="http://pane.flandolf.me/releases/a.apk"),
            feed(2,url="https://pane.flandolf.me/releases/../x.apk"),feed(2,digest="sha256:XYZ"),feed(2,size=0)).forEach {
            assertThrows(IllegalArgumentException::class.java) { Release.parse(it,1) }
        }
    }
}
