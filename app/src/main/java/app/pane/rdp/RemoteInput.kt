package app.pane.rdp

import android.content.res.Configuration
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Shared by physical keys, the IME and the shortcut strip so releases have one owner. */
internal class RemoteKeyboard(
    private val sendKey: (Int, Boolean) -> Unit,
    private val sendText: (String) -> Unit,
    private val toggleToolbar: () -> Unit,
    private val combineAccent: (Int, Int) -> Int
) {
    private val held = mutableMapOf<Pair<Int, Int>, Int>()
    private val localHome = mutableSetOf<Int>()
    private var accent = 0
    var ctrl by mutableStateOf(false); private set
    var alt by mutableStateOf(false); private set
    private fun isHeld(vk: Int) = held.containsValue(vk) || (vk == 0xa2 && ctrl) || (vk == 0xa4 && alt)

    fun toggleCtrl() { ctrl = !ctrl; if (!held.containsValue(0xa2)) sendKey(0xa2,ctrl) }
    fun toggleAlt() { alt = !alt; if (!held.containsValue(0xa4)) sendKey(0xa4,alt) }
    fun tap(vk: Int) { sendKey(vk,true); if (!isHeld(vk)) sendKey(vk,false) }

    fun toolbarShortcut(action: Int, code: Int, device: Int, meta: Int, repeat: Int = 0): Boolean {
        if (code != KeyEvent.KEYCODE_MOVE_HOME) return false
        if (action == KeyEvent.ACTION_UP) return localHome.remove(device)
        if (action != KeyEvent.ACTION_DOWN) return false
        if (device in localHome) {
            if (repeat > 0) return true
            localHome.remove(device)
        }
        if (meta and KeyEvent.META_CTRL_ON == 0 || meta and KeyEvent.META_ALT_ON == 0 ||
            meta and (KeyEvent.META_SHIFT_ON or KeyEvent.META_META_ON) != 0) return false
        localHome.add(device)
        if (repeat == 0) toggleToolbar()
        return true
    }

    private fun syncModifiers(device: Int, meta: Int, code: Int) {
        for (group in ModifierGroups) {
            val left=group[0]; val right=group[1]
            val leftMask=group[2]; val rightMask=group[3]; val aggregate=group[4]
            val leftVk=group[5]; val rightVk=group[6]
            if (code == left || code == right) continue
            val sides=meta and (leftMask or rightMask)
            val fallbackRight=sides == 0 && held[device to right] == rightVk
            val leftDown=meta and leftMask != 0 || (sides == 0 && meta and aggregate != 0 && !fallbackRight)
            val rightDown=meta and rightMask != 0 || (sides == 0 && meta and aggregate != 0 && fallbackRight)
            for (side in 0..1) {
                val key=if (side == 0) left else right
                val vk=if (side == 0) leftVk else rightVk
                val down=if (side == 0) leftDown else rightDown
                val id=device to key
                if (down && !held.containsKey(id)) {
                    val alreadyDown=isHeld(vk)
                    held[id]=vk
                    if (!alreadyDown) sendKey(vk,true)
                } else if (!down && held.remove(id) != null && !isHeld(vk)) sendKey(vk,false)
            }
        }
    }
    fun handle(action: Int, code: Int, device: Int, unicode: Int = 0, meta: Int = 0,
               repeat: Int = 0, characters: String? = null): Boolean {
        if (toolbarShortcut(action,code,device,meta,repeat)) return true
        if (action == KeyEvent.ACTION_MULTIPLE) {
            if (code == KeyEvent.KEYCODE_UNKNOWN && characters != null) { commitText(characters); return true }
            val vk = virtualKey(code) ?: return false
            repeat(repeat.coerceIn(0,100)) { tap(vk) }
            return true
        }
        if (action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_UP) return false
        if (virtualKey(code) != null || unicode != 0 || held.containsKey(device to code)) syncModifiers(device,meta,code)
        val id = device to code
        if (action == KeyEvent.ACTION_UP) {
            val vk = held.remove(id) ?: return virtualKey(code) != null
            if (vk != 0 && !isHeld(vk)) sendKey(vk,false)
            return true
        }
        // Never reinterpret a repeating raw key as text when its modifier changes mid-hold.
        held[id]?.let { if (it != 0) { sendKey(it,true); return true } }
        val altGr = meta and KeyEvent.META_ALT_RIGHT_ON != 0 && !ctrl && !alt
        val shortcut = ctrl || alt || meta and KeyEvent.META_META_ON != 0 ||
            (!altGr && meta and (KeyEvent.META_CTRL_ON or KeyEvent.META_ALT_ON) != 0)
        if (!shortcut && unicode and KeyCharacterMap.COMBINING_ACCENT != 0) {
            val next=unicode and KeyCharacterMap.COMBINING_ACCENT_MASK
            if (!isPrintableCodePoint(next)) return false
            val combined=if (accent != 0) combineAccent(accent,next) else 0
            if (isPrintableCodePoint(combined)) { emitText(String(Character.toChars(combined))); accent=0 }
            else {
                if (accent != 0) emitText(String(Character.toChars(accent)))
                accent=next
            }
            held[id] = 0
            return true
        }
        if (!shortcut && isPrintableCodePoint(unicode)) {
            val combined = if (accent != 0) combineAccent(accent,unicode) else 0
            val text = if (isPrintableCodePoint(combined)) String(Character.toChars(combined)) else
                (if (accent != 0) String(Character.toChars(accent)) else "") + String(Character.toChars(unicode))
            accent = 0
            held[id] = 0
            emitText(text)
            return true
        }
        if (accent != 0 && code == KeyEvent.KEYCODE_DEL) { accent=0; held[id]=0; return true }
        if (code == KeyEvent.KEYCODE_ESCAPE || shortcut) accent=0
        val vk = virtualKey(code) ?: return false
        if (accent != 0 && vk !in 0xa0..0xa5 && vk != 0x14) {
            emitText(String(Character.toChars(accent))); accent=0
        }
        val alreadyDown = isHeld(vk)
        held[id] = vk
        if (!alreadyDown) sendKey(vk,true)
        return true
    }

    fun commitText(text: String) {
        if (text.isEmpty()) return
        accent=0
        if ((ctrl || alt) && text.length == 1) {
            shortcutVirtualKey(text[0])?.let { tap(it); return }
        }
        emitText(text)
    }
    private fun emitText(text: String) {
        // Unicode already contains Shift/AltGr; do not apply those modifiers a second time remotely.
        val modifiers = held.values.filter { it in 0xa0..0xa5 || it == 0x5b || it == 0x5c }.toMutableSet()
        if (ctrl) modifiers.add(0xa2)
        if (alt) modifiers.add(0xa4)
        modifiers.forEach { sendKey(it,false) }
        try { sendText(text) } finally { modifiers.forEach { sendKey(it,true) } }
    }
    fun releasePhysical() {
        val keys = held.values.filter { it != 0 }.toSet()
        held.clear(); accent=0
        keys.filterNot { isHeld(it) }.forEach { sendKey(it,false) }
    }
    fun releaseAll() {
        val keys = held.values.filter { it != 0 }.toMutableSet()
        if (ctrl) keys.add(0xa2)
        if (alt) keys.add(0xa4)
        held.clear(); localHome.clear(); accent=0; ctrl=false; alt=false
        keys.forEach { sendKey(it,false) }
    }
}

