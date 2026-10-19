package com.ipcamlocal.app;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class OnvifDiscoveryParserTest {

    private static String respuesta(String uuid, String endpoint) {
        return "<d:ProbeMatch>"
                + "<a:EndpointReference><a:Address>" + uuid + "</a:Address></a:EndpointReference>"
                + "<d:XAddrs>" + endpoint + "</d:XAddrs>"
                + "<d:Scopes>onvif://www.onvif.org/name/Camara_Sala</d:Scopes>"
                + "</d:ProbeMatch>";
    }

    private static String envelope(String... matches) {
        StringBuilder body = new StringBuilder();
        for (String m : matches) body.append(m);
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                + "<e:Envelope xmlns:e=\"http://www.w3.org/2003/05/soap-envelope\" "
                + "xmlns:a=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\" "
                + "xmlns:d=\"http://schemas.xmlsoap.org/ws/2005/04/discovery\">"
                + "<e:Body><d:ProbeMatches>" + body + "</d:ProbeMatches></e:Body></e:Envelope>";
    }

    @Test
    public void parseaRespuestaSinNecesitarRed() throws Exception {
        List<OnvifDiscoveryResult> r = OnvifDiscoveryParser.parseProbeMatches(
                envelope(respuesta("urn:uuid:cam-1", "http://192.168.1.20/onvif/device_service"))
        );
        assertEquals(1, r.size());
        assertEquals("urn:uuid:cam-1", r.get(0).uuid);
        assertEquals("http://192.168.1.20/onvif/device_service", r.get(0).xAddr);
    }

    @Test
    public void eliminaDuplicadosPorEndpoint() throws Exception {
        List<OnvifDiscoveryResult> r = OnvifDiscoveryParser.parseProbeMatches(envelope(
                respuesta("urn:uuid:cam-1", "http://192.168.1.20/onvif/device_service"),
                respuesta("urn:uuid:cam-2", "http://192.168.1.20/onvif/device_service")
        ));
        assertEquals(1, r.size());
    }

    @Test
    public void eliminaDuplicadosPorUuid() throws Exception {
        List<OnvifDiscoveryResult> r = OnvifDiscoveryParser.parseProbeMatches(envelope(
                respuesta("urn:uuid:cam-1", "http://192.168.1.20/onvif/device_service"),
                respuesta("urn:uuid:cam-1", "http://192.168.1.21/onvif/device_service")
        ));
        assertEquals(1, r.size());
    }
}
