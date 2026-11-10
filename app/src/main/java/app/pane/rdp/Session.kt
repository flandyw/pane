package app.pane.rdp

import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.view.Surface
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import org.json.JSONObject
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * One horizontal band of the desktop with its own H.264 stream (port of web/src/worker.ts),
 * decoded by its own MediaCodec straight into the band's surface. decode() runs on the
 * socket thread, codec callbacks on [decodeThread], setSurface() on the main thread.
 */
class Stripe(val y: Int, val height: Int, val width: Int) {
    @Volatile var waitingKey = true
    private var surface: Surface? = null
    private var codec: MediaCodec? = null
    private var failed = false
    private val pending = ArrayDeque<ByteArray>()
    private val free = ArrayDeque<Int>()
    private var pts = 0L

    @Synchronized fun setSurface(s: Surface?) {
        reset()
        surface?.release()
        surface = s
    }

    /** Queues one Annex-B access unit. Returns false when the decoder needs a keyframe. */
    @Synchronized fun decode(data: ByteArray, key: Boolean): Boolean {
        if (failed) reset()
        val out = surface ?: return false
        if (codec == null) {
            if (!key) return false
            codec = runCatching { start(out) }.getOrNull() ?: return false
        }
        // A decoder that stopped taking input would buffer forever; start over from a keyframe.
        if (pending.size > 30) { reset(); return false }
        pending.add(data)
        feed()
        return !failed
    }

    private fun start(out: Surface): MediaCodec {
        val c = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        val f = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height)
        f.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, maxOf(width * height, 1 shl 20))
        if (android.os.Build.VERSION.SDK_INT >= 30) f.setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
        f.setInteger(MediaFormat.KEY_PRIORITY, 0) // realtime
        c.setCallback(object : MediaCodec.Callback() {
            override fun onInputBufferAvailable(m: MediaCodec, i: Int) = synchronized(this@Stripe) {
                if (m === codec) { free.add(i); feed() }
            }
            override fun onOutputBufferAvailable(m: MediaCodec, i: Int, info: MediaCodec.BufferInfo) {
                synchronized(this@Stripe) { if (m === codec) runCatching { m.releaseOutputBuffer(i, true) } }
            }
            override fun onError(m: MediaCodec, e: MediaCodec.CodecException) = synchronized(this@Stripe) {
                if (m === codec) failed = true
            }
            override fun onOutputFormatChanged(m: MediaCodec, f: MediaFormat) {}
        }, decodeHandler)
        try {
            c.configure(f, out, null, 0)
            c.start()
        } catch (e: Exception) {
            c.release()
            throw e
        }
        return c
    }

    private fun feed() {
        val c = codec ?: return
        try {
            while (pending.isNotEmpty() && free.isNotEmpty()) {
                val i = free.poll()!!
                val d = pending.poll()!!
                val buf = c.getInputBuffer(i)!!
                if (d.size > buf.capacity()) { failed = true; return }
                buf.clear(); buf.put(d)
                c.queueInputBuffer(i, 0, d.size, pts, 0)
                pts += 16_666
            }
        } catch (e: Exception) {
            failed = true
        }
    }

    @Synchronized fun reset() {
        waitingKey = true
        codec?.let { runCatching { it.stop() }; runCatching { it.release() } }
        codec = null
        failed = false
        pending.clear()
        free.clear()
    }

    companion object {
        private val decodeThread = HandlerThread("voxl-decode").apply { start() }
        val decodeHandler = Handler(decodeThread.looper)
    }
}

/** WebSocket connection to the voxl host. Listener calls arrive on the main thread. */
class Session(val host: Host, private val password: String, private val listener: Listener) {
    interface Listener {
        /** `displays`/`display`: how many displays the host can stream and which one this is. */
        fun onHello(width: Int, height: Int, stripes: List<Stripe>, resizeScale: Double?, displays: Int, display: Int)
        fun onCursor(image: Bitmap?, hotX: Int, hotY: Int)
        fun onState(text: String?)
        fun onClipboard(text: String)
        /** The host refused the password; ask for a replacement. */
        fun onAuthFailed()
    }

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var ws: WebSocket? = null
    @Volatile var connected = false; private set
    @Volatile private var stripes: List<Stripe> = emptyList()
    private val acks = FrameAcks()
    private var lastKeyRequest = 0L
    private var keyRetryPosted = false
    private var stopped = true
    fun sendClipboard(text: String) { send("c,${text.take(1_048_576)}") }

