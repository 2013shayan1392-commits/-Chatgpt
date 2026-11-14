# Recovery work log

## 2026-11-14 — Workspace initialization

- Created branch: `skvalex-recovery` from `main`.
- Added `skvalex-recovery/README.md` and `skvalex-recovery/RECOVERY_STATUS.md`.
- Recorded the preservation boundary: existing capture, privileged, native, and device-specific implementation remains untouched.
- Kept the original APK and analysis bundle out of the public repository.
- No APK bytes or application code have been modified.

## Current investigation checkpoint

The first unresolved area is the set of four encrypted-string call sites associated with file settings and dialog initialization. The current static evidence identifies the surrounding classes and preference keys, but does not prove all four original string values. Recovery must not replace unknown values with guesses.

## Next checkpoint

Inspect the exact bytecode around each call site and all resource/metadata references that may constrain the missing strings. Record confidence and evidence for each candidate. Only exact, independently supported values qualify for a minimal repair.
