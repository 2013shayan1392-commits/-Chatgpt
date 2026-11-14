# Phase 23 — Recovery work started (2026-11-14)

## Scope and preservation rule

This phase covers only the four requested workstreams:
1. Native dependency review.
2. Recovery of source/evidence that is legitimately available.
3. Reconstruction of independent, non-capture UI and settings.
4. Files Settings compatibility analysis and tests.

The original 3.7.3 APK, extracted DEX/native artifacts, existing audit notes, and the Safe Companion implementation are preserved. No original APK, DEX, native library, capture path, signing material, or unrelated project directory is modified by this phase.

## 1. Native dependency review — current findings

The supplied analysis bundle contains ARM32 and ARM64 native libraries, daemon binaries, plugin binaries, and disassembly. The reviewed architecture establishes several broad dependency groups:

- An opaque application-specific native component (`libcallrec_v2.so`) with Java/JNI entry points and unresolved internal dispatch.
- Audio codec and format support libraries (AMR, FLAC, MP3, Opus and related utilities).
- Native audio conversion/resampling functions called from Java wrappers.
- Separate ARM32 and ARM64 daemon/plugin sets; the plugin inventories are not perfectly symmetric.
- OEM configuration data, including a small HiSilicon mixer configuration.

Important limits:
- The exact internal contracts of several JNI methods and indirect native dispatch paths remain unresolved.
- Static presence of a library or OEM configuration does not prove that it is selected or works on a particular phone.
- The HiSilicon configuration does not establish a tested Mate 10 / Kirin 970 / EMUI 10 match.
- No source-compatible replacement can be inferred from filenames, exported symbols, or guessed return values.

Decision: preserve opaque/native components as evidence only. Do not synthesize fake native handles, no-op codec functions, private platform-library calls, privileged services, or hidden audio-capture behavior.

## 2. Source and build recovery — current findings

- The supplied 3.7.3 APK is a verified official binary baseline.
- The public upstream repository/release metadata previously inspected did not provide the original Android source tree, Gradle project, dependency lock state, or original signing key.
- The later 3.7.4–3.7.6 APKs are separate binaries, not missing 3.7.3 source files.
- Existing extracted DEX and native disassembly are useful reverse-engineering evidence, but are not a source-faithful build input.
- The raw DEX inventory is broader than the readable semantic disassembly; it is not accurate to claim every method has been understood.

Decision: continue evidence-based reconstruction of independent components. A separately signed application must use its own package identity and signing key; it cannot be presented as an official update to the original app.

## 3. Independent UI/settings and audio engine

The existing Safe Companion is the starting implementation for:
- Persian RTL settings and appearance modes.
- User-granted SAF audio documents and folder trees.
- Local playback, lifecycle handling, and media notifications.
- Informational SIM display through public Android APIs.

The app has already built and passed its current Android 10 emulator startup smoke test in workflow run 43614774493; Android 16 was still running at the time of this note. These are startup checks, not exhaustive playback or vendor-device tests.

The audio engine in this independent app is limited to playback and processing of files explicitly selected by the user. It is not a call recorder and must not imply that it captures both sides of a phone call. No microphone fallback is to be introduced.

Next UI work should be isolated to Safe Companion files and should not change the original APK or other repository projects.

## 4. Files Settings compatibility — current findings

The four known decoder call sites are still out-of-bounds under the recorded Java UTF-16 length contract (available tails: 22, 44, 66 and 96 code units; each call requests 110).

The call-site roles remain:
- One discarded result in the FilesSettingsFragment class initializer.
- Two consumed values in the folder-picker dialog path.
- One consumed value in the SIM/subscription dialog path.

The proposed namespaced fallback identifiers in audit 20 are compatibility proposals, not recovered original plaintext. Before integrating them into any independent implementation, verify state restoration, fragment-tag consumers, repeated open/close, process recreation, and cancellation behavior. Do not patch the original DEX merely on the basis of guessed strings.

## Work log for this phase

- Re-read the current branch tree and the four relevant audit/project areas.
- Rechecked the current CI run: build, unit tests, lint, artifact upload, and Android 10 startup smoke test passed; Android 16 startup smoke test was in progress.
- Re-inspected the extracted analysis bundle inventory and the JNI/native dependency audit.
- Reconfirmed the boundary between recoverable public Android features and opaque native/privileged functionality.
- No existing application source, original binary, or unrelated project file was changed in this phase.

## Next bounded tasks

1. Extend the static dependency map only where evidence is readable and relevant; leave opaque call-audio internals untouched.
2. Continue public-source and build-metadata checks without substituting later binaries.
3. Improve the independent UI/settings and user-selected-file playback engine in isolated commits.
4. Add tests around SAF permission loss, folder-provider edge cases, fragment/state lifecycle, and Persian RTL presentation.
5. Keep every unsupported feature explicitly unavailable rather than simulating success.
