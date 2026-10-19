package com.ipcamlocal.app;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.util.Log;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

final class OnvifDiscovery {
    private static final String ADDRESS = "239.255.255.250";
    private static final int PORT = 3702;
    private static final int WINDOW_MS = 4500;

    interface Callback {
        void onFinished(List<OnvifDiscoveryResult> results, Exception error);
    }

    static void buscar(Context context, Callback callback) {
        new Thread(() -> {
            WifiManager.MulticastLock lock = null;
            DatagramSocket socket = null;
            Exception error = null;
            List<OnvifDiscoveryResult> unicos = new ArrayList<>();
            try {
                WifiManager wifi = (WifiManager) context.getApplicationContext()
                        .getSystemService(Context.WIFI_SERVICE);
                if (wifi != null) {
                    lock = wifi.createMulticastLock("ipcam-ws-discovery");
                    lock.setReferenceCounted(false);
                    lock.acquire();
                }

                socket = new DatagramSocket();
                socket.setSoTimeout(700);
                byte[] probe = construirProbe().getBytes(StandardCharsets.UTF_8);
                DatagramPacket salida = new DatagramPacket(
                        probe, probe.length, InetAddress.getByName(ADDRESS), PORT
                );
                socket.send(salida);
                try { Thread.sleep(120); } catch (InterruptedException ignored) {}
                socket.send(salida);

                long fin = System.currentTimeMillis() + WINDOW_MS;
                byte[] buffer = new byte[65535];
                while (System.currentTimeMillis() < fin) {
                    DatagramPacket entrada = new DatagramPacket(buffer, buffer.length);
                    try {
                        socket.receive(entrada);
                        String xml = new String(
                                entrada.getData(), entrada.getOffset(), entrada.getLength(),
                                StandardCharsets.UTF_8
                        );
                        for (OnvifDiscoveryResult r : OnvifDiscoveryParser.parseProbeMatches(xml)) {
                            OnvifDiscoveryParser.addUnique(unicos, r);
                        }
                    } catch (SocketTimeoutException ignored) {
                    } catch (Exception parseError) {
                        Log.w("IPCam-H", "Respuesta WS-Discovery no válida", parseError);
                    }
                }
            } catch (Exception e) {
                error = e;
            } finally {
                if (socket != null) socket.close();
                if (lock != null && lock.isHeld()) lock.release();
            }
            callback.onFinished(new ArrayList<>(unicos), error);
        }, "IPCam-WSDiscovery").start();
    }

    private static String construirProbe() {
        String id = "uuid:" + UUID.randomUUID();
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<e:Envelope xmlns:e=\"http://www.w3.org/2003/05/soap-envelope\" "
                + "xmlns:w=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\" "
                + "xmlns:d=\"http://schemas.xmlsoap.org/ws/2005/04/discovery\" "
                + "xmlns:dn=\"http://www.onvif.org/ver10/network/wsdl\">"
                + "<e:Header><w:MessageID>" + id + "</w:MessageID>"
                + "<w:To e:mustUnderstand=\"true\">urn:schemas-xmlsoap-org:ws:2005:04:discovery</w:To>"
                + "<w:Action e:mustUnderstand=\"true\">http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</w:Action>"
                + "</e:Header><e:Body><d:Probe><d:Types>dn:NetworkVideoTransmitter</d:Types>"
                + "</d:Probe></e:Body></e:Envelope>";
    }
}
