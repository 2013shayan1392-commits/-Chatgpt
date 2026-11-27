# Shayan Suite

A single-APK Android project with two separate in-app feature areas:
- KillApps: installed-app selection and app stop requests.
- Brevent: a separate screen and engine for Android background-execution AppOps rules.

The home screen and its card order remain in place. Selecting a card opens an internal screen in this APK; it does not launch the original third-party APK.

## Current implementation

### KillApps core
- Lists launchable, user-installed apps.
- Supports search and multi-selection.
- If Shizuku is running and Shayan Suite is authorized, sends am force-stop commands through a dedicated Shizuku UserService.
- Without Shizuku, requests background-process termination through Android's public ActivityManager API. This fallback is not equivalent to Force stop and is restricted on newer Android versions.

### Brevent core
- Has its own screen, command builder, and saved managed-package list.
- Uses Shizuku to set RUN_ANY_IN_BACKGROUND to ignore and force-stop the selected app.
- Restore sets the AppOp back to default.
- This is an original AppOps-based implementation, not the proprietary Brevent service engine. OEM and Android-version behavior can differ.

## Shizuku

Shizuku is optional for the KillApps fallback, but required for full Force stop and the Brevent AppOps actions. The user must start Shizuku and explicitly grant Shayan Suite permission. On non-rooted devices, Shizuku runs with ADB privileges, not root privileges. The project does not request root access.

The app uses Shizuku API 13.1.5 and a separate AIDL UserService process to execute explicitly requested commands.

## Scope and compatibility

- Minimum Android: 8.0 (API 26).
- Target SDK: 35.
- App list is limited to launchable user apps to avoid offering critical system packages for bulk actions.
- Samsung-specific Accessibility workarounds are not included. These first internal cores use an explicitly authorized ADB/Shizuku bridge instead.
- Original Brevent and KillApps APKs are not bundled or re-signed. This project implements independent functionality; it does not copy proprietary source code or claim exact feature parity.

## Build

Run: gradle :suite:assembleDebug

Output: suite/build/outputs/apk/debug/suite-debug.apk
