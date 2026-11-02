# Pane polish ledger

181 individual refinements, grouped here as a review checklist. Several related refinements share an implementation; this is not a claim of 181 independent features or measured speedups.

## Theme and system chrome

1. Follow the system dark theme on the connection screen.
2. Give dark mode its own readable primary/action colours.
3. Use theme colours for the workspace card instead of hard-coded white.
4. Use theme colours for the introductory information panel.
5. Use theme colours for the brand tile.
6. Keep remote controls in a consistent dark Material theme regardless of home theme.
7. Use a theme-aware connection progress surface.
8. Match status-bar icon brightness to home/session state.
9. Match navigation-bar icon brightness to home/session state.
10. Use a dark native window background at night to reduce startup flashes.
11. Set native status-bar brightness from day/night resources.
## Adaptive home layout

12. Reduce horizontal home padding on phones.
13. Keep more generous home padding on tablets.
14. Apply keyboard insets to the connection form.
15. Use a compact heading on narrow screens.
16. Let the headline wrap naturally instead of forcing desktop line breaks.
17. Shorten the narrow-screen introduction so the form appears sooner.
18. Reserve the large introductory panel for the two-column layout.
19. Switch to two columns only when there is enough room for both.
20. Use a single column at large font scales even on wider screens.
21. Bound the single-column content width for readability.
22. Center single-column content within the available width.
23. Allow independent scrolling of the introduction and form in wide layouts.
24. Give wide-layout form content vertical breathing room.
25. Move the narrow-layout footer into scrolling content so it does not consume keyboard space.
26. Place updater status on its own line to avoid header collisions.
27. Announce updater status changes politely to accessibility services.
28. Give the update action its own full-width row.
29. Keep the app name flexible within the header.
30. Handle a missing browser or settings activity with a user-visible message.
31. Hide the decorative brand glyph from accessibility traversal.
## Connection form and server verification

32. Mark the main introductory headline as an accessibility heading.
33. Mark the workspace title as an accessibility heading.
34. Replace the fixed armvps label with the entered account and server.
35. Show a useful empty-server prompt.
36. Limit long account/server summaries to two ellipsized lines.
37. Use a full-width username field.
38. Use a full-width password field.
39. Add an explicit password show/hide action.
40. Give the password visibility action a complete accessibility label.
41. Reset password visibility when the server or account changes.
42. Disable username autocorrection.
43. Disable password autocorrection.
44. Disable address autocorrection.
45. Show an example Tailscale endpoint in the address field.
46. Show address validation at the address field.
47. Show username validation at the username field.
48. Show certificate validation at the certificate field.
49. Avoid painting untouched address fields as errors on first launch.
50. Avoid painting untouched username fields as errors on first launch.
51. Avoid painting untouched certificate fields as errors on first launch.
52. Only submit from the password keyboard action when the form is valid.
53. Clear form focus when starting a connection.
54. Only autofocus the password for a hardware keyboard when the server details are valid.
55. Preserve expanded verification details using saveable UI state.
56. Put verification details before the primary connect action.
57. Explain how to repair a missing fingerprint when verification details are collapsed.
58. Expose expanded/collapsed verification state to accessibility services.
59. Make the verification toggle full width.
60. Mark server verification as an accessibility heading.
61. Provide the exact openssl certificate fingerprint command in the form.
62. Allow selection/copying of the certificate command.
63. Use a multi-line fingerprint field with a bounded visible height.
64. Disable fingerprint autocorrection.
65. Use an ASCII keyboard for fingerprint entry.
66. Show normalized fingerprint digit progress.
67. Explain accepted separators next to fingerprint entry.
68. Accept line-wrapped and tab-separated certificate fingerprints.
69. Split validation into reusable field-specific functions.
70. Reject empty hostname labels.
71. Reject hostname labels longer than 63 characters.
72. Reject leading hostname-label hyphens.
73. Reject trailing hostname-label hyphens.
74. Reject out-of-range IPv4 octets.
75. Reject numeric addresses without four octets.
76. Keep an optional trailing DNS dot valid.
77. Explain the username length limit in validation feedback.
78. Keep compiled address and fingerprint regexes instead of allocating them on every validation.
79. Fall back to safe resolution dimensions for non-finite aspect ratios.
80. Replace the device-specific Sharp description with a general rendering tradeoff.
81. Label the quality selector explicitly.
82. Mark desktop quality as an accessibility heading.
83. Use vertical quality radio rows on narrow cards.
84. Use vertical quality radio rows at large font scales.
85. Show per-quality explanations inside the vertical selector.
86. Give vertical quality choices radio-group semantics and full-row selection.
87. Announce resolution/quality description changes politely.
88. Give settings rows a minimum height and vertical breathing room.
89. Add spacing between setting explanations and switches.
90. Shorten remembered-password and sound explanations.
91. Allow selection/copying of connection failure details.
92. Announce connection failure details politely.
93. Let the connect button grow vertically with its text.
94. Explain why connect is disabled when server details are incomplete.
95. Explain why connect is disabled when the password is empty.
96. Show certificate-verification readiness when the form is complete.
## Session controls and feedback

