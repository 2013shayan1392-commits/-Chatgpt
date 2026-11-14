# Upstream release scan — SKVALEX

Date: 2026-11-14

## Purpose

Check whether later official releases provide a legitimate source for the missing 3.7.3 material or make a source-faithful 3.7.3 recovery possible.

## Official releases checked

The official upstream release page currently lists 3.7.3 through 3.7.6:
https://github.com/skvalex/callrecorder/releases

| Version | Published | Asset | SHA-256 |
|---|---|---|---|
| 3.7.3 | 2026-04-10 | CallRecorder_v.3.7.3.apk | db6c9a2c45b7420051fe26f336ae6c2cc7d9c8a2633bccf86bb9f230c9052684 |
| 3.7.4 | 2026-08-22 | CallRecorder_v.3.7.4.apk | 121fefed33451f7acea1ca25b893e91d5b09f4a26ad7b24ea50cbacb415706e5 |
| 3.7.5 | 2026-09-06 | CallRecorder_v.3.7.5.apk | 602a4a918d1ce98ad21662b37e0e8c981e4bfa88c458a19e02b0dc871d22187c |
| 3.7.6 | 2026-09-14 | CallRecorder_v.3.7.6.apk | 87022bcd84397a9311175cdf36946e0560100550e5cb24ad59a91c79e9c9ead1 |

The supplied 3.7.3 APK remains an exact match for the official 3.7.3 asset by SHA-256 and byte size. The later assets are different application versions and cannot be treated as missing source files or silently substituted into a 3.7.3 recovery.

## Source availability

The official master contents endpoint returns an empty tree. The updates branch contains update metadata and changelog material, not an Android source tree, Gradle project, or original build configuration.

Checked endpoints:
- https://api.github.com/repos/skvalex/callrecorder/contents?ref=master
- https://api.github.com/repos/skvalex/callrecorder/contents?ref=updates
- https://api.github.com/repos/skvalex/callrecorder/releases/tags/3.7.4
- https://api.github.com/repos/skvalex/callrecorder/releases/tags/3.7.5
- https://api.github.com/repos/skvalex/callrecorder/releases/tags/3.7.6

No source archive or reproducible build input was found in these public release endpoints.

## Decision

- Keep the target pinned to official 3.7.3.
- Do not transplant code, resources, or obfuscated strings from later binaries without source-level equivalence evidence.
- Do not use a later APK as a replacement for the 3.7.3 baseline.
- Keep all capture, privileged, native, and device-specific implementation unchanged.
- Continue only with evidence-backed, non-capture UI/resource analysis.
- A final source build remains blocked until an authorized source tree/build configuration and the original signing authorization are available.

## Static review correction

An earlier stage-19 narrative reported remaining string lengths as 24/46/68/98 using decoded Unicode code-point counts. The authoritative DEX utf16_size values and Java String.length() semantics give the corrected lengths 22/44/66/96 UTF-16 code units. The corrected values in RECOVERY_STATUS.md and MISSING_COMPONENTS_AND_RECOVERY_SOURCES.md supersede the older narrative.
