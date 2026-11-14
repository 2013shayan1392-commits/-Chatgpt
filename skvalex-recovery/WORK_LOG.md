# Recovery work log

## 2026-11-14 — Workspace initialization

- Created branch: `skvalex-recovery` from `main`.
- Added recovery scope, status, string-gap analysis, and work-log documents.
- Recorded the preservation boundary: existing capture, privileged, native, and device-specific implementation remains untouched.
- Kept the original APK and third-party binary out of the public repository.
- No APK bytes or application code have been modified.

## 2026-11-14 — Baseline verification and string-callsite review

- Re-materialized the supplied `base(3).apk` and stage-17 analysis bundle from the Library.
- Recomputed the APK SHA-256 and matched the recorded baseline exactly.
- Ran ZIP CRC validation; all archive entries passed.
- Checked required APK entries and both DEX headers, including magic, declared size, header size, and endian tag.
- Added `tools/verify_skvalex_baseline.py`, a read-only baseline integrity checker.
- Added unit tests for missing-file and wrong-hash rejection; both passed.
- Rechecked the DEX decoder: it reads encoded characters through String.charAt without a prior length guard.
- Narrowed the four unresolved calls to the Files/recording-management preference UI. Both listener classes point back to FilesSettingsFragment and are constructed during its preference setup.
- Confirmed the class-initializer call discards the returned string. A no-op remains a hypothesis, not an approved patch.
- No APK bytes, DEX instructions, native libraries, or recording paths were modified.

## Current checkpoint

The repository now contains a recovery workspace, evidence notes, and a tested read-only integrity tool. It does not yet contain reconstructed application source or a rebuilt APK. The four string values remain unresolved, and no patch is justified until independent evidence establishes the expected behavior.

## Next checkpoint

Continue with non-capture static analysis of the two listener classes, resource references, and caller contracts. Do not alter recording, privileged, native, or device-specific code.