    fun start() {
        stopped = false
        if (ws != null) return
        val req = runCatching {
            require(Hosts.normalize(host.url) == host.url)
            Request.Builder().url(host.url).header("Authorization", "Bearer $password").build()
        }.getOrElse { listener.onState("Invalid server URL or password header."); return }
        listener.onState("Connecting…")
        ws = http.newWebSocket(req, Socket())
    }

    fun stop() {
        stopped = true
        ws?.cancel()
        dropped(retry = false)
    }

    /** Ends the session for good. */
    fun close() {
        stop()
        stripes.forEach { it.setSurface(null) }
        stripes = emptyList()
    }

    /** Returns whether the message was queued (false while disconnected). */
    fun send(s: String): Boolean = connected && ws?.send(s) == true

    private fun dropped(retry: Boolean) {
        ws = null
        connected = false
        acks.clear()
        stripes.forEach { it.reset() }
        if (!retry || stopped) return
        listener.onState("Reconnecting…")
        main.postDelayed({ if (!stopped) start() }, 1000)
    }

    private inner class Socket : WebSocketListener() {
        private fun mine(w: WebSocket) = w === ws

        override fun onOpen(w: WebSocket, r: Response) {
            if (!mine(w)) return
            connected = true
            w.send("v,1")
            w.send("au,0")
        }

        override fun onMessage(w: WebSocket, text: String) {
            if (!mine(w)) return
            try { text(w, text) } catch (_: Exception) {
                post(w) { stop(); listener.onState("Invalid Voxl server message. Tap to reconnect.") }
            }
        }
        override fun onMessage(w: WebSocket, bytes: ByteString) { if (mine(w)) binary(w, bytes.toByteArray()) }
        override fun onClosing(w: WebSocket, code: Int, reason: String) { w.close(1000, null) }

        override fun onClosed(w: WebSocket, code: Int, reason: String) {
            main.post { if (mine(w)) dropped(retry = true) }
        }

        override fun onFailure(w: WebSocket, t: Throwable, r: Response?) {
            val code = r?.code
            main.post {
                if (!mine(w)) return@post
                if (code == 401 || code == 403) {
                    stop()
                    listener.onState("Wrong password")
                    listener.onAuthFailed()
                } else {
                    dropped(retry = true)
                }
            }
        }
    }

    /** Runs on the main thread, unless the connection `w` was stopped or replaced meanwhile. */
    private fun post(w: WebSocket, f: () -> Unit) = main.post { if (w === ws) f() }

    private fun text(sock: WebSocket, s: String) {
        val j = runCatching { JSONObject(s) }.getOrNull() ?: return
        when (j.optString("t")) {
            "hello" -> {
                val hello = VideoHello.parse(j)
                val w = hello.width; val h = hello.height
                val next = hello.bands.map { (y, height) -> Stripe(y, height, w) }
                val old = stripes
                stripes = next
                acks.clear()
                val scale = j.optJSONObject("resize")?.optDouble("scale")?.takeIf { it.isFinite() && it > 0 }
                val displays = j.optInt("displays", 1).coerceIn(1, 32)
                val display = j.optInt("display", 0).coerceIn(0, displays - 1)
                main.post {
                    old.forEach { it.setSurface(null) }
                    if (sock !== ws) return@post
                    listener.onState(null)
                    listener.onHello(w, h, next, scale, displays, display)
                }
            }
            "ping" -> j.opt("s")?.let { send("po,$it") }
            "clip" -> if (j.has("d")) {
                val d = j.optString("d")
                post(sock) {
                    listener.onClipboard(d.take(1_048_576))
                }
            }
            "close" -> post(sock) {
                stop()
                listener.onState("Closed by host. Tap to reconnect.")
            }
        }
    }

