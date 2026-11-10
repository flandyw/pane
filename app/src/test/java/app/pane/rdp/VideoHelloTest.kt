package app.pane.rdp

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class VideoHelloTest {
    @Test fun acceptsSingleAndStripedVideo() {
        assertEquals(listOf(0 to 1080), VideoHello.parse(JSONObject("""{"w":1920,"h":1080}""")).bands)
        assertEquals(listOf(0 to 540, 540 to 540), VideoHello.parse(JSONObject("""{"w":1920,"h":1080,"stripes":[[0,540],[540,540]]}""")).bands)
    }
    @Test fun rejectsInvalidDimensionsAndBands() {
        listOf(
            """{"w":0,"h":1080}""", """{"w":8192,"h":8192}""",
            """{"w":1920,"h":1080,"stripes":[[0,-1]]}""",
            """{"w":1920,"h":1080,"stripes":[[0,500],[400,680]]}""",
            """{"w":1920,"h":1080,"stripes":[[0,540]]}""",
            """{"w":1920,"h":1080,"stripes":[[0,540],[540,2147483647]]}"""
        ).forEach { raw -> assertThrows(IllegalArgumentException::class.java) { VideoHello.parse(JSONObject(raw)) } }
    }
}
