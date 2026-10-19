package com.ipcamlocal.app;

import java.net.URI;
import java.nio.charset.StandardCharsets;

/** Pure Java RTSP URL helper so it can be unit-tested without Android. */
final class RtspUrlBuilder {
    private RtspUrlBuilder() {}

    static String addCredentials(String rawUrl, String user, String password) {
        if (rawUrl == null || rawUrl.isEmpty() || user == null || user.isEmpty()) return rawUrl;
        try {
            URI uri = new URI(rawUrl);
            String authority = uri.getRawAuthority();
            if (authority == null || authority.isEmpty() || authority.contains("@")) return rawUrl;
            String host = uri.getHost();
            if (host == null || host.isEmpty()) return rawUrl;

            StringBuilder out = new StringBuilder();
            out.append(uri.getScheme()).append("://");
            out.append(percentEncode(user));
            if (password != null && !password.isEmpty()) out.append(':').append(percentEncode(password));
            out.append('@');
            if (host.contains(":")) out.append('[').append(host).append(']'); else out.append(host);
            if (uri.getPort() != -1) out.append(':').append(uri.getPort());
            if (uri.getRawPath() != null) out.append(uri.getRawPath());
            if (uri.getRawQuery() != null) out.append('?').append(uri.getRawQuery());
            if (uri.getRawFragment() != null) out.append('#').append(uri.getRawFragment());
            return out.toString();
        } catch (Exception ignored) {
            return rawUrl;
        }
    }

    private static String percentEncode(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        StringBuilder out = new StringBuilder(bytes.length);
        for (byte b : bytes) {
            int c = b & 0xff;
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') ||
                    (c >= '0' && c <= '9') || c == '-' || c == '.' || c == '_' || c == '~') {
                out.append((char) c);
            } else {
                out.append('%');
                String hex = Integer.toHexString(c).toUpperCase();
                if (hex.length() == 1) out.append('0');
                out.append(hex);
            }
        }
        return out.toString();
    }
}
