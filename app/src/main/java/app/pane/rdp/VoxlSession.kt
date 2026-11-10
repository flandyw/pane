package app.pane.rdp

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Point
import android.view.KeyCharacterMap
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicInteger

enum class Phase { IDLE, CONNECTING, CONNECTED, DISCONNECTING }

/** Pane's existing screen/input contract, backed exclusively by Voxl. */
class VoxlSession(app: Application) : AndroidViewModel(app), Session.Listener {
    internal val focus = DesktopFocus(app)
    private val prefs = app.getSharedPreferences("pane_voxl", 0)
    var profile by mutableStateOf(Profile(prefs.getString("host", "") ?: "",
        Quality.entries.firstOrNull { it.name == prefs.getString("quality", "BALANCED") } ?: Quality.BALANCED))
    var phase by mutableStateOf(Phase.IDLE); private set
    var message by mutableStateOf(""); private set
    var remoteClipboard by mutableStateOf(""); private set
    var rememberPassword by mutableStateOf(prefs.getBoolean("remember_password", false)); private set
    var trackpad by mutableStateOf(prefs.getBoolean("trackpad", true))
    var toolbar by mutableStateOf(true)
    var inputSuspended = false
    internal val keyboard = RemoteKeyboard(::key, ::text, { toolbar = !toolbar }, KeyCharacterMap::getDeadChar)
    val aspect: Float = Point().also {
        @Suppress("DEPRECATION") app.getSystemService(WindowManager::class.java).defaultDisplay.getRealSize(it)
    }.let { maxOf(it.x, it.y).toFloat() / minOf(it.x, it.y).coerceAtLeast(1) }
    var width by mutableStateOf(0); private set
    var height by mutableStateOf(0); private set
    val size get() = if (width > 0) "$width × $height" else "${profile.quality.width(aspect)} × ${profile.quality.height}"
    var stripes: List<Stripe> = emptyList(); private set
    val frameLock = Any()
    var cursor: Bitmap? = null; private set
    var cursorHotX = 0; private set
    var cursorHotY = 0; private set
    var cursorVisible = true; private set
    private var desktop = WeakReference<DesktopView>(null)
    var view: DesktopView?
        get() = desktop.get()
        set(value) { desktop = WeakReference(value) }
    val paints = AtomicInteger()
    private var connection: Session? = null
    private var resizeRequested = false

    fun savedPassword(): String {
        if (!rememberPassword || profile.host != prefs.getString("host", "")) return ""
        return try { prefs.getString("password", null)?.let { PasswordCipher.decrypt(it, profile) }.orEmpty() }
        catch (_: Exception) { message = "Saved password unavailable. Enter it again."; "" }
    }
    fun updateRememberPassword(enabled: Boolean) {
        val edit = prefs.edit().putBoolean("remember_password", enabled)
        if (!enabled) edit.remove("password")
        if (edit.commit()) rememberPassword = enabled else message = "Could not save the password setting. Try again."
    }
    fun connect(password: String) {
        if (phase != Phase.IDLE) return
        profile.error()?.let { message = it; return }
        if (password.isEmpty()) { message = "Enter your Voxl server password."; return }
        try {
            val edit = prefs.edit().putString("host", profile.host).putString("quality", profile.quality.name)
            if (rememberPassword) edit.putString("password", PasswordCipher.encrypt(password, profile)) else edit.remove("password")
            check(edit.commit())
        } catch (_: Exception) { message = "Could not save settings securely. Try again."; return }
        phase = Phase.CONNECTING
        Updater.sessionActive = true
        message = ""; remoteClipboard = ""; toolbar = true
        width = 0; height = 0; resizeRequested = false
        connection = Session(Host(profile.host, profile.endpoint()), password, this)
        connection?.start()
    }
    fun disconnect() {
        if (phase == Phase.IDLE) return
        view?.releaseInput(); keyboard.releaseAll()
        connection?.close(); connection = null
        stripes = emptyList(); width = 0; height = 0; view?.showStripes()
        cursor = null; remoteClipboard = ""
        phase = Phase.IDLE
        focus.connected(false)
        Updater.sessionActive = false
    }
    override fun onHello(width: Int, height: Int, stripes: List<Stripe>, resizeScale: Double?, displays: Int, display: Int) {
        this.width = width; this.height = height; this.stripes = stripes
        phase = Phase.CONNECTED
        focus.connected(true)
        view?.showStripes()
        if (resizeScale != null && !resizeRequested) {
            resizeRequested = true
            connection?.send("sz,${profile.quality.width(aspect)},${profile.quality.height}")
        }
    }
    override fun onCursor(image: Bitmap?, hotX: Int, hotY: Int) {
        synchronized(frameLock) {
            cursor = image; cursorVisible = image != null
            cursorHotX = hotX; cursorHotY = hotY
        }
        view?.postInvalidateOnAnimation()
    }
    override fun onState(text: String?) {
        if (text == null) return
        message = text
        if (text == "Connecting…" || text == "Reconnecting…") { phase = Phase.CONNECTING; focus.connected(false) }
        else disconnect()
    }
    override fun onAuthFailed() { disconnect(); message = "Voxl rejected the password. Enter it again." }
    override fun onClipboard(text: String) { remoteClipboard = text }
    fun sendClipboard(text: String) { connection?.sendClipboard(text) }
    fun requestKeyframe() { connection?.requestKeyframe() }
    fun mouse(x: Int, y: Int, flags: Int) {
        val socket = connection ?: return
        socket.send("m,$x,$y")
        voxPointer(flags)?.let(socket::send)
    }

