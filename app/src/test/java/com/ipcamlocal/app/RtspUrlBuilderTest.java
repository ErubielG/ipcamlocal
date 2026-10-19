package com.ipcamlocal.app;

import org.junit.Test;
import static org.junit.Assert.*;

public class RtspUrlBuilderTest {
    @Test public void sinUsuarioNoModifica() {
        assertEquals("rtsp://cam/live", RtspUrlBuilder.addCredentials("rtsp://cam/live", "", "x"));
    }

    @Test public void agregaUsuarioYPassword() {
        assertEquals("rtsp://admin:1234@cam:554/live",
                RtspUrlBuilder.addCredentials("rtsp://cam:554/live", "admin", "1234"));
    }

    @Test public void codificaArrobaEnPassword() {
        assertEquals("rtsp://admin:a%40b@cam/live",
                RtspUrlBuilder.addCredentials("rtsp://cam/live", "admin", "a@b"));
    }

    @Test public void codificaDosPuntosEnPassword() {
        assertEquals("rtsp://admin:a%3Ab@cam/live",
                RtspUrlBuilder.addCredentials("rtsp://cam/live", "admin", "a:b"));
    }

    @Test public void noDuplicaCredenciales() {
        String url = "rtsp://admin:1234@cam/live";
        assertEquals(url, RtspUrlBuilder.addCredentials(url, "otro", "x"));
    }

    @Test public void sinPuertoNoInventaPuerto() {
        assertEquals("rtsp://admin:x@cam/live",
                RtspUrlBuilder.addCredentials("rtsp://cam/live", "admin", "x"));
    }

    @Test public void conservaPuerto() {
        assertEquals("rtsp://admin:x@cam:8554/live",
                RtspUrlBuilder.addCredentials("rtsp://cam:8554/live", "admin", "x"));
    }

    @Test public void urlMalformadaNoLanza() {
        String bad = "::::not a url";
        assertEquals(bad, RtspUrlBuilder.addCredentials(bad, "admin", "x"));
    }
}
