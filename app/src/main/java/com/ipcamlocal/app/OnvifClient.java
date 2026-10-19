package com.ipcamlocal.app;

import android.text.TextUtils;
import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

final class OnvifClient {
    private static final int TIMEOUT_MS = 4500;
    private static final String DEVICE_NS = "http://www.onvif.org/ver10/device/wsdl";
    private static final String MEDIA_NS = "http://www.onvif.org/ver10/media/wsdl";

    static OnvifConfig detectarConfiguracion(
            String deviceEndpoint, String usuario, String password
    ) throws Exception {
        OnvifConfig config = new OnvifConfig();
        config.deviceEndpoint = deviceEndpoint;

        String capabilities = soap(
                deviceEndpoint,
                DEVICE_NS + "/GetCapabilities",
                "<tds:GetCapabilities xmlns:tds=\"" + DEVICE_NS + "\">"
                        + "<tds:Category>All</tds:Category></tds:GetCapabilities>",
                usuario, password
        );
        Document caps = OnvifDiscoveryParser.parseXml(capabilities);
        config.mediaEndpoint = xAddrDeCapacidad(caps, "Media");
        config.ptzEndpoint = xAddrDeCapacidad(caps, "PTZ");
        if (TextUtils.isEmpty(config.mediaEndpoint)) {
            throw new IOException("La cámara no informó el servicio de video ONVIF");
        }

        String profiles = soap(
                config.mediaEndpoint,
                MEDIA_NS + "/GetProfiles",
                "<trt:GetProfiles xmlns:trt=\"" + MEDIA_NS + "\"/>",
                usuario, password
        );
        Document profilesDoc = OnvifDiscoveryParser.parseXml(profiles);
        NodeList profileNodes = profilesDoc.getElementsByTagNameNS("*", "Profiles");
        if (profileNodes.getLength() == 0) {
            throw new IOException("La cámara no devolvió perfiles de video");
        }
        Element profile = (Element) profileNodes.item(0);
        for (int i = 0; i < profileNodes.getLength(); i++) {
            Element candidate = (Element) profileNodes.item(i);
            if (candidate.getElementsByTagNameNS("*", "PTZConfiguration").getLength() > 0) {
                profile = candidate;
                break;
            }
        }
        config.profileToken = profile.getAttribute("token");
        if (TextUtils.isEmpty(config.profileToken)) {
            config.profileToken = OnvifDiscoveryParser.text(profile, "token");
        }
        if (TextUtils.isEmpty(config.profileToken)) {
            throw new IOException("Perfil ONVIF sin token");
        }

        String streamBody = "<trt:GetStreamUri xmlns:trt=\"" + MEDIA_NS + "\" "
                + "xmlns:tt=\"http://www.onvif.org/ver10/schema\">"
                + "<trt:StreamSetup><tt:Stream>RTP-Unicast</tt:Stream>"
                + "<tt:Transport><tt:Protocol>RTSP</tt:Protocol></tt:Transport>"
                + "</trt:StreamSetup><trt:ProfileToken>" + escapeXml(config.profileToken)
                + "</trt:ProfileToken></trt:GetStreamUri>";
        String stream = soap(
                config.mediaEndpoint,
                MEDIA_NS + "/GetStreamUri",
                streamBody,
                usuario, password
        );
        Document streamDoc = OnvifDiscoveryParser.parseXml(stream);
        NodeList uris = streamDoc.getElementsByTagNameNS("*", "Uri");
        if (uris.getLength() == 0) {
            throw new IOException("La cámara no devolvió una dirección de video");
        }
        config.streamUri = uris.item(0).getTextContent().trim();
        if (!config.streamUri.toLowerCase(Locale.US).startsWith("rtsp://")) {
            throw new IOException("La dirección de video devuelta no es RTSP");
        }
        return config;
    }

    private static String xAddrDeCapacidad(Document doc, String capability) {
        NodeList nodes = doc.getElementsByTagNameNS("*", capability);
        for (int i = 0; i < nodes.getLength(); i++) {
            Node n = nodes.item(i);
            if (n instanceof Element) {
                String value = OnvifDiscoveryParser.text((Element) n, "XAddr");
                if (!TextUtils.isEmpty(value)) return value;
            }
        }
        return "";
    }

