# Android input

Pane retains its custom DesktopView and RemoteKeyboard: keyboard layout routing,
Ctrl/Alt sticky modifiers, Ctrl+Alt+Home toolbar shortcut, composing IME support,
touch/trackpad gestures, zoom, pointer tracking and scroll accumulation. The backend
translates existing input at the transport boundary to Voxl's X11 keysyms and
WebSocket pointer messages. MediaCodec decodes video directly into stripe textures.

Back opens local controls. Focused desktop input routes volume/media keys to the
host; Android-reserved shortcuts stay with Android. Input is released on focus
loss and disconnect. Clipboard remains an explicit text transfer from the toolbar.

Physical-device checks: keyboard cover/Bluetooth keyboard, CJK/dead-key composition,
Ctrl/Alt shortcuts, touch drag/right-click, mouse buttons/scroll, touchpad pinch,
zoom/pointer tracking, IME occlusion, rotation/split screen, background/resume and
reconnects. Test Android's installation permission, confirmation fallback and silent
self-updates on actual devices; desktop sessions must remain uninterrupted.

## Captured input and Android integration

When the toolbar and IME are hidden, a focused, connected desktop requests Android
pointer capture. Relative mouse deltas are mapped through the desktop scale;
buttons and historical wheel deltas use the existing RemotePointer accumulator.
Raw captured touchpad contacts are scaled from device counts/mm to display pixels
before entering the original gesture detectors. Missing axis resolution falls
back to native units and needs hardware calibration. Capture-driven device-change
callbacks release held input without releasing/re-requesting capture in a loop.

Local controls/IME/dialogs, focus loss, disconnect and detach release capture.
Ctrl+Alt+Home and Back remain local escapes. Physical touchpad clicks suppress tap
and long-press synthesis to avoid duplicate clicks. App-owned keyboard shortcuts
are also handled through dispatchKeyShortcutEvent. Volume keys map to XF86 volume
keysyms; host support still determines whether these have an effect.

Quiet desktop uses an app-owned automatic zen rule on Android 10+, activated only
for opted-in connected foreground sessions. It never changes other DND rules or
policies. Check permission revocation, manual rule disable/removal, focus changes,
reconnects, restart cleanup and crash recovery on a real device. Screen pinning
uses Android's explicit confirmation and is stopped when leaving the desktop.

Hardware checks: OnePlus cover raw-contact capture, physical click/tap duplication,
long drags, two-finger scroll, pinch calibration, mouse acceleration, capture release
through every local overlay, Alt/Meta shortcuts, volume forwarding, edge/cutout
layout, accessibility, and OEM restrictions on system bars and pinning.
