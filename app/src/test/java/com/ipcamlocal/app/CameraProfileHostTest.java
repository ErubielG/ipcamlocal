package com.ipcamlocal.app;

import static org.junit.Assert.*;
import org.junit.Test;

// Cambio de IP de la cámara (DHCP / Ethernet -> Wi-Fi): CameraProfile.aplicarNuevoHost().
public class CameraProfileHostTest {
    private CameraProfile perfil(String host, String onvif) {
        CameraProfile p = new CameraProfile();
        p.setHost(host); p.setPuertoRtsp(554); p.setRutaRtsp("/11"); p.setEndpointOnvif(onvif);
        return p;
    }
    @Test public void actualizaHostUrlYOnvif() {
        CameraProfile p = perfil("192.168.1.24", "http://192.168.1.24:8080/onvif/ptz");
        p.aplicarNuevoHost("192.168.1.25", "http://192.168.1.25:8080/onvif/device_service", "uuid:abc");
        assertEquals("192.168.1.25", p.getHost());
        assertEquals("rtsp://192.168.1.25:554/11", p.construirUrlRtsp());
        assertEquals("http://192.168.1.25:8080/onvif/ptz", p.getEndpointOnvif());
        assertEquals("http://192.168.1.25:8080/onvif/device_service", p.getEndpointDevice());
        assertEquals("uuid:abc", p.getDeviceUuid());
    }
    @Test public void noReemplazaPrefijoDeOtraIp() {
        CameraProfile p = perfil("192.168.1.2", "http://192.168.1.25:8080/onvif/ptz");
        p.aplicarNuevoHost("192.168.1.30", "", "");
        assertEquals("http://192.168.1.25:8080/onvif/ptz", p.getEndpointOnvif());
    }
    @Test public void endpointSinPuertoNiRuta() {
        CameraProfile p = perfil("192.168.1.24", "http://192.168.1.24");
        p.aplicarNuevoHost("192.168.1.25", null, null);
        assertEquals("http://192.168.1.25", p.getEndpointOnvif());
    }
    @Test public void endpointSinPuertoConRuta() {
        CameraProfile p = perfil("192.168.1.24", "http://192.168.1.24/onvif/ptz");
        p.aplicarNuevoHost("192.168.1.25", null, null);
        assertEquals("http://192.168.1.25/onvif/ptz", p.getEndpointOnvif());
    }
}
