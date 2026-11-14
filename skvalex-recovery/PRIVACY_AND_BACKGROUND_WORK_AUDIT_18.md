# SKVALEX Call Recorder 3.7.3 — Background Work & Data-Flow Follow-up (Audit 18)

## Scope and method

This pass revisits the already extracted DEX instruction listing for the background workers and user-facing call-confirmation/logging services. It is static analysis only. No service was executed, no network request was made, and no recording/capture path or Android permission boundary was modified.

Source artifact remains the verified official 3.7.3 APK:
- Package: `org.skvalex.cr`
- Version: `3.7.3` / versionCode `9024000`
- SHA-256: `db6c9a2c45b7420051fe26f336ae6c2cc7d9c8a2633bccf86bb9f230c9052684`

## 1. MainService background workers

### ContactSyncWork

The WorkManager worker checks a global enum/state flag (`Lo/dj0.b.a`). If that flag is true, it returns `Result.success()` without scheduling the delegated operation. Otherwise it creates `MainService$ContactSyncWork$a`, passes it to `Lo/dj0.n(...)`, and returns success.

The worker itself therefore acts as a gate/dispatcher; the substantive work is delegated into the large `Lo/dj0` helper. The helper's raw DEX inventory contains numerous database/contact-shaped methods and ContentValues parameters, but the present readable disassembly does not establish the exact fields synchronized, direction of synchronization, remote destination, or whether call-record metadata is included. It would be inaccurate to claim a complete contact-data flow from this evidence.

### UploadWork

The worker obtains the application-scoped `Lo/gs` state object, checks `Lo/gs.g()`, and conditionally invokes `Lo/gs.r()`. It then returns WorkManager success. The worker is a small dispatcher, not the upload implementation.

The DEX string pool contains provider SDK and upload-related strings (including OAuth/token and upload-session terminology). Those strings occur in a large APK containing third-party SDKs and do not, by themselves, prove which provider is selected by the app or what user data is transmitted. The concrete file-selection → provider adapter → request-body chain remains unverified.

## 2. User-facing CallConfirm screen

The activity requires a `phone_number` intent extra and finishes when it is absent. Its layout binds a checkbox and a containing layout associated with the “record this call” choice. In a specific internal mode (value 5), both the layout and checkbox are disabled. Otherwise the activity dispatches an asynchronous helper (`Lo/ql`) with the phone number and activity context.

This confirms the presence of a per-call user-facing preference/control. It does **not** prove that the checkbox directly starts capture, nor does the excerpt establish the precise persistence/update semantics. Those effects are delegated and remain unresolved.

## 3. LogService

The service recognizes `RECORD_LOG`, `STOP_RECORD_LOG`, and `CLEAR_LOG` actions.
- `RECORD_LOG` starts foreground service state, constructs a worker/controller pair, stores it in application-scoped state, and starts a thread.
- `STOP_RECORD_LOG` updates controller state and has a visible notification action that sends the stop intent.
- `CLEAR_LOG` dispatches a separate thread task.
- The notification is visibly labeled for log recording and includes a Stop action.

The inspected service establishes lifecycle and notification behavior only. It does not identify the exact diagnostic data collected, its file destination, retention period, or whether any log content is uploaded. The worker/controller and clear task must be traced before making those claims.

## 4. Backup policy cross-check

The compiled backup rules explicitly include the app's named shared-preferences XML in full backup and Android cloud backup. The inspected rules do not explicitly include the SQLite database or audio files. This is not proof that recordings are excluded from every device-transfer or OEM backup mechanism; the device-transfer policy was not fully specified in the inspected XML.

## 5. Data-flow conclusions

| Area | Confirmed | Still unknown |
|---|---|---|
| Contact sync | WorkManager gate and delegation into `Lo/dj0` | Exact fields, destination, direction, trigger cadence |
| Cloud upload | Worker dispatches through `Lo/gs`; upload-related SDK strings exist | Active provider, file selection, metadata/body, credentials path |
| Call confirmation | Number extra and user-facing record-this-call checkbox exist | Exact callback side effects and preference persistence |
| Diagnostic logs | Foreground notification, start/stop/clear actions, background controller | Collected categories, local path, retention, network transfer |
| Backup | Shared-preferences file explicitly included | Device-transfer/OEM backup behavior beyond inspected rules |

## 6. Recovery implications

These findings do not justify patching the workers or changing their behavior. Any reconstruction should preserve the original worker entry points and state transitions. Before modifying a data-related feature, the exact downstream helper and its callers must be understood; otherwise a seemingly harmless change could disable sync, lose metadata, or change privacy behavior.

The next safe static-analysis pass should prioritize:
1. Recovering readable instruction-level output for the delegated contact-sync and cloud-state helpers, without exposing tokens or implementing any network workflow.
2. Identifying the log worker's data categories and local output path.
3. Mapping the CallConfirm callback to the preference/database update, without changing any recording/capture backend.
4. Building a resource/layout inventory for the user-facing settings and confirmation screens.

## 7. Limitations

- No runtime behavior or network traffic was tested.
- The selected readable disassembly is not a semantic decompilation of all 40,979 code-bearing methods.
- The obfuscated helper classes remain only partially understood.
- No source project, Gradle build configuration, or original signing key was recovered.
- No APK was rebuilt or modified in this audit.

**Safety boundary:** This is a static data-flow and UI audit. It does not provide instructions to bypass Android audio permissions, acquire privileged call audio, evade recording indicators, or defeat consent/security checks.
