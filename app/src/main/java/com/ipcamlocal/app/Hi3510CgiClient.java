package com.ipcamlocal.app;

import android.util.Base64;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cliente local para el CGI Hi3510 que expone el firmware web de la cámara.
 * Nunca registra contraseñas ni claves Wi-Fi.
 */
final class Hi3510CgiClient {
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 8000;
    private static final Pattern VAR = Pattern.compile("var\\s+([A-Za-z0-9_]+)\\s*=\\s*\\\"([^\\\"]*)\\\"\\s*;");
    private static final Pattern ARRAY_VALUE = Pattern.compile("([A-Za-z0-9_]+)\\[(\\d+)]\\s*=\\s*\\\"([^\\\"]*)\\\"\\s*;");

    static final class NetworkStatus {
        String dhcp = "";
        String ip = "";
        String netmask = "";
        String gateway = "";
        String dnsMode = "";
        String primaryDns = "";
        String secondaryDns = "";
        String networkType = "";

        boolean isDhcp() {
            return "on".equalsIgnoreCase(dhcp) || "1".equals(dhcp);
        }
    }

    static final class WirelessConfig {
        boolean enabled;
        String ssid = "";
        int auth;
        String key = ""; // normalmente el firmware no la devuelve; no persistir.
        int enc;
        int mode;
    }

    static final class WifiNetwork {
        String ssid = "";
        int signal;
        int channel;
        String auth = "";
        String encryption = "";
        String mode = "";

        boolean isOpen() {
            String a = auth == null ? "" : auth.toUpperCase(Locale.US);
            return a.isEmpty() || a.equals("NONE") || a.equals("OPEN");
        }

        int authCode() {
            String a = auth == null ? "" : auth.toUpperCase(Locale.US);
            if (a.contains("WPA2")) return 3;
            if (a.contains("WPA")) return 2;
            if (a.contains("WEP")) return 1;
            return 0;
        }

        int encryptionCode() {
            String e = encryption == null ? "" : encryption.toUpperCase(Locale.US);
            return e.contains("AES") ? 1 : 0; // 0=TKIP, 1=AES según CGI Hi3510.
        }

        String label() {
            String security = isOpen() ? "Abierta" : auth + (encryption.isEmpty() ? "" : " · " + encryption);
            return ssid + "\nSeñal " + signal + "% · " + security;
        }
    }

    private final String host;
    private final String user;
    private final String password;

    Hi3510CgiClient(String host, String user, String password) {
        this.host = host == null ? "" : host.trim();
        this.user = user == null ? "" : user;
        this.password = password == null ? "" : password;
    }

    boolean probe() throws Exception {
        String body = command("getserverinfo", Collections.emptyMap());
        return body.contains("var model=") || body.contains("var softVersion=");
    }

    NetworkStatus getNetworkStatus() throws Exception {
        Map<String,String> vars = parseVars(command("getnetattr", Collections.emptyMap()));
        NetworkStatus n = new NetworkStatus();
        n.dhcp = first(vars, "dhcpflag", "dhcp");
        n.ip = first(vars, "ip", "ipaddr");
        n.netmask = vars.getOrDefault("netmask", "");
        n.gateway = vars.getOrDefault("gateway", "");
        n.dnsMode = first(vars, "dnsstat", "dns_status");
        n.primaryDns = vars.getOrDefault("fdnsip", "");
        n.secondaryDns = vars.getOrDefault("sdnsip", "");
        n.networkType = vars.getOrDefault("networktype", "");
        return n;
    }

    WirelessConfig getWirelessConfig() throws Exception {
        Map<String,String> vars = parseVars(command("getwirelessattr", Collections.emptyMap()));
        WirelessConfig w = new WirelessConfig();
        w.enabled = "1".equals(vars.get("wf_enable"));
        w.ssid = vars.getOrDefault("wf_ssid", "");
        w.auth = intValue(vars.get("wf_auth"), 0);
        w.key = vars.getOrDefault("wf_key", "");
        w.enc = intValue(vars.get("wf_enc"), 0);
        w.mode = intValue(vars.get("wf_mode"), 0);
        return w;
    }

    List<WifiNetwork> searchWireless() throws Exception {
        String body = command("searchwireless", Collections.emptyMap());
        Map<Integer, WifiNetwork> byIndex = new HashMap<>();
        Matcher m = ARRAY_VALUE.matcher(body);
        while (m.find()) {
            String name = m.group(1);
            int index = Integer.parseInt(m.group(2));
            String value = m.group(3);
            WifiNetwork w = byIndex.computeIfAbsent(index, k -> new WifiNetwork());
            switch (name) {
                case "wessid": w.ssid = value; break;
                case "wrssi": w.signal = intValue(value, 0); break;
                case "wchannel": w.channel = intValue(value, 0); break;
                case "wauth": w.auth = value; break;
                case "wenc": w.encryption = value; break;
                case "wnet": w.mode = value; break;
            }
        }
        List<WifiNetwork> result = new ArrayList<>();
        for (WifiNetwork w : byIndex.values()) {
            if (w.ssid != null && !w.ssid.trim().isEmpty()) result.add(w);
        }
        result.sort(Comparator.comparingInt((WifiNetwork w) -> w.signal).reversed());

        // Varios puntos de acceso con el mismo SSID (mesh/repetidores): mostrar solo
        // el de mejor señal. La lista ya viene ordenada de mayor a menor señal.
        Map<String, WifiNetwork> porSsid = new LinkedHashMap<>();
        for (WifiNetwork w : result) {
            if (!porSsid.containsKey(w.ssid)) porSsid.put(w.ssid, w);
        }
        return new ArrayList<>(porSsid.values());
    }