    static String soap(
            String endpoint, String action, String body, String usuario, String password
    ) throws Exception {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(endpoint).openConnection();
            c.setRequestMethod("POST");
            c.setConnectTimeout(TIMEOUT_MS);
            c.setReadTimeout(TIMEOUT_MS);
            c.setDoOutput(true);
            c.setRequestProperty(
                    "Content-Type",
                    "application/soap+xml; charset=utf-8; action=\"" + action + "\""
            );
            c.setRequestProperty("SOAPAction", "\"" + action + "\"");
            byte[] data = envelope(body, usuario, password).getBytes(StandardCharsets.UTF_8);
            c.setFixedLengthStreamingMode(data.length);
            try (OutputStream out = c.getOutputStream()) { out.write(data); }

            int code = c.getResponseCode();
            InputStream in = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
            String respuesta = leerTodo(in);
            if (code < 200 || code >= 300) {
                String lower = respuesta.toLowerCase(Locale.US);
                if (code == 401 || code == 403
                        || lower.contains("notauthorized")
                        || lower.contains("failedauthentication")) {
                    throw new SecurityException("Usuario o contraseña incorrectos");
                }
                throw new IOException("ONVIF respondió HTTP " + code);
            }
            return respuesta;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String leerTodo(InputStream in) throws IOException {
        if (in == null) return "";
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int n;
            while ((n = input.read(buffer)) != -1) out.write(buffer, 0, n);
            return out.toString("UTF-8");
        }
    }

    private static String envelope(String body, String usuario, String password) throws Exception {
        String security = TextUtils.isEmpty(usuario)
                ? "" : wsSecurity(usuario, password == null ? "" : password);
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<s:Envelope xmlns:s=\"http://www.w3.org/2003/05/soap-envelope\">"
                + "<s:Header>" + security + "</s:Header><s:Body>"
                + body + "</s:Body></s:Envelope>";
    }

    private static String wsSecurity(String usuario, String password) throws Exception {
        byte[] nonce = new byte[16];
        new SecureRandom().nextBytes(nonce);
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        fmt.setTimeZone(TimeZone.getTimeZone("UTC"));
        String created = fmt.format(new Date());

        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        sha1.update(nonce);
        sha1.update(created.getBytes(StandardCharsets.UTF_8));
        sha1.update(password.getBytes(StandardCharsets.UTF_8));
        String digest = Base64.encodeToString(sha1.digest(), Base64.NO_WRAP);
        String nonce64 = Base64.encodeToString(nonce, Base64.NO_WRAP);

        return "<wsse:Security s:mustUnderstand=\"1\" "
                + "xmlns:wsse=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd\" "
                + "xmlns:wsu=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd\">"
                + "<wsse:UsernameToken><wsse:Username>" + escapeXml(usuario) + "</wsse:Username>"
                + "<wsse:Password Type=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest\">"
                + digest + "</wsse:Password>"
                + "<wsse:Nonce EncodingType=\"http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary\">"
                + nonce64 + "</wsse:Nonce><wsu:Created>" + created + "</wsu:Created>"
                + "</wsse:UsernameToken></wsse:Security>";
    }


