# SKVALEX 3.7.3 — APK Resource and Build-Metadata Inventory (Audit 19)

## Verified source artifact

- APK: official 3.7.3 asset already matched by SHA-256.
- Package/version: `org.skvalex.cr`, versionCode `9024000`.
- APK size: 16,021,782 bytes.
- SHA-256: `db6c9a2c45b7420051fe26f336ae6c2cc7d9c8a2633bccf86bb9f230c9052684`.
- ZIP entries: 2,122.
- `resources.arsc`: 3,272,564 bytes.
- Compiled binary manifest: 40,976 bytes.
- DEX: `classes.dex` and `classes2.dex`.

## Resource payload inventory

The APK contains 1,294 entries under `res/`:
- 638 PNG files
- 622 XML files
- 21 TXT files
- 13 WEBP files

Resource filenames are mostly short, opaque identifiers (for example `res/0W.xml`, `res/8M.xml`), rather than original Android resource names. This means filename-based reconstruction of layouts or drawable names is not reliable. The resource table is present in full, but aapt2/apktool-compatible decoding was not available in the analysis environment. The current recovery bundle does not contain a decoded `res/` tree or a readable resource-name map.

The APK also contains these assets:
- `assets/daemon_v24000.zip` (152,300 bytes)
- `assets/PublicSuffixDatabase.list` (132,737 bytes)
- `assets/offline.html` (184 bytes)
- `assets/offlinepng.png` (6,503 bytes)
- baseline profile files under `assets/dexopt/`
- `assets/sepolicy.cfg` (empty in the packaged APK entry inspected)

## Build metadata embedded in the APK

Verified metadata:
- Android Gradle Plugin: `8.13.2`
- Kotlin Gradle plugin: `2.2.21`
- Kotlin metadata reports Gradle build-system version: `8.14.3`
- Kotlin Android target reports Java source/target compatibility: `1.8`
- Embedded VCS revision: `52842a4a435ab488a90ea67da682f6304f359a61`

These are build fingerprints, not a recoverable project. They do not reveal module configuration, dependency declarations, product flavors, signing configuration, generated source, or original Gradle scripts.

## Recovery value and limits

1. The resource payload is present and CRC-valid inside the APK; the main blocker is decoding and restoring resource identifiers/layout relationships, not missing ZIP entries.
2. A resource decoder and DEX decompiler would materially improve source reconstruction. The environment used for this pass did not contain `aapt`, `aapt2`, `apktool`, `jadx`, `baksmali`, `smali`, `apksigner`, or `zipalign`.
3. The embedded AGP/Kotlin/Gradle versions provide a starting compatibility target if a legitimate source project is later recovered, but must not be treated as a complete build recipe.
4. The original developer signing key is not present in the APK. A rebuilt binary cannot retain the original developer signature merely by copying the certificate from the APK.

## Integrity check

The local baseline verifier was run against the original APK:
- SHA-256 matched the recorded baseline.
- ZIP entry and CRC checks passed.
- Both DEX headers and declared file sizes were consistent.
- No APK bytes were changed.

## Status

This is an inventory only. No resource was renamed, decoded into a replacement app, or modified. No APK was rebuilt.
