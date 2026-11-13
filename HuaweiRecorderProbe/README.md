# Huawei Recorder Probe

Standalone diagnostic Android app for Huawei call-recorder compatibility.

- Does not modify or replace Shayan Phone.
- Does not modify, re-sign, or redistribute Huawei APKs.
- Checks Huawei recorder package visibility, declared service metadata, and the app's permission state.
- Attempts a Binder connection only to test whether the service accepts a client.
- Does not invoke recording methods, capture audio, access call content, or save recordings.

Target test device: Huawei Mate 10 (ALP-L29, Android 10 / EMUI 12).
