# Playback engine verification plan

## Implemented behavior

- Asynchronous local playback for a user-selected SAF audio URI.
- Foreground media-playback service and ongoing notification.
- Play/pause/resume/stop through the app and notification.
- Audio focus acquisition/release on Android 10+; legacy focus API for API 29 and modern AudioFocusRequest from API 26.
- Temporary focus loss pauses and resumes only when appropriate; permanent focus loss pauses; transient ducking lowers volume and restores it.
- Headphone unplug / audio-becoming-noisy event pauses playback.
- MediaPlayer wake mode for screen-off playback, declared with WAKE_LOCK permission.
- Cleanup of player, focus and dynamic receiver on service shutdown.
- Clear error state when URI access or media decoding fails.

## Automated checks

The CI workflow runs:
- Gradle unit tests and debug APK assembly.
- Android lint.
- App install/start smoke checks on Android 10 (API 29) and Android 16 (API 36).

These smoke checks do not prove actual audio output or all media-provider behavior.

## Manual functional matrix

| Test | Android 10 | Android 16 |
|---|---|---|
| Select and play a local MP3 | Pending | Pending |
| Pause/resume from app | Pending | Pending |
| Pause/resume from notification | Pending | Pending |
| Stop from notification | Pending | Pending |
| Lock screen during playback | Pending | Pending |
| Unplug wired headset / disconnect audio route | Pending | Pending |
| Briefly interrupt audio with another app | Pending | Pending |
| Revoke/delete source file permission then retry | Pending | Pending |
| Unsupported/corrupt audio reports an error without crash | Pending | Pending |

## Vendor-device validation

Huawei Mate 10 / EMUI 10 and Xiaomi/POCO Android 16 must be tested physically. Emulator success is not a substitute for OEM battery/background policy, SAF provider, Bluetooth routing, or notification behavior.

## Explicit scope

This engine plays files explicitly selected by the user. It does not record calls, capture microphone audio, monitor call state, or bypass Android permissions.
