# Changelog

## 1.0.2
- Build: `compileSdk 36`, Android Gradle Plugin 8.10.1, Gradle 8.11.1. `libvlc-all:3.7.6`
  requires `minCompileSdk=36`; CodeAssist didn't enforce it, the first CI build did.
  `targetSdk` stays at 35 (no runtime change).
- CI: `actions/checkout@v5`, `actions/setup-java@v5`, runner pinned to `ubuntu-24.04`.

## 1.0.1
- `CameraProfile` no longer depends on `android.text.TextUtils`, so its unit tests run on a
  plain JVM (they passed locally only against a full Android implementation). No behavior change.
- Repository setup: Gradle wrapper, GitHub Actions (tests + debug APK), GPL-3.0 license, docs.

## 1.0.0
First official release. Same code as 1.0.0-rc1. App name changed from "IPCamLocal Beta" to
"IPCamLocal".

## 1.0.0-rc1
- YouTube-style fullscreen: a button to enter, an exit button that hides after 3 s, tap to show
  or hide it, zoom still active, Back exits.

## 0.13.0
- *Saved cameras*: action buttons above the list; the screen respects Android 15 system bars.
- The last used camera is loaded with its password (Android Keystore) on startup.
- No connection attempt without a user ([case 5](docs/case-studies/05-rtsp-missing-credentials.md)).
- DHCP IP change: find the camera by ONVIF UUID, update the profile, retry once.
- Normal view locked to portrait; only fullscreen rotates.
- *View away from home*: VPN guidance; manual remote settings moved to Advanced.
- Snapshots to `Pictures/IPCamLocal`; video moved to `TextureView` (LibVLC 3.7.6 has no
  snapshot call on Android).
- Pinch zoom (1x–4x), pan, double-tap reset.

## 0.12.0
- Automatic PiP only when *Continue in background* is on ([case 4](docs/case-studies/04-pip-ignored-background-toggle.md)).
- Stable 16:9 video: `AspectRatioFrameLayout` decides the height because LibVLC's
  `VLCVideoLayout` forces `MATCH_PARENT` ([case 1](docs/case-studies/01-libvlc-video-layout.md)).
- Fullscreen by tapping the video.

## 0.11.2-wifi
- Ethernet → Wi-Fi: UUID saved before applying, "unplug the cable" step, UUID search for ~3 min,
  profile updated with the new IP, duplicate SSIDs grouped ([case 3](docs/case-studies/03-hi3510-wifi-and-ip-recovery.md)).

## 0.11.2
- Build fix for CodeAssist: `org.gradle.jvmargs=-Xmx2048m` ([case 2](docs/case-studies/02-codeassist-d8-heap.md)).

## 0.11.1
- Playback hotfix and sanitized diagnostics (host / port / path / credentials yes-no, never
  the password).
- Limited retries in the foreground; session paused outside the app when background mode is off.

## 0.11.0
- Network setup through the camera's Hi3510 CGI API.

## 0.10.0
- First network setup attempt through ONVIF (not usable on this camera) and manual remote access.

## 0.9.2
- Optional background mode: foreground service sharing the same `CameraSession`.

## 0.9.1
- PTZ pad no longer hidden under the video.

## 0.9.0
- `CameraSession` owns LibVLC/`MediaPlayer`; reconnection with backoff and a watchdog.
- PiP, SQLite camera profiles, Keystore credentials (AES/GCM), WS-Security, WS-Discovery,
  serialized PTZ ([case 6](docs/case-studies/06-ptz-move-stop-order.md)).
- Unit tests for `RtspUrlBuilder` and the WS-Discovery parser.
