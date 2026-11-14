# Safe Companion — independent, non-recording Android utility

This is a new Android application module, not a rebuilt or modified SKVALEX APK.

## Included
- Android Storage Access Framework picker for a user-selected audio file.
- Optional persisted folder-tree grant, where the document provider supports it.
- Local playback with play, pause, stop, completion and error handling.
- Persists the last selected file and folder URI.
- Persian RTL interface.
- No broad storage permission and no direct filesystem traversal.

## Explicitly excluded
- No call recording, microphone capture, hidden audio source, call-state monitoring, privileged service, root integration, JNI/native recorder, or permission bypass.
- The folder tree URI is currently saved and displayed; this initial companion does not enumerate the folder's contents.
- SIM-selection UI and original application's settings are not yet recreated.

## Build
Open this directory in Android Studio with Android Gradle Plugin 8.7.3, JDK 17 and Android SDK 35. The project has no Gradle wrapper checked in. Build from Android Studio or with a compatible local Gradle installation.

## Compatibility
Minimum API 29 (Android 10). The source uses public Android APIs and the Storage Access Framework. Runtime validation on Huawei, Xiaomi and Samsung devices has not yet been performed. Codec support depends on Android's installed media decoders and the selected file provider.

## Status
Source scaffold committed. Not yet compiled or device-tested. Do not treat it as a tested APK or as a replacement for the original recorder.