    static java.util.List<OnvifNetworkInterface> getNetworkInterfaces(
            String deviceEndpoint, String usuario, String password
    ) throws Exception {
        String response = soap(
                deviceEndpoint,
                DEVICE_NS + "/GetNetworkInterfaces",
                "<tds:GetNetworkInterfaces xmlns:tds=\"" + DEVICE_NS + "\"/>",
                usuario, password
        );

        Document doc = OnvifDiscoveryParser.parseXml(response);
        NodeList nodes = doc.getElementsByTagNameNS("*", "NetworkInterfaces");
        java.util.List<OnvifNetworkInterface> result = new java.util.ArrayList<>();

        for (int i = 0; i < nodes.getLength(); i++) {
            if (!(nodes.item(i) instanceof Element)) continue;
            Element e = (Element) nodes.item(i);
            OnvifNetworkInterface n = new OnvifNetworkInterface();
            n.token = e.getAttribute("token");
            n.enabled = boolText(e, "Enabled", true);

            Element info = firstElement(e, "Info");
            if (info != null) n.name = OnvifDiscoveryParser.text(info, "Name");
            if (TextUtils.isEmpty(n.name)) n.name = TextUtils.isEmpty(n.token) ? "interfaz" : n.token;

            Element ipv4 = firstElement(e, "IPv4");
            if (ipv4 != null) {
                Element config = firstElement(ipv4, "Config");
                if (config != null) {
                    n.dhcp = boolText(config, "DHCP", false);
                    Element fromDhcp = firstElement(config, "FromDHCP");
                    Element manual = firstElement(config, "Manual");
                    Element addrSource = fromDhcp != null ? fromDhcp : manual;
                    if (addrSource != null) {
                        n.ipv4 = OnvifDiscoveryParser.text(addrSource, "Address");
                        try {
                            n.prefixLength = Integer.parseInt(OnvifDiscoveryParser.text(addrSource, "PrefixLength"));
                        } catch (Exception ignored) {}
                    }
                }
            }

            NodeList dot11 = e.getElementsByTagNameNS("*", "Dot11");
            n.wireless = dot11.getLength() > 0
                    || n.name.toLowerCase(Locale.US).contains("wlan")
                    || n.name.toLowerCase(Locale.US).contains("wifi")
                    || n.name.toLowerCase(Locale.US).contains("wireless");
            if (dot11.getLength() > 0 && dot11.item(0) instanceof Element) {
                n.ssid = OnvifDiscoveryParser.text((Element) dot11.item(0), "SSID");
            }
            result.add(n);
        }
        return result;
    }

    static java.util.List<OnvifWifiNetwork> scanWifi(
            String deviceEndpoint, String interfaceToken, String usuario, String password
    ) throws Exception {
        String body = "<tds:ScanAvailableDot11Networks xmlns:tds=\"" + DEVICE_NS + "\">"
                + "<tds:InterfaceToken>" + escapeXml(interfaceToken) + "</tds:InterfaceToken>"
                + "</tds:ScanAvailableDot11Networks>";
        String response = soap(
                deviceEndpoint,
                DEVICE_NS + "/ScanAvailableDot11Networks",
                body,
                usuario, password
        );

        Document doc = OnvifDiscoveryParser.parseXml(response);
        NodeList nodes = doc.getElementsByTagNameNS("*", "Networks");
        java.util.List<OnvifWifiNetwork> result = new java.util.ArrayList<>();
        java.util.HashSet<String> seen = new java.util.HashSet<>();

        for (int i = 0; i < nodes.getLength(); i++) {
            if (!(nodes.item(i) instanceof Element)) continue;
            Element e = (Element) nodes.item(i);
            String ssid = OnvifDiscoveryParser.text(e, "SSID");
            if (TextUtils.isEmpty(ssid) || seen.contains(ssid)) continue;
            OnvifWifiNetwork n = new OnvifWifiNetwork();
            n.ssid = ssid;
            n.signal = OnvifDiscoveryParser.text(e, "SignalStrength");
            result.add(n);
            seen.add(ssid);
        }
        return result;
    }

    static boolean setNetworkDhcp(
            String deviceEndpoint, String interfaceToken,
            String usuario, String password
    ) throws Exception {
        String body = "<tds:SetNetworkInterfaces xmlns:tds=\"" + DEVICE_NS + "\" "
                + "xmlns:tt=\"http://www.onvif.org/ver10/schema\">"
                + "<tds:InterfaceToken>" + escapeXml(interfaceToken) + "</tds:InterfaceToken>"
                + "<tds:NetworkInterface>"
                + "<tt:Enabled>true</tt:Enabled>"
                + "<tt:IPv4><tt:Enabled>true</tt:Enabled><tt:DHCP>true</tt:DHCP></tt:IPv4>"
                + "</tds:NetworkInterface>"
                + "</tds:SetNetworkInterfaces>";
        return parseRebootNeeded(soap(
                deviceEndpoint,
                DEVICE_NS + "/SetNetworkInterfaces",
                body,
                usuario, password
        ));
    }