    private fun binary(sock: WebSocket, d: ByteArray) {
        parseFrameHeader(d)?.let { h ->
            stripes.getOrNull(h.stripe)?.let { st ->
                if (h.key) st.waitingKey = false
                if (!st.waitingKey && !st.decode(d.copyOfRange(16, d.size), h.key)) st.waitingKey = true
                if (st.waitingKey) requestKeyframe()
            }
            if (acks.part(h.frame, h.parts)) send("a,${h.frame.toUInt()}")
            return
        }
        parseCursor(d)?.let { c ->
            val bmp = c.argb?.let { Bitmap.createBitmap(it, c.width, c.height, Bitmap.Config.ARGB_8888) }
            post(sock) { listener.onCursor(bmp, c.hotX, c.hotY) }
        }
    }

    /**
     * Asks the host for keyframes, at most every 500 ms. A request inside that window is
     * sent when it ends, so a stripe that only became ready meanwhile is not left blank
     * on a desktop that never changes. Any thread.
     */
    @Synchronized fun requestKeyframe() {
        val wait = lastKeyRequest + 500 - SystemClock.elapsedRealtime()
        if (wait > 0) {
            if (!keyRetryPosted) {
                keyRetryPosted = true
                main.postDelayed({
                    synchronized(this) { keyRetryPosted = false }
                    if (stripes.any { it.waitingKey }) requestKeyframe()
                }, wait)
            }
            return
        }
        lastKeyRequest = SystemClock.elapsedRealtime()
        send("kf")
    }

    companion object {
        private val http = OkHttpClient.Builder()
            .readTimeout(0, TimeUnit.MILLISECONDS) // the host pings; a quiet desktop is not a dead one
            .build()
    }
}

/** Header of a 0x01 video message; null when it is not one or is truncated. */
internal data class FrameHeader(val key: Boolean, val frame: Int, val stripe: Int, val parts: Int)

internal fun parseFrameHeader(d: ByteArray): FrameHeader? {
    if (d.size < 16 || d[0].toInt() != 0x01) return null
    val u8 = { i: Int -> d[i].toInt() and 0xff }
    return FrameHeader(u8(1) and 1 == 1, u8(2) shl 24 or (u8(3) shl 16) or (u8(4) shl 8) or u8(5), u8(10), maxOf(u8(12), 1))
}

/** A frame is acked once every stripe part arrived, decoded or not (same as the web worker). Any thread. */
internal class FrameAcks {
    private val pending = ConcurrentHashMap<Int, Int>() // frame id -> parts still to arrive
    /** Records one part; true when [frame] is complete and should be acked. */
    fun part(frame: Int, parts: Int): Boolean {
        if (pending.size > 256) pending.clear()
        val left = (pending[frame] ?: parts) - 1
        if (left <= 0) { pending.remove(frame); return true }
        pending[frame] = left
        return false
    }
    fun clear() = pending.clear()
}

internal class CursorImage(val width: Int, val height: Int, val hotX: Int, val hotY: Int, val argb: IntArray?)

/** Parses a 0x03 cursor message; argb is null (hidden cursor) when the image is absent or out of bounds. */
internal fun parseCursor(d: ByteArray): CursorImage? {
    if (d.size < 14 || d[0].toInt() != 0x03) return null
    val u8 = { i: Int -> d[i].toInt() and 0xff }
    val u16 = { o: Int -> u8(o) shl 8 or u8(o + 1) }
    val w = u16(2); val h = u16(4)
    // RGBA, not premultiplied; createBitmap premultiplies ARGB ints.
    val px = if (w in 1..512 && h in 1..512 && d.size >= 14 + w * h * 4) IntArray(w * h) { i ->
        val o = 14 + i * 4
        u8(o + 3) shl 24 or (u8(o) shl 16) or (u8(o + 1) shl 8) or u8(o + 2)
    } else null
    return CursorImage(w, h, u16(6), u16(8), px)
}
