# Case 6 — PTZ taps that didn't move the camera

**Version:** before 0.9 · **Area:** Concurrency / ONVIF PTZ

## Symptom

Holding a direction button moved the camera. A **short tap** often did nothing.

## Cause

Each button press sends ONVIF `ContinuousMove` on touch down and `Stop` on release. Both
requests were sent from separate threads. On a quick tap they could reach the camera too close
together, or `Stop` could even arrive before `Move`, so the camera never moved.

## Fix

- All PTZ requests go through a **single-thread `ExecutorService`**, so `Stop` can never
  overtake `Move`.
- On release, `Stop` is delayed until at least **280 ms** after the press, so a tap produces a
  small, visible movement.
- Both `ACTION_UP` and `ACTION_CANCEL` send `Stop`, so the camera doesn't keep turning if the
  touch is interrupted.

## Result

Short taps nudge the camera and long presses move it continuously. Validated on the real
camera and kept unchanged through 1.0.0.
