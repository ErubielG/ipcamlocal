# Validation

Every release was installed on a real phone and tested against a real camera before moving on.
Nothing below is "should work": each row was checked on the device.

**Test hardware**

- Android phone (Samsung), app built with CodeAssist.
- Wi-Fi PTZ IP camera, Hi3510 firmware family: hardware `V1.0.0.1`, software
  `V13.1.6.1.26-20171207`. RTSP on port 554, ONVIF on port 8080.

## What was verified

| Feature | Verified in |
|---|---|
| RTSP live video | 0.11.2 → 1.0.0 |
| PTZ (tap and hold, all four directions + stop) | 0.11.2 → 1.0.0 |
| Ethernet → Wi-Fi from the app, camera found again at its new IP | 0.11.2-wifi |
| Automatic PiP only with *Continue in background* on; PiP button always | 0.12.0 |
| 16:9 video with every control on screen; stable after PiP and app switching | 0.12.0 |
| Fullscreen (landscape, no system bars) | 0.12.0 |
| Last camera loaded with its password on startup; no connection attempt without a user | 0.13.0 |
| Camera found by UUID after a DHCP IP change | 0.13.0 |
| Portrait-only normal view; "Saved cameras" buttons above the list | 0.13.0 |
| Snapshot to gallery (`Pictures/IPCamLocal`) | 0.13.0 |
| Pinch zoom 1x–4x, pan, double-tap reset | 0.13.0 |
| Fullscreen enter/exit buttons, auto-hiding exit button | 1.0.0-rc1 |

## Automated checks

- Unit tests (JVM): `RtspUrlBuilder`, WS-Discovery parser, and `CameraProfile` IP update
  (including that `.2` is not replaced inside `.25`).
- GitHub Actions builds the APK and runs the tests on every push.

## Not validated yet

- Other phones and Android versions.
- Other camera brands or models.
- Long-running sessions (hours) and repeated network drops.
- A signed release build.
