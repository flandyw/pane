# Pane UI and Voxl

Pane's original custom connection screen, responsive layouts, purple/lime palette,
light/dark theme, quality selector, remembered-password toggle, immersive session,
auto-hiding toolbar, keyboard bar, touch/trackpad input, IME handling and explicit
text clipboard remain in place. The backend-specific account/certificate fields
are replaced by a Voxl URL and server password. H.264 stripes replace the RDP bitmap.

The update card adds download size/percentage, verification/install progress,
permission and confirmation actions, retry and a successful-update celebration.
It fits the existing Material Expressive styling. Confetti is short, decorative,
non-interactive and respects Android animation settings.

See README.md and ANDROID-INPUT.md for device checks still required.

## Connected immersion (0.6.0)

Pane's custom UI and Voxl backend are retained. The connected desktop extends
through the display cutout while controls remain safe; immersive bars are restored
after focus changes, the toolbar hides sooner and the keyboard strip only appears
with the IME. Pointer capture keeps mouse/trackpad input inside the desktop, with
raw touchpad contact calibration and safe release for controls/focus loss. Local
feedback/autofill is suppressed in the remote view. Volume keys are forwarded.
Optional toolbar controls add app-owned priority DND and confirmed screen pinning.
Real device checks and Android platform limits are listed in ANDROID-INPUT.md.
