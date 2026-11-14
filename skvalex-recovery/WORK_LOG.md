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


## 2026-11-14 — GitHub project wiring

- Added a GitHub Actions workflow for automated unit tests of the recovery tooling.
- Updated the workspace README with exact local verification commands and current recovery boundaries.
- The CI workflow is configured to run on changes to this workspace; its remote run result has not yet been confirmed in this session.
- No application APK build job was added because reconstructed SKVALEX source is not available and the original binary must not be repackaged as if it were a source build.


## 2026-11-14 — Missing-source and official-release audit

- Queried the official upstream GitHub release metadata for tag 3.7.3.
- Confirmed the official asset is `CallRecorder_v.3.7.3.apk`, 16,021,782 bytes, SHA-256 `db6c9a2c45b7420051fe26f336ae6c2cc7d9c8a2633bccf86bb9f230c9052684`.
- Confirmed the local `base(3).apk` has the same byte size and SHA-256. The supplied APK is byte-for-byte the official release asset; the four missing-string edge cases are not caused by local transfer damage.
- Inspected the public upstream repository: the `master` branch has an empty tree; the `updates` branch contains update metadata and changelog JSON, not the app's Gradle/source project.
- Retrieved the official 3.7.3 changelog metadata. It documents a finishing-notification fix and an automatic-renaming battery/zero-byte-file fix, but does not describe the four unresolved decoder outputs.
- Rechecked the DEX string-table header. The final string declares 7,025 UTF-16 code units. A previous 7,027 Python code-point count was not equivalent to Java String.length(); corrected the remaining lengths to 22, 44, 66, and 96 UTF-16 code units.
- Added `MISSING_COMPONENTS_AND_RECOVERY_SOURCES.md` to distinguish located evidence from unavailable source/build/signing/runtime inputs.
- No APK bytes or recording/capture code were modified.
