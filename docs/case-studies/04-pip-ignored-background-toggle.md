# Case 4 — "Continue in background" was off, and the video kept going anyway

**Version:** 0.12.0 · **Area:** Android lifecycle

## Symptom

With the *Continue in background* switch **off**, leaving the app still opened a floating
video window and kept the stream alive. The switch looked broken.

## Cause

The switch did control the foreground service and its notification. The problem was
Picture-in-Picture: the app entered PiP automatically when the user left, and that path never
read the switch.

```java
// simplified
// Android 12+
builder.setAutoEnterEnabled(cameraSession.isPlaying() && cameraPanel.isShown());

// Android 8–11
@Override public void onUserLeaveHint() {
    if (cameraSession.isPlaying() && ...) entrarEnPiP();
}
```

And because `onStop()` skips pausing the session while in PiP, the stream stayed alive.

## Fix

- Automatic PiP now also requires the background switch to be **on**, in both paths.
- Moving the switch updates the PiP parameters immediately, not on the next state change.
- The PiP **button** still works on demand.

| Switch | Leaving the app |
|---|---|
| Off | No floating window; the session pauses |
| On | Floating window + notification; same stream |

## Lesson

A setting has to be enforced in every code path that can do what it controls, not just the
obvious one.
