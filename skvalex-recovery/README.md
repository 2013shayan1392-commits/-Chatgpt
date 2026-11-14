# SKVALEX Recovery Workspace

A separate static-recovery workspace for SKVALEX Call Recorder 3.7.3.

## Preservation rules

- Preserve the original app identity, package structure, UI, preferences, database schema, and working implementation.
- Do not rewrite functioning code for convenience.
- Keep existing Android/device-specific recording backends, privileged integrations, native components, and device profiles unchanged. This project documents them; it does not patch or replace them.
- Consider changes only for demonstrably incomplete, non-capture components, with their original interfaces preserved.
- Keep the pristine APK separate from derived artifacts.
- Do not claim runtime compatibility without device testing.

## Baseline

- Application ID: `org.skvalex.cr`
- Version: `3.7.3`
- versionCode: `9024000`
- minSdk: `24`
- targetSdk: `36`
- Analysis so far: static DEX/resource/native inspection
- Runtime/device testing: not performed

## Signing constraint

Any APK modification invalidates its original signature. Without the original developer's private signing key, a modified build cannot retain the official signature or be installed as an in-place update over the official signed package.

## Status

This is a recovery workspace, not a completed recovered APK. The original APK and third-party binary are not included in this repository. See [RECOVERY_STATUS.md](RECOVERY_STATUS.md).
