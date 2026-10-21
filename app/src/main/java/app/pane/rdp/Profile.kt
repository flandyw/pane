package app.pane.rdp

enum class Quality(val label: String, val width: Int, val height: Int, val detail: String) {
    FAST("Fast", 1280, 800, "Less work for the VPS"),
    BALANCED("Balanced", 1600, 1000, "A good place to start"),
    SHARP("Sharp", 1920, 1200, "More room for code")
}

data class Profile(
    val host: String = "",
    val username: String = "",
    val fingerprint: String = "",
    val quality: Quality = Quality.BALANCED
) {
    fun error(): String? = when {
        host.isBlank() || host.length > 253 || !host.matches(Regex("[a-zA-Z0-9.-]+")) -> "Enter a Tailscale IPv4 address or hostname, without a port."
        username.isBlank() || username.length > 128 || username.any { it.isISOControl() } -> "Enter your Linux username."
        normalizedPin().length != 64 || !normalizedPin().matches(Regex("[A-Fa-f0-9]{64}")) -> "The server certificate needs a 64-digit SHA-256 fingerprint."
        else -> null
    }
    fun normalizedPin() = fingerprint.replace(":", "").replace(" ", "").trim()
    fun arguments(password: String): Array<String> {
        require(error() == null)
        require(password.isNotEmpty())
        return arrayOf(
            "Pane", "/v:$host:3389", "/u:$username", "/p:$password",
            "/size:${quality.width}x${quality.height}", "/bpp:24", "/gdi:sw",
            "/sec:tls", "/cert:deny,fingerprint:sha256:${normalizedPin()}",
            "/timeout:10000", "/network:lan", "/compression", "/compression-level:2",
            "-wallpaper", "-window-drag", "-menu-anims", "-aero", "+fonts",
            "/audio-mode:2", "/clipboard", "/kbd:unicode:on", "/log-level:ERROR"
        )
    }
}