    /** Prueba SSID/clave sin guardar la clave en la app. */
    boolean checkWireless(WifiNetwork network, String wifiPassword) throws Exception {
        Map<String,String> args = wirelessArgs(network, wifiPassword, false);
        command("chkwirelessattr", args);
        for (int i = 0; i < 10; i++) {
            Thread.sleep(900L);
            Map<String,String> vars = parseVars(command("getchkwireless", Collections.emptyMap()));
            String value = first(vars, "chkres", "checkresult", "result");
            if ("1".equals(value)) return true;
            if ("0".equals(value) && i >= 2) return false;
        }
        return false;
    }

    void applyWireless(WifiNetwork network, String wifiPassword) throws Exception {
        Map<String,String> args = wirelessArgs(network, wifiPassword, true);
        String result = command("setwirelessattr", args);
        assertNoCgiError(result);
    }

    void disableWireless() throws Exception {
        Map<String,String> args = new HashMap<>();
        // No reenviamos SSID/clave: la cámara no devuelve la clave actual.
        // Cambiamos únicamente el flag confirmado por la API CGI.
        args.put("-wf_enable", "0");
        String result = command("setwirelessattr", args);
        assertNoCgiError(result);
    }

    void setDhcp() throws Exception {
        Map<String,String> args = new HashMap<>();
        args.put("-dhcp", "on");
        args.put("-dnsstat", "1");
        String result = command("setnetattr", args);
        assertNoCgiError(result);
    }

    void setStaticNetwork(String ip, String mask, String gateway, String dns1, String dns2) throws Exception {
        Map<String,String> args = new HashMap<>();
        args.put("-dhcp", "off");
        args.put("-ipaddr", ip);
        args.put("-netmask", mask);
        args.put("-gateway", gateway);
        args.put("-dnsstat", "0");
        args.put("-fdnsip", dns1 == null ? "" : dns1);
        args.put("-sdnsip", dns2 == null ? "" : dns2);
        String result = command("setnetattr", args);
        assertNoCgiError(result);
    }

    private Map<String,String> wirelessArgs(WifiNetwork network, String wifiPassword, boolean enable) {
        Map<String,String> args = new HashMap<>();
        if (enable) args.put("-wf_enable", "1");
        args.put("-wf_ssid", network.ssid);
        args.put("-wf_auth", String.valueOf(network.authCode()));
        args.put("-wf_key", network.isOpen() ? "" : (wifiPassword == null ? "" : wifiPassword));
        args.put("-wf_enc", String.valueOf(network.encryptionCode()));
        args.put("-wf_mode", "0");
        return args;
    }

    private String command(String cmd, Map<String,String> args) throws Exception {
        StringBuilder q = new StringBuilder("cmd=").append(enc(cmd));
        for (Map.Entry<String,String> e : args.entrySet()) {
            q.append('&').append(enc(e.getKey())).append('=').append(enc(e.getValue()));
        }
        String url = "http://" + host + "/cgi-bin/hi3510/param.cgi?" + q;
        return get(url);
    }

    private String get(String urlString) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(urlString).openConnection();
        c.setRequestMethod("GET");
        c.setConnectTimeout(CONNECT_TIMEOUT_MS);
        c.setReadTimeout(READ_TIMEOUT_MS);
        c.setUseCaches(false);
        c.setRequestProperty("Connection", "close");
        c.setRequestProperty("User-Agent", "IPCamLocal/0.11");
        if (!user.isEmpty()) {
            String raw = user + ":" + password;
            String basic = Base64.encodeToString(raw.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
            c.setRequestProperty("Authorization", "Basic " + basic);
        }

        int status = c.getResponseCode();
        InputStream stream = status >= 200 && status < 400 ? c.getInputStream() : c.getErrorStream();
        String body = readAll(stream);
        c.disconnect();

        if (status == HttpURLConnection.HTTP_UNAUTHORIZED || status == HttpURLConnection.HTTP_FORBIDDEN) {
            throw new SecurityException("Credenciales HTTP rechazadas por la cámara");
        }
        if (status < 200 || status >= 400) {
            throw new java.io.IOException("HTTP " + status);
        }
        assertNoCgiError(body);
        return body;
    }

    private static String readAll(InputStream in) throws Exception {
        if (in == null) return "";
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
            return sb.toString();
        }
    }

    private static Map<String,String> parseVars(String body) {
        Map<String,String> map = new HashMap<>();
        Matcher m = VAR.matcher(body == null ? "" : body);
        while (m.find()) map.put(m.group(1), m.group(2));
        return map;
    }

    private static String first(Map<String,String> vars, String... keys) {
        for (String k : keys) {
            String v = vars.get(k);
            if (v != null && !v.isEmpty()) return v;
        }
        return "";
    }

    private static int intValue(String value, int fallback) {
        try { return Integer.parseInt(value); }
        catch (Exception e) { return fallback; }
    }

    private static String enc(String value) throws Exception {
        return URLEncoder.encode(value == null ? "" : value, "UTF-8");
    }

    private static void assertNoCgiError(String body) throws java.io.IOException {
        if (body == null) return;
        String lower = body.toLowerCase(Locale.US);
        if (lower.contains("[error]") || lower.contains("invalid request") || lower.contains("errcode")) {
            throw new java.io.IOException("La cámara devolvió un error CGI");
        }
    }
}
