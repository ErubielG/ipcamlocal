package com.ipcamlocal.app;

import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.util.Rational;
import android.view.MotionEvent;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.videolan.libvlc.util.VLCVideoLayout;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String DEFAULT_PROFILE_TOKEN = "MainProfileToken";
    private static final String TAG = "IPCam-Beta";
    private static final int REQUEST_SELECCIONAR_CAMARA = 1001;
    private static final int TIMEOUT_RED_MS = 4000;
    private static final long PTZ_MIN_TAP_MS = 280L;

    private enum CameraError {
        NETWORK_UNREACHABLE("No se pudo alcanzar la cámara en la red. Revisa la IP y que el celular esté en la misma red WiFi."),
        TIMEOUT("La cámara no respondió a tiempo. Puede estar apagada, ocupada, o la red está saturada."),
        AUTH_FAILED("La cámara rechazó el usuario o la contraseña."),
        INVALID_URL("La dirección RTSP no es válida."),
        ONVIF_ERROR("Falló la comunicación con el control PTZ (ONVIF). El video no se ve afectado."),
        UNKNOWN("No se pudo determinar la causa exacta del error.");

        private final String friendly;
        CameraError(String friendly) { this.friendly = friendly; }
        String friendly() { return friendly; }
    }

    private final StringBuilder diagLog = new StringBuilder();
    private final SimpleDateFormat diagFmt = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);
    private static final int DIAG_MAX_CHARS = 30000;

    private LinearLayout connectionPanel;
    private LinearLayout cameraPanel;
    private LinearLayout headerPanel;
    private LinearLayout ptzPanel;
    private LinearLayout actionsPanel;
    private VLCVideoLayout videoLayout;
    private View videoContainer;
    private View rootMain;
    private boolean pantallaCompleta;
    private VideoZoomController zoom;
    private ImageButton fullscreenButton;
    private Runnable ocultarBotonPantallaCompleta;
    private Button snapshotButton;
    private CameraProfileDbHelper dbHelper;
    private int[] paddingRootNormal;
    private int[] paddingCameraPanelNormal;
    private EditText rtspInput;
    private EditText userInput;
    private EditText passwordInput;
    private EditText onvifInput;
    private TextView statusText;
    private TextView connectedStatus;
    private TextView ptzTitle;
    private TextView ptzStatus;
    private Button connectButton;
    private Button disconnectButton;
    private Button settingsButton;
    private Button pipButton;
    private Switch backgroundSwitch;
    private Button upButton;
    private Button downButton;
    private Button leftButton;
    private Button rightButton;
    private Button stopButton;
    private SharedPreferences prefs;
    private boolean backgroundEnabled;

    private CameraSession cameraSession;
    private boolean conectado;
    private String profileTokenActivo = DEFAULT_PROFILE_TOKEN;

    // Acceso remoto solo se carga desde un CameraProfile guardado.
    private long selectedProfileId = CameraProfile.SIN_ID;
    private String selectedLocalUrl = "";
    private boolean remoteEnabled;
    private boolean remoteVerified;
    private String remoteHost = "";
    private int remotePort;
    private String remotePath = "";
    private boolean usingRemoteAccess;

    private final Handler uiHandler = new Handler(Looper.getMainLooper());
    private Runnable hideConnectedStatus;

    private final ExecutorService ptzExecutor = Executors.newSingleThreadExecutor();
    private final Handler ptzHandler = new Handler(Looper.getMainLooper());
    private long ptzPressStartedAt;
    private Runnable ptzStopPendiente;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        diag("onCreate()");
        setContentView(R.layout.activity_main);
        aplicarInsets();
        enlazarVistas();
        configurarSesion();
        configurarInterfaz(savedInstanceState);
    }

    private void aplicarInsets() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            View root = findViewById(android.R.id.content);
            root.setOnApplyWindowInsetsListener((v, insets) -> {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            });
        }
    }

    private void enlazarVistas() {
        connectionPanel = findViewById(R.id.connection_panel);
        cameraPanel = findViewById(R.id.camera_panel);
        videoContainer = findViewById(R.id.video_container);
        fullscreenButton = findViewById(R.id.fullscreen_button);
        rootMain = findViewById(R.id.root_main);
        headerPanel = findViewById(R.id.header_panel);
        ptzPanel = findViewById(R.id.ptz_panel);
        actionsPanel = findViewById(R.id.actions_panel);
        videoLayout = findViewById(R.id.video_layout);
        rtspInput = findViewById(R.id.rtsp_input);
        userInput = findViewById(R.id.user_input);
        passwordInput = findViewById(R.id.password_input);
        onvifInput = findViewById(R.id.onvif_input);
        statusText = findViewById(R.id.status_text);
        connectedStatus = findViewById(R.id.connected_status);
        ptzTitle = findViewById(R.id.ptz_title);
        ptzStatus = findViewById(R.id.ptz_status);
        connectButton = findViewById(R.id.connect_button);
        disconnectButton = findViewById(R.id.disconnect_button);
        settingsButton = findViewById(R.id.settings_button);
        pipButton = findViewById(R.id.pip_button);
        snapshotButton = findViewById(R.id.snapshot_button);
        backgroundSwitch = findViewById(R.id.background_switch);
        upButton = findViewById(R.id.up_button);
        downButton = findViewById(R.id.down_button);
        leftButton = findViewById(R.id.left_button);
        rightButton = findViewById(R.id.right_button);
        stopButton = findViewById(R.id.stop_button);

    }

    private void configurarSesion() {
        cameraSession = CameraSessionHolder.get(this);
        cameraSession.setListener(new CameraSession.Listener() {
            @Override public void onStateChanged(CameraSession.State state, String message) {
                runOnUiThread(() -> {
                    mostrarEstadoSesion(state, message);
                    diag("CameraSession: " + state + " - " + message);
                    conectado = state == CameraSession.State.PLAYING;
                    connectButton.setEnabled(state != CameraSession.State.CONNECTING);
                    pipButton.setEnabled(conectado);
                    snapshotButton.setEnabled(conectado);
                    actualizarParametrosPiP();
                    if (backgroundEnabled && (state == CameraSession.State.CONNECTING
                            || state == CameraSession.State.PLAYING
                            || state == CameraSession.State.WAITING_RETRY)) {
                        iniciarServicioSegundoPlano();
                    } else if (state == CameraSession.State.DISCONNECTED_MANUALLY
                            || state == CameraSession.State.RELEASED) {
                        detenerServicioSegundoPlano();
                    }
                });
            }

            @Override public void onTechnicalError(String detail) {
                diag("CameraSession error: " + detail);
            }

            @Override public void onRetryScheduled(int attempt, long delayMs) {
                diag("Reconexión programada: intento=" + attempt + " delay=" + delayMs + "ms");
            }
        });
    }

    private void mostrarEstadoSesion(CameraSession.State state, String message) {
        if (hideConnectedStatus != null) {
            uiHandler.removeCallbacks(hideConnectedStatus);
            hideConnectedStatus = null;
        }

        if (state == CameraSession.State.PLAYING) {
            connectedStatus.setVisibility(View.VISIBLE);
            connectedStatus.setText(usingRemoteAccess ? "Conectado · acceso remoto" : "Conectado");
            hideConnectedStatus = () -> {
                if (cameraSession != null && cameraSession.isPlaying()) {
                    connectedStatus.setText("");
                    connectedStatus.setVisibility(View.GONE);
                }
            };
            uiHandler.postDelayed(hideConnectedStatus, 1200L);
        } else {
            connectedStatus.setVisibility(View.VISIBLE);
            connectedStatus.setText(message);
        }
    }

    private void configurarInterfaz(Bundle savedInstanceState) {
        prefs = getSharedPreferences("ipcam_settings", MODE_PRIVATE);
        rtspInput.setText(prefs.getString("last_rtsp", ""));
        userInput.setText(prefs.getString("last_user", ""));
        onvifInput.setText(prefs.getString("last_onvif", ""));
        cargarUltimaCamaraGuardada();
        backgroundEnabled = prefs.getBoolean("background_enabled", false);
        cameraSession.setBackgroundAllowed(backgroundEnabled);
        cameraSession.setUiVisible(true);
        backgroundSwitch.setChecked(backgroundEnabled);
        backgroundSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            backgroundEnabled = isChecked;
            cameraSession.setBackgroundAllowed(isChecked);
            prefs.edit().putBoolean("background_enabled", isChecked).apply();
            // El PiP automático depende del interruptor: aplicarlo ya, no en el próximo cambio de estado.
            actualizarParametrosPiP();
            if (isChecked) {
                if (cameraSession.hasLiveSession()) iniciarServicioSegundoPlano();
                Toast.makeText(this, "Segundo plano activado", Toast.LENGTH_SHORT).show();
            } else {
                detenerServicioSegundoPlano();
                Toast.makeText(this, "Segundo plano desactivado", Toast.LENGTH_SHORT).show();
            }
        });

        connectButton.setOnClickListener(v -> conectar());
        disconnectButton.setOnClickListener(v -> desconectar());
        settingsButton.setOnClickListener(v -> mostrarConfiguracion());
        pipButton.setOnClickListener(v -> entrarEnPiP());
        zoom = new VideoZoomController(this, videoLayout, this::alTocarVideo);
        videoContainer.setOnTouchListener(zoom);
        fullscreenButton.setOnClickListener(v -> alternarPantallaCompleta());
        pipButton.setEnabled(false);
        snapshotButton.setOnClickListener(v -> tomarFoto());
        snapshotButton.setEnabled(false);

        Button savedCamerasButton = new Button(this);
        savedCamerasButton.setText("Cámaras guardadas");
        savedCamerasButton.setAllCaps(false);
        savedCamerasButton.setOnClickListener(v -> abrirMisCamaras());
        connectionPanel.addView(savedCamerasButton, 0);

        settingsButton.setOnLongClickListener(v -> {
            abrirMisCamaras();
            return true;
        });

        View.OnLongClickListener copiarDiag = v -> {
            copiarDiagnostico();
            return true;
        };
        connectedStatus.setOnLongClickListener(copiarDiag);
        statusText.setOnLongClickListener(copiarDiag);

        instalarBotonPTZ(upButton, 0.0f, 0.55f);
        instalarBotonPTZ(downButton, 0.0f, -0.55f);
        instalarBotonPTZ(leftButton, -0.55f, 0.0f);
        instalarBotonPTZ(rightButton, 0.55f, 0.0f);
        stopButton.setOnClickListener(v -> enviarStopPTZ());

        actualizarParametrosPiP();
        if (cameraSession.hasLiveSession()) {
            connectionPanel.setVisibility(View.GONE);
            cameraPanel.setVisibility(View.VISIBLE);
            videoLayout.post(() -> cameraSession.attachSurface(videoLayout));
            pipButton.setEnabled(cameraSession.isPlaying());
            snapshotButton.setEnabled(cameraSession.isPlaying());
            if (backgroundEnabled) iniciarServicioSegundoPlano();
        } else if (savedInstanceState == null) {
            connectionPanel.post(this::abrirMisCamaras);
        }
    }

    @Override protected void onStart() {
        super.onStart();
        diag("onStart()");
        cameraSession.setUiVisible(true);
        if (cameraPanel.getVisibility() == View.VISIBLE) {
            videoLayout.post(() -> cameraSession.attachSurface(videoLayout));
        }
    }

    @Override protected void onResume() {
        super.onResume();
        diag("onResume()");
    }

    @Override protected void onPause() {
        super.onPause();
        diag("onPause()");
    }

    @Override protected void onStop() {
        super.onStop();
        diag("onStop()");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode()) return;
        cameraSession.detachSurface();
        cameraSession.setUiVisible(false);
    }

    private void conectar() {
        conectar(true);
    }

    /** @param permitirRedescubrir false en el reintento tras actualizar la IP (evita bucles). */
    private void conectar(boolean permitirRedescubrir) {
        String rawUrl = rtspInput.getText().toString().trim();
        String user = userInput.getText().toString().trim();
        String password = passwordInput.getText().toString();

        if (TextUtils.isEmpty(rawUrl) || !rawUrl.toLowerCase(Locale.US).startsWith("rtsp://")) {
            mostrarError(CameraError.INVALID_URL, "RTSP vacío o inválido", statusText);
            return;
        }

        Uri uri = Uri.parse(rawUrl);
        String host = uri.getHost();
        if (TextUtils.isEmpty(host)) {
            mostrarError(CameraError.INVALID_URL, "No se pudo extraer host", statusText);
            return;
        }
        int port = uri.getPort() != -1 ? uri.getPort() : 554;

        // Sin usuario la cámara rechaza el RTSP y LibVLC solo informa un error genérico
        // (EncounteredError) que se reintentaría en bucle. Mejor avisar antes.
        if (TextUtils.isEmpty(user)) {
            statusText.setText("Falta el usuario de la cámara. Elige la cámara en «Cámaras guardadas» "
                    + "o escribe usuario y contraseña.");
            return;
        }

        prefs.edit()
                .putString("last_rtsp", rawUrl)
                .putString("last_user", user)
                .putString("last_onvif", onvifInput.getText().toString().trim())
                .apply();

        connectButton.setEnabled(false);
        statusText.setText("Verificando cámara en la red local…");

        boolean puedeUsarRemoto = selectedProfileId != CameraProfile.SIN_ID
                && remoteEnabled && remoteVerified
                && rawUrl.equals(selectedLocalUrl)
                && !TextUtils.isEmpty(remoteHost);

        verificarAlcanceRed(host, port,
                () -> iniciarConexionRtsp(rawUrl, user, password, false),
                (error, detail) -> {
                    if (permitirRedescubrir && rawUrl.equals(selectedLocalUrl)
                            && buscarCamaraConNuevaIp(error, detail, puedeUsarRemoto, user, password)) {
                        return;
                    }
                    continuarTrasFalloLan(error, detail, puedeUsarRemoto, user, password);
                });
    }

    private void continuarTrasFalloLan(CameraError error, String detail, boolean puedeUsarRemoto,
                                       String user, String password) {
        if (!puedeUsarRemoto) {
            connectButton.setEnabled(true);
            mostrarError(error, detail, statusText);
            return;
        }
        probarFallbackRemoto(user, password, error, detail);
    }

    /**
     * La IP guardada no responde. Si la cámara guardada tiene UUID ONVIF, se busca en la red;
     * si aparece con otra IP (DHCP), se actualiza el perfil y se reintenta UNA vez.
     * Devuelve false si no hay nada que buscar (el llamador muestra el error normal).
     */
    private boolean buscarCamaraConNuevaIp(CameraError error, String detail, boolean puedeUsarRemoto,
                                           String user, String password) {
        CameraProfile perfil = perfilSeleccionado();
        if (perfil == null || TextUtils.isEmpty(perfil.getDeviceUuid())) return false;

        final String uuid = perfil.getDeviceUuid();
        final String hostAnterior = perfil.getHost();
        statusText.setText("La cámara no responde en " + hostAnterior + ". Buscándola en tu red…");
        diag("Redescubrimiento por UUID tras fallo en " + hostAnterior);

        OnvifDiscovery.buscar(this, (results, discoveryError) -> runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            OnvifDiscoveryResult match = null;
            if (discoveryError == null && results != null) {
                for (OnvifDiscoveryResult r : results) {
                    String h = r.host();
                    if (!TextUtils.isEmpty(h) && !h.equals(hostAnterior) && uuid.equalsIgnoreCase(r.uuid)) {
                        match = r;
                        break;
                    }
                }
            }
            if (match == null) {
                diag("Redescubrimiento: la cámara no apareció con otra IP");
                continuarTrasFalloLan(error, detail, puedeUsarRemoto, user, password);
                return;
            }

            perfil.aplicarNuevoHost(match.host(), match.xAddr, match.uuid);
            perfilesDb().actualizar(perfil);
            selectedLocalUrl = perfil.construirUrlRtsp();
            rtspInput.setText(selectedLocalUrl);
            onvifInput.setText(perfil.getEndpointOnvif() == null ? "" : perfil.getEndpointOnvif());
            diag("La cámara cambió de IP: " + hostAnterior + " -> " + match.host());
            Toast.makeText(this, "La cámara cambió de IP: " + match.host(), Toast.LENGTH_LONG).show();
            conectar(false);
        }));
        return true;
    }

    private CameraProfileDbHelper perfilesDb() {
        if (dbHelper == null) dbHelper = new CameraProfileDbHelper(this);
        return dbHelper;
    }

    private CameraProfile perfilSeleccionado() {
        if (selectedProfileId == CameraProfile.SIN_ID) return null;
        try {
            return perfilesDb().obtener(selectedProfileId);
        } catch (Exception e) {
            Log.w(TAG, "No se pudo leer el perfil " + selectedProfileId, e);
            return null;
        }
    }

    /**
     * Al abrir la app, carga la última cámara usada con sus credenciales del Keystore.
     * Antes solo se restauraban URL y usuario sueltos, y la contraseña quedaba vacía.
     */
    private void cargarUltimaCamaraGuardada() {
        long id = prefs.getLong("last_profile_id", CameraProfile.SIN_ID);
        if (id == CameraProfile.SIN_ID) return;
        try {
            CameraProfile perfil = perfilesDb().obtener(id);
            if (perfil == null) return;
            aplicarSeleccionCamara(CamerasActivity.crearResultado(perfil, new SecureCredentialsStore(this)));
        } catch (Exception e) {
            Log.w(TAG, "No se pudo cargar la última cámara", e);
        }
    }

    private void probarFallbackRemoto(
            String user, String password,
            CameraError localError, String localDetail
    ) {
        int port = remotePort > 0 ? remotePort : 554;
        String path = TextUtils.isEmpty(remotePath) ? "" : remotePath;
        StringBuilder remote = new StringBuilder("rtsp://")
                .append(remoteHost)
                .append(":")
                .append(port);
        if (!TextUtils.isEmpty(path)) remote.append(path.startsWith("/") ? path : "/" + path);
        String remoteUrl = remote.toString();

        statusText.setText("La cámara no está disponible por LAN. Probando acceso remoto…");
        verificarAlcanceRed(remoteHost, port,
                () -> iniciarConexionRtsp(remoteUrl, user, password, true),
                (remoteError, remoteDetail) -> {
                    connectButton.setEnabled(true);
                    diag("LAN falló: " + localDetail + " | remoto falló: " + remoteDetail);
                    statusText.setText("No pude alcanzar la cámara ni por red local ni por acceso remoto.");
                });
    }

    private void iniciarConexionRtsp(String rawUrl, String user, String password, boolean remoto) {
        usingRemoteAccess = remoto;
        String finalUrl = RtspUrlBuilder.addCredentials(rawUrl, user, password);
        connectionPanel.setVisibility(View.GONE);
        cameraPanel.setVisibility(View.VISIBLE);
        statusText.setText("");
        connectedStatus.setVisibility(View.VISIBLE);
        connectedStatus.setText("Preparando video…");
        configurarPtzParaModoConexion();

        // IMPORTANT: this matches the behavior of the original build that was proven
        // to work on the user's phone. The panel becomes visible first, then we wait
        // for VLCVideoLayout to complete a layout pass, and only then attach + connect.
        videoLayout.post(() -> {
            diag("VLC surface before connect: attached=" + videoLayout.isAttachedToWindow()
                    + " width=" + videoLayout.getWidth()
                    + " height=" + videoLayout.getHeight());
            diagRtspTarget(finalUrl, remoto);
            cameraSession.setUiVisible(true);
            cameraSession.attachSurface(videoLayout);
            // attachSurface itself posts to the View; a second post guarantees that the
            // attachment request has been queued before connect() asks CameraSession to start.
            videoLayout.post(() -> cameraSession.connect(finalUrl));
        });

        if (backgroundEnabled) iniciarServicioSegundoPlano();
    }

    private void diagRtspTarget(String url, boolean remoto) {
        try {
            Uri u = Uri.parse(url);
            String authority = u.getEncodedAuthority();
            boolean credentials = authority != null && authority.contains("@");
            int port = u.getPort() > 0 ? u.getPort() : 554;
            String path = u.getEncodedPath();
            if (TextUtils.isEmpty(path)) path = "/";
            diag("RTSP target: host=" + u.getHost()
                    + " port=" + port
                    + " path=" + path
                    + " credentials=" + (credentials ? "yes" : "no")
                    + " mode=" + (remoto ? "REMOTE" : "LAN"));
        } catch (Exception e) {
            diag("RTSP target: no se pudo analizar la URL");
        }
    }

    private void configurarPtzParaModoConexion() {
        boolean enable = !usingRemoteAccess;
        upButton.setEnabled(enable);
        downButton.setEnabled(enable);
        leftButton.setEnabled(enable);
        rightButton.setEnabled(enable);
        stopButton.setEnabled(enable);

        if (usingRemoteAccess) {
            ptzStatus.setText("Acceso remoto activo · PTZ remoto desactivado por seguridad.");
        } else {
            ptzStatus.setText("PTZ detenido");
        }
    }

    private interface FallaRedListener {
        void onFalla(CameraError error, String detalleTecnico);
    }

    private void verificarAlcanceRed(String host, int port, Runnable success, FallaRedListener failure) {
        new Thread(() -> {
            try (Socket socket = new Socket()) {
                socket.connect(new InetSocketAddress(host, port), TIMEOUT_RED_MS);
                runOnUiThread(success);
            } catch (UnknownHostException e) {
                runOnUiThread(() -> failure.onFalla(CameraError.NETWORK_UNREACHABLE, "Host no resoluble: " + host));
            } catch (SocketTimeoutException e) {
                runOnUiThread(() -> failure.onFalla(CameraError.TIMEOUT, "Timeout a " + host + ":" + port));
            } catch (IOException e) {
                runOnUiThread(() -> failure.onFalla(CameraError.NETWORK_UNREACHABLE,
                        "IOException " + e.getClass().getSimpleName() + " a " + host + ":" + port));
            }
        }, "IPCam-NetworkCheck").start();
    }

    private void desconectar() {
        salirPantallaCompleta();
        detenerServicioSegundoPlano();
        cameraSession.disconnect();
        conectado = false;
        usingRemoteAccess = false;
        configurarPtzParaModoConexion();
        cameraPanel.setVisibility(View.GONE);
        connectionPanel.setVisibility(View.VISIBLE);
        statusText.setText("Desconectado");
        connectButton.setEnabled(true);
        pipButton.setEnabled(false);
        snapshotButton.setEnabled(false);
        zoom.reset();
        actualizarParametrosPiP();
    }

    private void iniciarServicioSegundoPlano() {
        Intent intent = new Intent(this, BackgroundCameraService.class);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent);
            else startService(intent);
            diag("Foreground service activo");
        } catch (Exception e) {
            diag("No se pudo iniciar foreground service: " + e.getClass().getSimpleName());
            Toast.makeText(this, "No se pudo activar el segundo plano", Toast.LENGTH_SHORT).show();
        }
    }

    private void detenerServicioSegundoPlano() {
        stopService(new Intent(this, BackgroundCameraService.class));
        diag("Foreground service detenido");
    }

    private void actualizarParametrosPiP() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder()
                .setAspectRatio(new Rational(16, 9));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // PiP automático al salir SOLO con «Continuar en segundo plano» activado.
            // El botón PiP sigue funcionando siempre.
            builder.setAutoEnterEnabled(backgroundEnabled
                    && cameraSession != null && cameraSession.isPlaying()
                    && cameraPanel != null && cameraPanel.getVisibility() == View.VISIBLE);
        }
        setPictureInPictureParams(builder.build());
    }

    private void entrarEnPiP() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !cameraSession.isPlaying()) return;
        try {
            PictureInPictureParams params = new PictureInPictureParams.Builder()
                    .setAspectRatio(new Rational(16, 9))
                    .build();
            enterPictureInPictureMode(params);
        } catch (Exception e) {
            diag("PiP error: " + e.getClass().getSimpleName());
        }
    }

    @Override public void onUserLeaveHint() {
        super.onUserLeaveHint();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                && backgroundEnabled && cameraSession.isPlaying() && cameraPanel.getVisibility() == View.VISIBLE) {
            entrarEnPiP();
        }
    }

    @Override public void onPictureInPictureModeChanged(boolean inPip, Configuration newConfig) {
        super.onPictureInPictureModeChanged(inPip, newConfig);
        if (inPip) ponerInterfazPiP(); else restaurarInterfazCompleta();
    }

    private void ponerInterfazPiP() {
        zoom.reset();
        mostrarControles(false);
        cancelarOcultarBoton();
        fullscreenButton.setVisibility(View.GONE);
    }

    private void restaurarInterfazCompleta() {
        // Si se entró en PiP desde pantalla completa, se vuelve a pantalla completa.
        mostrarControles(!pantallaCompleta);
        if (pantallaCompleta) mostrarBotonTemporal();
        else fullscreenButton.setVisibility(View.VISIBLE);
        videoLayout.post(() -> cameraSession.attachSurface(videoLayout));
    }

    /** Controles de la vista de cámara. PiP y pantalla completa ocultan el mismo conjunto. */
    private void mostrarControles(boolean visibles) {
        int v = visibles ? View.VISIBLE : View.GONE;
        headerPanel.setVisibility(v);
        ptzTitle.setVisibility(v);
        ptzPanel.setVisibility(v);
        ptzStatus.setVisibility(v);
        actionsPanel.setVisibility(v);
        backgroundSwitch.setVisibility(v);
    }

    // ----------------------------------------------------------------
    // Pantalla completa: tocar el video. Misma CameraSession, sin reconectar.
    // El manifest declara configChanges de orientación, así que la Activity
    // no se recrea al girar; LibVLC reajusta la imagen con su OnLayoutChangeListener.
    // ----------------------------------------------------------------

    /**
     * Un toque en el video: en vista normal entra en pantalla completa; en pantalla completa
     * muestra u oculta el botón de salir (como YouTube). Atrás también sale.
     */
    private void alTocarVideo() {
        if (!pantallaCompleta) {
            entrarPantallaCompleta();
        } else if (fullscreenButton.getVisibility() == View.VISIBLE) {
            cancelarOcultarBoton();
            fullscreenButton.setVisibility(View.GONE);
        } else {
            mostrarBotonTemporal();
        }
    }

    /** En pantalla completa el botón se oculta solo tras 3 s para no tapar el video. */
    private void mostrarBotonTemporal() {
        cancelarOcultarBoton();
        fullscreenButton.setVisibility(View.VISIBLE);
        ocultarBotonPantallaCompleta = () -> {
            if (pantallaCompleta) fullscreenButton.setVisibility(View.GONE);
        };
        uiHandler.postDelayed(ocultarBotonPantallaCompleta, 3000L);
    }

    private void cancelarOcultarBoton() {
        if (ocultarBotonPantallaCompleta != null) {
            uiHandler.removeCallbacks(ocultarBotonPantallaCompleta);
            ocultarBotonPantallaCompleta = null;
        }
    }

    private void alternarPantallaCompleta() {
        if (pantallaCompleta) salirPantallaCompleta();
        else entrarPantallaCompleta();
    }

    private void entrarPantallaCompleta() {
        if (pantallaCompleta || cameraPanel.getVisibility() != View.VISIBLE) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isInPictureInPictureMode()) return;

        pantallaCompleta = true;
        zoom.reset();
        paddingRootNormal = leerPadding(rootMain);
        paddingCameraPanelNormal = leerPadding(cameraPanel);
        rootMain.setPadding(0, 0, 0, 0);
        cameraPanel.setPadding(0, 0, 0, 0);
        mostrarControles(false);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        ocultarBarrasSistema(true);
        fullscreenButton.setImageResource(R.drawable.ic_fullscreen_exit);
        fullscreenButton.setContentDescription("Salir de pantalla completa");
        mostrarBotonTemporal();
    }

    private void salirPantallaCompleta() {
        if (!pantallaCompleta) return;
        pantallaCompleta = false;
        zoom.reset();
        ocultarBarrasSistema(false);
        // La vista normal está diseñada solo en vertical (manifest: screenOrientation=portrait).
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        aplicarPadding(rootMain, paddingRootNormal);
        aplicarPadding(cameraPanel, paddingCameraPanelNormal);
        mostrarControles(true);
        cancelarOcultarBoton();
        fullscreenButton.setImageResource(R.drawable.ic_fullscreen);
        fullscreenButton.setContentDescription("Pantalla completa");
        fullscreenButton.setVisibility(View.VISIBLE);
    }

    private static int[] leerPadding(View v) {
        return new int[] {v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), v.getPaddingBottom()};
    }

    private static void aplicarPadding(View v, int[] p) {
        if (p != null) v.setPadding(p[0], p[1], p[2], p[3]);
    }

    @SuppressWarnings("deprecation")
    private void ocultarBarrasSistema(boolean ocultar) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller == null) return;
            if (ocultar) {
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(WindowInsets.Type.systemBars());
            } else {
                controller.show(WindowInsets.Type.systemBars());
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(ocultar
                    ? View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    : 0);
        }
    }

    // ----------------------------------------------------------------
    // Foto del video. LibVLC 3.7.6 no expone captura en Android: se lee el
    // fotograma del TextureView (fotograma completo, sin el zoom aplicado).
    // ----------------------------------------------------------------

    private void tomarFoto() {
        TextureView texture = buscarTextureView(videoLayout);
        Bitmap foto = (texture != null && texture.isAvailable() && cameraSession.isPlaying())
                ? texture.getBitmap() : null;
        if (foto == null) {
            Toast.makeText(this, "No hay video para capturar", Toast.LENGTH_SHORT).show();
            return;
        }
        snapshotButton.setEnabled(false);
        new Thread(() -> {
            String destino = null;
            try {
                destino = guardarFoto(foto);
            } catch (Exception e) {
                Log.w(TAG, "No se pudo guardar la foto", e);
            } finally {
                foto.recycle();
            }
            final String resultado = destino;
            runOnUiThread(() -> {
                snapshotButton.setEnabled(conectado);
                Toast.makeText(this, resultado != null
                        ? "Foto guardada en " + resultado
                        : "No se pudo guardar la foto", Toast.LENGTH_LONG).show();
            });
        }, "IPCam-Snapshot").start();
    }

    private static TextureView buscarTextureView(View v) {
        if (v instanceof TextureView) return (TextureView) v;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                TextureView t = buscarTextureView(g.getChildAt(i));
                if (t != null) return t;
            }
        }
        return null;
    }

    /** Hilo de fondo. Android 10+: galería (Pictures/IPCamLocal), sin permisos. */
    private String guardarFoto(Bitmap foto) throws IOException {
        String nombre = "IPCam_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".jpg";

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.DISPLAY_NAME, nombre);
            values.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/IPCamLocal");
            values.put(MediaStore.Images.Media.IS_PENDING, 1);
            ContentResolver resolver = getContentResolver();
            Uri uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IOException("MediaStore no creó la imagen");
            try (OutputStream out = resolver.openOutputStream(uri)) {
                if (out == null || !foto.compress(Bitmap.CompressFormat.JPEG, 92, out)) {
                    throw new IOException("No se pudo escribir el JPEG");
                }
            } catch (IOException e) {
                resolver.delete(uri, null, null);
                throw e;
            }
            values.clear();
            values.put(MediaStore.Images.Media.IS_PENDING, 0);
            resolver.update(uri, values, null, null);
            return "Galería › Pictures/IPCamLocal";
        }

        // Android 6 a 9: carpeta de la app (no requiere permiso de almacenamiento).
        File dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        if (dir == null) throw new IOException("Almacenamiento no disponible");
        File archivo = new File(dir, nombre);
        try (OutputStream out = new FileOutputStream(archivo)) {
            if (!foto.compress(Bitmap.CompressFormat.JPEG, 92, out)) throw new IOException("No se pudo escribir el JPEG");
        }
        return archivo.getAbsolutePath();
    }

    @Override public void onBackPressed() {
        if (pantallaCompleta) {
            salirPantallaCompleta();
            return;
        }
        super.onBackPressed();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void abrirMisCamaras() {
        startActivityForResult(new Intent(this, CamerasActivity.class), REQUEST_SELECCIONAR_CAMARA);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_SELECCIONAR_CAMARA || resultCode != RESULT_OK || data == null) return;
        aplicarSeleccionCamara(data);
        prefs.edit().putLong("last_profile_id", selectedProfileId).apply();
    }

    /** Rellena la pantalla de conexión con una cámara guardada (incluida su contraseña). */
    private void aplicarSeleccionCamara(Intent data) {
        String host = data.getStringExtra(CamerasActivity.EXTRA_HOST);
        int port = data.getIntExtra(CamerasActivity.EXTRA_PUERTO_RTSP, 0);
        String path = data.getStringExtra(CamerasActivity.EXTRA_RUTA_RTSP);
        String onvif = data.getStringExtra(CamerasActivity.EXTRA_ENDPOINT_ONVIF);
        String user = data.getStringExtra(CamerasActivity.EXTRA_USUARIO);
        String password = data.getStringExtra(CamerasActivity.EXTRA_PASSWORD);
        String profileToken = data.getStringExtra(CamerasActivity.EXTRA_PROFILE_TOKEN);

        selectedProfileId = data.getLongExtra(CamerasActivity.EXTRA_PROFILE_ID, CameraProfile.SIN_ID);
        remoteEnabled = data.getBooleanExtra(CamerasActivity.EXTRA_REMOTE_ENABLED, false);
        remoteVerified = data.getBooleanExtra(CamerasActivity.EXTRA_REMOTE_VERIFIED, false);
        remoteHost = data.getStringExtra(CamerasActivity.EXTRA_REMOTE_HOST);
        remotePort = data.getIntExtra(CamerasActivity.EXTRA_REMOTE_PORT, 0);
        remotePath = data.getStringExtra(CamerasActivity.EXTRA_REMOTE_PATH);
        if (remoteHost == null) remoteHost = "";
        if (remotePath == null) remotePath = "";

        profileTokenActivo = TextUtils.isEmpty(profileToken) ? DEFAULT_PROFILE_TOKEN : profileToken;
        StringBuilder url = new StringBuilder("rtsp://").append(host == null ? "" : host);
        if (port > 0) url.append(':').append(port);
        if (!TextUtils.isEmpty(path)) url.append(path.startsWith("/") ? path : "/" + path);

        selectedLocalUrl = url.toString();
        rtspInput.setText(selectedLocalUrl);
        userInput.setText(user == null ? "" : user);
        passwordInput.setText(password == null ? "" : password);
        onvifInput.setText(onvif == null ? "" : onvif);
        statusText.setText(remoteEnabled && remoteVerified
                ? "Cámara lista. Si la LAN no responde, se intentará el acceso remoto verificado."
                : "Cámara lista. Pulsa Conectar.");
    }

    private void mostrarConfiguracion() {
        cameraPanel.setVisibility(View.GONE);
        connectionPanel.setVisibility(View.VISIBLE);
        statusText.setText(cameraSession.isPlaying()
                ? "El video sigue activo. Puedes cambiar la configuración."
                : "Listo para conectar.");
        actualizarParametrosPiP();
    }

    private void instalarBotonPTZ(Button button, float x, float y) {
        button.setOnTouchListener((v, event) -> {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                if (ptzStopPendiente != null) {
                    ptzHandler.removeCallbacks(ptzStopPendiente);
                    ptzStopPendiente = null;
                }
                ptzPressStartedAt = SystemClock.uptimeMillis();
                enviarContinuousMove(x, y);
                v.setPressed(true);
                return true;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                v.setPressed(false);
                long held = SystemClock.uptimeMillis() - ptzPressStartedAt;
                long delay = Math.max(0L, PTZ_MIN_TAP_MS - held);
                ptzStopPendiente = () -> {
                    enviarStopPTZ();
                    ptzStopPendiente = null;
                };
                ptzHandler.postDelayed(ptzStopPendiente, delay);
                if (action == MotionEvent.ACTION_UP) v.performClick();
                return true;
            }
            return true;
        });
    }

    private void enviarContinuousMove(float x, float y) {
        String body = "<tptz:ContinuousMove xmlns:tptz=\"http://www.onvif.org/ver20/ptz/wsdl\" "
                + "xmlns:tt=\"http://www.onvif.org/ver10/schema\">"
                + "<tptz:ProfileToken>" + xmlEscape(profileTokenActivo) + "</tptz:ProfileToken>"
                + "<tptz:Velocity><tt:PanTilt x=\"" + x + "\" y=\"" + y + "\" "
                + "space=\"http://www.onvif.org/ver10/tptz/PanTiltSpaces/VelocityGenericSpace\"/>"
                + "</tptz:Velocity></tptz:ContinuousMove>";
        enviarSoapPTZ(body, "http://www.onvif.org/ver20/ptz/wsdl/ContinuousMove", "Moviendo cámara…");
    }

    private void enviarStopPTZ() {
        String body = "<tptz:Stop xmlns:tptz=\"http://www.onvif.org/ver20/ptz/wsdl\">"
                + "<tptz:ProfileToken>" + xmlEscape(profileTokenActivo) + "</tptz:ProfileToken>"
                + "<tptz:PanTilt>true</tptz:PanTilt><tptz:Zoom>true</tptz:Zoom></tptz:Stop>";
        enviarSoapPTZ(body, "http://www.onvif.org/ver20/ptz/wsdl/Stop", "PTZ detenido");
    }

    private void enviarSoapPTZ(String body, String action, String okText) {
        if (usingRemoteAccess) {
            ptzStatus.setText("PTZ remoto desactivado por seguridad. El acceso remoto de esta Beta es solo para video.");
            return;
        }
        String endpoint = onvifInput.getText().toString().trim();
        if (TextUtils.isEmpty(endpoint)) {
            ptzStatus.setText("Esta cámara no informó controles de movimiento.");
            return;
        }
        String user = userInput.getText().toString().trim();
        String password = passwordInput.getText().toString();
        ptzExecutor.execute(() -> {
            try {
                OnvifClient.soap(endpoint, action, body, user, password);
                runOnUiThread(() -> ptzStatus.setText(okText));
                diag("ONVIF PTZ OK: " + action);
            } catch (SecurityException e) {
                runOnUiThread(() -> ptzStatus.setText(CameraError.AUTH_FAILED.friendly()));
                diag("ONVIF PTZ: autenticación rechazada");
            } catch (Exception e) {
                runOnUiThread(() -> ptzStatus.setText(CameraError.ONVIF_ERROR.friendly()));
                diag("ONVIF PTZ: " + e.getClass().getSimpleName() + " endpoint=" + ocultarCredenciales(endpoint));
            }
        });
    }

    private static String xmlEscape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private void mostrarError(CameraError error, String detail, TextView target) {
        diag("Error [" + error + "]: " + detail);
        target.setText(error.friendly());
    }

    private String ocultarCredenciales(String url) {
        if (url == null) return null;
        try {
            Uri uri = Uri.parse(url);
            String authority = uri.getEncodedAuthority();
            if (authority != null && authority.contains("@")) {
                String host = authority.substring(authority.lastIndexOf('@') + 1);
                return uri.buildUpon().encodedAuthority(host).build().toString();
            }
        } catch (Exception ignored) {}
        return url;
    }

    private void diag(String msg) {
        Log.d(TAG, msg);
        synchronized (diagLog) {
            diagLog.append(diagFmt.format(new Date())).append(' ').append(msg).append('\n');
            if (diagLog.length() > DIAG_MAX_CHARS) diagLog.delete(0, diagLog.length() - DIAG_MAX_CHARS);
        }
    }

    private void copiarDiagnostico() {
        String text;
        synchronized (diagLog) { text = diagLog.toString(); }
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("IPCam diag", text));
            Toast.makeText(this, "Diagnóstico copiado", Toast.LENGTH_SHORT).show();
        }
    }

    @Override protected void onDestroy() {
        diag("onDestroy()");
        if (ptzStopPendiente != null) {
            ptzHandler.removeCallbacks(ptzStopPendiente);
            ptzStopPendiente = null;
        }
        ptzExecutor.shutdownNow();
        if (hideConnectedStatus != null) {
            uiHandler.removeCallbacks(hideConnectedStatus);
            hideConnectedStatus = null;
        }
        cancelarOcultarBoton();
        if (cameraSession != null) {
            cameraSession.setListener(null);
            if (!backgroundEnabled) CameraSessionHolder.release();
        }
        super.onDestroy();
    }
}
