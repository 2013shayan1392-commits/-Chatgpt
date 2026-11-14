# SKVALEX 3.7.3 — Recovery status

## Baseline

Original analysis target: `org.skvalex.cr`, version 3.7.3, versionCode 9024000. The pristine APK must remain byte-for-byte unchanged. No third-party APK binary is included in this public repository.

The original APK has now been revalidated:
- SHA-256: `db6c9a2c45b7420051fe26f336ae6c2cc7d9c8a2633bccf86bb9f230c9052684`
- ZIP CRC test: passed.
- Required entries: AndroidManifest.xml, classes.dex, classes2.dex, resources.arsc.
- Both DEX headers: valid magic, 0x70 header size, consistent declared file size, standard endian tag.
- Read-only verifier and two unit tests were added; both tests pass locally.
- A GitHub Actions workflow was added to run the recovery-tool unit tests on workspace changes; the remote run result has not yet been confirmed.

## Static investigation completed

- Indexed DEX files and extracted application-specific and reachable code.
- Mapped principal call-event, recording-controller, recorder-backend, file-import, encoding, playback, encryption, and integration components.
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

Four unresolved encrypted-string call sites are associated with the Files/recording-management preference UI:
- `FilesSettingsFragment.<clinit>`
- two listener paths in `Lo/pg2`
- one listener path in `Lo/rg2`

Additional evidence narrows the context:
- Both listener classes retain a reference to FilesSettingsFragment and are instantiated by its preference setup.
- Nearby recoverable preference identifiers include `filename_pattern`, `rename_all_files`, `rename_files_automatically`, `trash_max_age`, `delete_short_recordings`, `max_number_of_files`, `max_total_size_of_files`, `max_age_of_recordings`, `folder`, and `cleanup_uploaded_only`.
- All four decoder calls request 110 characters and share the same mode/flag and table parameter.
- The fourth encoded chunk contains 7,027 UTF-16 characters. The four offsets leave 22, 44, 66, and 96 UTF-16 code units respectively.
- The FilesSettingsFragment class initializer discards the returned string. A no-op correction is plausible, but its original intent and safety are not yet proven.

No replacement should be made until exact expected values and call contracts are established independently. Guessed strings must not be silently substituted. See [STRING_GAP_ANALYSIS.md](STRING_GAP_ANALYSIS.md) and [RECOVERY_CHECKPOINT_2026-11-14.md](RECOVERY_CHECKPOINT_2026-11-14.md).

## Signing constraint

Changing any APK byte invalidates its original signature. Without the original developer's signing key, a modified APK cannot retain the official signature and generally cannot update the official installed package. A derived build would require a distinct signing identity. This project does not attempt to forge or imitate the developer's signature.

## Next steps

1. Find an independently verifiable source for the four missing string values, or establish that a call is safely removable without changing its contract.
2. Complete semantic disassembly of the two listener classes and their direct callers.
3. Audit candidate non-capture changes for callers, resource references, and behavioral contracts.
4. Produce a minimal patch proposal and before/after diff before changing code.
5. Keep all capture/privileged/native/device-specific code byte-for-byte untouched.
6. Build and validate only from source or legally modifiable materials.
7. Perform runtime testing separately and report device/model/OS details.

## Completion state

- Static investigation: substantial, not exhaustive semantic review of every method.
- Baseline integrity: reverified.
- Read-only validation tooling: added and tested locally.
- GitHub Actions test workflow: configured; remote execution not yet confirmed.
- Four missing string values: unresolved.
- Source-level reconstruction: not started.
- APK patch: not started.
- Build/install/runtime verification: not started.


## Upstream source audit — 2026-11-14

- The official upstream 3.7.3 release asset metadata reports the exact same SHA-256 and byte size as the supplied APK. The local APK is therefore the official release binary byte-for-byte.
- The public upstream `master` tree is empty. The `updates` branch contains only update metadata and changelog files, not the Android source/Gradle project.
- The official 3.7.3 changelog mentions the finishing-notification and auto-rename battery/zero-byte-file fixes; it does not explain the unresolved string calls.
- The final DEX string chunk declares 7,025 UTF-16 code units. Corrected available lengths for the four failing offsets are 22, 44, 66, and 96 UTF-16 code units (not the earlier Python code-point counts).
- See [MISSING_COMPONENTS_AND_RECOVERY_SOURCES.md](MISSING_COMPONENTS_AND_RECOVERY_SOURCES.md) for source endpoints and the complete gap inventory.


## Upstream version scan and CI confirmation — 2026-11-14

- Scanned official releases 3.7.4, 3.7.5, and 3.7.6. They are separate APK binaries with separate hashes; none supplies the missing source/build project.
- The official public master tree remains empty; updates remains metadata/changelog-only.
- Do not transplant later-version binary fragments into 3.7.3 without source-level equivalence evidence.
- GitHub Actions run 43605120585 completed successfully. Job verify-tooling and the recovery-tool unit-test step both report success.
- Canonical missing-string lengths remain 22, 44, 66, and 96 UTF-16 code units. The older stage-19 narrative's 24/46/68/98 values are superseded.
- No application source patch or APK build was started: source/build inputs and official signing authorization are unavailable. See [UPSTREAM_RELEASE_SCAN.md](UPSTREAM_RELEASE_SCAN.md).
