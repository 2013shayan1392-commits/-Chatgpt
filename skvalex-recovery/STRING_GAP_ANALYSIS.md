# Encrypted string gap analysis

## Decoder behavior

The inspected `Lo/w80;.c(long)` implementation:
1. Derives a chunk index and character offset from the supplied key.
2. Reads the initial encoded character with `String.charAt(offset)`.
3. Reads each remaining encoded character using another `String.charAt(offset + i)`.
4. Does not perform an explicit length check before those reads.

Therefore, if a call requests more characters than remain in its selected chunk, Java string indexing will throw `StringIndexOutOfBoundsException`. This conclusion follows from the bytecode; no runtime test was performed.

## Call site A — FilesSettingsFragment class initializer

The DEX disassembly shows:

```smali
.method <clinit>()V
    const-wide v0, 0xffff84a7fe2d47cb
    invoke-static {v0, v1}, Lo/w80;->c(J)Ljava/lang/String;
    return-void
.end method
```

The returned string is discarded. The string decoder is nevertheless executed during class initialization. The recorded offset is 7003 in the fourth chunk, with 22 UTF-16 code units available for a 110-character request. The read therefore exceeds the available string.

**Static consequence:** when the class initializer is executed against this exact decoded chunk data, it is expected to fail with a string-index exception, and the class will not initialize successfully. This can prevent the Files settings fragment from loading. This has not been confirmed by runtime testing.

Because the decoded return value is discarded, the call may be an obfuscation/validation artifact rather than a required preference value. That is a hypothesis, not a proven original design intent. Do not replace it with an invented string.

## Call sites B–D — listener and dialog paths

Further static evidence narrows the context:
- `Lo/pg2` contains two unresolved decoder calls in its preference-listener method.
- `Lo/rg2` contains one unresolved decoder call in its preference-listener method.
- Both listener classes retain a reference to `org/skvalex/cr/Settings$FilesSettingsFragment`.
- The Files settings fragment constructs these listener objects during its preference setup.
- Nearby successfully recovered preference identifiers include `filename_pattern`, `rename_all_files`, `rename_files_automatically`, `trash_max_age`, `delete_short_recordings`, `max_number_of_files`, `max_total_size_of_files`, `max_age_of_recordings`, `folder`, and `cleanup_uploaded_only`.

These facts place the unresolved calls in the file/recording-management settings UI, not in the audio backend. They do not establish the literal missing values or prove what each dialog expects.

## Evidence table

| Key | Caller | Offset | Available | Requested |
|---|---|---:|---:|---:|
| `0xffff84a7fe2d47cb` | `FilesSettingsFragment.<clinit>` | 7003 | 22 | 110 |
| `0xffff84bdfe2d47cb` | `Lo/pg2.a` | 6981 | 44 | 110 |
| `0xffff84d3fe2d47cb` | `Lo/pg2.a` | 6959 | 66 | 110 |
| `0xffff84f1fe2d47cb` | `Lo/rg2.a` | 6929 | 96 | 110 |

All four calls share the same decoder length/mode/table metadata. The fourth encoded chunk declares 7,025 UTF-16 code units in the DEX string header (a previous Python code-point count of 7,027 was not the Java String.length value); the required tails are absent from this chunk.

## Recovery decision

Exact reconstruction from this APK's encoded chunk is not currently possible. The first call is a candidate for a minimal no-op correction because its return value is discarded, but this is not yet approved: independent evidence is needed to establish that the call has no intended side effect and that removing it preserves initialization behavior.

The other three calls require complete semantic disassembly of their listener methods and independent evidence for the expected strings. Do not invent string literals or substitute generic defaults.

No source code, DEX bytecode, APK signature, or recording backend has been modified.
