# Android keyboard and touchpad compatibility

This pass follows Android's documented input contracts and the Android 16 AOSP implementation. The findings below informed code changes; they are not a claim of testing on a physical tablet. The native FreeRDP engine, permissions, dependencies, saved profiles and release configuration are unchanged.

## Platform findings and decisions

Android delivers physical keys through system policy, pre-IME dispatch, the IME, and normal view dispatch. System policy can reserve shortcuts before an app sees them. Pane now handles app-owned hardware typing/shortcuts in the focused desktop's pre-IME path. Back, device Home, app switch, volume and language-switch keys remain unclaimed. This preserves remote terminal shortcuts while leaving Android's reserved actions intact. [AOSP keyboard pipeline](https://source.android.com/docs/core/interaction/input/keyboard-devices)

Android supplies text according to the device's character map. Soft keyboards are not required to produce key events, and key streams can contain repeats, cancellations or missing pairs. Pane uses Android Unicode values for ordinary typing and physical virtual keys for shortcuts/navigation. Releases follow the original press, and current modifier metadata repairs missed transitions. [KeyEvent contract](https://developer.android.com/reference/android/view/KeyEvent)

A touchpad normally advertises both mouse and touchpad capabilities but emits mouse-source events. Pane therefore keeps mouse-source routing; it does not change physical trackpad behavior when the touchscreen's trackpad/direct switch changes. Unsupported mouse back/forward buttons retain platform handling. [InputDevice source definitions](https://developer.android.com/reference/android/view/InputDevice)

Android 14+ exposes classified touchpad scroll/pinch streams. Their gesture axes contain per-sample scroll distances or pinch ratios, including historical samples. Pane processes those samples without treating gesture coordinates as cursor positions. Older devices retain wheel/coordinate fallbacks. Scroll pixels and fractional wheel notches have separate accumulation domains. AOSP reserves the hidden multi-finger classification for system UI; Pane ignores it locally. [MotionEvent contract](https://developer.android.com/reference/android/view/MotionEvent), [AOSP MotionEvent definitions](https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-16.0.0_r2/core/java/android/view/MotionEvent.java), [AOSP gesture conversion](https://android.googlesource.com/platform/frameworks/native/+/refs/tags/android-16.0.0_r2/services/inputflinger/reader/mapper/gestures/GestureConverter.cpp)

IME composition is replaceable pending text, not a stream of characters to append. Pane now uses BaseInputConnection's composing buffer, sends the final value on commit/finish, supports UTF-16 and code-point deletion callbacks, and reports selection after batch edits. It invalidates old connections so delayed IME callbacks cannot type into a later focus target. [InputConnection contract](https://developer.android.com/reference/android/view/inputmethod/InputConnection), [BaseInputConnection](https://developer.android.com/reference/android/view/inputmethod/BaseInputConnection)

Software-keyboard visibility depends on focus and IME policy. Pane provides an explicit keyboard request even with a cover attached, never uses SHOW_FORCED, and lets Back dismiss the IME before session navigation. A hardware keyboard and a visible software keyboard can coexist. [Keyboard visibility](https://developer.android.com/develop/ui/views/touch-and-input/keyboard-input/visibility)

Pane requests no personalized IME learning and no fullscreen/extracted editor. These flags are advisory to the installed IME. [EditorInfo options](https://developer.android.com/reference/android/view/inputmethod/EditorInfo)

## Implemented conflict fixes

- Shared ownership for physical keys, sticky Ctrl/Alt and shortcut-strip key taps; one source cannot release a modifier still held by another.
- Physical-key tracking includes device identity, with idempotent release on window loss, pause, device removal/change, session disconnect and view detach.
- Plain typing follows Android's layout, including shifted punctuation, accented text, AltGr and supplementary characters. Shift/AltGr are temporarily released remotely while sending already-transformed Unicode.
- Ctrl/Alt combinations remain physical shortcuts. Sticky Ctrl/Alt work with single-character IME commits, including Enter and Tab.
- Dead accents compose through Android's KeyCharacterMap, can be cancelled with Backspace/Escape, and cannot crash on an invalid code point. Repeated accents and accent+space finish the accent correctly.
- Legacy ACTION_MULTIPLE text and bounded repeated-key batches are handled; unmatched releases do not manufacture new presses.
- Ctrl+Alt+Home is local as a complete press/release pair, including when modifiers release first or toolbar controls own focus. Shift+Ctrl+Alt+Home remains remote. A lost Home release does not block the next press.
- Stale IME connections reject typing. Active connections finish pending composition on normal close; already-unfocused/suspended connections discard it. Physical typing flushes pending composition before remote navigation or text.
- IME composition stays local until committed or finished; intermediate replacements do not generate duplicate remote text.
- Editor actions generate remote Enter, and legacy IME Back/volume events fall back through Android's window dispatch.
- Newline/CRLF, Tab and Backspace text controls become real remote keys. CRLF generates one Enter. Malformed surrogate units are dropped and the input-size boundary never cuts a valid pair in half.
- Incomplete/negative IME deletion requests are rejected or bounded. No fabricated remote surrounding-text history is exposed.
- Hardware-keyboard detection checks both QWERTY presence and hardware-hidden state; it does not mistake a software-keyboard visibility change for hardware attachment.
- The software-keyboard action remains available with a physical keyboard; the shortcut strip appears when a software keyboard is chosen. Keyboard visibility changes clear sticky modifiers.
- Session Back handling waits until the software keyboard is hidden. Dialogs suspend remote input and prevent toolbar auto-hide from stealing focus behind them.
- Desktop window refocus restarts input without unconditionally taking focus away from toolbar controls.
- Mouse button-state and generic button events are deduplicated. Secondary/middle clicks never manufacture a primary click, and zero-button synthetic mouse downs do not click.
- Primary, secondary and middle mouse buttons release together on cancellation/focus loss. Unknown button actions remain unclaimed.
- Classified touchpad scroll/pinch never reposition the remote pointer. Supported pinches zoom; system multi-finger swipes perform no Pane gesture.
- Fractional wheel and pixel-scroll remainders reset when devices or input domains change, preventing scroll jumps. Historical scroll/pinch samples are processed.
- Two-finger touchscreen scrolling uses the finger centroid, so symmetric pinch movement does not create a scroll notch before scale recognition. Additional fingers do not scroll.
- Cancelled/palm-rejected touch streams cancel pending recognizers and drags. Delayed long-press callbacks cannot click after leaving the desktop.
- Pointer events are ignored during local dialogs or outside a connected session; keyboard and motion handling require the active window.

## Scope and checks

Production changes reach `RemoteInput.kt`, `DesktopView`, `RdpSession`, the activity lifecycle, the Compose toolbar/shortcut strip and its focus/Back handlers. JVM regression tests exercise the shared keyboard/pointer state and outgoing text encoding. They do not execute Android View/IME callbacks or the native engine.

Final verification: `:app:testDebugUnitTest :app:lintRelease :app:assembleDebug` passed. All 59 JVM tests passed, including 39 new input regressions. Release lint reports zero errors and 18 existing advisory warnings. `git diff --check` passed. The debug APK is `app/build/outputs/apk/debug/app-debug.apk`. The existing FreeRDP checkout modifications are preserved.

## Remaining limits and physical-device checklist

Android/OEM reserved shortcuts and system gestures cannot be forwarded if the OS consumes them first. Pane does not disable system gestures, capture the pointer globally, install an accessibility service or intercept keys outside the desktop.

Only pending composition is available locally. RDP provides a bitmap and key events here, not authoritative surrounding text, selections or grapheme boundaries. IME corrections of already-committed text, arbitrary reconversion, rich content and exact UTF-16-versus-remote-grapheme deletion are not promised. Pending text may be discarded when focus/window ownership is already lost. Commit a candidate before changing apps; use explicit clipboard transfer for long passages. Unicode rendering and physical shortcut layout still depend on server support and configuration.

Before declaring hardware compatibility verified, test on the target tablet/OS and keyboard firmware:

1. Cover attached/detached, Bluetooth/USB keyboard hotplug, reconnect and sleep/wake.
2. Android US and non-US layouts; Shift punctuation, Caps Lock, dead accents, accent+space, AltGr, emoji and Num Lock/navigation.
3. Ctrl+C/V/Z, Ctrl+Space, Ctrl/Alt+arrows, Shift+selection, Escape, function keys, repeat and cancellation; release keys in different orders.
4. Ctrl+Alt+Home with desktop focus and toolbar-button focus; release modifiers before Home and hold Home through repeats.
5. Gboard and the OEM IME: Latin composition, CJK candidates, finish without commitText, delete/backspace, Enter, keyboard switch, multiline text and supplementary characters.
6. Open/hide the IME with the cover attached, Back with the IME visible, rotation and split screen while composing.
7. Primary click/tap, right click, middle click, combined buttons, drag outside the view and cancel while a button is held.
8. Vertical/horizontal scroll, natural-scroll settings, slow fractional wheel movement, fast scrolling, batched samples, pinch followed by scroll, and switching between mouse and touchscreen.
9. System three/four-finger navigation, Alt+Tab/Meta shortcuts, volume/media keys, app switching with Ctrl/Alt held and return from a dialog.
10. TalkBack focus and toolbar navigation, pointer hover, zoom/cursor alignment, and repeated connect/disconnect against the real VPS.