97. Make the session toolbar horizontally scrollable on narrow screens.
98. Inset the floating toolbar from screen edges.
99. Let toolbar height grow with text size.
100. Bound and ellipsize the toolbar hostname.
101. Show connection phase as text alongside the status dot.
102. Distinguish disconnecting from connecting in toolbar status.
103. Allow more time to discover controls before the initial toolbar auto-hide.
104. Keep the toolbar visible for touch-exploration users.
105. Listen for touch-exploration changes and remove that listener when leaving.
106. Give the toolbar reveal handle a 48dp touch target.
107. Give the toolbar reveal handle a button role and accessible name.
108. Visually dim disabled touch-mode controls.
109. Visually dim disabled keyboard controls.
110. Visually dim disabled clipboard controls.
111. Visually dim disabled fit controls.
112. Make connection progress content scrollable for short windows.
113. Bound connection progress content width.
114. Show the target hostname during connection setup.
115. Explain Tailscale and connection setup wait time in the progress card.
116. Announce connection progress text changes politely.
117. Hide the shortcut strip until the desktop is connected.
118. Give shortcut buttons 48dp minimum height.
119. Give Ctrl and Alt chips 48dp minimum height.
120. Provide spoken arrow-key names for symbolic shortcut buttons.
121. Provide full spoken names for Page up, Page down and Escape shortcuts.
122. Cache the immutable shortcut list instead of allocating it during recomposition.
123. Prevent the session Back handler from handling Back while a dialog is open.
124. Make session help scrollable.
125. Show app version in session help.
126. Show desktop audio status in session help.
127. Collect redraw statistics only while help is open and connected.
128. Reset the redraw counter before starting a help measurement.
129. Restore desktop focus when dismissing session help.
130. Restore desktop focus when cancelling disconnection.
131. Release held input before opening session dialogs.
132. Reset on-screen modifiers when opening session dialogs.
133. Reset on-screen modifiers when hardware-keyboard availability changes.
134. Reset on-screen modifiers when connection readiness changes.
## Clipboard

135. Name the clipboard dialog explicitly as text clipboard.
136. Explain whether desktop clipboard text is available.
137. Add an explicit clipboard Cancel action.
138. Keep clipboard actions disabled when disconnected.
139. Safely handle tablet clipboards with no items.
140. Tell the user when there is no tablet text to send.
141. Read clipboard text directly rather than coercing URI content into text.
142. Confirm successful clipboard send with feedback.
143. Explain the existing 1 MB send limit when text is truncated.
144. Confirm successful desktop clipboard copying with feedback.
145. Restore desktop focus on all clipboard dismiss paths.
146. Clear remote clipboard content at the start of a new session.
147. Clear remote clipboard content at session teardown.
148. Reset accumulated paint counts at session start.
## Input, rendering and event work

149. Track physical keys held by the desktop view.
150. Release physical keys when the window loses focus.
151. Release physical keys when the desktop view detaches.
152. Release touchscreen drag when input is reset.
153. Release a held primary mouse button when input is reset.
154. Track secondary and middle mouse buttons for cleanup.
155. Release secondary and middle mouse buttons during input reset.
156. Clear scroll remainders when input is reset.
157. Release held input before changing touch mode.
158. Cache the hidden system pointer icon instead of resolving it for every pointer query.
159. Clamp local cursor coordinates before drawing after a remote resize.
160. Reuse one desktop scale value throughout each draw.
161. Reuse one desktop scale value when positioning input.
162. Ignore non-finite pointer coordinates.
163. Make touch clicks schedule a local cursor redraw immediately.
164. Provide haptic feedback on touchscreen right-click.
165. Mark the remote desktop view as clickable for accessibility.
166. Avoid requesting focus on every touch packet when already focused.
167. Release parent touch interception when a gesture ends or is cancelled.
168. Reset both scroll axes at the start of a touchscreen gesture.
169. Support horizontal two-finger touchscreen scrolling.
170. Reset scroll remainders during pinch zoom to avoid a later accidental notch.
171. Ignore unrelated non-pointer generic motion before moving the remote cursor.
172. Reject non-finite scroll amounts.
173. Reject invalid scroll step sizes.
174. Bound a pathological scroll packet to 32 notches to protect the native event queue.
175. Map D-pad center to remote Enter.
176. Map numpad Equals to the remote equals key.
177. Map numpad Comma to the remote separator key.
178. Clamp cursor hotspots to cursor dimensions.
179. Skip graphics updates for non-positive damage dimensions.
180. Skip redraw scheduling for empty or offscreen damage rectangles.

181. Route accessibility click actions through the remote left-click operation.

## Scope and validation

Changes reach MainActivity (Compose UI and all connection/session callers), Profile (validation and resolution arguments), DesktopView (render/input), RdpSession (clipboard/frame lifecycle), day/night resources, and JVM tests. No native engine, updater protocol, stored-profile schema, signing, or dependency changes. Existing FreeRDP submodule modifications are left alone.

Regression checks cover profile validation boundaries, fingerprint paste normalization, safe resolution inputs, argument security, key mappings, and scroll direction, fractional accumulation, malformed values and event caps. Final verification: `:app:testDebugUnitTest :app:lintRelease :app:assembleDebug` passed. All 20 JVM tests passed; release lint reported zero errors and 18 warnings (SDK/dependency upgrades, platform/ABI/tooling advisories, style suggestions and the existing gesture-delegated click warning). `git diff --check` passed. The debug APK is at `app/build/outputs/apk/debug/app-debug.apk`.

Device checks remain necessary: day/night appearance, large fonts, TalkBack, split-screen/rotation, IME and hardware keyboards, pinch/drag/mouse buttons, clipboard transfer, and connect/cancel/reconnect against a real server. No runtime latency or throughput benchmark was performed.
