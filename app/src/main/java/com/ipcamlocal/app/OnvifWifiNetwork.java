package com.ipcamlocal.app;

final class OnvifWifiNetwork {
    String ssid = "";
    String signal = "";

    String label() {
        return signal == null || signal.isEmpty() ? ssid : ssid + " · " + signal;
    }
}
