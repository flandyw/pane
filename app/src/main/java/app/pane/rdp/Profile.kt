package app.pane.rdp

enum class Quality(val label: String, val height: Int, val detail: String) {
    FAST("Fast", 800, "Less work for the VPS"),
    BALANCED("Balanced", 1000, "A good place to start"),
    SHARP("Sharp", 1200, "Exactly 2× on a 2400-pixel-tall tablet");
    // Match the screen's shape so the desktop fills it without bars; capped at 16:10 to stay within the VPS budget.
    fun width(aspect: Float) = (height*aspect.coerceIn(1.25f, 1.6f)).toInt()/8*8
}

data class Profile(
    val host: String = "",
    val username: String = "",
    val fingerprint: String = "",
    val quality: Quality = Quality.BALANCED,
    val audio: Boolean = true
) {
    fun error(): String? = when {
        host.isBlank() || host.length > 253 || !host.matches(Regex("[a-zA-Z0-9.-]+")) -> "Enter a Tailscale IPv4 address or hostname, without a port."
        username.isBlank() || username.length > 128 || username.any { it.isISOControl() } -> "Enter your Linux username."
        normalizedPin().length != 64 || !normalizedPin().matches(Regex("[A-Fa-f0-9]{64}")) -> "The server certificate needs a 64-digit SHA-256 fingerprint."
        else -> null
    }
    fun normalizedPin() = fingerprint.replace(":", "").replace(" ", "").trim()
    fun arguments(password: String, aspect: Float = 1.6f): Array<String> {
        require(error() == null)
        require(password.isNotEmpty())
        return arrayOf(
            "Pane", "/v:$host:3389", "/u:$username", "/p:$password",
            "/size:${quality.width(aspect)}x${quality.height}", "/bpp:24", "/gdi:sw",
            "/sec:tls", "/cert:deny,fingerprint:sha256:${normalizedPin()}",
            "/timeout:10000", "/network:lan", "/compression", "/compression-level:2",
            "-wallpaper", "-window-drag", "-menu-anims", "-aero", "+fonts",
            // rdpsnd picks AAudio, falling back to OpenSL ES; the server is offered PCM/ADPCM only.
            if (audio) "/sound" else "/audio-mode:2", "/clipboard", "/kbd:unicode:on", "/log-level:ERROR"
        )
    }
}
