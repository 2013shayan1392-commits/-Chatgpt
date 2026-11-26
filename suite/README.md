# Shayan App Hub

A small Android launcher for the original Brevent and KillApps applications.

## Why this architecture

The hub is one APK. It launches the original apps by their Android package names:
- Brevent: `me.piebridge.brevent`
- KillApps: `com.tafayor.killall`

This intentionally keeps each target app installed as its own Android package. The target apps retain their original certificates, permissions, preferences, activities and processes. The hub does not copy, patch, re-sign, or redistribute either third-party APK.

If a target is missing, its card opens the official Google Play listing. Install it there, return to the hub, and the card will switch to **Open**.

## Build

```bash
gradle :suite:assembleDebug
```

Output:
`suite/build/outputs/apk/debug/suite-debug.apk`

Minimum Android: 8.0 (API 26). Target SDK: 35.

## Signing

The debug build is signed with Android's debug certificate. A personalized release signature is not configured in this public repository. Do not commit a keystore or its password. Configure signing through private CI secrets or sign the release locally.

## Notes

- Brevent and KillApps remain two separate installed apps; the hub is their single entry point.
- Brevent may require debugging authorization, depending on Android version.
- KillApps may request its own Accessibility permission.
- The hub does not grant those permissions or operate either app in the background.
