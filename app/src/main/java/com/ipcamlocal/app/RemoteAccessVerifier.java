package com.ipcamlocal.app;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

final class RemoteAccessVerifier {
    private static final int TIMEOUT_MS = 5000;

    static final class Result {
        final boolean ok;
        final String message;

        Result(boolean ok, String message) {
            this.ok = ok;
            this.message = message;
        }
    }

    private RemoteAccessVerifier() {}

    static Result verify(String host, int port, String path) {
        if (host == null || host.trim().isEmpty()) {
            return new Result(false, "Escribe una IP pública o nombre DDNS.");
        }
        if (port <= 0 || port > 65535) {
            return new Result(false, "Puerto remoto inválido.");
        }

        String cleanPath = path == null ? "" : path.trim();
        if (!cleanPath.isEmpty() && !cleanPath.startsWith("/")) cleanPath = "/" + cleanPath;
        String uri = "rtsp://" + host.trim() + ":" + port + cleanPath;

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host.trim(), port), TIMEOUT_MS);
            socket.setSoTimeout(TIMEOUT_MS);

            BufferedWriter out = new BufferedWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.US_ASCII)
            );
            out.write("OPTIONS " + uri + " RTSP/1.0\r\n");
            out.write("CSeq: 1\r\n");
            out.write("User-Agent: IPCamLocal/0.10\r\n");
            out.write("\r\n");
            out.flush();

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII)
            );
            String first = in.readLine();
            if (first == null || !first.startsWith("RTSP/")) {
                return new Result(false, "El puerto respondió, pero no parece ser un servidor RTSP.");
            }

            // 200 = accesible; 401/403 también prueban que alcanzamos un RTSP real.
            if (first.contains(" 200 ") || first.contains(" 401 ") || first.contains(" 403 ")) {
                return new Result(true, "Endpoint RTSP remoto alcanzable.");
            }

            return new Result(false, "El servidor RTSP respondió: " + first);
        } catch (Exception e) {
            return new Result(false, "No se pudo alcanzar el endpoint remoto (" + e.getClass().getSimpleName() + ").");
        }
    }
}
