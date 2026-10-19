package com.ipcamlocal.app;

import java.net.URI;
import java.net.URLDecoder;

public final class OnvifDiscoveryResult {
    public final String uuid;
    public final String xAddr;
    public final String scopes;

    public OnvifDiscoveryResult(String uuid, String xAddr, String scopes) {
        this.uuid = uuid == null ? "" : uuid.trim();
        this.xAddr = xAddr == null ? "" : xAddr.trim();
        this.scopes = scopes == null ? "" : scopes.trim();
    }

    public String host() {
        try { return new URI(xAddr).getHost(); }
        catch (Exception e) { return null; }
    }

    public String nombreAmigable() {
        for (String scope : scopes.split("\\s+")) {
            int i = scope.indexOf("/name/");
            if (i >= 0 && i + 6 < scope.length()) {
                try {
                    String n = URLDecoder.decode(scope.substring(i + 6), "UTF-8")
                            .replace('_', ' ').trim();
                    if (!n.isEmpty()) return n;
                } catch (Exception ignored) {}
            }
        }
        return "Cámara ONVIF";
    }
}