    static boolean setNetworkStaticIpv4(
            String deviceEndpoint, String interfaceToken, String address, int prefixLength,
            String usuario, String password
    ) throws Exception {
        String body = "<tds:SetNetworkInterfaces xmlns:tds=\"" + DEVICE_NS + "\" "
                + "xmlns:tt=\"http://www.onvif.org/ver10/schema\">"
                + "<tds:InterfaceToken>" + escapeXml(interfaceToken) + "</tds:InterfaceToken>"
                + "<tds:NetworkInterface>"
                + "<tt:Enabled>true</tt:Enabled>"
                + "<tt:IPv4><tt:Enabled>true</tt:Enabled>"
                + "<tt:Manual><tt:Address>" + escapeXml(address) + "</tt:Address>"
                + "<tt:PrefixLength>" + prefixLength + "</tt:PrefixLength></tt:Manual>"
                + "<tt:DHCP>false</tt:DHCP></tt:IPv4>"
                + "</tds:NetworkInterface>"
                + "</tds:SetNetworkInterfaces>";
        return parseRebootNeeded(soap(
                deviceEndpoint,
                DEVICE_NS + "/SetNetworkInterfaces",
                body,
                usuario, password
        ));
    }

    static boolean configureWifi(
            String deviceEndpoint, String interfaceToken, String ssid, String wifiPassword,
            String usuario, String password
    ) throws Exception {
        boolean open = TextUtils.isEmpty(wifiPassword);
        if (!open && (wifiPassword.length() < 8 || wifiPassword.length() > 63)) {
            throw new IllegalArgumentException("La contraseña Wi-Fi debe tener entre 8 y 63 caracteres.");
        }

        String security = open
                ? "<tt:Security><tt:Mode>None</tt:Mode></tt:Security>"
                : "<tt:Security><tt:Mode>PSK</tt:Mode><tt:Algorithm>CCMP</tt:Algorithm>"
                + "<tt:PSK><tt:Passphrase>" + escapeXml(wifiPassword) + "</tt:Passphrase></tt:PSK>"
                + "</tt:Security>";

        String body = "<tds:SetNetworkInterfaces xmlns:tds=\"" + DEVICE_NS + "\" "
                + "xmlns:tt=\"http://www.onvif.org/ver10/schema\">"
                + "<tds:InterfaceToken>" + escapeXml(interfaceToken) + "</tds:InterfaceToken>"
                + "<tds:NetworkInterface>"
                + "<tt:Enabled>true</tt:Enabled>"
                + "<tt:IPv4><tt:Enabled>true</tt:Enabled><tt:DHCP>true</tt:DHCP></tt:IPv4>"
                + "<tt:Extension><tt:Dot11>"
                + "<tt:SSID>" + escapeXml(ssid) + "</tt:SSID>"
                + "<tt:Mode>Infrastructure</tt:Mode>"
                + "<tt:Alias>IPCamLocal</tt:Alias>"
                + "<tt:Priority>1</tt:Priority>"
                + security
                + "</tt:Dot11></tt:Extension>"
                + "</tds:NetworkInterface>"
                + "</tds:SetNetworkInterfaces>";

        return parseRebootNeeded(soap(
                deviceEndpoint,
                DEVICE_NS + "/SetNetworkInterfaces",
                body,
                usuario, password
        ));
    }

    static void systemReboot(
            String deviceEndpoint, String usuario, String password
    ) throws Exception {
        soap(
                deviceEndpoint,
                DEVICE_NS + "/SystemReboot",
                "<tds:SystemReboot xmlns:tds=\"" + DEVICE_NS + "\"/>",
                usuario, password
        );
    }

    private static boolean parseRebootNeeded(String xml) throws Exception {
        Document doc = OnvifDiscoveryParser.parseXml(xml);
        NodeList nodes = doc.getElementsByTagNameNS("*", "RebootNeeded");
        if (nodes.getLength() == 0) return false;
        return "true".equalsIgnoreCase(nodes.item(0).getTextContent().trim())
                || "1".equals(nodes.item(0).getTextContent().trim());
    }

    private static boolean boolText(Element root, String localName, boolean defaultValue) {
        String v = OnvifDiscoveryParser.text(root, localName);
        if (TextUtils.isEmpty(v)) return defaultValue;
        return "true".equalsIgnoreCase(v) || "1".equals(v);
    }

    private static Element firstElement(Element root, String localName) {
        NodeList nodes = root.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0 || !(nodes.item(0) instanceof Element)) return null;
        return (Element) nodes.item(0);
    }

    private static String escapeXml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
