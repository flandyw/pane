package app.pane.rdp

import org.junit.Assert.*
import org.junit.Test

class ScrollTest {
    @Test fun fractionalMotionAccumulatesWithoutExtraClicks() {
        val sent = mutableListOf<Int>()
        val remainder = scrollNotches(2.75f,1f,sent::add)
        assertEquals(listOf(1,1),sent)
        assertEquals(0.75f,remainder,0f)
        assertEquals(0.25f,scrollNotches(remainder+0.5f,1f,sent::add),0f)
        assertEquals(listOf(1,1,1),sent)
    }
    @Test fun negativeMotionKeepsItsDirectionAndRemainder() {
        val sent = mutableListOf<Int>()
        assertEquals(-0.5f,scrollNotches(-72.5f,36f,sent::add),0f)
        assertEquals(listOf(-1,-1),sent)
    }
    @Test fun malformedMotionCannotLoopOrEmitEvents() {
        listOf(Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY).forEach {
            assertEquals(0f,scrollNotches(it,1f) { fail() },0f)
            assertEquals(0f,scrollNotches(1f,it) { fail() },0f)
        }
        listOf(0f,-1f).forEach { assertEquals(0f,scrollNotches(1f,it) { fail() },0f) }
    }
    @Test fun largeMotionIsCappedToThirtyTwoNotches() {
        val sent = mutableListOf<Int>()
        assertEquals(0f,scrollNotches(Float.MAX_VALUE,1f,sent::add),0f)
        assertEquals(32,sent.size)
        assertTrue(sent.all { it == 1 })
        sent.clear()
        scrollNotches(-10000f,36f,sent::add)
        assertEquals(32,sent.size)
        assertTrue(sent.all { it == -1 })
    }
}
