# Recovery checkpoint — 2026-11-14

## Verified baseline

The supplied `base(3).apk` was rechecked locally.

- SHA-256 matches the recorded reference:
  `db6c9a2c45b7420051fe26f336ae6c2cc7d9c8a2633bccf86bb9f230c9052684`
- ZIP integrity test: passed; no corrupt archive entry reported.
- Required entries are present: `AndroidManifest.xml`, `classes.dex`, `classes2.dex`, `resources.arsc`.
- Both DEX headers have valid magic, expected header size, matching declared file size, and standard endian tag.
- No APK byte was changed.

## Additional string-callsite evidence

The available stage-17 static audit narrows all four unresolved calls to the Files/recording-management preference UI:

- All four use decoder metadata requesting 110 characters with the same mode/flag and table parameter.
- The two `Lo/pg2` listeners and the `Lo/rg2` listener retain references to `FilesSettingsFragment` and are constructed by its preference setup.
- Nearby recoverable preference identifiers include `filename_pattern`, `rename_all_files`, `rename_files_automatically`, `trash_max_age`, `delete_short_recordings`, `max_number_of_files`, `max_total_size_of_files`, `max_age_of_recordings`, `folder`, and `cleanup_uploaded_only`.
- The `FilesSettingsFragment.<clinit>` call discards the decoder result. This makes a no-op correction a plausible *candidate*, but not yet a proven safe patch.
- The other three calls are in listener methods; their literal strings and exact contracts remain unresolved.

The fourth string chunk ends at 7,027 UTF-16 characters. The recorded offsets leave 24, 46, 68, and 98 characters, while each call requests 110. The missing tails are not present in the supplied chunk. No literal replacements are guessed.

## Baseline verifier added

`tools/verify_skvalex_baseline.py` verifies the exact reference hash, ZIP CRC integrity, required entries, and DEX headers. Its tests cover missing-file and wrong-hash rejection. The verifier is intentionally read-only and does not inspect or alter recording functionality.

Local validation result:

- Baseline verifier: PASS.
- Unit tests: 2 passed.
- Original APK remains byte-for-byte unchanged.

## Recovery state

- Static inventory and architecture mapping: substantial, but not a semantic review of every DEX method.
- Exact four missing string values: unresolved.
- Candidate repair: not applied; evidence is insufficient to claim a safe patch.
- Source reconstruction: not yet available from the APK alone.
- APK rebuild/signing: not performed.
- Runtime/device tests: not performed.

The repository currently contains the recovery workspace, findings, and read-only validation tooling. It does not yet contain a rebuilt SKVALEX application or a claim of completed recovery.
