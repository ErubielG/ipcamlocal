package com.ipcamlocal.app;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public final class OnvifDiscoveryParser {
    private OnvifDiscoveryParser() {}

    public static List<OnvifDiscoveryResult> parseProbeMatches(String xml) throws Exception {
        Document doc = parseXml(xml);
        NodeList matches = doc.getElementsByTagNameNS("*", "ProbeMatch");
        List<OnvifDiscoveryResult> unicos = new ArrayList<>();
        for (int i = 0; i < matches.getLength(); i++) {
            Element match = (Element) matches.item(i);
            String xAddr = primeraUrl(text(match, "XAddrs"));
            if (xAddr.isEmpty()) continue;
            addUnique(unicos, new OnvifDiscoveryResult(
                    text(match, "Address"), xAddr, text(match, "Scopes")
            ));
        }
        return unicos;
    }

    static void addUnique(List<OnvifDiscoveryResult> list, OnvifDiscoveryResult nuevo) {
        for (OnvifDiscoveryResult actual : list) {
            boolean mismoUuid = !nuevo.uuid.isEmpty()
                    && nuevo.uuid.equalsIgnoreCase(actual.uuid);
            boolean mismoEndpoint = !nuevo.xAddr.isEmpty()
                    && nuevo.xAddr.equalsIgnoreCase(actual.xAddr);
            if (mismoUuid || mismoEndpoint) return;
        }
        list.add(nuevo);
    }

    static Document parseXml(String xml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        try { f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true); }
        catch (Exception ignored) {}
        try { f.setFeature("http://xml.org/sax/features/external-general-entities", false); }
        catch (Exception ignored) {}
        try { f.setFeature("http://xml.org/sax/features/external-parameter-entities", false); }
        catch (Exception ignored) {}
        return f.newDocumentBuilder().parse(
                new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8))
        );
    }

    static String text(Element root, String localName) {
        NodeList nodes = root.getElementsByTagNameNS("*", localName);
        if (nodes.getLength() == 0) return "";
        String value = nodes.item(0).getTextContent();
        return value == null ? "" : value.trim();
    }

    private static String primeraUrl(String value) {
        if (value == null) return "";
        for (String part : value.trim().split("\\s+")) {
            if (part.startsWith("http://") || part.startsWith("https://")) return part;
        }
        return "";
    }
}
