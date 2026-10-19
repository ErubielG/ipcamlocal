package com.ipcamlocal.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class CamerasActivity extends Activity {
    public static final String EXTRA_PROFILE_ID = "extra_profile_id";
    public static final String EXTRA_HOST = "extra_host";
    public static final String EXTRA_PUERTO_RTSP = "extra_puerto_rtsp";
    public static final String EXTRA_RUTA_RTSP = "extra_ruta_rtsp";
    public static final String EXTRA_ENDPOINT_ONVIF = "extra_endpoint_onvif";
    public static final String EXTRA_USUARIO = "extra_usuario";
    public static final String EXTRA_PASSWORD = "extra_password";
    public static final String EXTRA_PROFILE_TOKEN = "extra_profile_token";

    public static final String EXTRA_REMOTE_ENABLED = "extra_remote_enabled";
    public static final String EXTRA_REMOTE_VERIFIED = "extra_remote_verified";
    public static final String EXTRA_REMOTE_HOST = "extra_remote_host";
    public static final String EXTRA_REMOTE_PORT = "extra_remote_port";
    public static final String EXTRA_REMOTE_PATH = "extra_remote_path";

    private CameraProfileDbHelper dbHelper;
    private SecureCredentialsStore secureStore;
    private ListView listaCamaras;
    private TextView textoVacio;
    private List<CameraProfile> perfiles = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        dbHelper = new CameraProfileDbHelper(this);
        secureStore = new SecureCredentialsStore(this);
        setContentView(construirVista());
        aplicarInsets();
        cargarPerfiles();
    }

    /** Android 15 dibuja bajo las barras del sistema: compensar como en MainActivity. */
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

    private View construirVista() {
        LinearLayout raiz = new LinearLayout(this);
        raiz.setOrientation(LinearLayout.VERTICAL);
        raiz.setBackgroundColor(Color.parseColor("#121212"));
        raiz.setPadding(dp(16), dp(24), dp(16), dp(16));

        TextView titulo = new TextView(this);
        titulo.setText("Mis cámaras");
        titulo.setTextColor(Color.WHITE);
        titulo.setTextSize(24);
        titulo.setPadding(0, 0, 0, dp(6));
        raiz.addView(titulo);

        TextView ayuda = new TextView(this);
        ayuda.setText("Toca una cámara para usarla. Mantén pulsado para editar, configurar red o acceso remoto.");
        ayuda.setTextColor(Color.parseColor("#AAAAAA"));
        ayuda.setPadding(0, 0, 0, dp(12));
        raiz.addView(ayuda);

        // Acciones arriba, antes de la lista (antes quedaban al fondo, bajo la barra de navegación).
        Button buscar = new Button(this);
        buscar.setText("Buscar cámaras en mi Wi‑Fi");
        buscar.setAllCaps(false);
        buscar.setOnClickListener(v -> mostrarBusqueda());
        raiz.addView(buscar);

        Button agregar = new Button(this);
        agregar.setText("Agregar cámara manualmente");
        agregar.setAllCaps(false);
        agregar.setOnClickListener(v -> mostrarDialogoEdicion(null, null));
        raiz.addView(agregar);

        textoVacio = new TextView(this);
        textoVacio.setText("Todavía no hay cámaras guardadas.\nBusca una en tu Wi‑Fi o agrégala manualmente.");
        textoVacio.setTextColor(Color.parseColor("#AAAAAA"));
        textoVacio.setPadding(0, dp(24), 0, dp(24));
        textoVacio.setGravity(Gravity.CENTER);
        raiz.addView(textoVacio);

        listaCamaras = new ListView(this);
        listaCamaras.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f
        ));
        listaCamaras.setDivider(new android.graphics.drawable.ColorDrawable(Color.parseColor("#2A2A2A")));
        listaCamaras.setDividerHeight(1);
        raiz.addView(listaCamaras);

        listaCamaras.setOnItemClickListener((parent, view, position, id) ->
                seleccionarPerfil(perfiles.get(position)));
        listaCamaras.setOnItemLongClickListener((parent, view, position, id) -> {
            mostrarMenuPerfil(perfiles.get(position));
            return true;
        });

        return raiz;
    }

    private int dp(int valor) {
        return Math.round(valor * getResources().getDisplayMetrics().density);
    }

    private void cargarPerfiles() {
        perfiles = dbHelper.listarTodos();
        List<String> lineas = new ArrayList<>();
        for (CameraProfile p : perfiles) {
            String estado = TextUtils.isEmpty(p.getHost()) ? "" : p.getHost();
            String remoto = p.isRemoteEnabled() && p.isRemoteVerified() ? " · remoto ✓" : "";
            lineas.add(p.getNombre() + remoto + "\n" + estado);
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<String>(
                this, android.R.layout.simple_list_item_1, lineas
        ) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView vista = (TextView) super.getView(position, convertView, parent);
                vista.setTextColor(Color.WHITE);
                vista.setPadding(dp(4), dp(12), dp(4), dp(12));
                vista.setTextSize(15);
                return vista;
            }
        };

        listaCamaras.setAdapter(adapter);
        textoVacio.setVisibility(perfiles.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private void seleccionarPerfil(CameraProfile p) {
        setResult(RESULT_OK, crearResultado(p, secureStore));
        finish();
    }

    /** Datos de una cámara guardada para MainActivity. También se usa al abrir la app. */
    static Intent crearResultado(CameraProfile p, SecureCredentialsStore secureStore) {
        Intent r = new Intent();
        r.putExtra(EXTRA_PROFILE_ID, p.getId());
        r.putExtra(EXTRA_HOST, p.getHost());
        r.putExtra(EXTRA_PUERTO_RTSP, p.getPuertoRtsp());
        r.putExtra(EXTRA_RUTA_RTSP, p.getRutaRtsp());
        r.putExtra(EXTRA_ENDPOINT_ONVIF, p.getEndpointOnvif());
        r.putExtra(EXTRA_USUARIO, p.getUsuario());
        r.putExtra(EXTRA_PASSWORD, secureStore.obtenerSecreto(p.getId()));
        r.putExtra(EXTRA_PROFILE_TOKEN, p.getProfileToken());

        r.putExtra(EXTRA_REMOTE_ENABLED, p.isRemoteEnabled());
        r.putExtra(EXTRA_REMOTE_VERIFIED, p.isRemoteVerified());
        r.putExtra(EXTRA_REMOTE_HOST, p.getRemoteHost());
        r.putExtra(EXTRA_REMOTE_PORT, p.getRemotePort());
        r.putExtra(EXTRA_REMOTE_PATH, p.getRemotePath());
        return r;
    }

    private void mostrarBusqueda() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(18), dp(12), dp(18), dp(8));

        TextView estado = new TextView(this);
        estado.setText("Buscando cámaras en la red local…");
        estado.setPadding(0, 0, 0, dp(10));
        box.addView(estado);

        ListView lista = new ListView(this);
        lista.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(300)
        ));
        box.addView(lista);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Buscar cámaras")
                .setView(box)
                .setNegativeButton("Cerrar", null)
                .create();
        dialog.show();

        OnvifDiscovery.buscar(this, (results, error) -> runOnUiThread(() -> {
            if (!dialog.isShowing()) return;

            if (error != null) {
                estado.setText("No se pudo buscar en la red. Verifica que estés conectado al mismo Wi‑Fi que la cámara.");
                return;
            }

            if (results.isEmpty()) {
                estado.setText("No encontré cámaras ONVIF. Puedes intentar de nuevo o agregarla manualmente.");
                return;
            }

            estado.setText("Encontré " + results.size() + (results.size() == 1 ? " cámara:" : " cámaras:"));
            List<String> labels = new ArrayList<>();
            for (OnvifDiscoveryResult r : results) {
                String host = r.host();
                labels.add(r.nombreAmigable() + (TextUtils.isEmpty(host) ? "" : "\n" + host));
            }

            lista.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels));
            lista.setOnItemClickListener((parent, view, position, id) -> {
                OnvifDiscoveryResult r = results.get(position);
                dialog.dismiss();
                mostrarDialogoEdicion(null, r);
            });
        }));
    }

    private void mostrarMenuPerfil(CameraProfile perfil) {
        String remoto = perfil.isRemoteEnabled() && perfil.isRemoteVerified()
                ? "Ver fuera de casa ✓" : "Ver fuera de casa";

        new AlertDialog.Builder(this)
                .setTitle(perfil.getNombre())
                .setItems(new String[]{
                        "Usar",
                        "Editar",
                        "Red / Wi‑Fi",
                        remoto,
                        "Eliminar",
                        "Cancelar"
                }, (dialog, which) -> {
                    if (which == 0) seleccionarPerfil(perfil);
                    else if (which == 1) mostrarDialogoEdicion(perfil, null);
                    else if (which == 2) mostrarAjustesRed(perfil);
                    else if (which == 3) mostrarFueraDeCasa(perfil);
                    else if (which == 4) confirmarEliminar(perfil);
                })
                .show();
    }

    /**
     * Camino recomendado: VPN (Tailscale) hacia la red de casa; la app usa la misma IP local.
     * El acceso manual (DDNS / puerto / ruta) queda como configuración avanzada.
     */
    private void mostrarFueraDeCasa(CameraProfile perfil) {
        new AlertDialog.Builder(this)
                .setTitle("Ver fuera de casa")
                .setMessage(
                        "Recomendado: una VPN hacia tu casa (por ejemplo Tailscale en una PC o "
                                + "Raspberry Pi que esté siempre encendida).\n\n"
                                + "Con la VPN activa en el teléfono, IPCamLocal se conecta a la misma IP de "
                                + "siempre (" + perfil.getHost() + "), con video y PTZ, sin abrir puertos "
                                + "en el router.\n\n"
                                + "Importante: reserva la IP de la cámara en tu router. Fuera de casa la "
                                + "app no puede buscarla si cambia de IP."
                )
                .setPositiveButton("Entendido", null)
                .setNeutralButton("Configuración avanzada", (d, w) -> mostrarAccesoRemoto(perfil))
                .show();
    }

    private void confirmarEliminar(CameraProfile perfil) {
        new AlertDialog.Builder(this)
                .setTitle("Eliminar cámara")
                .setMessage("¿Eliminar \"" + perfil.getNombre() + "\"? Esta acción no se puede deshacer.")
                .setPositiveButton("Eliminar", (dialog, which) -> {
                    dbHelper.eliminar(perfil.getId());
                    secureStore.eliminarSecreto(perfil.getId());
                    cargarPerfiles();
                    Toast.makeText(this, "Cámara eliminada", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ----------------------------------------------------------------
    // Red / Wi-Fi
    // Para esta familia usamos primero el CGI real del panel /web/admin.html.
    // ONVIF queda como tecnología de video/PTZ, no como fuente principal de Wi-Fi.
    // ----------------------------------------------------------------

    private void mostrarAjustesRed(CameraProfile perfil) {
        if (perfil.getId() == CameraProfile.SIN_ID) {
            mensaje("Red de la cámara", "Primero guarda la cámara.");
            return;
        }

        ProgressDialog progress = ProgressDialog.show(
                this,
                "Red de la cámara",
                "Leyendo configuración…",
                true,
                false
        );

        new Thread(() -> {
            try {
                Hi3510CgiClient client = cgiClient(perfil);
                if (!client.probe()) throw new java.io.IOException("CGI Hi3510 no detectado");
                Hi3510CgiClient.NetworkStatus network = client.getNetworkStatus();
                Hi3510CgiClient.WirelessConfig wireless = client.getWirelessConfig();

                runOnUiThread(() -> {
                    progress.dismiss();
                    mostrarPanelRedHi3510(perfil, network, wireless);
                });
            } catch (SecurityException e) {
                runOnUiThread(() -> {
                    progress.dismiss();
                    mensaje("Red de la cámara", "La cámara rechazó las credenciales HTTP. Revisa usuario y contraseña guardados.");
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progress.dismiss();
                    mensaje(
                            "Red de la cámara",
                            "No pude abrir la API local de red de este modelo. El video y PTZ pueden seguir funcionando, pero esta cámara no respondió al CGI Hi3510 esperado."
                    );
                    Log.w("IPCam-CGI", "No se pudo abrir configuración Hi3510", e);
                });
            }
        }, "IPCam-Hi3510-Network").start();
    }

    private Hi3510CgiClient cgiClient(CameraProfile perfil) {
        return new Hi3510CgiClient(
                perfil.getHost(),
                valorSeguro(perfil.getUsuario()),
                valorSeguro(secureStore.obtenerSecreto(perfil.getId()))
        );
    }

    private void mostrarPanelRedHi3510(
            CameraProfile perfil,
            Hi3510CgiClient.NetworkStatus network,
            Hi3510CgiClient.WirelessConfig wireless
    ) {
        LinearLayout box = formulario();

        TextView estado = new TextView(this);
        String tipo = "WLAN".equalsIgnoreCase(network.networkType) ? "Wi-Fi" : "Ethernet";
        String dhcp = network.isDhcp() ? "Automática (DHCP)" : "IP estática";
        String wifi = wireless.enabled
                ? (TextUtils.isEmpty(wireless.ssid) ? "Activado, sin red seleccionada" : "Activado · " + wireless.ssid)
                : "Desactivado";
        estado.setText(
                "Conexión actual\n"
                        + "• " + tipo + "\n"
                        + "• IP: " + valorSeguro(network.ip) + "\n"
                        + "• Dirección: " + dhcp + "\n"
                        + "• Wi-Fi: " + wifi
        );
        estado.setTextSize(16);
        estado.setPadding(0, 0, 0, dp(12));
        box.addView(estado);

        Button wifiButton = new Button(this);
        wifiButton.setText("Cambiar / configurar Wi-Fi");
        wifiButton.setAllCaps(false);
        box.addView(wifiButton);

        Button ethernetButton = new Button(this);
        ethernetButton.setText("Usar Ethernet");
        ethernetButton.setAllCaps(false);
        box.addView(ethernetButton);

        Button advanced = new Button(this);
        advanced.setText("Configuración avanzada");
        advanced.setAllCaps(false);
        box.addView(advanced);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Red · " + perfil.getNombre())
                .setView(box)
                .setNegativeButton("Cerrar", null)
                .create();

        wifiButton.setOnClickListener(v -> {
            dialog.dismiss();
            buscarWifiHi3510(perfil);
        });
        ethernetButton.setOnClickListener(v -> {
            dialog.dismiss();
            confirmarEthernetHi3510(perfil);
        });
        advanced.setOnClickListener(v -> mostrarRedAvanzadaHi3510(perfil, network));
        dialog.show();
    }

    private void buscarWifiHi3510(CameraProfile perfil) {
        ProgressDialog progress = ProgressDialog.show(
                this,
                "Wi-Fi",
                "La cámara está buscando redes…",
                true,
                false
        );

        new Thread(() -> {
            try {
                List<Hi3510CgiClient.WifiNetwork> networks = cgiClient(perfil).searchWireless();
                runOnUiThread(() -> {
                    progress.dismiss();
                    if (networks.isEmpty()) {
                        mensaje("Wi-Fi", "La cámara no encontró redes Wi-Fi.");
                        return;
                    }
                    String[] labels = new String[networks.size()];
                    for (int i = 0; i < networks.size(); i++) labels[i] = networks.get(i).label();

                    new AlertDialog.Builder(this)
                            .setTitle("Selecciona tu Wi-Fi")
                            .setItems(labels, (d, which) -> pedirClaveWifiHi3510(perfil, networks.get(which)))
                            .setNegativeButton("Cancelar", null)
                            .show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progress.dismiss();
                    mensaje("Wi-Fi", "No pude obtener la lista de redes desde la cámara.");
                    Log.w("IPCam-CGI", "searchwireless falló", e);
                });
            }
        }, "IPCam-Hi3510-WifiScan").start();
    }

    private void pedirClaveWifiHi3510(CameraProfile perfil, Hi3510CgiClient.WifiNetwork network) {
        LinearLayout form = formulario();

        TextView selected = new TextView(this);
        selected.setText(network.label());
        selected.setTextSize(16);
        selected.setPadding(0, 0, 0, dp(8));
        form.addView(selected);

        EditText pass = campoTexto(network.isOpen() ? "Red abierta: no necesita contraseña" : "Contraseña Wi-Fi", form);
        pass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        pass.setEnabled(!network.isOpen());

        TextView note = new TextView(this);
        note.setText("La contraseña se usa únicamente para configurar la cámara y no se guarda en IPCamLocal.");
        note.setTextColor(Color.parseColor("#777777"));
        form.addView(note);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Conectar cámara a " + network.ssid)
                .setView(form)
                .setPositiveButton("Conectar", null)
                .setNegativeButton("Cancelar", null)
                .create();

        dialog.setOnShowListener(ignore -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String password = network.isOpen() ? "" : pass.getText().toString();
            if (!network.isOpen() && (password.length() < 8 || password.length() > 63)) {
                note.setText("La contraseña WPA/WPA2 debe tener entre 8 y 63 caracteres.");
                return;
            }
            dialog.dismiss();
            probarYAplicarWifiHi3510(perfil, network, password);
        }));
        dialog.show();
    }

    private void probarYAplicarWifiHi3510(
            CameraProfile perfil,
            Hi3510CgiClient.WifiNetwork network,
            String wifiPassword
    ) {
        ProgressDialog progress = ProgressDialog.show(
                this,
                "Conectando Wi-Fi",
                "Comprobando SSID y contraseña…",
                true,
                false
        );

        new Thread(() -> {
            try {
                Hi3510CgiClient client = cgiClient(perfil);
                boolean ok = client.checkWireless(network, wifiPassword);
                if (!ok) {
                    runOnUiThread(() -> {
                        progress.dismiss();
                        mensaje("Wi-Fi", "La cámara no pudo validar esa red o contraseña. No se cambió la configuración.");
                    });
                    return;
                }

                // Con la IP nueva solo se puede reconocer la cámara por su UUID ONVIF:
                // hay que tenerlo guardado ANTES de que cambie de red.
                runOnUiThread(() -> progress.setMessage("Credenciales correctas. Guardando identidad de la cámara…"));
                boolean uuidGuardado = guardarUuidBloqueante(perfil);

                runOnUiThread(() -> progress.setMessage("Aplicando Wi-Fi…"));
                client.applyWireless(network, wifiPassword);

                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    progress.dismiss();
                    mostrarPasoDesconectarCable(perfil, uuidGuardado);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progress.dismiss();
                    mensaje("Wi-Fi", "No se pudo completar la configuración Wi-Fi. No se guardó la contraseña en la app.");
                    Log.w("IPCam-CGI", "Configuración Wi-Fi falló", e);
                });
            }
        }, "IPCam-Hi3510-WifiApply").start();
    }

    private void confirmarEthernetHi3510(CameraProfile perfil) {
        new AlertDialog.Builder(this)
                .setTitle("Usar Ethernet")
                .setMessage(
                        "Esto desactivará el Wi-Fi de la cámara. Continúa solo si el cable Ethernet está conectado al router."
                )
                .setPositiveButton("Usar Ethernet", (d, w) -> {
                    ProgressDialog progress = ProgressDialog.show(
                            this, "Ethernet", "Desactivando Wi-Fi…", true, false
                    );
                    new Thread(() -> {
                        try {
                            Hi3510CgiClient client = cgiClient(perfil);
                            client.disableWireless();
                            client.setDhcp();
                            runOnUiThread(() -> {
                                progress.dismiss();
                                Toast.makeText(this, "Wi-Fi desactivado. Ethernet queda en DHCP.", Toast.LENGTH_LONG).show();
                                redescubrirPerfil(perfil);
                            });
                        } catch (Exception e) {
                            runOnUiThread(() -> {
                                progress.dismiss();
                                mensaje("Ethernet", "No pude cambiar la cámara a Ethernet.");
                                Log.w("IPCam-CGI", "Cambio Ethernet falló", e);
                            });
                        }
                    }, "IPCam-Hi3510-Ethernet").start();
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void mostrarRedAvanzadaHi3510(CameraProfile perfil, Hi3510CgiClient.NetworkStatus current) {
        LinearLayout form = formulario();

        CheckBox dhcp = new CheckBox(this);
        dhcp.setText("Obtener IP automáticamente (DHCP)");
        dhcp.setChecked(current.isDhcp());
        form.addView(dhcp);

        EditText ip = campoTexto("Dirección IP", form);
        EditText mask = campoTexto("Máscara de red", form);
        EditText gateway = campoTexto("Gateway", form);
        EditText dns1 = campoTexto("DNS primario", form);
        EditText dns2 = campoTexto("DNS secundario", form);
        ip.setText(current.ip);
        mask.setText(current.netmask);
        gateway.setText(current.gateway);
        dns1.setText(current.primaryDns);
        dns2.setText(current.secondaryDns);

        Runnable refreshEnabled = () -> {
            boolean manual = !dhcp.isChecked();
            ip.setEnabled(manual);
            mask.setEnabled(manual);
            gateway.setEnabled(manual);
            dns1.setEnabled(manual);
            dns2.setEnabled(manual);
        };
        dhcp.setOnCheckedChangeListener((b, checked) -> refreshEnabled.run());
        refreshEnabled.run();

        new AlertDialog.Builder(this)
                .setTitle("Configuración avanzada de red")
                .setView(form)
                .setPositiveButton("Aplicar", (d, w) -> {
                    if (!dhcp.isChecked() && (TextUtils.isEmpty(ip.getText()) || TextUtils.isEmpty(mask.getText()))) {
                        Toast.makeText(this, "Escribe IP y máscara válidas", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    aplicarRedAvanzadaHi3510(
                            perfil,
                            dhcp.isChecked(),
                            ip.getText().toString().trim(),
                            mask.getText().toString().trim(),
                            gateway.getText().toString().trim(),
                            dns1.getText().toString().trim(),
                            dns2.getText().toString().trim()
                    );
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    private void aplicarRedAvanzadaHi3510(
            CameraProfile perfil, boolean dhcp,
            String ip, String mask, String gateway, String dns1, String dns2
    ) {
        ProgressDialog progress = ProgressDialog.show(
                this, "Red", "Aplicando configuración…", true, false
        );
        new Thread(() -> {
            try {
                Hi3510CgiClient client = cgiClient(perfil);
                if (dhcp) client.setDhcp();
                else client.setStaticNetwork(ip, mask, gateway, dns1, dns2);
                runOnUiThread(() -> {
                    progress.dismiss();
                    Toast.makeText(this, "Configuración enviada. La IP puede cambiar.", Toast.LENGTH_LONG).show();
                    redescubrirPerfil(perfil);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    progress.dismiss();
                    mensaje("Red", "No se pudo aplicar la configuración avanzada.");
                    Log.w("IPCam-CGI", "setnetattr falló", e);
                });
            }
        }, "IPCam-Hi3510-NetApply").start();
    }

    private void asegurarUuidSiEsPosible(CameraProfile perfil) {
        if (!TextUtils.isEmpty(perfil.getDeviceUuid())) return;
        OnvifDiscovery.buscar(this, (results, error) -> {
            if (error != null) return;
            for (OnvifDiscoveryResult r : results) {
                if (perfil.getHost() != null && perfil.getHost().equals(r.host())) {
                    perfil.setDeviceUuid(r.uuid);
                    dbHelper.actualizar(perfil);
                    break;
                }
            }
        });
    }

    private void redescubrirPerfil(CameraProfile perfil) {
        asegurarUuidSiEsPosible(perfil);
        new android.os.Handler(getMainLooper()).postDelayed(() ->
                OnvifDiscovery.buscar(this, (results, error) -> runOnUiThread(() -> {
                    if (error != null) {
                        Toast.makeText(this, "No pude redescubrir la cámara todavía.", Toast.LENGTH_LONG).show();
                        return;
                    }

                    OnvifDiscoveryResult match = null;
                    for (OnvifDiscoveryResult r : results) {
                        if (!TextUtils.isEmpty(perfil.getDeviceUuid())
                                && perfil.getDeviceUuid().equalsIgnoreCase(r.uuid)) {
                            match = r;
                            break;
                        }
                        if (match == null && perfil.getHost() != null && perfil.getHost().equals(r.host())) {
                            match = r;
                        }
                    }

                    if (match == null) {
                        Toast.makeText(
                                this,
                                "La cámara todavía no reaparece. Puedes usar «Buscar cámaras» dentro de unos segundos.",
                                Toast.LENGTH_LONG
                        ).show();
                        return;
                    }

                    String newHost = match.host();
                    if (!TextUtils.isEmpty(newHost)) {
                        actualizarHostPerfil(perfil, match);
                        Toast.makeText(this, "Cámara localizada en " + newHost, Toast.LENGTH_LONG).show();
                    }
                })), 2500L);
    }

    /** Actualiza IP, endpoint de dispositivo, endpoint ONVIF/PTZ y UUID del perfil. */
    private void actualizarHostPerfil(CameraProfile perfil, OnvifDiscoveryResult match) {
        perfil.aplicarNuevoHost(match.host(), match.xAddr, match.uuid);
        dbHelper.actualizar(perfil);
        cargarPerfiles();
    }

    // ----------------------------------------------------------------
    // Cambio Ethernet -> Wi-Fi: la cámara obtiene otra IP por DHCP.
    // ----------------------------------------------------------------

    private static final int REDESCUBRIR_INTENTOS = 12;
    private static final long REDESCUBRIR_PAUSA_MS = 10_000L;

    /** WS-Discovery síncrono. Llamar solo fuera del hilo principal. */
    private List<OnvifDiscoveryResult> descubrirBloqueante() {
        AtomicReference<List<OnvifDiscoveryResult>> encontrados = new AtomicReference<>(new ArrayList<>());
        CountDownLatch terminado = new CountDownLatch(1);
        OnvifDiscovery.buscar(this, (results, error) -> {
            if (error == null && results != null) encontrados.set(results);
            terminado.countDown();
        });
        try {
            terminado.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return encontrados.get();
    }

    /** Llamar solo fuera del hilo principal. Devuelve true si el perfil queda con UUID. */
    private boolean guardarUuidBloqueante(CameraProfile perfil) {
        if (!TextUtils.isEmpty(perfil.getDeviceUuid())) return true;
        for (OnvifDiscoveryResult r : descubrirBloqueante()) {
            if (perfil.getHost() != null && perfil.getHost().equals(r.host())
                    && !TextUtils.isEmpty(r.uuid)) {
                perfil.setDeviceUuid(r.uuid);
                dbHelper.actualizar(perfil);
                return true;
            }
        }
        return false;
    }

    private void mostrarPasoDesconectarCable(CameraProfile perfil, boolean uuidGuardado) {
        if (!uuidGuardado) {
            mensaje(
                    "Wi-Fi configurado",
                    "La cámara ya tiene tu Wi-Fi. Desconecta el cable Ethernet y espera unos 2 minutos.\n\n"
                            + "No pude leer su identificador ONVIF, así que no puedo reconocerla automáticamente "
                            + "con su nueva IP. Usa «Buscar cámaras en mi Wi-Fi»."
            );
            return;
        }
        new AlertDialog.Builder(this)
                .setTitle("Wi-Fi configurado")
                .setMessage(
                        "La cámara ya tiene tu Wi-Fi.\n\n"
                                + "1. Desconecta ahora el cable Ethernet de la cámara. No hace falta desenchufarla de la corriente.\n"
                                + "2. Pulsa «Ya lo desconecté». IPCamLocal la buscará en tu Wi-Fi durante unos 3 minutos "
                                + "y actualizará su IP.\n\n"
                                + "Mientras el cable siga conectado, la cámara seguirá usando Ethernet."
                )
                .setCancelable(false)
                .setPositiveButton("Ya lo desconecté", (d, w) -> buscarCamaraTrasCambioDeRed(perfil))
                .setNegativeButton("Más tarde", null)
                .show();
    }

    /**
     * Tras quitar el cable, la cámara tarda en unirse al Wi-Fi y recibe otra IP.
     * Busca por UUID varias veces, ignorando la respuesta por la IP anterior (sigue en cable).
     */
    private void buscarCamaraTrasCambioDeRed(CameraProfile perfil) {
        final String hostAnterior = perfil.getHost();
        final String uuid = perfil.getDeviceUuid();
        if (TextUtils.isEmpty(uuid)) {
            mensaje("Wi-Fi", "No tengo el identificador ONVIF de esta cámara. Usa «Buscar cámaras en mi Wi-Fi».");
            return;
        }
        final AtomicBoolean cancelado = new AtomicBoolean(false);

        ProgressDialog progress = new ProgressDialog(this);
        progress.setTitle("Buscando cámara en tu Wi-Fi");
        progress.setMessage("Esperando a que la cámara se conecte…");
        progress.setIndeterminate(true);
        progress.setCancelable(false);
        progress.setButton(ProgressDialog.BUTTON_NEGATIVE, "Cancelar", (d, w) -> cancelado.set(true));
        progress.show();

        new Thread(() -> {
            OnvifDiscoveryResult encontrada = null;
            boolean sigueEnHostAnterior = false;

            for (int intento = 1; intento <= REDESCUBRIR_INTENTOS && !cancelado.get(); intento++) {
                final int n = intento;
                runOnUiThread(() -> progress.setMessage(
                        "Buscando… intento " + n + " de " + REDESCUBRIR_INTENTOS));

                sigueEnHostAnterior = false;
                for (OnvifDiscoveryResult r : descubrirBloqueante()) {
                    String host = r.host();
                    if (TextUtils.isEmpty(host) || !uuid.equalsIgnoreCase(r.uuid)) continue;
                    if (host.equals(hostAnterior)) {
                        sigueEnHostAnterior = true;
                    } else {
                        encontrada = r;
                    }
                }
                if (encontrada != null) break;

                // Pausa en tramos cortos para que «Cancelar» responda rápido.
                for (long t = 0; t < REDESCUBRIR_PAUSA_MS && !cancelado.get(); t += 500L) {
                    try {
                        Thread.sleep(500L);
                    } catch (InterruptedException e) {
                        cancelado.set(true);
                    }
                }
            }

            final OnvifDiscoveryResult match = encontrada;
            final boolean enCable = sigueEnHostAnterior;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                progress.dismiss();
                if (cancelado.get()) return;

                if (match != null) {
                    actualizarHostPerfil(perfil, match);
                    mensaje(
                            "Cámara encontrada",
                            "La cámara ya funciona por Wi-Fi en " + match.host()
                                    + ". Su perfil quedó actualizado; tócala en la lista para conectarte."
                    );
                    return;
                }

                String detalle = enCable
                        ? "La cámara sigue respondiendo en " + hostAnterior
                                + " por el cable. Desconecta el cable Ethernet y vuelve a buscar."
                        : "La cámara todavía no aparece en tu Wi-Fi. Puedes buscar de nuevo, o volver a "
                                + "conectar el cable para que regrese a " + hostAnterior + ".";
                new AlertDialog.Builder(this)
                        .setTitle(enCable ? "Sigue en Ethernet" : "No la encontré todavía")
                        .setMessage(detalle)
                        .setPositiveButton("Buscar de nuevo", (d, w) -> buscarCamaraTrasCambioDeRed(perfil))
                        .setNegativeButton("Cerrar", null)
                        .show();
            });
        }, "IPCam-Rediscover").start();
    }

    private String endpointDevice(CameraProfile perfil) {
        if (!TextUtils.isEmpty(perfil.getEndpointDevice())) return perfil.getEndpointDevice();
        return "http://" + perfil.getHost() + "/onvif/device_service";
    }

    // ----------------------------------------------------------------
    // Acceso remoto - SOLO perfiles guardados y verificados.
    // No abre puertos ni expone ONVIF automáticamente.
    // ----------------------------------------------------------------

    private void mostrarAccesoRemoto(CameraProfile perfil) {
        if (perfil.getId() == CameraProfile.SIN_ID) {
            mensaje("Acceso remoto", "Primero guarda la cámara.");
            return;
        }

        LinearLayout box = formulario();

        TextView intro = new TextView(this);
        intro.setText(
                "Acceso remoto automático\n\n"
                        + "La cámara anuncia un sistema P2P propio, pero la integración segura del protocolo todavía está en investigación. "
                        + "No te voy a pedir host, DDNS ni puerto en el flujo normal."
        );
        intro.setTextSize(16);
        intro.setPadding(0, 0, 0, dp(10));
        box.addView(intro);

        TextView status = new TextView(this);
        status.setText(
                perfil.isRemoteVerified()
                        ? "Fallback avanzado RTSP: configurado ✓\nP2P automático: pendiente"
                        : "P2P automático: pendiente de integración\nNo hay acceso remoto automático activo todavía"
        );
        status.setPadding(0, 0, 0, dp(10));
        box.addView(status);

        Button advanced = new Button(this);
        advanced.setText("Configuración avanzada");
        advanced.setAllCaps(false);
        box.addView(advanced);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Acceso remoto · " + perfil.getNombre())
                .setView(box)
                .setNegativeButton("Cerrar", null)
                .create();
        advanced.setOnClickListener(v -> {
            dialog.dismiss();
            mostrarAccesoRemotoAvanzado(perfil);
        });
        dialog.show();
    }

    private void mostrarAccesoRemotoAvanzado(CameraProfile perfil) {
        if (perfil.getId() == CameraProfile.SIN_ID) {
            mensaje("Acceso remoto", "Primero guarda la cámara.");
            return;
        }

        LinearLayout form = formulario();

        TextView intro = new TextView(this);
        intro.setText(
                "Esta función solo se habilita para cámaras guardadas.\n\n"
                        + "IPCamLocal no abrirá puertos del router automáticamente. El endpoint RTSP remoto debe ser alcanzable mediante una IP pública, DDNS, port forwarding o relay ya configurado. "
                        + "CGNAT puede impedir este método."
        );
        intro.setTextColor(Color.parseColor("#444444"));
        intro.setPadding(0, 0, 0, dp(8));
        form.addView(intro);

        CheckBox enabled = new CheckBox(this);
        enabled.setText("Habilitar acceso remoto para esta cámara");
        enabled.setChecked(perfil.isRemoteEnabled());
        form.addView(enabled);

        EditText host = campoTexto("Host remoto / DDNS", form);
        host.setText(valorSeguro(perfil.getRemoteHost()));

        EditText port = campoTexto("Puerto RTSP remoto", form);
        port.setInputType(InputType.TYPE_CLASS_NUMBER);
        port.setText(String.valueOf(perfil.getRemotePort() > 0 ? perfil.getRemotePort() : 554));

        EditText path = campoTexto("Ruta RTSP remota (vacía = usar ruta local)", form);
        path.setText(valorSeguro(perfil.getRemotePath()));

        TextView state = new TextView(this);
        state.setText(perfil.isRemoteVerified()
                ? "Estado: endpoint remoto verificado ✓"
                : "Estado: no verificado");
        state.setPadding(0, dp(6), 0, dp(6));
        form.addView(state);

        Button test = new Button(this);
        test.setText("Probar acceso remoto");
        test.setAllCaps(false);
        form.addView(test);

        final boolean[] verifiedThisDialog = {perfil.isRemoteVerified()};
        final String[] verifiedHost = {valorSeguro(perfil.getRemoteHost())};
        final int[] verifiedPort = {perfil.getRemotePort()};
        final String[] verifiedPath = {valorSeguro(perfil.getRemotePath())};

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Acceso remoto · " + perfil.getNombre())
                .setView(form)
                .setPositiveButton("Guardar", null)
                .setNegativeButton("Cancelar", null)
                .create();

        dialog.setOnShowListener(ignored -> {
            test.setOnClickListener(v -> {
                String h = host.getText().toString().trim();
                int p;
                try { p = Integer.parseInt(port.getText().toString().trim()); }
                catch (Exception e) { p = -1; }
                String rp = path.getText().toString().trim();
                if (TextUtils.isEmpty(rp)) rp = perfil.getRutaRtsp();

                if (TextUtils.isEmpty(h) || p <= 0 || p > 65535) {
                    state.setText("Escribe un host y puerto válidos.");
                    return;
                }

                final int finalPort = p;
                final String finalPath = valorSeguro(rp);
                state.setText("Probando endpoint RTSP…");
                test.setEnabled(false);

                new Thread(() -> {
                    RemoteAccessVerifier.Result result = RemoteAccessVerifier.verify(h, finalPort, finalPath);
                    runOnUiThread(() -> {
                        test.setEnabled(true);
                        state.setText(result.message);
                        verifiedThisDialog[0] = result.ok;
                        if (result.ok) {
                            verifiedHost[0] = h;
                            verifiedPort[0] = finalPort;
                            verifiedPath[0] = finalPath;
                        }
                    });
                }, "IPCam-RemoteVerify").start();
            });

            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            save.setOnClickListener(v -> {
                boolean wantEnabled = enabled.isChecked();
                String h = host.getText().toString().trim();
                int p;
                try { p = Integer.parseInt(port.getText().toString().trim()); }
                catch (Exception e) { p = -1; }
                String rp = path.getText().toString().trim();
                if (TextUtils.isEmpty(rp)) rp = perfil.getRutaRtsp();

                boolean sameAsVerified = verifiedThisDialog[0]
                        && h.equals(verifiedHost[0])
                        && p == verifiedPort[0]
                        && valorSeguro(rp).equals(verifiedPath[0]);

                if (wantEnabled && !sameAsVerified) {
                    state.setText("Debes probar y verificar este endpoint antes de habilitarlo.");
                    return;
                }

                perfil.setRemoteEnabled(wantEnabled);
                perfil.setRemoteVerified(wantEnabled && sameAsVerified);
                perfil.setRemoteHost(h);
                perfil.setRemotePort(p > 0 ? p : 0);
                perfil.setRemotePath(valorSeguro(rp));
                dbHelper.actualizar(perfil);
                cargarPerfiles();
                dialog.dismiss();

                Toast.makeText(
                        this,
                        wantEnabled ? "Acceso remoto guardado" : "Acceso remoto desactivado",
                        Toast.LENGTH_SHORT
                ).show();
            });
        });

        dialog.show();
    }

    // ----------------------------------------------------------------
    // Crear / editar perfil
    // ----------------------------------------------------------------

    private void mostrarDialogoEdicion(CameraProfile existente, OnvifDiscoveryResult detectada) {
        boolean editar = existente != null;
        LinearLayout form = formulario();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.addView(form);

        TextView intro = new TextView(this);
        intro.setText("Solo necesitas los datos que normalmente aparecen en la etiqueta o app de la cámara.");
        intro.setPadding(0, 0, 0, dp(8));
        form.addView(intro);

        EditText nombre = campoTexto("Nombre (ej. Sala, Entrada)", form);
        EditText host = campoTexto("Dirección de la cámara (ej. 192.168.1.98)", form);
        EditText usuario = campoTexto("Usuario (ej. admin)", form);
        EditText password = campoTexto(editar ? "Contraseña (vacía = conservar)" : "Contraseña", form);
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        Button probar = new Button(this);
        probar.setText("Probar conexión");
        probar.setAllCaps(false);
        form.addView(probar);

        Button toggleAvanzado = new Button(this);
        toggleAvanzado.setText("Configuración avanzada ▾");
        toggleAvanzado.setAllCaps(false);
        form.addView(toggleAvanzado);

        LinearLayout avanzado = new LinearLayout(this);
        avanzado.setOrientation(LinearLayout.VERTICAL);
        avanzado.setVisibility(View.GONE);
        form.addView(avanzado);

        EditText puerto = campoTexto("Puerto de video (normalmente 554)", avanzado);
        puerto.setInputType(InputType.TYPE_CLASS_NUMBER);
        EditText ruta = campoTexto("Ruta de video", avanzado);
        EditText endpointDevice = campoTexto("Servicio ONVIF del dispositivo", avanzado);
        EditText endpointPtz = campoTexto("Dirección de control ONVIF", avanzado);

        TextView estado = new TextView(this);
        estado.setPadding(0, dp(8), 0, 0);
        form.addView(estado);

        toggleAvanzado.setOnClickListener(v -> {
            boolean mostrar = avanzado.getVisibility() != View.VISIBLE;
            avanzado.setVisibility(mostrar ? View.VISIBLE : View.GONE);
            toggleAvanzado.setText(mostrar ? "Configuración avanzada ▴" : "Configuración avanzada ▾");
        });

        if (editar) {
            nombre.setText(existente.getNombre());
            host.setText(existente.getHost());
            usuario.setText(existente.getUsuario());
            puerto.setText(existente.getPuertoRtsp() > 0 ? String.valueOf(existente.getPuertoRtsp()) : "554");
            ruta.setText(existente.getRutaRtsp());
            endpointDevice.setText(existente.getEndpointDevice());
            endpointPtz.setText(existente.getEndpointOnvif());
        } else {
            puerto.setText("554");
            if (detectada != null) {
                nombre.setText(detectada.nombreAmigable());
                host.setText(detectada.host());
                endpointDevice.setText(detectada.xAddr);
                estado.setText("Cámara encontrada. Escribe usuario y contraseña; la app buscará el video automáticamente.");
            }
        }

        final String[] tokenDetectado = {editar ? valorSeguro(existente.getProfileToken()) : ""};
        final String uuid = editar
                ? valorSeguro(existente.getDeviceUuid())
                : detectada == null ? "" : valorSeguro(detectada.uuid);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(editar ? "Editar cámara" : "Agregar cámara")
                .setView(scroll)
                .setPositiveButton("Guardar cámara", null)
                .setNegativeButton("Cancelar", null)
                .create();

        dialog.setOnShowListener(ignored -> {
            Button guardar = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            guardar.setOnClickListener(v -> guardarDesdeFormulario(
                    dialog, existente, uuid, nombre, host, usuario, password,
                    puerto, ruta, endpointDevice, endpointPtz, avanzado,
                    toggleAvanzado, estado, guardar, tokenDetectado
            ));

            probar.setOnClickListener(v -> detectarDesdeFormulario(
                    existente, host, usuario, password, puerto, ruta,
                    endpointDevice, endpointPtz, avanzado, toggleAvanzado,
                    estado, probar, tokenDetectado, cfg ->
                            estado.setText("Conexión correcta. Video y controles detectados automáticamente.")
            ));
        });

        dialog.show();
    }

    private interface ConfigDetectadaCallback {
        void onSuccess(OnvifConfig config);
    }

    private void guardarDesdeFormulario(
            AlertDialog dialog, CameraProfile existente, String uuid,
            EditText nombre, EditText host, EditText usuario, EditText password,
            EditText puerto, EditText ruta, EditText endpointDevice, EditText endpointPtz,
            LinearLayout avanzado, Button toggleAvanzado, TextView estado,
            Button guardar, String[] tokenDetectado
    ) {
        String n = nombre.getText().toString().trim();
        String h = limpiarHost(host.getText().toString());
        String u = usuario.getText().toString().trim();
        String passNueva = password.getText().toString();

        if (TextUtils.isEmpty(n) || TextUtils.isEmpty(h)) {
            estado.setText("Escribe un nombre y la dirección de la cámara.");
            return;
        }

        host.setText(h);
        String device = endpointDevice.getText().toString().trim();
        if (TextUtils.isEmpty(device)) device = "http://" + h + "/onvif/device_service";

        if (!TextUtils.isEmpty(ruta.getText().toString().trim())) {
            guardarPerfil(
                    dialog, existente, uuid, n, h, u, passNueva,
                    puerto, ruta, device, endpointPtz.getText().toString().trim(), tokenDetectado[0]
            );
            return;
        }

        detectarDesdeFormulario(
                existente, host, usuario, password, puerto, ruta,
                endpointDevice, endpointPtz, avanzado, toggleAvanzado,
                estado, guardar, tokenDetectado, cfg ->
                        guardarPerfil(
                                dialog, existente, uuid, n,
                                limpiarHost(host.getText().toString()), u, passNueva,
                                puerto, ruta, cfg.deviceEndpoint, cfg.ptzEndpoint, cfg.profileToken
                        )
        );
    }

    private void detectarDesdeFormulario(
            CameraProfile existente, EditText host, EditText usuario, EditText password,
            EditText puerto, EditText ruta, EditText endpointDevice, EditText endpointPtz,
            LinearLayout avanzado, Button toggleAvanzado, TextView estado,
            Button boton, String[] tokenDetectado, ConfigDetectadaCallback callback
    ) {
        String h = limpiarHost(host.getText().toString());
        String u = usuario.getText().toString().trim();
        String passNueva = password.getText().toString();

        if (TextUtils.isEmpty(h)) {
            estado.setText("Escribe la dirección de la cámara.");
            return;
        }

        host.setText(h);
        String passEfectiva = !passNueva.isEmpty()
                ? passNueva
                : existente == null ? "" : valorSeguro(secureStore.obtenerSecreto(existente.getId()));

        String device = endpointDevice.getText().toString().trim();
        if (TextUtils.isEmpty(device)) device = "http://" + h + "/onvif/device_service";
        String finalDevice = device;

        estado.setText("Detectando video y controles automáticamente…");
        boton.setEnabled(false);

        new Thread(() -> {
            try {
                OnvifConfig cfg = OnvifClient.detectarConfiguracion(finalDevice, u, passEfectiva);
                runOnUiThread(() -> {
                    boton.setEnabled(true);
                    aplicarStreamUri(cfg.streamUri, host, puerto, ruta);
                    endpointDevice.setText(cfg.deviceEndpoint);
                    endpointPtz.setText(cfg.ptzEndpoint);
                    tokenDetectado[0] = cfg.profileToken;
                    callback.onSuccess(cfg);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    boton.setEnabled(true);
                    avanzado.setVisibility(View.VISIBLE);
                    toggleAvanzado.setText("Configuración avanzada ▴");
                    estado.setText(e instanceof SecurityException
                            ? "Usuario o contraseña incorrectos."
                            : "No pude detectar la cámara automáticamente. Verifica que ONVIF esté activado o revisa la configuración avanzada.");
                    Log.w("IPCam-H", "Autoconfiguración ONVIF falló", e);
                });
            }
        }, "IPCam-ONVIF-Setup").start();
    }

    private void guardarPerfil(
            AlertDialog dialog, CameraProfile existente, String uuid,
            String nombre, String host, String usuario, String passNueva,
            EditText puertoCampo, EditText rutaCampo,
            String deviceEndpoint, String ptzEndpoint, String profileToken
    ) {
        int port = 554;
        try { port = Integer.parseInt(puertoCampo.getText().toString().trim()); }
        catch (Exception ignored) {}

        String ruta = rutaCampo.getText().toString().trim();
        if (TextUtils.isEmpty(ruta)) return;

        CameraProfile p = existente == null ? new CameraProfile() : existente;
        p.setNombre(nombre);
        p.setHost(host);
        p.setUsuario(usuario);
        p.setPuertoRtsp(port);
        p.setRutaRtsp(ruta);
        p.setEndpointDevice(deviceEndpoint);
        p.setEndpointOnvif(ptzEndpoint);
        p.setProfileToken(profileToken);
        p.setDeviceUuid(uuid);
        p.setFechaUltimaConexion(System.currentTimeMillis());

        if (existente == null) dbHelper.insertar(p);
        else dbHelper.actualizar(p);

        if (!passNueva.isEmpty()) secureStore.guardarSecreto(p.getId(), passNueva);

        cargarPerfiles();
        dialog.dismiss();
        Toast.makeText(this, "Cámara guardada", Toast.LENGTH_SHORT).show();
    }

    private void aplicarStreamUri(String streamUri, EditText host, EditText puerto, EditText ruta) {
        Uri uri = Uri.parse(streamUri);
        if (!TextUtils.isEmpty(uri.getHost())) host.setText(uri.getHost());

        int p = uri.getPort() > 0 ? uri.getPort() : 554;
        String path = valorSeguro(uri.getEncodedPath());
        if (!TextUtils.isEmpty(uri.getEncodedQuery())) path += "?" + uri.getEncodedQuery();

        puerto.setText(String.valueOf(p));
        ruta.setText(path);
    }

    private LinearLayout formulario() {
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(20), dp(12), dp(20), dp(4));
        return form;
    }

    private String limpiarHost(String value) {
        String v = value == null ? "" : value.trim();

        if (v.contains("://")) {
            try {
                String host = Uri.parse(v).getHost();
                if (!TextUtils.isEmpty(host)) return host;
            } catch (Exception ignored) {}
        }

        int slash = v.indexOf('/');
        if (slash >= 0) v = v.substring(0, slash);

        int colon = v.lastIndexOf(':');
        if (colon > 0 && v.indexOf(':') == colon) v = v.substring(0, colon);

        return v.trim();
    }

    private void mensaje(String title, String message) {
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton("Aceptar", null)
                .show();
    }

    private static String valorSeguro(String value) {
        return value == null ? "" : value;
    }

    private EditText campoTexto(String hint, LinearLayout contenedor) {
        EditText campo = new EditText(this);
        campo.setHint(hint);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
        params.bottomMargin = dp(8);

        campo.setLayoutParams(params);
        contenedor.addView(campo);
        return campo;
    }
}
