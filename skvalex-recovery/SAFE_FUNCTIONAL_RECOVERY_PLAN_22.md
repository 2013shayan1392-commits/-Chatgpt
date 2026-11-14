# SKVALEX 3.7.3 — Safe Functional Recovery Plan (22)

## Scope

This pass separates components that can be reconstructed as ordinary Android application functionality from components whose behavior depends on hidden call-audio capture, privileged services, or opaque native code.

The goal is behavioral correctness, not visual imitation that reports success without performing the operation.

## Recoverable, non-capture areas

| Area | Evidence in 3.7.3 analysis | Safe implementation target | Required verification |
|---|---|---|---|
| Files/folder picker | `Lo/ql0` uses file/folder UI, RecyclerView, toolbar and selection callbacks | SAF-based picker using `ACTION_OPEN_DOCUMENT_TREE` / `ACTION_OPEN_DOCUMENT`; persist URI grants; display selected URI and permission state | Select/cancel/back; process recreation; revoked grant; removable SD card; Unicode and long paths |
| SIM selection dialog | `Lo/mj0` queries SubscriptionManager and active subscription information | Read-only SIM list using public SubscriptionManager APIs where permission and OS policy allow; show unavailable/permission-denied state | No SIM, one SIM, dual SIM, inactive SIM, permission denied, subscription changes |
| Playback controls | PlaybackService exposes PLAY/PAUSE/STOP actions for existing files | Media3/MediaPlayer playback of user-selected local audio files only; foreground playback notification with visible controls | Play/pause/stop; audio focus; headset unplug; background; screen lock; corrupt/unsupported file |
| Local file library | Storage/database audits show local recording/file metadata paths | Read-only library browser for files the user explicitly selects or grants access to; metadata display and safe rename/share/delete only after explicit confirmation | Missing file, revoked URI, duplicate names, storage removal, permission loss |
| Settings UI | FilesSettingsFragment and preference fragments are present | Recreate settings screens with typed defaults, validation, summaries and explicit unsupported states | Fresh install, missing preference, invalid value, rotation, process death, RTL/Persian locale |
| Notifications for playback | PlaybackService updates foreground notification title/summary and actions | Android-supported media notification for playback only | Android 10–16 behavior, notification permission, channel disabled, action delivery |

## Components deliberately not replaced

- Call-audio capture, AudioRecord/MediaRecorder call paths, hidden audio sources, native recording backends and JNI capture methods.
- Privileged Binder/socket/daemon paths, root helpers, SELinux policy changes, process-memory inspection, or dynamic loading used to access restricted audio.
- Any implementation that fabricates a successful recording state while no valid user-authorized source exists.

A UI control related to these capabilities must show a truthful unsupported/unavailable state. It must not silently fall back to microphone capture or imply that both call participants are being recorded.

## Compatibility rules

1. Prefer public Android APIs and Storage Access Framework over direct filesystem paths.
2. Treat Android 10 (API 29) as a key compatibility target, while guarding newer APIs by SDK level.
3. Never assume that READ/WRITE storage permissions grant access to arbitrary shared-storage paths on modern Android.
4. Use persisted content URI permissions for folders selected by the user; handle SecurityException and revoked grants.
5. For SIM information, query public subscription APIs and tolerate restricted/empty results. Do not infer phone-call routing or capture capability from SIM visibility.
6. Use explicit foreground-service types and notification behavior only for legitimate media playback, not to disguise recording.
7. Keep UI state separate from backend capability state. A disabled backend must not be represented as active.
8. Preserve RTL layout and test Persian digits, long translated labels, font scaling and dark/light themes.

## Release gate

No installable APK is claimed by this document. A valid release requires a source project, reproducible dependency versions, build configuration, test coverage and signing provenance. The original 3.7.3 APK is a verified reference artifact, not a source tree.

## Test matrix

- OS: Android 10, 13, 14, 15 and 16 where available.
- Device families: Huawei/EMUI, Xiaomi/HyperOS and Samsung/One UI; vendor-specific behavior must be observed, not guessed.
- Storage: internal storage, user-granted SD-card tree, revoked permission, unavailable volume.
- Locale/UI: Persian RTL, English LTR, dark/light, large font.
- Lifecycle: rotate, background/foreground, process recreation, force-stop/relaunch.
- Failure handling: missing file, malformed URI, denied permission, unavailable SIM API, unsupported media codec.

## Evidence and confidence

- PlaybackService action routing and notification behavior are supported by the existing static service audit; no runtime test has been performed.
- `Lo/ql0` file/folder picker and `Lo/mj0` subscription selection roles are supported by the prior callsite and method tracing; proposed fallback fragment tags are compatibility identifiers, not recovered original plaintext.
- The compatibility targets above are design requirements, not claims that a rebuilt application has passed them.
