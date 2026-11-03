package app.pane.rdp

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Rect
import android.hardware.input.InputManager
import android.os.SystemClock
import android.text.Selection
import android.view.KeyCharacterMap
import androidx.annotation.RequiresApi
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
    private val pointer = RemotePointer(::sendMouse)
    private var connection: DesktopInputConnection? = null
    private val inputManager = context.getSystemService(InputManager::class.java)
    private val inputDevices = object : InputManager.InputDeviceListener {
        override fun onInputDeviceAdded(deviceId: Int) = Unit
        override fun onInputDeviceRemoved(deviceId: Int) { releaseInput(); restartInput() }
        override fun onInputDeviceChanged(deviceId: Int) { releaseInput(); restartInput() }
    }
    private val hiddenPointer = PointerIcon.getSystemIcon(context, PointerIcon.TYPE_NULL)
    private var lastX = 0f
    private var lastY = 0f
    private var gestureAxes = false
    private var multiTouch = false
    private val scaler = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleBy(detector.scaleFactor)
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
            if (!hasFocus() || !hasWindowFocus() || session.inputSuspended || session.phase != Phase.CONNECTED) return
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
    override fun onResolvePointerIcon(event: MotionEvent, pointerIndex: Int): PointerIcon? =
        if (event.isFromSource(InputDevice.SOURCE_MOUSE)) hiddenPointer else super.onResolvePointerIcon(event,pointerIndex)
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (!hasWindowFocus) releaseInput() else restartInput()
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow(); session.view = this
        inputManager.registerInputDeviceListener(inputDevices,null)
        requestFocus()
    }
    override fun onDetachedFromWindow() {
        inputManager.unregisterInputDeviceListener(inputDevices)
        if (session.view === this) session.view = null
        releaseInput(); super.onDetachedFromWindow()
    }
    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus,direction,previouslyFocusedRect)
        if (!gainFocus) { session.keyboard.releasePhysical(); pointer.release(); releaseDrag(); connection?.closeConnection(); cancelGestures() }
    }
    fun restartInput() {
        if (hasFocus() && hasWindowFocus() && !session.inputSuspended) context.getSystemService(InputMethodManager::class.java).restartInput(this)
    }
    private fun scaleBy(factor: Float) {
        if (!factor.isFinite() || factor <= 0f) return
        zoom = (zoom*factor).coerceIn(1f,2.5f)
        postInvalidateOnAnimation()
    }
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
    private fun scrollStep() = 36*resources.displayMetrics.density
    private fun click(button: Int) { sendMouse(button or 0x8000); sendMouse(button); postInvalidateOnAnimation() }
    private fun releaseDrag() { if (dragging) { sendMouse(0x1000); dragging = false } }
    fun releaseInput() {
        releaseDrag(); pointer.release(); session.keyboard.releaseAll()
        connection?.closeConnection()
        cancelGestures()
        sentX=-1; sentY=-1
    }
    private fun cancelGestures() {
        val now = SystemClock.uptimeMillis()
        val cancel = MotionEvent.obtain(now,now,MotionEvent.ACTION_CANCEL,0f,0f,0)
        gestures.onTouchEvent(cancel); scaler.onTouchEvent(cancel); cancel.recycle()
        multiTouch=false
    }
    fun hideKeyboard() { context.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(windowToken,0) }
    fun resetZoom() { zoom = 1f; postInvalidateOnAnimation() }
    fun keyboard() {
        if (session.phase != Phase.CONNECTED || session.inputSuspended) return
        requestFocus()
        // SHOW_IMPLICIT may be suppressed with a hardware keyboard; this is an explicit user request.
        post { if (hasWindowFocus() && hasFocus() && session.phase == Phase.CONNECTED && !session.inputSuspended) context.getSystemService(InputMethodManager::class.java).showSoftInput(this,0) }
    }
    override fun performClick(): Boolean {
        if (!hasWindowFocus() || session.inputSuspended || session.phase != Phase.CONNECTED) return false
        super.performClick(); click(0x1000); return true
    }

    @RequiresApi(34)
    private fun touchpadGesture(event: MotionEvent): Boolean {
        when (event.classification) {
            MotionEvent.CLASSIFICATION_TWO_FINGER_SWIPE -> {
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    releaseDrag(); pointer.resetScroll()
                    gestureAxes = event.device?.getMotionRange(MotionEvent.AXIS_GESTURE_SCROLL_X_DISTANCE) != null
                    lastX=event.x; lastY=event.y
                }
                if (event.actionMasked == MotionEvent.ACTION_MOVE) {
                    val dx=event.getAxisValue(MotionEvent.AXIS_GESTURE_SCROLL_X_DISTANCE)
                    val dy=event.getAxisValue(MotionEvent.AXIS_GESTURE_SCROLL_Y_DISTANCE)
                    gestureAxes = gestureAxes || dx != 0f || dy != 0f
                    if (gestureAxes) {
                        for (h in 0 until event.historySize) pointer.scroll(
                            event.getHistoricalAxisValue(MotionEvent.AXIS_GESTURE_SCROLL_X_DISTANCE,h),
                            -event.getHistoricalAxisValue(MotionEvent.AXIS_GESTURE_SCROLL_Y_DISTANCE,h),scrollStep(),2,event.deviceId)
                        pointer.scroll(dx,-dy,scrollStep(),2,event.deviceId)
                    } else pointer.scroll(-(event.x-lastX),event.y-lastY,scrollStep(),2,event.deviceId)
                }
                lastX=event.x; lastY=event.y
                if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) pointer.resetScroll()
                return true
            }
            MotionEvent.CLASSIFICATION_PINCH -> {
                pointer.resetScroll()
                if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                    releaseDrag()
                    gestureAxes=event.device?.getMotionRange(MotionEvent.AXIS_GESTURE_PINCH_SCALE_FACTOR) != null
                }
                val factor=event.getAxisValue(MotionEvent.AXIS_GESTURE_PINCH_SCALE_FACTOR)
                gestureAxes=gestureAxes || factor > 0f
                if (gestureAxes) {
                    if (event.actionMasked == MotionEvent.ACTION_MOVE) {
                        for (h in 0 until event.historySize) scaleBy(event.getHistoricalAxisValue(MotionEvent.AXIS_GESTURE_PINCH_SCALE_FACTOR,h))
                        scaleBy(factor)
                    }
                } else scaler.onTouchEvent(event)
                return true
            }
            // AOSP reserves this hidden classification for system three/four-finger navigation.
            4 -> return true
        }
        return false
    }
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (session.inputSuspended || session.phase != Phase.CONNECTED) return true
        if (!hasFocus()) requestFocus()
        parent.requestDisallowInterceptTouchEvent(event.actionMasked != MotionEvent.ACTION_UP && event.actionMasked != MotionEvent.ACTION_CANCEL)
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            connection?.finishComposingText()
            session.toolbar = false
        }
        if (event.actionMasked == MotionEvent.ACTION_CANCEL ||
            (Build.VERSION.SDK_INT >= 33 && event.flags and MotionEvent.FLAG_CANCELED != 0)) {
            releaseInput()
            return true
        }
        if (event.isFromSource(InputDevice.SOURCE_MOUSE)) {
            if (Build.VERSION.SDK_INT >= 34 && touchpadGesture(event)) return true
            position(event.x,event.y)
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> pointer.buttons(event.buttonState)
                MotionEvent.ACTION_MOVE -> { pointer.buttons(event.buttonState); sendMouse(0x0800) }
                MotionEvent.ACTION_UP -> pointer.buttons(event.buttonState)
            }
            postInvalidateOnAnimation()
            return true
        }
        scaler.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_DOWN) { multiTouch=false; pointer.resetScroll() }
        if (event.pointerCount > 1) {
            if (!multiTouch) {
                multiTouch=true
                val cancel=MotionEvent.obtain(event).apply { action=MotionEvent.ACTION_CANCEL }
                gestures.onTouchEvent(cancel); cancel.recycle(); releaseDrag()
                lastX=(event.getX(0)+event.getX(1))/2
                lastY=(event.getY(0)+event.getY(1))/2
            }
            val x=(event.getX(0)+event.getX(1))/2
            val y=(event.getY(0)+event.getY(1))/2
            if (event.pointerCount == 2 && event.actionMasked == MotionEvent.ACTION_MOVE && !scaler.isInProgress)
                pointer.scroll(-(x-lastX),y-lastY,scrollStep(),3,event.deviceId)
            else pointer.resetScroll()
            lastX=x; lastY=y
            return true
        }
        if (!multiTouch) gestures.onTouchEvent(event)
        if (event.actionMasked == MotionEvent.ACTION_UP) releaseDrag()
        return true
    }
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (session.inputSuspended || session.phase != Phase.CONNECTED || !hasWindowFocus() ||
            !event.isFromSource(InputDevice.SOURCE_CLASS_POINTER)) return super.onGenericMotionEvent(event)
        if (Build.VERSION.SDK_INT >= 34 && event.isFromSource(InputDevice.SOURCE_MOUSE) && touchpadGesture(event)) return true
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_MOVE, MotionEvent.ACTION_HOVER_ENTER -> {
                position(event.x,event.y); sendMouse(0x0800); postInvalidateOnAnimation(); return true
            }
            MotionEvent.ACTION_SCROLL -> {
                position(event.x,event.y)
                for (h in 0 until event.historySize) pointer.scroll(
                    event.getHistoricalAxisValue(MotionEvent.AXIS_HSCROLL,h),event.getHistoricalAxisValue(MotionEvent.AXIS_VSCROLL,h),1f,1,event.deviceId)
                pointer.scroll(event.getAxisValue(MotionEvent.AXIS_HSCROLL),event.getAxisValue(MotionEvent.AXIS_VSCROLL),1f,1,event.deviceId)
                postInvalidateOnAnimation()
                return true
            }
            MotionEvent.ACTION_BUTTON_PRESS, MotionEvent.ACTION_BUTTON_RELEASE -> {
                if (event.actionButton and (MotionEvent.BUTTON_PRIMARY or MotionEvent.BUTTON_SECONDARY or MotionEvent.BUTTON_TERTIARY) == 0)
                    return super.onGenericMotionEvent(event)
                connection?.finishComposingText()
                position(event.x,event.y)
                pointer.button(event.actionButton,event.actionMasked == MotionEvent.ACTION_BUTTON_PRESS)
                postInvalidateOnAnimation()
                return true
            }
        }
        return super.onGenericMotionEvent(event)
    }
    override fun onCheckIsTextEditor() = true
    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        connection?.closeConnection()
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN or
            EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING or EditorInfo.IME_ACTION_NONE
        outAttrs.initialSelStart=0; outAttrs.initialSelEnd=0; outAttrs.initialCapsMode=0
        return DesktopInputConnection().also { connection=it }
    }
    // shortcut: only pending composition is local; a remote text channel is needed for surrounding-text correction.
    private inner class DesktopInputConnection : BaseInputConnection(this@DesktopView,true) {
        private var closed=false
        private var batchDepth=0
        private var selectionDirty=false
        private fun active() = !closed && hasFocus() && hasWindowFocus() && session.phase == Phase.CONNECTED && !session.inputSuspended
        private fun selectionChanged() {
            if (closed) return
            if (batchDepth > 0) { selectionDirty=true; return }
            selectionDirty=false
            val text=editable ?: return
            context.getSystemService(InputMethodManager::class.java).updateSelection(this@DesktopView,
                Selection.getSelectionStart(text),Selection.getSelectionEnd(text),getComposingSpanStart(text),getComposingSpanEnd(text))
        }
        private fun flush() {
            val text=editable?.toString().orEmpty()
            editable?.clear()
            if (text.isNotEmpty()) session.keyboard.commitText(text)
            selectionChanged()
        }
        override fun beginBatchEdit(): Boolean {
            if (!active()) return false
            batchDepth++
            return true
        }
        override fun endBatchEdit(): Boolean {
            if (batchDepth == 0) return false
            batchDepth--
            if (batchDepth == 0 && selectionDirty) selectionChanged()
            return batchDepth > 0
        }
        override fun setSelection(start: Int,end: Int): Boolean {
            if (!active()) return false
            return super.setSelection(start,end).also { selectionChanged() }
        }
        override fun setComposingRegion(start: Int,end: Int): Boolean {
            if (!active()) return false
            return super.setComposingRegion(start,end).also { selectionChanged() }
        }
        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
            if (!active() || text == null || text.length > 16384) return false
            return super.setComposingText(text,newCursorPosition).also { selectionChanged() }
        }
        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            if (!active() || text == null || text.length > 16384) return false
            val result=super.commitText(text,newCursorPosition)
            flush()
            return result
        }
        override fun finishComposingText(): Boolean {
            if (!active()) return false
            super.finishComposingText()
            flush()
            return true
        }
        override fun deleteSurroundingText(beforeLength: Int,afterLength: Int): Boolean {
            if (!active() || beforeLength < 0 || afterLength < 0) return false
            if (editable?.isNotEmpty() == true)
                return super.deleteSurroundingText(beforeLength.coerceAtMost(100),afterLength.coerceAtMost(100)).also { selectionChanged() }
            repeat(beforeLength.coerceAtMost(100)) { session.keyboard.tap(0x08) }
            repeat(afterLength.coerceAtMost(100)) { session.keyboard.tap(0x2e) }
            return true
        }
        override fun deleteSurroundingTextInCodePoints(beforeLength: Int,afterLength: Int): Boolean {
            if (!active() || beforeLength < 0 || afterLength < 0) return false
            if (editable?.isNotEmpty() == true)
                return super.deleteSurroundingTextInCodePoints(beforeLength.coerceAtMost(100),afterLength.coerceAtMost(100)).also { selectionChanged() }
            return deleteSurroundingText(beforeLength,afterLength)
        }
        override fun performEditorAction(actionCode: Int): Boolean {
            if (!active()) return false
            return when (actionCode) {
                EditorInfo.IME_ACTION_UNSPECIFIED,EditorInfo.IME_ACTION_NONE,EditorInfo.IME_ACTION_GO,
                EditorInfo.IME_ACTION_DONE,EditorInfo.IME_ACTION_SEARCH,EditorInfo.IME_ACTION_SEND -> {
                    finishComposingText(); session.keyboard.tap(0x0d); true
                }
                else -> false
            }
        }
        override fun sendKeyEvent(event: KeyEvent): Boolean {
            if (!active()) return false
            if (event.action == KeyEvent.ACTION_MULTIPLE && event.keyCode == KeyEvent.KEYCODE_UNKNOWN && event.characters != null)
                return commitText(event.characters,1)
            return handleKey(event) || super.sendKeyEvent(event)
        }
        override fun closeConnection() {
            if (closed) return
            if (active()) finishComposingText()
            closed=true
            editable?.clear()
            super.closeConnection()
        }
    }
    private fun handleKey(event: KeyEvent): Boolean {
        if (session.phase != Phase.CONNECTED || session.inputSuspended || !hasWindowFocus()) return false
        val unicode=event.unicodeChar
        if (event.action == KeyEvent.ACTION_DOWN && !KeyEvent.isModifierKey(event.keyCode) &&
            (virtualKey(event.keyCode) != null || unicode != 0)) connection?.finishComposingText()
        val handled=session.keyboard.handle(event.action,event.keyCode,event.deviceId,unicode,event.metaState,event.repeatCount,event.characters)
        if (handled && event.action == KeyEvent.ACTION_DOWN && event.keyCode != KeyEvent.KEYCODE_MOVE_HOME) session.toolbar=false
        return handled
    }
    override fun dispatchKeyEventPreIme(event: KeyEvent): Boolean {
        // Physical terminal shortcuts must not be rewritten by the local IME. Back and volume pass through.
        if (event.deviceId != KeyCharacterMap.VIRTUAL_KEYBOARD && event.flags and KeyEvent.FLAG_SOFT_KEYBOARD == 0 &&
            event.isFromSource(InputDevice.SOURCE_KEYBOARD) && handleKey(event)) return true
        return super.dispatchKeyEventPreIme(event)
    }
    override fun dispatchKeyEvent(event: KeyEvent): Boolean = handleKey(event) || super.dispatchKeyEvent(event)

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
