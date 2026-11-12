# Call recording readiness

This project intentionally does not request microphone or call-audio recording access in the current release.

The call lifecycle must remain independent from any future recording implementation. A later release may add a separate recording feature behind a capability check, rather than placing recording logic inside the dialer UI or the core call controls.

## Planned extension boundary

- Observe Telecom call lifecycle events from `PhoneInCallService`.
- Identify each call session independently; do not assume only one call can exist.
- Keep recording state and storage handling in a separate feature component.
- Check device/API/OEM support before exposing a recording action.
- Show a clear recording state to the user and provide an obvious stop action.
- Handle unsupported devices without affecting ordinary calling.
- Apply applicable consent, privacy, and local-law requirements before enabling recording.

## Compatibility notes

A third-party default dialer does not automatically receive privileged access to the carrier call's audio stream. Support varies by Android version, device manufacturer, region, and system configuration. Therefore, a future update must detect actual capability at runtime and must not promise recording on every device.

No hidden recording, automatic recording, or background microphone capture is part of this project.
