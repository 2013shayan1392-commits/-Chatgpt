# SKVALEX 3.7.3 — Files Settings String Fallback Design (Audit 20)

## Purpose

This audit narrows the four unresolved decoder calls to their actual call contracts and defines deterministic compatibility fallbacks for the non-capture Files settings UI. These are replacement identifiers, **not claims about the original plaintext strings**.

The pristine APK remains unchanged. No call-audio, privileged, native, or device-specific recording code is altered.

## Confirmed call-site behavior

### A. `FilesSettingsFragment.<clinit>` — offset 7003

The method invokes `Lo/w80.c(long)` and immediately returns. There is no `move-result`, field write, method argument, or other use of the returned string.

The decoder's inspected implementation is deterministic string reconstruction: arithmetic/state mixing, reads from the static string table using `String.charAt`, construction of a `char[]`, and return of a new `String`. The associated `Lo/x40.b(long)` helper is a deterministic integer transform. No mutation of application state is visible in this call path.

**Compatibility fallback:** remove only this unused decoder invocation from the class initializer. This avoids an exception caused by the short tail without changing any observable result from the discarded return value. This is the strongest of the four proposed fallbacks, subject to a runtime class-initialization test after a legitimate build.

### B–C. `Lo/pg2.a(Preference)` — offsets 6981 and 6959

The listener:
1. Constructs a `Lo/ql0` dialog fragment.
2. Creates a `Bundle`.
3. Writes the integer under the literal key `fragment_id`.
4. Writes the first decoded string under the literal key `fragment_tag`.
5. Attaches the bundle to the fragment.
6. Obtains the fragment manager/transaction wrapper.
7. Passes the second decoded string into `Lo/ia0.o0(fragment, tag)`.

The `Lo/ia0.o0` implementation forwards the supplied string to `Lo/ae.d(...)` as the fragment transaction tag. Therefore these two strings are not arbitrary user-visible labels or file names: they are stable fragment identifiers used for dialog state/transaction management.

**Compatibility fallback identifiers:**
- Bundle `fragment_tag`: `skvalex.files.folder-picker`
- Fragment transaction tag: `skvalex.files.folder-picker.dialog`

These values are deliberately namespaced and deterministic. They preserve the required non-empty string contract and keep the two identifiers distinct. They do not claim to recover the original strings.

### D. `Lo/rg2.a(Preference)` — offset 6929

The listener constructs a `Lo/mj0` dialog fragment and passes the decoded string to the same `Lo/ia0.o0(fragment, tag)` path, which forwards it as the fragment transaction tag.

**Compatibility fallback identifier:**
- Fragment transaction tag: `skvalex.files.preference-dialog`

This is a stable, namespaced fragment tag. Its exact semantic label is intentionally generic because the original literal and user-visible dialog purpose are not recoverable from the missing tail alone.

## Replacement contract

| Original call | Confirmed role | Proposed compatibility behavior |
|---|---|---|
| `FilesSettingsFragment.<clinit>` offset 7003 | Unused return value | Remove only the dead decoder call |
| `Lo/pg2.a` offset 6981 | Bundle `fragment_tag` value for `Lo/ql0` | `skvalex.files.folder-picker` |
| `Lo/pg2.a` offset 6959 | Fragment transaction tag for `Lo/ql0` | `skvalex.files.folder-picker.dialog` |
| `Lo/rg2.a` offset 6929 | Fragment transaction tag for `Lo/mj0` | `skvalex.files.preference-dialog` |

## Why stable identifiers are a reasonable substitute

Android fragment transaction tags are identity strings, not resource IDs and not localized text. The call path forwards the supplied tag to the fragment transaction wrapper. A deterministic, unique string is therefore a compatible replacement at the API-contract level.

The `fragment_tag` Bundle value is likewise passed as a string argument to the fragment's own state/configuration path. A stable namespaced value satisfies its string contract. Whether any code compares that value against a hard-coded legacy tag elsewhere must still be checked before integrating the fallback.

## Required validation before integration

1. Search all DEX code for comparisons against the proposed or original fragment-tag values; none of the proposed values exist in the pristine APK, so the audit must inspect consumers of the `fragment_tag` Bundle key.
2. Verify `Lo/ql0` reads `fragment_tag` only as a tag/configuration value and does not parse or display it.
3. Verify no saved-fragment restoration path depends on the original opaque tag.
4. Run class initialization and open/close/reopen tests for both dialogs on a test build.
5. Verify process recreation and activity recreation restore the dialogs without duplicate fragments.
6. Confirm the Files settings screen remains usable when these dialogs are opened repeatedly.

## Integration status

- The three consumed values now have explicit, deterministic compatibility replacements at the identified API boundary.
- The discarded class-initializer call has a minimal no-op replacement proposal.
- The original values are still unknown and must not be presented as recovered.
- No DEX patch has been applied.
- No APK has been rebuilt or signed.
- No recording/capture implementation has been changed.

## Build and signing gate

A real derived APK still requires a DEX/resource editing toolchain or reconstructed source project, a complete build configuration, dependency resolution, and a signing key controlled by the person producing the derived build. The official developer signing key is not present. A derived APK can only be signed under a separate identity and cannot be represented as an official signed update.

This compatibility design is limited to the non-capture Files settings UI.