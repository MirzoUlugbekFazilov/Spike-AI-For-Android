# Spike AI — Accessibility API resubmission pack

Rejection addressed: **Prominent disclosure — Non-compliant design** (AccessibilityService API),
received on version code 5. This pack covers version code **6 / 1.0.4**.

---

## 1. What changed in the app

| Google's requirement | How v6 satisfies it |
|---|---|
| Consent dialog shown **before** requesting the permission | `AccessibilityDisclosureDialog` is the only code path to `Settings.ACTION_ACCESSIBILITY_*`. The settings intent fires exclusively from the dialog's accept handler (`rememberAccessibilityConsentGate`). |
| Requires **affirmative user action** | The user must tap one of two buttons. Nothing happens until they do. |
| Navigating away is **not** consent | `DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)`. Back and outside-tap are disabled entirely; there is no code path where dismissal grants anything. |
| **No** auto-dismissing / expiring message | The dialog has no timer, no animation-out, and no side effects. It stays until a button is tapped. |
| **Two** buttons showing both consent options | "Decline" and "I agree", rendered at equal width and equal height side by side. No toggle, no single "Continue"/"OK". |

Previously, the "Grant" button on the accessibility row called
`BlockingPermissions.openAccessibility()` directly — a single button that jumped straight
to system settings with no disclosure. That was the violation. It is gone.

Also removed: a dead, unreferenced onboarding permission step that contained a second
non-compliant single-button path.

## 2. Narrower scope (Google: "use limited, more narrowly scoped APIs … when possible")

`res/xml/accessibility_service_config.xml` in v6:

- `accessibilityEventTypes` reduced from `typeWindowStateChanged|typeWindowContentChanged`
  to **`typeWindowStateChanged` only**. The service discarded every content-changed event
  anyway; it no longer subscribes to them.
- `isAccessibilityTool="false"` — explicitly declared. Spike AI does not claim to be an
  assistive tool and does not use the flag to designate itself as one.
- `canRetrieveWindowContent="true"` is retained for one narrow reason: on some OEM skins
  `AccessibilityEvent.getPackageName()` returns null, and the service falls back to
  `rootInActiveWindow.getPackageName()`. **Only the package name is read from that node.**
  No node text, no `findAccessibilityNodeInfosByText`, no content is ever accessed.

## 3. Why the Accessibility API is necessary (answer this in the declaration form)

> Spike AI's core feature is blocking distracting apps during a user-initiated focus
> session. To do that we must know, immediately, which app has come to the foreground.
>
> We already request and use `PACKAGE_USAGE_STATS` (UsageStatsManager), but it is not
> sufficient on its own: `queryEvents`/`queryUsageStats` are aggregated and delivered
> with a delay of one to several seconds, and require continuous polling. By the time a
> poll detects the switch, the distracting content is already on screen — which defeats
> the entire purpose of the feature the user asked for.
>
> `AccessibilityService.TYPE_WINDOW_STATE_CHANGED` is the only Android API that delivers a
> foreground-app change synchronously, at the moment it happens. We subscribe to that one
> event type and read one field from it: the package name.
>
> Spike AI does not read screen content, does not record audio, does not change user
> settings, does not block uninstallation or disabling of itself or any other app, and
> does not work around any Android privacy control or notification.

## 4. Store listing — add this to the description

Google requires the AccessibilityService use to be documented in the listing. Paste this
near the end of the full description:

> **Accessibility Service disclosure**
> Spike AI uses Android's Accessibility Service to power app blocking. With your explicit
> permission, it reads the package name of the app you currently have open so it can show
> the focus shield when you open an app you chose to block during a focus session. Spike AI
> does not read, record, store or share the text, passwords, messages or any other content
> shown on your screen, and this information never leaves your device. App blocking is
> optional — the rest of Spike AI works without it, and you can turn the service off at any
> time in Settings › Accessibility › Spike AI App Blocking.

## 5. Demo video (required with every submission)

Upload an unlisted YouTube video and paste the link into the accessibility declaration.
Record it in one unbroken take, screen-recorded on a real device, no cuts and no voiceover
edits. Show, in this order:

1. Fresh install, open Spike AI, reach the permissions screen.
2. Tap **Grant** on "Enable app blocking" → the disclosure dialog appears.
   **Hold on it for 4-5 seconds so the reviewer can read the whole text.**
3. Press the system **back** button and tap outside the dialog — show that it does *not*
   dismiss and does *not* grant anything. This is the exact point the reviewer failed you
   on; make it unmissable.
4. Tap **Decline** → dialog closes, permission is still off, app still usable.
5. Tap **Grant** again → tap **I agree** → system accessibility settings open.
6. Enable "Spike AI App Blocking", return to the app, see the green check.
7. Start a focus session with (e.g.) Instagram blocked.
8. Leave the app, open Instagram → the focus shield appears immediately.
9. Return to Spike AI, stop the session, open Instagram again → it opens normally.

## 6. Play Console checklist before you hit submit

- [ ] Upload the v6 (1.0.4) bundle.
- [ ] App content › **Accessibility API declaration**: paste §3, attach the §5 video link.
- [ ] Store listing: add the §4 disclosure block.
- [ ] App content › **Data safety**: camera (Sweat Mode rep counting, on-device, not
      collected), account data (email, name), app activity. The foreground package name is
      processed on-device and not collected — declare accordingly.
- [ ] App content › **Foreground service permissions**: justify `SPECIAL_USE` (keeps the
      user-initiated focus session and blocking alive; no other FGS type covers it).
- [ ] Confirm `USE_EXACT_ALARM` is no longer declared (see repo TODO) or complete the
      exact-alarm declaration.
- [ ] Account deletion URL present.
- [ ] Privacy policy at https://spikeai.tech/privacy.html mentions the Accessibility
      Service and the usage-access data explicitly.

## 7. Do not regress this

`BlockingPermissions.openAccessibility()` must never be called directly from UI code.
The only supported entry point is:

```kotlin
val gate = rememberAccessibilityConsentGate()
// …
Button(onClick = { gate.request() }) { … }
```

Adding a direct call anywhere re-introduces the exact rejection above.
