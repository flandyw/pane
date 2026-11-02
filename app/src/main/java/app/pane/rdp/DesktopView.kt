package app.pane.rdp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Build
import android.text.InputType
import android.view.GestureDetector
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.PointerIcon
import android.view.ScaleGestureDetector
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import kotlin.math.abs
import kotlin.math.min

class DesktopView(context: Context, private val session: RdpSession) : View(context) {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val pointerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; strokeWidth = 2f }
    private var zoom = 1f
    private var cursorX = session.profile.quality.width(session.aspect) / 2f
    private var cursorY = session.profile.quality.height / 2f
    private val bounds = RectF()
    private val cursorBounds = RectF()
    private var dragging = false
    private var mousePrimaryDown = false
    private val heldKeys = mutableSetOf<Int>()
    private val mouseButtons = mutableSetOf<Int>()
    private val hiddenPointer = PointerIcon.getSystemIcon(context, PointerIcon.TYPE_NULL)
    private var lastX = 0f
    private var lastY = 0f
    private var scrollX = 0f
    private var scrollY = 0f
    private var multiTouch = false
    private val scaler = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            zoom = (zoom * detector.scaleFactor).coerceIn(1f, 2.5f)
            postInvalidateOnAnimation()
            return true
        }
    })
    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent) = true
        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (!session.trackpad) position(e.x,e.y)
            performClick()
            return true
        }
        override fun onLongPress(e: MotionEvent) {
            if (!session.trackpad) position(e.x,e.y)
            click(0x2000)
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            if (multiTouch) return true
            if (session.trackpad) {
                val scale = desktopScale()
                cursorX -= distanceX / scale
                cursorY -= distanceY / scale
                clampCursor()
            } else position(e2.x,e2.y)
            sendMouse(0x0800)
            postInvalidateOnAnimation()
            return true
        }
        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (!session.trackpad) position(e.x,e.y)
            sendMouse(0x9000)
            dragging = true
            return true
        }
        override fun onDoubleTapEvent(e: MotionEvent): Boolean {
            if (e.actionMasked == MotionEvent.ACTION_UP && dragging) releaseDrag()
            return true
        }
    })
    init {
        isClickable = true
        isFocusable = true
        isFocusableInTouchMode = true
        contentDescription = "Remote desktop. Tap to click, hold to right click, double tap and hold to drag. Two fingers scroll; pinch to zoom. Ctrl+Alt+Home shows the toolbar."
        setBackgroundColor(Color.rgb(20,21,28))
    }
    // The remote cursor is drawn locally at the pointer, so hide Android's arrow to avoid two cursors.
    override fun onResolvePointerIcon(event: MotionEvent, pointerIndex: Int): PointerIcon =
        hiddenPointer
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) { super.onWindowFocusChanged(hasWindowFocus); if (hasWindowFocus) requestFocus() else releaseInput() }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); session.view = this; requestFocus() }
    override fun onDetachedFromWindow() { if (session.view === this) session.view = null; releaseInput(); super.onDetachedFromWindow() }
    private fun desktopScale(): Float {
        val image = session.bitmap ?: return 1f
        return min(width.toFloat()/image.width, height.toFloat()/image.height).coerceAtLeast(0.01f)*zoom
    }
    private fun updateBounds() {
        val image = session.bitmap ?: return
        val scale = desktopScale()
        val w = image.width*scale; val h = image.height*scale
        val x = if (w <= width) (width-w)/2 else (width/2f-cursorX*scale).coerceIn(width-w,0f)
        val y = if (h <= height) (height-h)/2 else (height/2f-cursorY*scale).coerceIn(height-h,0f)
        bounds.set(x,y,x+w,y+h)
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        synchronized(session.frameLock) {
            val image = session.bitmap ?: return
            clampCursor()
            updateBounds()
            val scale = desktopScale()
            canvas.drawBitmap(image,null,bounds,paint)
            if (session.cursorVisible) {
                val x = bounds.left+cursorX*scale
                val y = bounds.top+cursorY*scale
                val cursor = session.cursor
                if (cursor != null) {
                    cursorBounds.set(x-session.cursorHotX*scale,y-session.cursorHotY*scale,
                        x+(cursor.width-session.cursorHotX)*scale,y+(cursor.height-session.cursorHotY)*scale)
                    canvas.drawBitmap(cursor,null,cursorBounds,paint)
                } else {
                    pointerPaint.color = Color.BLACK; pointerPaint.strokeWidth = 5f
                    canvas.drawLine(x,y,x+13,y+19,pointerPaint)
                    pointerPaint.color = Color.WHITE; pointerPaint.strokeWidth = 2f
                    canvas.drawLine(x,y,x+13,y+19,pointerPaint)
                }
            }
        }
        session.paints.incrementAndGet()
    }
    private fun clampCursor() {
        val image = session.bitmap ?: return
        cursorX = cursorX.coerceIn(0f,(image.width-1).toFloat())
        cursorY = cursorY.coerceIn(0f,(image.height-1).toFloat())
    }
    // No frameLock: bounds are UI-thread state, so input never waits on a native frame copy.
    private fun position(x: Float,y: Float) {
        if (!x.isFinite() || !y.isFinite()) return
        updateBounds()
        val scale = desktopScale()
        cursorX = (x-bounds.left)/scale; cursorY = (y-bounds.top)/scale
        clampCursor()
    }
    private var sentX = -1
    private var sentY = -1
    private fun sendMouse(flags: Int) {
        val x = cursorX.toInt(); val y = cursorY.toInt()
        // Touch/hover report sub-pixel moves at up to 240 Hz; only send moves that change the desktop pixel.
        if (flags == 0x0800 && x == sentX && y == sentY) return
        sentX = x; sentY = y
        session.mouse(x,y,flags)
    }
    // xrdp turns every wheel event into one X button click, so only whole notches are sent.
    private fun notches(amount: Float, step: Float, wheel: Int): Float {
        return scrollNotches(amount,step) { direction -> sendMouse(wheel or ((direction*120) and 0x1ff)) }
    }
    private fun scrollStep() = 36*resources.displayMetrics.density
    private fun click(button: Int) { sendMouse(button or 0x8000); sendMouse(button); postInvalidateOnAnimation() }
    private fun releaseDrag() { if (dragging) { sendMouse(0x1000); dragging = false } }
    fun releaseInput() {
        releaseDrag()
        if (mousePrimaryDown) { sendMouse(0x1000); mousePrimaryDown = false }
        mouseButtons.forEach { sendMouse(it) }
        mouseButtons.clear()
        heldKeys.forEach { session.key(it,false) }
        heldKeys.clear()
        scrollX = 0f; scrollY = 0f
    }
    fun resetZoom() { zoom = 1f; postInvalidateOnAnimation() }
    fun keyboard() { requestFocus(); (context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(this,InputMethodManager.SHOW_IMPLICIT) }
    override fun performClick(): Boolean { super.performClick(); click(0x1000); return true }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!hasFocus()) requestFocus()
        parent.requestDisallowInterceptTouchEvent(event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL)
        if (event.actionMasked == MotionEvent.ACTION_DOWN) session.toolbar = false
        if (event.isFromSource(InputDevice.SOURCE_MOUSE)) {
            // Android 14+ reports touchpad gestures as fake fingers at the pointer; they must not move it.
            if (Build.VERSION.SDK_INT >= 34) when (event.classification) {
                MotionEvent.CLASSIFICATION_TWO_FINGER_SWIPE -> {
                    if (event.actionMasked == MotionEvent.ACTION_MOVE) {
                        scrollY = notches(scrollY+event.y-lastY,scrollStep(),0x0200)
                        scrollX = notches(scrollX-(event.x-lastX),scrollStep(),0x0400)
                    } else { scrollX = 0f; scrollY = 0f }
                    lastX = event.x; lastY = event.y
                    return true
                }
                MotionEvent.CLASSIFICATION_PINCH, 4 /* hidden CLASSIFICATION_MULTI_FINGER_SWIPE */ -> return true
            }
            position(event.x,event.y)
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN -> if (event.buttonState and MotionEvent.BUTTON_PRIMARY != 0) {
                    mousePrimaryDown = true
                    sendMouse(0x9000)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> if (mousePrimaryDown) {
                    mousePrimaryDown = false
                    sendMouse(0x1000)
                }
                MotionEvent.ACTION_MOVE -> sendMouse(0x0800)
            }
            postInvalidateOnAnimation()
            return true
        }
        scaler.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_DOWN) { multiTouch = false; scrollY = 0f; scrollX = 0f }
        if (event.pointerCount > 1) {
            if (!multiTouch) {
                multiTouch = true; lastY = event.y; lastX = event.x
                val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                gestures.onTouchEvent(cancel)
                cancel.recycle()
                releaseDrag()
            }
            if (event.actionMasked == MotionEvent.ACTION_MOVE && !scaler.isInProgress) {
                scrollY = notches(scrollY+event.y-lastY,scrollStep(),0x0200)
                scrollX = notches(scrollX-(event.x-lastX),scrollStep(),0x0400)
            }
            if (scaler.isInProgress) { scrollX = 0f; scrollY = 0f }
            lastY = event.y; lastX = event.x
            return true
        }
        if (!multiTouch) gestures.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) releaseDrag()
        lastY = event.y
        return true
    }
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (!event.isFromSource(InputDevice.SOURCE_CLASS_POINTER)) return super.onGenericMotionEvent(event)
        position(event.x,event.y)
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_MOVE -> { sendMouse(0x0800); postInvalidateOnAnimation(); return true }
            MotionEvent.ACTION_SCROLL -> {
                // Precision wheels and older touchpads report fractions of a notch.
                scrollY = notches(scrollY+event.getAxisValue(MotionEvent.AXIS_VSCROLL),1f,0x0200)
                scrollX = notches(scrollX+event.getAxisValue(MotionEvent.AXIS_HSCROLL),1f,0x0400)
                return true
            }
            MotionEvent.ACTION_BUTTON_PRESS, MotionEvent.ACTION_BUTTON_RELEASE -> {
                val button = when(event.actionButton) { MotionEvent.BUTTON_SECONDARY -> 0x2000; MotionEvent.BUTTON_TERTIARY -> 0x4000; else -> return true }
                val down = event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS
                if (down) mouseButtons.add(button) else mouseButtons.remove(button)
                sendMouse(button or if (down) 0x8000 else 0)
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }
    override fun onCheckIsTextEditor() = true
    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_ACTION_NONE
        return object : BaseInputConnection(this,false) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean { session.text(text?.toString().orEmpty()); return true }
            override fun setComposingText(text: CharSequence?, newCursorPosition: Int) = true
            override fun deleteSurroundingText(beforeLength: Int,afterLength: Int): Boolean {
                repeat(beforeLength.coerceIn(0,100)) { session.key(0x08,true); session.key(0x08,false) }
                repeat(afterLength.coerceIn(0,100)) { session.key(0x2e,true); session.key(0x2e,false) }
                return true
            }
            override fun sendKeyEvent(event: KeyEvent) = dispatchKeyEvent(event)
        }
    }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Ctrl+Alt+Home is the mstsc connection-bar shortcut; Home itself stays local.
        if (event.keyCode == KeyEvent.KEYCODE_MOVE_HOME && event.isCtrlPressed && event.isAltPressed) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) session.toolbar = !session.toolbar
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN) session.toolbar = false
        val vk = virtualKey(event.keyCode)
        if (vk != null && (event.action == KeyEvent.ACTION_DOWN || event.action == KeyEvent.ACTION_UP)) {
            val down = event.action == KeyEvent.ACTION_DOWN
            if (down) heldKeys.add(vk) else heldKeys.remove(vk)
            session.key(vk,down)
            return true
        }
        if (event.action == KeyEvent.ACTION_DOWN && event.unicodeChar != 0 && !event.isCtrlPressed && !event.isAltPressed) {
            session.text(String(Character.toChars(event.unicodeChar)))
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}