internal fun isPrintableCodePoint(code: Int) = code in 0x20..0x10ffff && code !in 0xd800..0xdfff && !Character.isISOControl(code)

private fun shortcutVirtualKey(char: Char): Int? = when (char) {
    in 'a'..'z' -> char.uppercaseChar().code
    in 'A'..'Z', in '0'..'9' -> char.code
    '\r', '\n' -> 0x0d; '\t' -> 0x09; '\b' -> 0x08
    ' ' -> 0x20; '[' -> 0xdb; ']' -> 0xdd; '\\' -> 0xdc
    ';' -> 0xba; '\'' -> 0xde; ',' -> 0xbc; '.' -> 0xbe; '/' -> 0xbf
    '-' -> 0xbd; '=' -> 0xbb; '`' -> 0xc0
    else -> null
}

internal class RemotePointer(private val send: (Int) -> Unit) {
    private var buttons = 0
    private var x = 0f
    private var y = 0f
    private var source = 0
    private var device = -1
    fun buttons(state: Int) {
        val supported = state and (MotionEvent.BUTTON_PRIMARY or MotionEvent.BUTTON_SECONDARY or MotionEvent.BUTTON_TERTIARY)
        for (button in 0..2) {
            val bit = 1 shl button
            if ((buttons xor supported) and bit != 0)
                send((0x1000 shl button) or if (supported and bit != 0) 0x8000 else 0)
        }
        buttons = supported
    }
    fun button(button: Int, down: Boolean) = buttons(if (down) buttons or button else buttons and button.inv())
    fun scroll(dx: Float, dy: Float, step: Float, source: Int, device: Int) {
        if (this.source != source || this.device != device) resetScroll()
        this.source=source; this.device=device
        x = scrollNotches(x+dx,step) { send(0x0400 or ((it*120) and 0x1ff)) }
        y = scrollNotches(y+dy,step) { send(0x0200 or ((it*120) and 0x1ff)) }
    }
    fun resetScroll() { x=0f; y=0f }
    fun release() { buttons(0); resetScroll(); source=0; device=-1 }
}

