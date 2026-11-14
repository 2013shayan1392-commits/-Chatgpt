# SKVALEX 3.7.3 — Native Dependency Recovery Boundaries (Audit 21)

## Scope

This pass separates recoverable non-capture UI defects from native and obfuscated components whose contracts are not sufficiently known. It is a static audit; it does not implement or explain hidden audio capture, privileged access, or recording-permission bypasses.

## Native components with established roles

| Component | Evidence from the DEX/ELF audit | Recovery decision |
|---|---|---|
| `Native.r(...)` and `Lo/ph` | Native call returns a handle consumed by a conditional AudioRecord construction path | Preserve the original implementation; do not synthesize a replacement handle or re-create the hidden construction path |
| AMR JNI methods | Called by AMR decode helpers | Preserve; a fake decoder would corrupt playback and file compatibility |
| `srcInit/srcProcess/srcRelease` | Called by PCM/sample-rate conversion paths | Preserve; a stub would produce invalid or silent output |
| `libcallrec_v2.so` dynamic loader | ARM64 path decodes `libmedia.so` names and calls `dlopen` | Do not guess symbol mappings or replace with private platform-library access |
| `Native.q/v/z/l` | Direct Java call sites and native state/dispatch behavior exist; internal contracts remain incomplete | Preserve unchanged; no safe behavioral substitute is justified by current evidence |

## What can be substituted safely

1. **Non-capture settings identifiers:** use the deterministic fragment-tag fallbacks documented in `FILES_SETTINGS_COMPATIBILITY_FALLBACKS_AUDIT_20.md`.
2. **Unavailable optional UI integrations:** present an explicit unavailable/unsupported state rather than fabricating success or returning fake native handles.
3. **Unknown diagnostic-only features:** keep them disabled until their data inputs, output location, and lifecycle are understood.
4. **Unknown recording backends:** no replacement is supplied. A substitute would alter the audio-capture system and could misrepresent whether recording is functioning.

## Why the native pieces are not interchangeable

The JNI methods are not independent cosmetic helpers. The inspected call graph connects them to AudioRecord handle management, encoding/decoding, and sample-rate conversion. Replacing them with no-op methods or arbitrary return values could cause crashes, empty files, corrupted playback, or false status reporting. Reimplementing their hidden call-audio path would also require behavior outside the safe scope of this recovery.

## Build conclusion

The official binary is intact, but the original application source, Gradle project, dependency lock/configuration, and developer signing key are absent. Static DEX/resource inventories do not provide enough information to claim a faithful source rebuild. A generated test key would create a different signing identity and would not produce an official update.

No APK packaging workflow is retained in this workspace. The repository remains a static-recovery and validation project. It does not produce a functioning call-recorder APK.

## Completion classification

- Four string call sites: compatible UI-level fallback contracts identified; not integrated into the APK.
- Non-capture settings dialogs: roles narrowed to file/folder picker and SIM/subscription selector.
- Native capture/codec components: mapped at architectural level, intentionally not replaced.
- Obfuscated JNI dispatch: partially mapped; unresolved methods remain unchanged.
- Source-faithful build: not possible from current inputs.
- Runtime validation: not performed.

These boundaries prevent a guessed replacement from silently changing the application's recording behavior or falsely claiming device compatibility.