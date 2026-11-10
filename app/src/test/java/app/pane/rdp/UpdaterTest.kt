package app.pane.rdp

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.*
import org.junit.Test

class UpdaterTest {
    @Test fun downloadProgressIsBoundedAndCompletes() {
        val bytes = ByteArray(150_000) { (it % 127).toByte() }
        val output = ByteArrayOutputStream()
        val progress = mutableListOf<Long>()
        copyUpdate(ByteArrayInputStream(bytes), output, bytes.size.toLong(), progress::add)
        assertArrayEquals(bytes, output.toByteArray())
        assertEquals(0L, progress.first())
        assertEquals(bytes.size.toLong(), progress.last())
        assertTrue(progress.zipWithNext().all { (a, b) -> a <= b })
    }
    @Test fun rejectsTruncatedAndOversizedDownloads() {
        listOf(2L, 4L).forEach { expected ->
            val output = ByteArrayOutputStream()
            assertThrows(IllegalStateException::class.java) { copyUpdate(ByteArrayInputStream(byteArrayOf(1, 2, 3)), output, expected) {} }
            assertTrue(output.size() <= expected)
        }
    }
    @Test fun celebrationHappensOnceForAnUpgradeAndNeverForAFreshInstall() {
        assertTrue(shouldCelebrate(4, 5, true))
        assertTrue(shouldCelebrate(0, 5, true))
        assertFalse(shouldCelebrate(0, 5, false))
        assertFalse(shouldCelebrate(5, 5, true))
        assertFalse(shouldCelebrate(6, 5, true))
    }
    @Test fun pointerButtonsAndWheelDirectionsMatchVoxl() {
        assertEquals("b,1,1", voxPointer(0x9000))
        assertEquals("b,3,0", voxPointer(0x2000))
        assertEquals("b,2,1", voxPointer(0xc000))
        assertEquals("s,0,-1", voxPointer(0x0200 or 120))
        assertEquals("s,0,1", voxPointer(0x0200 or (-120 and 0x1ff)))
        assertEquals("s,1,0", voxPointer(0x0400 or 120))
        assertNull(voxPointer(0x0800))
    }
    @Test fun committedTextUsesUnicodeKeysymsAndOneEnterForCrLf() {
        assertArrayEquals(intArrayOf(0x61, 0xff0d, 0x01004e2d, 0x0101f600), voxText("a\r\n中😀"))
        assertArrayEquals(intArrayOf(0xff09, 0xff08), voxText("\t\b\u0000\ud800"))
    }
    @Test fun paneKeysTranslateToVoxlWithoutChangingTheToolbarContract() {
        assertEquals(0xffe3, voxKey(0xa2))
        assertEquals(0xffe9, voxKey(0xa4))
        assertEquals(0xff51, voxKey(0x25))
        assertEquals(0xffc9, voxKey(0x7b))
        assertEquals('a'.code, voxKey(0x41))
        assertNull(voxKey(-1))
    }
}
