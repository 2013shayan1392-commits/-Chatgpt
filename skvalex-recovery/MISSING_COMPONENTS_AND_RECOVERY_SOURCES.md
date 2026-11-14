# Missing components and recovery-source audit

Date: 2026-11-14

This document records what is still missing, what has been located, and which sources can legitimately fill each gap. It does not modify the original APK or any recording/capture implementation.

## 1. Official 3.7.3 binary — located and matched

The official upstream release is:

- Repository: https://github.com/skvalex/callrecorder
- Release tag: https://github.com/skvalex/callrecorder/releases/tag/3.7.3
- Asset: `CallRecorder_v.3.7.3.apk`
- Asset size: 16,021,782 bytes
- Upstream SHA-256: `db6c9a2c45b7420051fe26f336ae6c2cc7d9c8a2633bccf86bb9f230c9052684`

The locally supplied `base(3).apk` has exactly the same SHA-256 and byte size. This proves the provided APK is byte-for-byte the official 3.7.3 release asset. A second mirror of this exact release is therefore not expected to restore the missing bytes: the incomplete string-table edge case is present in the official published binary too.

Official release metadata API:
https://api.github.com/repos/skvalex/callrecorder/releases/tags/3.7.3

## 2. Upstream source project — not present in the public repository

The public upstream repository's `master` branch has an empty tree (the branch commit reports zero changed files and the contents endpoint returns an empty list). Its `updates` branch contains update metadata, `latest.json`, `magisk.json`, and changelog JSON files; it does not contain the Android application source, Gradle project, wrapper, resource source tree, or build scripts.

Relevant upstream endpoints:
- https://api.github.com/repos/skvalex/callrecorder/contents?ref=master
- https://api.github.com/repos/skvalex/callrecorder/contents?ref=updates
- https://api.github.com/repos/skvalex/callrecorder/commits/17121c1982077fc019af7e96b91901483abfe801

This means a source-faithful rebuild cannot currently be produced from the public upstream repository. It does not prove that the developer has no private source repository or private build system.

## 3. Four unresolved encrypted-string call sites

The APK's string decoder uses four string-table chunks. The DEX header declares the final chunk as 7,025 UTF-16 code units. A previous analysis used Python's decoded code-point count (7,027), which is not the same as Java `String.length()`. The corrected remaining lengths are:

| Call site | Start offset in final chunk | Java UTF-16 units remaining | Requested |
|---|---:|---:|---:|
| `FilesSettingsFragment.<clinit>` | 7003 | 22 | 110 |
| `Lo/pg2.a(Preference)` — call 1 | 6981 | 44 | 110 |
| `Lo/pg2.a(Preference)` — call 2 | 6959 | 66 | 110 |
| `Lo/rg2.a(Preference)` | 6929 | 96 | 110 |

The raw DEX confirms the three listener calls are inside their preference-click handlers. Both helper classes are instantiated by the Files settings fragment. This localizes the defect to file/recording-management settings UI; it does not reveal the missing literal values.

The official 3.7.3 asset hash matches the supplied APK, so this is not a local file-transfer corruption. The exact tails are not present in the official binary's final string chunk. No values are guessed.

## 4. Additional reconstruction gaps

| Missing item | What exists | What is still needed |
|---|---|---|
| Original application source | DEX, resources, Manifest, native binaries, static inventories | Original source tree or an authorized source archive |
| Full readable DEX decompilation | Complete raw DEX method/code-unit inventory; selected app/core disassembly | Reliable full DEX decompiler output with exception tables, annotations, debug metadata, and method references |
| Full semantic review | Primary app architecture and selected dependency paths mapped | Class-by-class analysis of the remaining obfuscated/helper methods |
| Native source/symbols | ARM32/ARM64 binaries and disassembly; several binaries stripped | Matching native source, symbols, or reproducible debug builds |
| Build configuration | APK manifest, resource tables, compiled XML, packaged dependencies | Original Gradle files, dependency versions, generated-source configuration, build variants |
| Official signing | Original APK certificate can be inspected | Developer's private signing key and authorization; unavailable to us |
| Runtime verification | Static inspection only | Controlled device/emulator tests with logs and consent-based, non-sensitive test data |
| Four missing string values | Call-site keys, exact offsets, callers, neighboring preference identifiers | Original source, an independently verified same-code string table, or authoritative developer confirmation |

## 5. Version 3.7.3 official changelog

The upstream `9024000` changelog records two changes:
- fixed a stuck “finishing recording” notification;
- fixed battery drain and zero-byte files caused by automatic renaming when multiple contacts shared a phone number.

This is useful context for the file-management UI, but it does not document or explain the four missing decoder outputs.

Source:
https://raw.githubusercontent.com/skvalex/callrecorder/updates/changelog/9024000.json

## 6. What can be recovered safely from current materials

1. Continue non-capture static analysis of file-management settings, UI resources, preference definitions, and their callers.
2. Verify every decoded preference key against compiled resource XML and the APK's resource table.
3. Compare the relevant UI/resource metadata across official release artifacts only where there is a clear reason and without modifying any recording implementation.
4. Preserve the original APK and record every proposed change as a minimal, reviewable diff.
5. Do not claim a source rebuild or complete APK until original build inputs, signing constraints, and runtime verification are resolved.

## 7. Current conclusion

The exact official APK is available and verified. The public upstream source project is not. The four missing strings are a defect/edge case in the official binary rather than damage introduced by the local copy. A full source-faithful reconstruction is blocked by missing source/build inputs and the original signing key. Static recovery of non-capture UI behavior can continue, but a complete official-equivalent APK cannot honestly be promised from the current materials alone.

All capture, privileged, native, and device-specific recording code remains out of scope and unmodified.
