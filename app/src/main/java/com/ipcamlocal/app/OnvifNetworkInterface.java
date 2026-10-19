package com.ipcamlocal.app;

final class OnvifNetworkInterface {
    String token = "";
    String name = "";
    boolean enabled;
    boolean wireless;
    boolean dhcp;
    String ipv4 = "";
    int prefixLength = -1;
    String ssid = "";

    String label() {
        String tipo = wireless ? "Wi-Fi" : "Ethernet";
        String ip = ipv4 == null || ipv4.isEmpty() ? "sin IPv4" : ipv4;
        String modo = dhcp ? "DHCP" : "manual";
        String extra = wireless && ssid != null && !ssid.isEmpty() ? " · " + ssid : "";
        return name + " · " + tipo + " · " + modo + " · " + ip + extra;
    }
}
