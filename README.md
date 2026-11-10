# Pane

Pane's custom Android tablet UI, now backed exclusively by
[Voxl](https://github.com/tembo/voxl). The original responsive connection card,
purple/lime light and dark themes, quality presets, floating auto-hiding toolbar,
keyboard shortcuts, touch/trackpad gestures, zoom, IME handling and explicit text
clipboard are retained. Voxl WebSockets and MediaCodec H.264 replace FreeRDP.

## Connect

Download the signed APK from https://pane.flandolf.me/releases/latest.
Enter the Voxl server URL, for example `wss://server.example.com:6901/ws`, and
its **Voxl password**, then tap **Open desktop**. An omitted scheme defaults to
WSS; an omitted path defaults to `/ws`. Use the port your Voxl server serves.
The Linux username and xrdp fingerprint fields no longer apply to this protocol.

**Remember password** is optional and off by default. It stores an AES-GCM
password encrypted with Android Keystore and bound to the server URL. Turning
it off deletes the saved password. Old RDP settings are left untouched and never
reused for Voxl. The app retains package `app.pane.rdp` and its signing identity.

WSS verifies certificates and hostnames using Android's trusted authorities.
Explicit WS addresses are supported over a trusted private network such as
Tailscale; WS itself has no TLS encryption. Pane embeds neither a VPN nor a server.

Fast/Balanced/Sharp keep their original desktop sizes; the server must enable
`--enable-resize` to apply them. Otherwise the host retains its resolution.
The original toolbar provides touch mode, keyboard, clipboard, fit and help.
Ctrl+Alt+Home toggles it. Back opens local controls; volume/media keys are sent to
the host while the desktop has focus (host support determines their effect). Clipboard transfer
is explicit and text-only. No audio is requested by the current Voxl transport.
Disconnect leaves host applications running.

## Connected immersion

The desktop fills the display, including the camera-cutout area, while the toolbar
stays inside the safe area. System bars are hidden again on focus/resume, and the
original Pane toolbar hides after two seconds. The shortcut strip appears only with
the software keyboard; attaching a physical keyboard hides the software keyboard.
Android focus highlights, desktop autofill/content capture, local click sounds and
long-press haptics are disabled for the remote view.

With a mouse or trackpad connected, Android pointer capture is requested while the
session is ready, the toolbar/IME are hidden and the desktop owns focus. The cursor
can move without hitting Android screen edges. Raw captured touchpad contacts use
Pane's existing tap, drag, two-finger scroll and pinch gestures, normalized using
the device's reported axis resolution. Capture releases for local controls,
keyboard, dialogs, focus loss and disconnect. Ctrl+Alt+Home, Back or touching the
handle opens the toolbar. Android accessibility touch exploration keeps the toolbar
available and disables automatic capture. Mouse capture bypasses Android pointer
acceleration; devices without touchpad resolution need real-hardware calibration.

Two optional controls are in the existing toolbar:

- **Quiet desktop** (Android 10+): grant Do Not Disturb access once, then enable it.
  Pane activates its own priority-DND rule only for a connected foreground desktop.
  Existing call/alarm exceptions and other DND rules are retained. The preference
  is remembered; disconnecting, reconnecting or leaving Pane pauses its rule. A
  killed/crashed foreground process can leave this rule active until Pane next
  opens or you turn off its mode in Android's DND settings.
- **Pin desktop**: Android asks to pin Pane and restrict Home/app switching. Use
  the system unpin gesture or Pane's **Unpin desktop** control; disconnect also
  unpins. Pinning is never requested automatically.

Android still owns reserved shortcuts, power/lock controls, mandatory navigation
and some OEM gestures. Complete kiosk lockdown requires managed-device privileges;
ordinary app immersion cannot guarantee that all Android UI is inaccessible.

## Updates

Pane checks `https://pane.flandolf.me/releases/latest.json` on launch and downloads
new releases in the background. Its update card shows percentage, downloaded size,
verification, readiness and install status, with retry on failure. Downloads are
bounded by the published size and verified by SHA-256, package, version and signing
certificate. Redirects outside the release service are not followed.

Enable **Install unknown apps** for Pane once using **Enable quiet updates**.
On Android 12+, Pane requests a silent self-update when you leave the app without
an open desktop, including when a download finishes after you have left. Android
may still require confirmation; **Finish update** handles that without opening
prompts during a desktop session. Android 8–11 use the explicit **Update now** flow.
The next launch after an upgrade shows a short confetti celebration, respecting
Android's animation setting, and a dismissible success card. Fresh installs and
repeat launches do not replay the celebration.

## Build

Install JDK 25 and Android SDK platform/build-tools 36. No NDK, CMake, native
build, or FreeRDP checkout is needed.

```sh
export JAVA_HOME=/path/to/jdk-25
export ANDROID_HOME="$HOME/android-sdk"
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleDebug
./scripts/verify-apk.py app/build/outputs/apk/debug/app-debug.apk
```

For signed releases, retain `.signing/pane.p12` (alias `pane`) and
`.signing/password`, then run `./scripts/build.sh`. Never replace a published
app's signing key. `./scripts/build.sh --publish` also publishes through the
existing `release-server` submodule. Signing files remain excluded from Git.
The unused FreeRDP submodule and its pre-existing local modifications are preserved.

## Validation and licenses

JVM tests cover Pane's existing keyboard and scroll handling, Voxl input translation,
endpoint validation, encrypted password binding, video metadata, release-feed
validation, bounded downloads/progress, and celebration eligibility. Release builds
run lint, APK contents checks and signature verification. Physical-device and live
Voxl testing remain necessary for video decoding, input, reconnects and Android's
permission/confirmation/silent-install behavior.

Pane's code is Apache-2.0. Voxl's transport and stripe decoder are adapted from
[voxl-pocket-android](https://github.com/ericmigi/voxl-pocket-android) under MIT;
the pinned upstream commit and license are in `app/src/main/assets/third-party/`.
