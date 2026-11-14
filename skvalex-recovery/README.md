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
- Baseline SHA-256: `db6c9a2c45b7420051fe26f336ae6c2cc7d9c8a2633bccf86bb9f230c9052684`
- Analysis so far: static DEX/resource/native inspection
- Runtime/device testing: not performed

## Recovery diagnostics

The UTF-16 string-gap contract checker records the four known 3.7.3 decoder call sites without inventing missing values:

```bash
python3 skvalex-recovery/tools/check_string_gap_contract.py
```

Its unit tests run with the workspace test suite. This checker is diagnostic tooling only; it does not patch the APK.

## Read-only baseline verification

The original APK is intentionally not stored in this public repository. After obtaining the same original file, run:

```bash
python3 skvalex-recovery/tools/verify_skvalex_baseline.py /path/to/base(3).apk
python3 -m unittest discover -s skvalex-recovery/tests -v
```

The verifier checks the recorded SHA-256, ZIP CRC integrity, required archive entries, and both DEX headers. It never modifies the APK.

## Current findings

- Four encrypted-string call sites remain unresolved in the Files/recording-management settings UI.
- The first call discards its return value during class initialization; removing it is only a candidate hypothesis, not an approved patch.
- The other three calls are in preference listeners; their exact expected strings remain unknown.
- See [RECOVERY_STATUS.md](RECOVERY_STATUS.md), [STRING_GAP_ANALYSIS.md](STRING_GAP_ANALYSIS.md), [RECOVERY_CHECKPOINT_2026-11-14.md](RECOVERY_CHECKPOINT_2026-11-14.md), and [UPSTREAM_RELEASE_SCAN.md](UPSTREAM_RELEASE_SCAN.md).

## Signing constraint

Any APK modification invalidates its original signing certificate. Without the developer's private signing key, a modified build cannot retain the official signature or be installed as an in-place update over the official signed package.

## Status

This is an active recovery workspace, not a completed recovered APK. The repository contains analysis notes and read-only validation tooling; it does not yet contain reconstructed application source or a rebuilt SKVALEX APK.