internal fun virtualKey(code: Int): Int? = when(code) {
    in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> 0x41+code-KeyEvent.KEYCODE_A
    in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> 0x30+code-KeyEvent.KEYCODE_0
    in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12 -> 0x70+code-KeyEvent.KEYCODE_F1
    KeyEvent.KEYCODE_ENTER,KeyEvent.KEYCODE_NUMPAD_ENTER,KeyEvent.KEYCODE_DPAD_CENTER -> 0x0d
    KeyEvent.KEYCODE_DEL -> 0x08
    KeyEvent.KEYCODE_FORWARD_DEL -> 0x2e
    KeyEvent.KEYCODE_INSERT -> 0x2d
    in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> 0x60+code-KeyEvent.KEYCODE_NUMPAD_0
    KeyEvent.KEYCODE_NUMPAD_MULTIPLY -> 0x6a
    KeyEvent.KEYCODE_NUMPAD_ADD -> 0x6b
    KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> 0x6d
    KeyEvent.KEYCODE_NUMPAD_EQUALS -> 0xbb
    KeyEvent.KEYCODE_NUMPAD_COMMA -> 0x6c
    KeyEvent.KEYCODE_NUMPAD_DOT -> 0x6e
    KeyEvent.KEYCODE_NUMPAD_DIVIDE -> 0x6f
    KeyEvent.KEYCODE_NUM_LOCK -> 0x90
    KeyEvent.KEYCODE_SCROLL_LOCK -> 0x91
    KeyEvent.KEYCODE_SYSRQ -> 0x2c
    KeyEvent.KEYCODE_BREAK -> 0x13
    KeyEvent.KEYCODE_MENU -> 0x5d
    KeyEvent.KEYCODE_TAB -> 0x09
    KeyEvent.KEYCODE_ESCAPE -> 0x1b
    KeyEvent.KEYCODE_SPACE -> 0x20
    KeyEvent.KEYCODE_DPAD_LEFT -> 0x25
    KeyEvent.KEYCODE_DPAD_UP -> 0x26
    KeyEvent.KEYCODE_DPAD_RIGHT -> 0x27
    KeyEvent.KEYCODE_DPAD_DOWN -> 0x28
    KeyEvent.KEYCODE_MOVE_HOME -> 0x24
    KeyEvent.KEYCODE_MOVE_END -> 0x23
    KeyEvent.KEYCODE_PAGE_UP -> 0x21
    KeyEvent.KEYCODE_PAGE_DOWN -> 0x22
    KeyEvent.KEYCODE_SHIFT_LEFT -> 0xa0
    KeyEvent.KEYCODE_SHIFT_RIGHT -> 0xa1
    KeyEvent.KEYCODE_CTRL_LEFT -> 0xa2
    KeyEvent.KEYCODE_CTRL_RIGHT -> 0xa3
    KeyEvent.KEYCODE_ALT_LEFT -> 0xa4
    KeyEvent.KEYCODE_ALT_RIGHT -> 0xa5
    KeyEvent.KEYCODE_META_LEFT -> 0x5b
    KeyEvent.KEYCODE_META_RIGHT -> 0x5c
    KeyEvent.KEYCODE_CAPS_LOCK -> 0x14
    KeyEvent.KEYCODE_MINUS -> 0xbd
    KeyEvent.KEYCODE_EQUALS -> 0xbb
    KeyEvent.KEYCODE_LEFT_BRACKET -> 0xdb
    KeyEvent.KEYCODE_RIGHT_BRACKET -> 0xdd
    KeyEvent.KEYCODE_BACKSLASH -> 0xdc
    KeyEvent.KEYCODE_SEMICOLON -> 0xba
    KeyEvent.KEYCODE_APOSTROPHE -> 0xde
    KeyEvent.KEYCODE_COMMA -> 0xbc
    KeyEvent.KEYCODE_PERIOD -> 0xbe
    KeyEvent.KEYCODE_SLASH -> 0xbf
    KeyEvent.KEYCODE_GRAVE -> 0xc0
    // Media keys control the desktop's player; volume keys stay with the tablet.
    KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> 0xb3
    KeyEvent.KEYCODE_MEDIA_NEXT -> 0xb0
    KeyEvent.KEYCODE_MEDIA_PREVIOUS -> 0xb1
    KeyEvent.KEYCODE_MEDIA_STOP -> 0xb2
    else -> null
}

// A pathological input packet must not flood the native event queue.
internal inline fun scrollNotches(amount: Float, step: Float, send: (Int)->Unit): Float {
    if (!amount.isFinite() || !step.isFinite() || step <= 0f) return 0f
    var left = amount.coerceIn(-step*32,step*32)
    while (abs(left) >= step) {
        val direction = if (left > 0) 1 else -1
        send(direction)
        left -= direction*step
    }
    return left
}
