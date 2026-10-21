package app.pane.rdp

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Point
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import com.freerdp.freerdpcore.services.LibFreeRDP as Rdp
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

enum class Phase { IDLE, CONNECTING, CONNECTED, DISCONNECTING }

class RdpSession(app: Application) : AndroidViewModel(app), Rdp.Listener {
    private val prefs = app.getSharedPreferences("pane", 0)
    var profile by mutableStateOf(Profile(
        host = prefs.getString("host", null) ?: Profile().host,
        username = prefs.getString("username", "ubuntu")!!,
        fingerprint = prefs.getString("pin", null) ?: Profile().fingerprint,
        quality = Quality.entries.firstOrNull { it.name == prefs.getString("quality", "BALANCED") } ?: Quality.BALANCED
    ))
    var phase by mutableStateOf(Phase.IDLE); private set
    var message by mutableStateOf(""); private set
    var remoteClipboard by mutableStateOf(""); private set
    var trackpad by mutableStateOf(prefs.getBoolean("trackpad", true))
    var toolbar by mutableStateOf(true)
    /** Long/short ratio of the physical screen, so the remote desktop fills it in either orientation. */
    val aspect: Float = Point().also {
        @Suppress("DEPRECATION") app.getSystemService(WindowManager::class.java).defaultDisplay.getRealSize(it)
    }.let { maxOf(it.x,it.y).toFloat()/minOf(it.x,it.y).coerceAtLeast(1) }
    val size get() = "${profile.quality.width(aspect)} × ${profile.quality.height}"
    val frameLock = Any()
    @Volatile var bitmap: Bitmap? = null; private set
    var cursor: Bitmap? = null; private set
    var cursorHotX = 0; private set
    var cursorHotY = 0; private set
    var cursorVisible = true; private set
    @Volatile private var desktop = WeakReference<DesktopView>(null)
    var view: DesktopView?
        get() = desktop.get()
        set(value) { desktop = WeakReference(value) }
    val paints = AtomicInteger()
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val nativeLock = Any()
    @Volatile private var instance = 0L
    private var ownsListener = false
    private var cleared = false
    @Volatile private var requestedStop = false

    fun connect(password: String) {
        if (phase != Phase.IDLE) return
        val error = profile.error()
        if (error != null || password.isEmpty()) { message = error ?: "Enter your Linux password."; return }
        prefs.edit().putString("host", profile.host).putString("username", profile.username)
            .putString("pin", profile.normalizedPin()).putString("quality", profile.quality.name).apply()
        phase = Phase.CONNECTING
        message = ""
        toolbar = true
        requestedStop = false
        val args = profile.arguments(password, aspect)
        worker.execute {
            try {
                synchronized(nativeLock) {
                    Rdp.listener = this
                    ownsListener = true
                    instance = Rdp.freerdp_new(getApplication())
                    check(instance != 0L) { "The native RDP engine could not start." }
                    check(Rdp.freerdp_parse_arguments(instance, args)) { "The RDP configuration was rejected." }
                    check(Rdp.freerdp_connect(instance)) { "The RDP connection could not start." }
                    if (requestedStop) Rdp.freerdp_disconnect(instance)
                }
            } catch (e: Exception) {
                finish(e.message ?: "Connection failed.")
            } catch (e: LinkageError) {
                finish("The native engine is unavailable on this device. Pane requires ARM64.")
            } finally {
                args.fill("")
            }
        }
    }
    fun disconnect() {
        if (phase == Phase.IDLE || phase == Phase.DISCONNECTING) return
        phase = Phase.DISCONNECTING
        requestedStop = true
        // Abort outside the connection executor: native connection setup may still be running.
        synchronized(nativeLock) { if (instance != 0L) Rdp.freerdp_disconnect(instance) }
    }
    override fun connected() { main.post { if (!requestedStop) phase = Phase.CONNECTED } }
    override fun ended(failed: Boolean) {
        val detail = synchronized(nativeLock) {
            if (instance == 0L) "" else Rdp.freerdp_get_last_error_string(instance)
        }
        finish(if (failed && !requestedStop) "Connection ended: $detail. Check Tailscale, your password and the certificate pin." else "")
    }
    private fun finish(detail: String) {
        worker.execute {
            synchronized(nativeLock) {
                if (instance != 0L) Rdp.freerdp_free(instance)
                instance = 0
                if (ownsListener) Rdp.listener = null
                ownsListener = false
            }
            synchronized(frameLock) { bitmap = null; cursor = null }
            main.post { message = detail; phase = Phase.IDLE; if (cleared) worker.shutdown() }
        }
    }
    override fun resized(width: Int, height: Int) {
        if (width !in 1..4096 || height !in 1..2560) { main.post { disconnect() }; return }
        synchronized(frameLock) {
            if (bitmap?.width != width || bitmap?.height != height)
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply { setHasAlpha(false) }
        }
        view?.postInvalidateOnAnimation()
    }
    override fun damaged(x: Int, y: Int, width: Int, height: Int) {
        // Copy while the native paint callback owns GDI, before its next update can change pixels.
        synchronized(frameLock) {
            val image = bitmap ?: return
            val left = x.coerceAtLeast(0); val top = y.coerceAtLeast(0)
            val right = (x.toLong()+width).coerceAtMost(image.width.toLong()).toInt()
            val bottom = (y.toLong()+height).coerceAtMost(image.height.toLong()).toInt()
            if (right > left && bottom > top)
                Rdp.freerdp_update_graphics(instance, image, left, top, right-left, bottom-top)
        }
        view?.postInvalidateOnAnimation()
    }
    override fun pointer(pixels: IntArray?, width: Int, height: Int, hotX: Int, hotY: Int) {
        synchronized(frameLock) {
            cursorVisible = width != 0
            cursor = if (pixels != null && width in 1..384 && height in 1..384 && pixels.size >= width*height)
                Bitmap.createBitmap(pixels,width,height,Bitmap.Config.ARGB_8888) else null
            cursorHotX = hotX; cursorHotY = hotY
        }
        view?.postInvalidateOnAnimation()
    }
    override fun clipboard(text: String) { main.post { remoteClipboard = text.take(1_048_576) } }
    fun sendClipboard(text: String) = input { Rdp.freerdp_send_clipboard_data(it, text.take(1_048_576)) }
    fun mouse(x: Int, y: Int, flags: Int) = input { Rdp.freerdp_send_cursor_event(it,x,y,flags) }
    fun key(vk: Int, down: Boolean) = input { Rdp.freerdp_send_key_event(it,vk,down) }
    fun text(text: String) = input { i -> text.take(16384).forEach { Rdp.freerdp_send_unicodekey_event(i,it.code,true); Rdp.freerdp_send_unicodekey_event(i,it.code,false) } }
    private inline fun input(action: (Long) -> Unit) {
        synchronized(nativeLock) { if (instance != 0L && phase == Phase.CONNECTED) action(instance) }
    }
    fun toggleTrackpad() { trackpad = !trackpad; prefs.edit().putBoolean("trackpad", trackpad).apply() }
    override fun onCleared() { cleared = true; if (phase == Phase.IDLE) worker.shutdown() else disconnect() }
}
