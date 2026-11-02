package app.pane.rdp

enum class Quality(val label: String, val height: Int, val detail: String) {
    FAST("Fast", 800, "Less work for the VPS"),
    BALANCED("Balanced", 1000, "A good place to start"),
    SHARP("Sharp", 1200, "Crisper text; more pixels to render");
    // Match the screen's shape so the desktop fills it without bars; capped at 16:10 to stay within the VPS budget.
    fun width(aspect: Float) = (height*(if (aspect.isFinite()) aspect else 1.6f).coerceIn(1.25f, 1.6f)).toInt()/8*8
}

data class Profile(
    val host: String = "",
    val username: String = "",
    val fingerprint: String = "",
    val quality: Quality = Quality.BALANCED,
    val audio: Boolean = true
) {
    fun hostError(): String? {
        if (host.isBlank()) return "Enter your Tailscale address."
        if (host.length > 253 || !hostPattern.matches(host)) return "Use a hostname or IPv4 address, without a port or URL."
        val labels = host.removeSuffix(".").split('.')
        if (labels.any { it.isEmpty() || it.length > 63 || it.startsWith('-') || it.endsWith('-') })
            return "Check the hostname for empty labels or misplaced hyphens."
        if (host.all { it.isDigit() || it == '.' } &&
            (labels.size != 4 || labels.any { (it.toIntOrNull() ?: -1) !in 0..255 }))
            return "Enter a valid IPv4 address, such as 100.64.0.1."
        return null
    }
    fun usernameError(): String? = if (username.isBlank() || username.length > 128 || username.any { it.isISOControl() })
        "Enter your Linux username (up to 128 characters)." else null
    fun fingerprintError(): String? = if (!pinPattern.matches(normalizedPin()))
        "Paste all 64 hexadecimal digits of the SHA-256 fingerprint." else null
    fun error(): String? = hostError() ?: usernameError() ?: fingerprintError()
    fun normalizedPin() = fingerprint.filterNot { it == ':' || it.isWhitespace() }
    companion object {
        private val hostPattern = Regex("[a-zA-Z0-9.-]+")
        private val pinPattern = Regex("[A-Fa-f0-9]{64}")
    }
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
