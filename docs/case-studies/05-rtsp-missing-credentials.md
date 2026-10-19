# Case 5 — The stream failed because no user was sent

**Version:** 0.13.0 · **Area:** RTSP / app state

## Symptom

After the camera moved to a new IP, the app reached it but never played video:

```
RTSP target: host=192.168.1.25 port=554 path=/11 credentials=no mode=LAN
CameraSession: CONNECTING - Abriendo RTSP…
CameraSession: CONNECTING - Cargando video… 0%
CameraSession error: LibVLC EncounteredError      (~0.6 s later)
CameraSession: WAITING_RETRY - Reconectando… intento 1 en 1 s
...
```

## Reading the log

- The app writes `RTSP target:` only **after** a TCP check to `host:554` succeeds, so the
  network was fine.
- `credentials=no` means the RTSP URL had no `user@` part. `RtspUrlBuilder` adds the user even
  when the password is empty, so the **user field itself was empty**.
- Without credentials the camera rejects the request, and LibVLC only reports a generic
  `EncounteredError`. The retries could never succeed.

The diagnostic line exists on purpose: it logs host, port, path and whether credentials are
present, **without printing the password**. It made this a five-minute diagnosis.

## Most likely cause

On startup the connection screen was filled from loose preferences (last URL, last user), not
from the saved camera profile, and the password is never stored there. Picking the camera from
*Saved cameras* loads user and password from the profile, and after doing that the stream
worked. I didn't confirm exactly how the user field ended up empty, so the fix closes both
paths instead of patching one.

## Fix

- On startup the app loads the **last used camera profile** with its password from the
  Android Keystore.
- With an empty user, the app does not try to connect; it says what's missing instead of
  retrying a request that cannot succeed.

## Lesson

Good diagnostics beat guessing. One log line that says what was sent, without exposing
secrets, pointed straight at the cause.
