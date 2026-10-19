# Case 3 — Moving the camera from Ethernet to Wi-Fi (and finding it again)

**Versions:** 0.10 → 0.13.0 · **Area:** Networking, reverse engineering

This was the hardest part of the project for me: getting information out of a camera with no
documentation and no official app, and making the switch from Ethernet to Wi-Fi work from
inside IPCamLocal.

## Step 1 — ONVIF was a dead end

The obvious route was ONVIF `GetNetworkInterfaces` / `SetNetworkInterfaces`. On this camera it
returned nothing usable: the app asked me to pick a network interface and showed an empty
list. Instead of guessing, I went looking at what the camera itself uses.

## Step 2 — Reading the camera's own web panel

The camera serves a local admin page (`/web/admin.html`) that *can* change LAN and Wi-Fi
settings, so the capability existed somewhere. Researching this firmware family (public CGI
documentation from INSTAR, which uses the same platform) pointed to the **Hi3510** CGI API:

```
/cgi-bin/hi3510/param.cgi?cmd=<command>
```

I tested read-only commands against the real camera, from the phone's browser and from
**Termux**, before writing any app code:

| Command | What came back |
|---|---|
| `getserverinfo` | model and hardware/software/web versions |
| `getwirelessattr` | Wi-Fi enabled flag, SSID, auth/encryption fields |
| `searchwireless` | 20 nearby access points, with SSID, signal, channel, auth and encryption |
| `getnetattr` | DHCP on, IP, netmask, gateway, DNS, `networktype=LAN` |

The responses are JavaScript-like variables (`wessid[0]="..."; wrssi[0]="100"; ...`), which
`Hi3510CgiClient` parses. The parser was checked against the real `searchwireless` response.

## Step 3 — The provisioning flow

```
searchwireless  ->  pick a network  ->  type the password
chkwirelessattr ->  getchkwireless (the camera tests the credentials itself)
setwirelessattr ->  only if the test passed
```

The Wi-Fi password is sent to the camera once and **never stored** by the app.

<p align="center">
  <img src="../images/case-03-wifi-flow.jpg" width="820"
       alt="Three app screens: camera network status over Ethernet, list of Wi-Fi networks found by the camera, and the Wi-Fi configured confirmation">
  <br><sub>Network status read from the camera → networks scanned <i>by the camera</i> → settings
  applied. App UI in Spanish; network names blurred.</sub>
</p>

## Step 4 — "It worked, but the camera disappeared"

First real test: the camera validated the network, the settings were saved (the panel showed
`Wi-Fi: enabled · <my network>`), I unplugged the Ethernet cable… and the app could no longer
reach the camera.

**Hypothesis:** on Wi-Fi the camera uses a different network interface, with a different MAC
address, so the router gives it a **different IP** by DHCP. The app was still looking at the
old one.

**Test:** unplug the cable, wait two minutes, and use the app's general WS-Discovery search
(which doesn't care about IPs). The camera showed up at a new address, **without a reboot**.
So the Hi3510 flow worked; the bug was in how the app found the camera afterwards.

Two app bugs explained it:

1. "Find camera" searched **once, 2.5 s** after applying the settings, long before the camera
   switches networks.
2. If the saved profile had no ONVIF **UUID**, nothing could match the camera at its new IP.

## Fix

- Save the camera's UUID **before** applying Wi-Fi, while it still answers on the old IP.
- Tell the user the actual step: *"Unplug the Ethernet cable now"* (this model keeps using
  the cable while it is connected).
- Search by UUID up to 12 times over ~3 minutes, ignoring answers from the old IP (that means
  the cable is still in), then update IP, RTSP URL and ONVIF/PTZ endpoint in the profile.
- Group duplicate SSIDs from mesh networks: with the real scan, 20 entries became 15 networks.

In 0.13.0 the same idea was extended to everyday use: when "Connect" can't reach the saved IP
(for example after the router hands out a new DHCP lease), the app looks for the camera's UUID
on the network, updates the profile and retries once. `CameraProfile.aplicarNuevoHost()` has
unit tests, including making sure `.2` does not get replaced inside `.25`.

## Result

Validated with the real camera: configure Wi-Fi from the app → unplug the cable → the app finds
the camera at its new IP, updates the saved profile, and video and PTZ work.

## Limits

Validated on **one** camera model of the Hi3510 family. Other brands may use different CGI
commands or none at all; for those, the ONVIF path is still in the code, but it has not been
validated with another camera.
