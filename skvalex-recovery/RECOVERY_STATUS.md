# SKVALEX 3.7.3 — Recovery status

## Baseline

Original analysis target: `org.skvalex.cr`, version 3.7.3, versionCode 9024000. The pristine APK must remain byte-for-byte unchanged. No third-party APK binary is included in this public repository.

## Static investigation completed

- Indexed DEX files and extracted application-specific and reachable code.
- Mapped principal call-event, recording-controller, recorder-backend, file-import, encoding, playback, encryption, and integration components.
- Identified four unresolved encrypted-string call sites associated with file-settings UI/dialog initialization.
- Inspected manifest permissions, exported components, providers, database schema references, backup rules, native libraries, ABI variants, and device-profile resources.
- Located HiSilicon profile resources, without establishing that they work on a particular Huawei model.
- Recorded static-analysis limitations: reflection, dynamic dispatch, callbacks, JNI/native behavior, and runtime device behavior are not fully established by DEX call-graph inspection.

## Must remain untouched

These areas are explicitly out of scope for modification:

- Android/device-specific recording backends and profiles
- Native libraries and JNI interfaces
- Existing privileged/system integration code
- Existing root/service/socket integration code
- Audio capture selection and routing implementation

This is a preservation requirement, not a claim that those paths work on every device.

## First candidate recovery area

Four unresolved encrypted-string call sites are associated with:
- `FilesSettingsFragment` initialization
- chooser dialog initialization
- filename-pattern dialog initialization

Surrounding preference names and dialog-related classes are partly recoverable. No replacement should be made until exact expected values and call contracts are established independently. Guessed strings must not be silently substituted.

## Signing constraint

Changing any APK byte invalidates its original signature. Without the original developer's signing key, a modified APK cannot retain the official signature and generally cannot update the official installed package. A derived build would require a distinct signing identity. This project does not attempt to forge or imitate the developer's signature.

## Next steps

1. Re-verify the original APK hash and archive integrity.
2. Trace the four unresolved string call sites through available DEX/resource evidence.
3. Establish exact values, or document why they cannot be recovered.
4. Audit candidate non-capture changes for callers, resource references, and behavioral contracts.
5. Produce a minimal patch proposal and before/after diff before changing code.
6. Keep all capture/privileged/native/device-specific code byte-for-byte untouched.
7. Build and validate only from source or legally modifiable materials.
8. Perform runtime testing separately and report device/model/OS details.

## Completion state

- Static investigation: substantial, not exhaustive semantic review of every method.
- Four missing string values: unresolved.
- Source-level reconstruction: not started.
- APK patch: not started.
- Build/install/runtime verification: not started.
