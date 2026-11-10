package app.pane.rdp

import org.junit.Assert.*
import org.junit.Test

class SessionProtocolTest {
    private fun bytes(vararg v: Int) = ByteArray(v.size) { v[it].toByte() }
    private fun header(key: Int = 1, frame: IntArray = intArrayOf(0x12, 0x34, 0x56, 0x78), stripe: Int = 3, parts: Int = 4) =
        bytes(0x01, key, *frame, 0, 0, 0, 0, stripe, 0, parts, 0, 0, 0)

    @Test fun parsesFrameHeader() {
        assertEquals(FrameHeader(key = true, frame = 0x12345678, stripe = 3, parts = 4), parseFrameHeader(header()))
    }
    @Test fun onlyBitZeroMarksKeyframe() = assertFalse(parseFrameHeader(header(key = 0x02))!!.key)
    @Test fun zeroPartsCountsAsOne() = assertEquals(1, parseFrameHeader(header(parts = 0))!!.parts)
    @Test fun frameIdIsUnsignedInAckText() {
        val f = parseFrameHeader(header(frame = intArrayOf(0xff, 0xff, 0xff, 0xff)))!!.frame
        assertEquals(-1, f)
        assertEquals(4294967295u, f.toUInt())
    }
    @Test fun rejectsBadFrameHeaders() {
        assertNull(parseFrameHeader(header().copyOf(15)))
        assertNull(parseFrameHeader(header().also { it[0] = 0x03 }))
        assertNull(parseFrameHeader(ByteArray(0)))
    }

    @Test fun singlePartFrameAcksImmediately() = assertTrue(FrameAcks().part(7, 1))
    @Test fun multiPartFrameAcksOnLastPart() {
        val a = FrameAcks()
        assertFalse(a.part(9, 3)); assertFalse(a.part(9, 3)); assertTrue(a.part(9, 3))
        assertFalse(a.part(9, 3)) // new count after completion
    }
    @Test fun interleavedFramesCompleteIndependently() {
        val a = FrameAcks()
        assertFalse(a.part(1, 2)); assertFalse(a.part(2, 2))
        assertTrue(a.part(1, 2)); assertTrue(a.part(2, 2))
    }
    @Test fun clearRestartsCounting() {
        val a = FrameAcks()
        assertFalse(a.part(5, 2)); a.clear()
        assertFalse(a.part(5, 2))
    }
    @Test fun overflowClearsPending() {
        val a = FrameAcks()
        for (i in 0..256) a.part(i, 2)
        assertFalse(a.part(0, 2)) // map was cleared; without the cap this would be true
    }

    private fun cursor(w: Int, h: Int, px: ByteArray) =
        bytes(0x03, 0, w shr 8, w and 0xff, h shr 8, h and 0xff, 0, 1, 0, 0, 0, 0, 0, 0) + px

    @Test fun parsesCursorPixelsAsArgb() {
        val c = parseCursor(cursor(2, 1, bytes(0x11, 0x22, 0x33, 0x44, 0xff, 0x00, 0x80, 0x00)))!!
        assertEquals(2, c.width); assertEquals(1, c.height); assertEquals(1, c.hotX); assertEquals(0, c.hotY)
        assertArrayEquals(intArrayOf(0x44112233, 0x00ff0080), c.argb)
    }
    @Test fun cursorOutOfBoundsHasNoImage() {
        assertNull(parseCursor(cursor(0, 1, ByteArray(0)))!!.argb)
        assertNull(parseCursor(cursor(513, 1, ByteArray(513 * 4)))!!.argb)
        assertNull(parseCursor(cursor(2, 1, ByteArray(7)))!!.argb)
    }
    @Test fun rejectsBadCursorMessages() {
        assertNull(parseCursor(ByteArray(13).also { it[0] = 0x03 }))
        assertNull(parseCursor(ByteArray(14).also { it[0] = 0x01 }))
    }
}
