package app.pane.rdp

enum class Quality(val label: String, val height: Int, val detail: String) {
    FAST("Fast", 800, "Less work for the VPS"),
    BALANCED("Balanced", 1000, "A good place to start"),
    SHARP("Sharp", 1200, "Crisper text; more pixels to render");
    // Match the screen's shape so the desktop fills it without bars; capped at 16:10 to stay within the VPS budget.
    fun width(aspect: Float) = (height*(if (aspect.isFinite()) aspect else 1.6f).coerceIn(1.25f, 1.6f)).toInt()/8*8
}

data class Profile(val host: String = "", val quality: Quality = Quality.BALANCED) {
    fun hostError(): String? = if (Hosts.normalize(host) == null) "Enter a Voxl address, such as wss://server:6901/ws." else null
    fun error(): String? = hostError()
    fun endpoint(): String = requireNotNull(Hosts.normalize(host))
}