    private fun key(vk: Int, down: Boolean) { voxKey(vk)?.let { connection?.send("k,$it,${if (down) 1 else 0}") } }
    private fun text(text: String) {
        voxText(text).forEach { sym -> connection?.send("k,$sym,1"); connection?.send("k,$sym,0") }
    }
    fun toggleTrackpad() { trackpad = !trackpad; prefs.edit().putBoolean("trackpad", trackpad).apply() }
    override fun onCleared() { disconnect() }
}

/** Translate Pane's existing key contract at the transport boundary. */
internal fun voxKey(vk: Int): Int? = when (vk) {
    in 0x41..0x5a -> vk + 32
    in 0x30..0x39 -> vk
    in 0x70..0x7b -> 0xffbe + vk - 0x70
    in 0x60..0x69 -> 0xffb0 + vk - 0x60
    else -> VoxKeys[vk]
}

private val VoxKeys = mapOf(
        0x08 to 0xff08, 0x09 to 0xff09, 0x0d to 0xff0d, 0x1b to 0xff1b, 0x20 to 0x20,
        0x21 to 0xff55, 0x22 to 0xff56, 0x23 to 0xff57, 0x24 to 0xff50,
        0x25 to 0xff51, 0x26 to 0xff52, 0x27 to 0xff53, 0x28 to 0xff54,
        0x2d to 0xff63, 0x2e to 0xffff, 0x14 to 0xffe5, 0x90 to 0xff7f, 0x91 to 0xff14,
        0x13 to 0xff13, 0x2c to 0xff61, 0x5b to 0xffeb, 0x5c to 0xffec, 0x5d to 0xff67,
        0xa0 to 0xffe1, 0xa1 to 0xffe2, 0xa2 to 0xffe3, 0xa3 to 0xffe4, 0xa4 to 0xffe9, 0xa5 to 0xffea,
        0x6a to 0xffaa, 0x6b to 0xffab, 0x6c to 0xffac, 0x6d to 0xffad, 0x6e to 0xffae, 0x6f to 0xffaf,
        0xba to 0x3b, 0xbb to 0x3d, 0xbc to 0x2c, 0xbd to 0x2d, 0xbe to 0x2e, 0xbf to 0x2f,
        0xc0 to 0x60, 0xdb to 0x5b, 0xdc to 0x5c, 0xdd to 0x5d, 0xde to 0x27,
        0xad to 0x1008ff12, 0xae to 0x1008ff11, 0xaf to 0x1008ff13,
        0xb0 to 0x1008ff17, 0xb1 to 0x1008ff16, 0xb2 to 0x1008ff15, 0xb3 to 0x1008ff14
    )

internal fun voxPointer(flags: Int): String? {
    if (flags and 0x0200 != 0 || flags and 0x0400 != 0) {
        val raw = flags and 0x1ff
        val delta = (if (raw and 0x100 != 0) raw - 0x200 else raw) / 120
        return if (flags and 0x0400 != 0) "s,$delta,0" else "s,0,${-delta}"
    }
    val button = when { flags and 0x1000 != 0 -> 1; flags and 0x2000 != 0 -> 3; flags and 0x4000 != 0 -> 2; else -> return null }
    return "b,$button,${if (flags and 0x8000 != 0) 1 else 0}"
}

internal fun voxText(text: String): IntArray = text.take(16384).replace("\r\n", "\n").codePoints()
    .filter { isPrintableCodePoint(it) || it in listOf(8, 9, 10, 13) }
    .map { when (it) { 10, 13 -> 0xff0d; 9 -> 0xff09; 8 -> 0xff08; else -> if (it < 256) it else 0x01000000 + it } }.toArray()
