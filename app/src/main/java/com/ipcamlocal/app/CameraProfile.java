package com.ipcamlocal.app;

/** Datos de una cámara guardada. Java puro (sin clases de Android) para poder probarlo en la JVM. */
final class CameraProfile {
    public static final long SIN_ID = -1;

    private long id = SIN_ID;
    private String nombre;
    private String host;
    private int puertoRtsp;
    private String rutaRtsp;
    private String endpointDevice;
    private String endpointOnvif;
    private String profileToken;
    private String usuario;
    private String ultimoEstado;
    private long fechaUltimaConexion;

    // Identidad estable ONVIF. Permite volver a encontrar la cámara si cambia de IP.
    private String deviceUuid;

    // Acceso remoto: SOLO existe dentro de un perfil guardado.
    private boolean remoteEnabled;
    private boolean remoteVerified;
    private String remoteHost;
    private int remotePort;
    private String remotePath;

    public long getId() { return id; }
    public void setId(long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }

    public int getPuertoRtsp() { return puertoRtsp; }
    public void setPuertoRtsp(int puertoRtsp) { this.puertoRtsp = puertoRtsp; }

    public String getRutaRtsp() { return rutaRtsp; }
    public void setRutaRtsp(String rutaRtsp) { this.rutaRtsp = rutaRtsp; }

    public String getEndpointDevice() { return endpointDevice; }
    public void setEndpointDevice(String endpointDevice) { this.endpointDevice = endpointDevice; }

    public String getEndpointOnvif() { return endpointOnvif; }
    public void setEndpointOnvif(String endpointOnvif) { this.endpointOnvif = endpointOnvif; }

    public String getProfileToken() { return profileToken; }
    public void setProfileToken(String profileToken) { this.profileToken = profileToken; }

    public String getUsuario() { return usuario; }
    public void setUsuario(String usuario) { this.usuario = usuario; }

    public String getUltimoEstado() { return ultimoEstado; }
    public void setUltimoEstado(String ultimoEstado) { this.ultimoEstado = ultimoEstado; }

    public long getFechaUltimaConexion() { return fechaUltimaConexion; }
    public void setFechaUltimaConexion(long fechaUltimaConexion) { this.fechaUltimaConexion = fechaUltimaConexion; }

    public String getDeviceUuid() { return deviceUuid; }
    public void setDeviceUuid(String deviceUuid) { this.deviceUuid = deviceUuid; }

    public boolean isRemoteEnabled() { return remoteEnabled; }
    public void setRemoteEnabled(boolean remoteEnabled) { this.remoteEnabled = remoteEnabled; }

    public boolean isRemoteVerified() { return remoteVerified; }
    public void setRemoteVerified(boolean remoteVerified) { this.remoteVerified = remoteVerified; }

    public String getRemoteHost() { return remoteHost; }
    public void setRemoteHost(String remoteHost) { this.remoteHost = remoteHost; }

    public int getRemotePort() { return remotePort; }
    public void setRemotePort(int remotePort) { this.remotePort = remotePort; }

    public String getRemotePath() { return remotePath; }
    public void setRemotePath(String remotePath) { this.remotePath = remotePath; }

    public String construirUrlRtsp() {
        StringBuilder sb = new StringBuilder("rtsp://").append(host == null ? "" : host);
        if (puertoRtsp > 0) sb.append(":").append(puertoRtsp);
        if (!vacio(rutaRtsp)) {
            sb.append(rutaRtsp.startsWith("/") ? rutaRtsp : "/" + rutaRtsp);
        }
        return sb.toString();
    }

    /**
     * La cámara cambió de IP (DHCP o paso de Ethernet a Wi-Fi): actualiza host, endpoint
     * de dispositivo, endpoint ONVIF/PTZ y UUID. Solo sustituye el host completo dentro de
     * la URL ONVIF ("//host:" o "//host/"), para que .2 no reemplace parte de .25.
     */
    public void aplicarNuevoHost(String nuevoHost, String xAddr, String uuid) {
        String anterior = host;
        host = nuevoHost;
        if (!vacio(xAddr)) endpointDevice = xAddr;
        if (!vacio(uuid)) deviceUuid = uuid;
        if (!vacio(endpointOnvif) && !vacio(anterior)) {
            endpointOnvif = endpointOnvif
                    .replace("//" + anterior + ":", "//" + nuevoHost + ":")
                    .replace("//" + anterior + "/", "//" + nuevoHost + "/");
            if (endpointOnvif.endsWith("//" + anterior)) {
                endpointOnvif = endpointOnvif.substring(0, endpointOnvif.length() - anterior.length()) + nuevoHost;
            }
        }
    }

    public String construirUrlRtspRemota() {
        if (!remoteEnabled || !remoteVerified || vacio(remoteHost)) return "";
        int port = remotePort > 0 ? remotePort : puertoRtsp;
        String path = vacio(remotePath) ? rutaRtsp : remotePath;
        StringBuilder sb = new StringBuilder("rtsp://").append(remoteHost);
        if (port > 0) sb.append(":").append(port);
        if (!vacio(path)) sb.append(path.startsWith("/") ? path : "/" + path);
        return sb.toString();
    }

    /** Equivalente a TextUtils.isEmpty() para String, sin depender de Android. */
    private static boolean vacio(String s) {
        return s == null || s.isEmpty();
    }
}
