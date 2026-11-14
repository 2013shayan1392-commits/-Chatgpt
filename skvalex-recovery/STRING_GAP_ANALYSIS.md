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

The returned string is discarded. The string decoder is nevertheless executed during class initialization. The recorded offset is 7003 in the fourth chunk, with 24 characters available for a 110-character request. The read therefore exceeds the available string.

**Static consequence:** when the class initializer is executed against this exact decoded chunk data, it is expected to fail with a string-index exception, and the class will not initialize successfully. This can prevent the Files settings fragment from loading. This has not been confirmed by runtime testing.

Because the decoded return value is discarded, the call may be an obfuscation/validation artifact rather than a required preference value. That is a hypothesis, not a proven original design intent. Do not replace it with an invented string.

## Call sites B–D — chooser and filename-pattern paths

The remaining unresolved calls are associated with:
- two chooser paths in `Lo/pg2`
- one filename-pattern path in `Lo/rg2`

The current disassembly inventory establishes their keys and truncated chunk offsets, but does not include complete semantic disassembly for these two helper classes. Their exact call timing and expected outputs remain unproven.

## Evidence table

| Key | Caller | Offset | Available | Requested |
|---|---|---:|---:|---:|
| `0xffff84a7fe2d47cb` | `FilesSettingsFragment.<clinit>` | 7003 | 24 | 110 |
| `0xffff84bdfe2d47cb` | `Lo/pg2.a` | 6981 | 46 | 110 |
| `0xffff84d3fe2d47cb` | `Lo/pg2.a` | 6959 | 68 | 110 |
| `0xffff84f1fe2d47cb` | `Lo/rg2.a` | 6929 | 98 | 110 |

## Recovery decision

No encoded tail is present in the available fourth chunk, so exact reconstruction from this APK alone is not currently possible. The first call is a strong candidate for a minimal no-op correction because its return value is discarded, but that change must remain a proposal until corroborated against an unmodified official copy of the same build or source-level evidence. The other three calls require complete caller disassembly and evidence about their expected strings.

No source code, DEX bytecode, APK signature, or recording backend has been modified.
