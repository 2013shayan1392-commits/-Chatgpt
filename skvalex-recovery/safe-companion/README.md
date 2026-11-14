# Safe Companion — independent, non-recording Android utility

This is a standalone Android application module. It is not a rebuilt, patched, or impersonated SKVALEX APK.

## Implemented
- Persian RTL interface with system/light/dark appearance settings.
- User-selected audio files through Android Storage Access Framework (SAF).
- Persisted read access to a selected audio document where the provider supports it.
- User-selected folder tree and in-app browsing of subfolders and audio files through `DocumentsContract`.
- Folder browsing is limited to the selected SAF tree; no broad storage permission or direct filesystem traversal is used.
- Playback with play, pause/resume, stop, completion and error handling.
- Foreground media-playback service and a notification with playback and stop controls.
- Notification channel and Android 13+ notification permission handling.
- Read-only display of active SIM slot, carrier label and display name through the public `SubscriptionManager` API. No phone number is displayed and no call routing or call-state monitoring is performed.
- Settings to change appearance and clear the saved file/folder selections.

## Explicitly excluded
- No call recording, microphone capture, hidden audio source, call-state monitoring, privileged service, root integration, JNI/native recorder, or permission bypass.
- This app only plays audio files explicitly selected by the user.
- SIM information is informational only and requires the Android `READ_PHONE_STATE` runtime permission. If denied or restricted by the device vendor, the SIM panel reports that the information is unavailable.
- Folder enumeration depends on the selected document provider. Some cloud or vendor providers may not expose child documents or may expose them slowly.

## Build
- Android Gradle Plugin: 8.7.3
- Gradle: 8.9
- JDK: 17
- Compile SDK: 35
- Target SDK: 35
- Minimum SDK: 29 (Android 10)

Open this directory in Android Studio, or run:

```bash
gradle --no-daemon clean testDebugUnitTest assembleDebug
gradle --no-daemon lintDebug
```

A GitHub Actions workflow at `.github/workflows/safe-companion.yml` builds the debug APK, runs lint, and performs startup smoke tests on Android 10 (API 29) and Android 16 (API 36) emulators when this project changes on branch `skvalex-recovery`. A successful run uploads an artifact named `Safe-Companion-debug`.

## Compatibility and verification
- Source compatibility floor: Android 10 / API 29.
- Build target: API 35; Android 16 / API 36 is above the target and can run the app under platform compatibility behavior.
- The project uses public Android APIs and SAF. Huawei, Xiaomi and Samsung document providers may differ.
- Automated compilation/lint is not a substitute for physical-device testing. Android 10 and Android 16 runtime tests require real or emulated devices on those API levels.
- Debug APKs produced by CI are unsigned with a release key and are for testing only.

## Status
The app source now includes the listed features. Build and device-test status must be read from the latest GitHub Actions run; do not assume an APK exists until the workflow reports success and publishes the artifact.
