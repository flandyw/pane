package app.pane.rdp

import org.json.JSONObject

/** Validate server-controlled dimensions before allocating surfaces or codecs. */
internal data class VideoHello(val width: Int, val height: Int, val bands: List<Pair<Int, Int>>) {
    companion object {
        fun parse(json: JSONObject): VideoHello {
            val width = json.getInt("w"); val height = json.getInt("h")
            require(width in 1..8192 && height in 1..8192 && width.toLong() * height <= 33_554_432)
            val stripes = json.optJSONArray("stripes")
            val bands = if (stripes == null || stripes.length() == 0) listOf(0 to height) else {
                require(stripes.length() <= 16)
                List(stripes.length()) { i ->
                    val band = stripes.getJSONArray(i)
                    band.getInt(0) to band.getInt(1)
                }
            }
            var end = 0
            bands.forEach { (y, h) ->
                require(y == end && h > 0 && h <= height - y)
                end = y + h
            }
            require(end == height)
            return VideoHello(width, height, bands)
        }
    }
}