/** RDP Unicode events carry UTF-16 units; control characters need physical key events. */
internal inline fun sendRemoteText(text: String, key: (Int, Boolean)->Unit, unicode: (Int, Boolean)->Unit) {
    var i = 0
    val end = minOf(text.length,16384)
    while (i < end) {
        val char = text[i++]
        val vk = when (char) { '\r', '\n' -> 0x0d; '\t' -> 0x09; '\b' -> 0x08; else -> null }
        if (vk != null) {
            if (char == '\r' && i < end && text[i] == '\n') i++
            key(vk,true); key(vk,false)
        } else if (char.isHighSurrogate()) {
            if (i < end && text[i].isLowSurrogate()) {
                unicode(char.code,true); unicode(char.code,false)
                val low = text[i++].code
                unicode(low,true); unicode(low,false)
            }
        } else if (!char.isLowSurrogate() && !char.isISOControl()) {
            unicode(char.code,true); unicode(char.code,false)
        }
    }
}

internal fun hasHardwareKeyboard(keyboard: Int, hidden: Int) =
    keyboard == Configuration.KEYBOARD_QWERTY && hidden == Configuration.HARDKEYBOARDHIDDEN_NO

private val ModifierGroups = listOf(
    intArrayOf(KeyEvent.KEYCODE_SHIFT_LEFT,KeyEvent.KEYCODE_SHIFT_RIGHT,KeyEvent.META_SHIFT_LEFT_ON,KeyEvent.META_SHIFT_RIGHT_ON,KeyEvent.META_SHIFT_ON,0xa0,0xa1),
    intArrayOf(KeyEvent.KEYCODE_CTRL_LEFT,KeyEvent.KEYCODE_CTRL_RIGHT,KeyEvent.META_CTRL_LEFT_ON,KeyEvent.META_CTRL_RIGHT_ON,KeyEvent.META_CTRL_ON,0xa2,0xa3),
    intArrayOf(KeyEvent.KEYCODE_ALT_LEFT,KeyEvent.KEYCODE_ALT_RIGHT,KeyEvent.META_ALT_LEFT_ON,KeyEvent.META_ALT_RIGHT_ON,KeyEvent.META_ALT_ON,0xa4,0xa5),
    intArrayOf(KeyEvent.KEYCODE_META_LEFT,KeyEvent.KEYCODE_META_RIGHT,KeyEvent.META_META_LEFT_ON,KeyEvent.META_META_RIGHT_ON,KeyEvent.META_META_ON,0x5b,0x5c)
)

/** Convert touchpad counts/mm to display pixels/mm before running Pane's existing gestures. */
internal fun touchpadCoordinateScale(resolution: Float, dpi: Float): Float {
    // shortcut: devices without axis resolution retain their native units; calibrate on affected hardware.
    if (!resolution.isFinite() || resolution <= 0 || !dpi.isFinite() || dpi <= 0) return 1f
    return (dpi / 25.4f / resolution).coerceIn(0.01f, 100f)
}
