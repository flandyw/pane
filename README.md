# Pane

An ARM64 Android RDP client for a tablet connecting over Tailscale to a CPU-only
Ubuntu / XFCE / xrdp VPS. Native FreeRDP 3.32.2, Material 3 Expressive, Android 8+.

## Install

Download the signed APK from https://pane.flandolf.me/releases/latest.
Allow your browser to install apps when Android asks. Open Tailscale and connect
to the same tailnet as the VPS, then open Pane. The private address, username and
server certificate SHA-256 are prefilled. Enter your Linux password and tap
**Open desktop**. The password is held for the connection only and never saved.

Start with **Balanced** (1600×1000); use **Fast** (1280×800) if the server struggles
or **Sharp** (1920×1200) for more workspace. Each connection uses 24-bit colour,
TLS, bitmap compression, and no H.264 / RemoteFX / audio / desktop effects.
These are starting settings, not a measured claim of superiority over other clients.

Trackpad is the default. Tap to click, hold to right click, double tap and hold
to drag. Two fingers scroll; pinch to zoom. When zoomed, the viewport follows
the pointer. Switch to direct touch in the session toolbar. Bluetooth keyboards
and mice work too. The keyboard button opens the Android keyboard; Ctrl and Alt
in the shortcut bar toggle until tapped again. Clipboard transfer is explicit in
the session menu. Disconnect leaves your applications running on the VPS.

## Rendering and security

FreeRDP decodes RDP on a native thread. Its paint callback copies only damaged
regions into a reusable bitmap while GDI is stable. Android's hardware Canvas
presents that bitmap; redraw requests combine at display refresh, without a
queue of full-screen Java bitmap allocations. Server cursor shapes are drawn
locally at the current pointer position. Input goes straight to FreeRDP's event
queue. Touch clicks are sent on release, without waiting for double-tap timeout.

The SHA-256 certificate pin is obtained directly from the VPS. A native patch
makes a supplied pin mandatory even if a different certificate chains to a
trusted CA. No certificate-ignore or trust-on-first-use mode. Unknown/changed
certificate callbacks reject. Profile fields are validated; credentials aren't
persisted, backed up, or included in logs. TLS is required; older RDP security
is not enabled. Clipboard access is initiated by the user.

If the VPS certificate is intentionally replaced, verify the new fingerprint
in a trusted VPS terminal with:

```sh
openssl x509 -in /etc/xrdp/cert.pem -noout -fingerprint -sha256
```

Then update **Connection details** in Pane. RDP stays private through the VPS's
Tailscale-only UFW rules. Pane uses the separately installed Tailscale app;
it does not embed a VPN. No microphone, camera, storage, or file redirection.

## Build

```sh
git submodule update --init --recursive
# Install JDK 25, Android SDK platform/build-tools 36, NDK r29, CMake, Ninja,
# pkg-config, Perl, make and llvm-18. On an ARM Linux builder also install
# clang-18 and lld-18.
export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_NDK_HOME="$ANDROID_HOME/android-ndk-r29"
./scripts/build.sh                 # native engine, unit tests, lint, signed release
./scripts/build.sh --publish       # also publish through release-server
```

The first native build fetches hash-verified dependencies using FreeRDP's
superbuild. Optional video/image/audio codecs are disabled for this use case.
Builds use two jobs. On an ARM Linux host the script makes a local hard-linked
NDK overlay and uses the host's LLVM 18 with the NDK's Android sysroot and
compiler runtime; the original SDK is unchanged. On x86 Linux it uses the NDK
compiler. Both link with 16 KiB ELF page alignment. Native sources are pinned
by the submodule commit; local changes are in `scripts/patches/freerdp.patch`
and applied idempotently by the build script. The JNI free patch joins the
native connection thread before freeing its context.

Signing material is in `.signing/pane.p12` (alias `pane`) with a matching
`.signing/password`, excluded from Git. Back these files up securely: the same
key is required for Android updates. A new installation can generate a PKCS12
RSA signing key with `keytool -genkeypair`; provide its password privately in the
password file. Never replace the signing key of a published app.

Version name/code live in `app/build.gradle.kts`; increment both before publishing.
`release-server` verifies the APK package, signature and pinned signing certificate
before publication, then serves immutable builds and SHA-256 checksums.

## Release service

```sh
./release-server/install.sh none
./release-server/pin-cert.sh app/build/outputs/apk/release/app-release.apk
./release-server/publish.sh app/build/outputs/apk/release/app-release.apk
sudo systemctl status pane-releases
curl -f https://pane.flandolf.me/releases/latest.json
```

Configured domain: `pane.flandolf.me`; service: `pane-releases`; loopback port:
8789. HTTPS terminates in Nginx, Certbot renews it, Cloudflare is configured.
The Go service has read-only release storage and no upload API. Existing apps'
release services aren't changed. Update installation is manual through Android.

## Validation

JVM tests cover profile validation, TLS/CPU settings, argument boundaries,
resolution budgets and physical key mapping. Android lint and release signing
checks run before publishing. Native ABI/ELF dependency checks run on the built APK.
The release server's own tests and health endpoint are checked during installation.

Physical Android device automation is disabled on this VPS. Before calling this
production ready, test tablet installation, password entry, certificate mismatch,
connect/cancel/reconnect, rotation/split screen, mouse buttons/scroll, touch drag,
IME composition, Ctrl/Alt shortcuts, clipboard and background/resume. Compare
Fast/Balanced/Sharp on the same network with Tailscale reporting a direct peer
connection. Screen redraw count is activity-dependent, not an RTT measurement.

## Licenses

Pane's own code is Apache-2.0. FreeRDP's main libraries are Apache-2.0; its Android
JNI files retain MPL-2.0 notices. OpenSSL is Apache-2.0 and uriparser BSD-3-Clause.
The APK includes third-party notices, the modified JNI source and native patch in
`assets/third-party/`; exact upstream sources are pinned by the FreeRDP submodule.
