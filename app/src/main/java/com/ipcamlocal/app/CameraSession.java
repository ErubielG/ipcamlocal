package com.ipcamlocal.app;

import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.util.VLCVideoLayout;

import java.util.ArrayList;

/**
 * Owns the RTSP/VLC session independently from MainActivity.
 *
 * 0.11.1 hotfix goals:
 * - never start VLC against a not-yet-laid-out video surface while the UI is visible;
 * - stop foreground retries when the app leaves screen unless Background mode is enabled;
 * - keep the same session architecture for PiP/background;
 * - avoid infinite retry loops during a normal foreground connection attempt.
 */
final class CameraSession {
    enum State {
        IDLE,
        CONNECTING,
        PLAYING,
        WAITING_RETRY,
        PAUSED,
        ERROR,
        DISCONNECTED_MANUALLY,
        RELEASED
    }

    interface Listener {
        void onStateChanged(State state, String message);
        void onTechnicalError(String detail);
        void onRetryScheduled(int attempt, long delayMs);
    }

    private static final long CONNECTION_TIMEOUT_MS = 12_000L;
    private static final long[] RETRY_DELAYS_MS = {1_000L, 2_000L, 5_000L, 10_000L, 30_000L};
    private static final int MAX_FOREGROUND_RETRIES = 5;
    // TextureView en lugar de SurfaceView: permite capturar fotos (getBitmap) y hacer zoom
    // escalando la vista; un SurfaceView no se recorta ni se escala de forma fiable.
    private static final boolean USE_TEXTURE_VIEW = true;

    private final Context appContext;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Listener listener;

    private LibVLC libVLC;
    private MediaPlayer mediaPlayer;
    private VLCVideoLayout attachedView;
    private boolean surfaceAttached;
    private String activeUrl;
    private State state = State.IDLE;
    private String lastMessage = "Listo";
    private boolean manualDisconnect = true;
    private boolean released;
    private boolean uiVisible = true;
    private boolean backgroundAllowed;
    private int retryAttempt;
    private int generation;
    private boolean startPosted;
    private Runnable retryRunnable;
    private Runnable watchdogRunnable;

    CameraSession(Context context, Listener listener) {
        this.appContext = context.getApplicationContext();
        this.listener = listener;
    }

    void setListener(Listener listener) {
        this.listener = listener;
        if (listener != null) listener.onStateChanged(state, lastMessage);
    }

    void setBackgroundAllowed(boolean allowed) {
        backgroundAllowed = allowed;
        if (!allowed && !uiVisible && hasLiveSession()) {
            pauseOutsideUi();
        } else if (allowed && hasLiveSession() && mediaPlayer == null && retryRunnable == null) {
            requestStartPlayer();
        }
    }

    void setUiVisible(boolean visible) {
        uiVisible = visible;
        if (!visible && !backgroundAllowed && hasLiveSession()) {
            pauseOutsideUi();
            return;
        }
        if (visible && hasLiveSession() && mediaPlayer == null && retryRunnable == null) {
            // When visible we deliberately wait for attachSurface() before creating VLC.
            if (attachedView != null) requestStartPlayer();
        }
    }

    void connect(String url) {
        if (released) return;
        manualDisconnect = false;
        activeUrl = url;
        retryAttempt = 0;
        cancelRetry();
        cancelWatchdog();
        technical("RTSP target: " + sanitizedTarget(url));
        requestStartPlayer();
    }

    void attachSurface(VLCVideoLayout view) {
        if (released || view == null) return;
        attachedView = view;

        view.post(() -> {
            if (released || attachedView != view) return;
            technical("Surface ready: attachedToWindow=" + view.isAttachedToWindow()
                    + " width=" + view.getWidth() + " height=" + view.getHeight());

            if (mediaPlayer != null && !surfaceAttached) {
                try {
                    mediaPlayer.attachViews(view, null, false, USE_TEXTURE_VIEW);
                    surfaceAttached = true;
                } catch (Exception e) {
                    technical("attachViews: " + e.getClass().getSimpleName());
                }
            }

            if (uiVisible && hasLiveSession() && mediaPlayer == null && retryRunnable == null) {
                requestStartPlayer();
            }
        });
    }

    void detachSurface() {
        if (mediaPlayer != null && surfaceAttached) {
            try {
                mediaPlayer.detachViews();
            } catch (Exception e) {
                technical("detachViews: " + e.getClass().getSimpleName());
            }
        }
        surfaceAttached = false;
        attachedView = null;
    }

    void disconnect() {
        manualDisconnect = true;
        activeUrl = null;
        retryAttempt = 0;
        startPosted = false;
        cancelRetry();
        cancelWatchdog();
        releasePlayerOnly();
        setState(State.DISCONNECTED_MANUALLY, "Desconectado");
    }

    void release() {
        if (released) return;
        released = true;
        manualDisconnect = true;
        activeUrl = null;
        startPosted = false;
        cancelRetry();
        cancelWatchdog();
        releasePlayerOnly();
        attachedView = null;
        setState(State.RELEASED, "Sesión liberada");
    }

    boolean isPlaying() {
        return state == State.PLAYING;
    }

    boolean hasLiveSession() {
        return !released && !manualDisconnect && activeUrl != null;
    }

    State getState() {
        return state;
    }

    private boolean mayRunNow() {
        return uiVisible || backgroundAllowed;
    }

    private void requestStartPlayer() {
        if (released || manualDisconnect || activeUrl == null || !mayRunNow()) return;
        if (mediaPlayer != null || retryRunnable != null || startPosted) return;

        // In foreground, reproduce the proven behavior of the original working build:
        // wait until VLCVideoLayout has completed a layout pass before creating/attaching VLC.
        if (uiVisible) {
            VLCVideoLayout view = attachedView;
            if (view == null) {
                technical("Esperando superficie de video antes de iniciar VLC");
                return;
            }

            startPosted = true;
            view.post(() -> {
                startPosted = false;
                if (released || manualDisconnect || activeUrl == null || !uiVisible) return;
                if (attachedView != view) return;
                if (!view.isAttachedToWindow() || view.getWidth() <= 0 || view.getHeight() <= 0) {
                    technical("Superficie todavía no lista: attached=" + view.isAttachedToWindow()
                            + " width=" + view.getWidth() + " height=" + view.getHeight());
                    handler.postDelayed(this::requestStartPlayer, 60L);
                    return;
                }
                startPlayerNow();
            });
            return;
        }

        // Background mode explicitly enabled: a surface is not required.
        startPlayerNow();
    }

    private void startPlayerNow() {
        if (released || manualDisconnect || activeUrl == null || !mayRunNow()) return;
        if (mediaPlayer != null) return;

        cancelWatchdog();
        releasePlayerOnly();
        final int token = ++generation;
        setState(State.CONNECTING, retryAttempt == 0
                ? "Conectando…"
                : "Reconectando… intento " + retryAttempt);

        try {
            ArrayList<String> options = new ArrayList<>();
            options.add("--rtsp-tcp");
            options.add("--network-caching=500");

            libVLC = new LibVLC(appContext, options);
            mediaPlayer = new MediaPlayer(libVLC);
            MediaPlayer player = mediaPlayer;
            player.setEventListener(event -> handler.post(() -> handleEvent(token, event)));

            VLCVideoLayout view = attachedView;
            if (uiVisible && view != null) {
                technical("attachViews before play: width=" + view.getWidth() + " height=" + view.getHeight());
                player.attachViews(view, null, false, USE_TEXTURE_VIEW);
                surfaceAttached = true;
            }

            Media media = new Media(libVLC, Uri.parse(activeUrl));
            media.setHWDecoderEnabled(true, false);
            media.addOption(":rtsp-tcp");
            media.addOption(":network-caching=500");
            player.setMedia(media);
            media.release();
            player.play();
            startWatchdog(token);
        } catch (Exception e) {
            technical("startPlayer: " + e.getClass().getSimpleName() + " " + safeMessage(e));
            scheduleRetry("No se pudo iniciar el reproductor");
        }
    }

    private void handleEvent(int token, MediaPlayer.Event event) {
        if (released || token != generation) return;

        if (event.type == MediaPlayer.Event.Opening) {
            setState(State.CONNECTING, "Abriendo RTSP…");
        } else if (event.type == MediaPlayer.Event.Buffering) {
            int buffering = (int) event.getBuffering();
            if (state != State.PLAYING && buffering < 100) {
                setState(State.CONNECTING, "Cargando video… " + buffering + "%");
            }
        } else if (event.type == MediaPlayer.Event.Playing) {
            cancelWatchdog();
            retryAttempt = 0;
            setState(State.PLAYING, "Conectado");
        } else if (event.type == MediaPlayer.Event.EncounteredError) {
            technical("LibVLC EncounteredError");
            scheduleRetry("No se pudo abrir el stream RTSP");
        } else if (event.type == MediaPlayer.Event.EndReached) {
            technical("LibVLC EndReached");
            scheduleRetry("El stream terminó");
        } else if (event.type == MediaPlayer.Event.Stopped && !manualDisconnect) {
            scheduleRetry("El stream se detuvo");
        }
    }

    private void scheduleRetry(String reason) {
        if (released || manualDisconnect || activeUrl == null) {
            setState(State.ERROR, reason);
            return;
        }

        cancelWatchdog();
        releasePlayerOnly();

        if (!mayRunNow()) {
            setState(State.PAUSED, "Pausado");
            return;
        }

        retryAttempt++;
        if (!backgroundAllowed && retryAttempt > MAX_FOREGROUND_RETRIES) {
            cancelRetry();
            setState(State.ERROR, "No se pudo conectar después de " + MAX_FOREGROUND_RETRIES
                    + " intentos. Pulsa Conectar para reintentar.");
            technical("Reintentos detenidos en primer plano tras " + MAX_FOREGROUND_RETRIES + " intentos");
            return;
        }

        int index = Math.min(retryAttempt - 1, RETRY_DELAYS_MS.length - 1);
        long delay = RETRY_DELAYS_MS[index];
        setState(State.WAITING_RETRY, "Reconectando… intento " + retryAttempt
                + " en " + (delay / 1000) + " s");
        if (listener != null) listener.onRetryScheduled(retryAttempt, delay);

        retryRunnable = () -> {
            retryRunnable = null;
            if (!mayRunNow()) {
                setState(State.PAUSED, "Pausado");
                return;
            }
            requestStartPlayer();
        };
        handler.postDelayed(retryRunnable, delay);
    }

    private void pauseOutsideUi() {
        cancelRetry();
        cancelWatchdog();
        releasePlayerOnly();
        retryAttempt = 0;
        setState(State.PAUSED, "Pausado fuera de la app");
    }

    private void startWatchdog(int token) {
        cancelWatchdog();
        watchdogRunnable = () -> {
            watchdogRunnable = null;
            if (!released && token == generation && state != State.PLAYING) {
                technical("Timeout RTSP tras " + CONNECTION_TIMEOUT_MS + " ms");
                scheduleRetry("La cámara no respondió a tiempo");
            }
        };
        handler.postDelayed(watchdogRunnable, CONNECTION_TIMEOUT_MS);
    }

    private void cancelRetry() {
        if (retryRunnable != null) {
            handler.removeCallbacks(retryRunnable);
            retryRunnable = null;
        }
    }

    private void cancelWatchdog() {
        if (watchdogRunnable != null) {
            handler.removeCallbacks(watchdogRunnable);
            watchdogRunnable = null;
        }
    }

    private void releasePlayerOnly() {
        generation++;
        MediaPlayer player = mediaPlayer;
        mediaPlayer = null;
        surfaceAttached = false;
        if (player != null) {
            try { player.detachViews(); } catch (Exception ignored) {}
            try { player.stop(); } catch (Exception ignored) {}
            try { player.release(); } catch (Exception ignored) {}
        }
        if (libVLC != null) {
            try { libVLC.release(); } catch (Exception ignored) {}
            libVLC = null;
        }
    }

    private void setState(State newState, String message) {
        state = newState;
        lastMessage = message;
        if (listener != null) listener.onStateChanged(newState, message);
    }

    private void technical(String detail) {
        if (listener != null) listener.onTechnicalError(detail);
    }

    private static String sanitizedTarget(String url) {
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            int port = uri.getPort() > 0 ? uri.getPort() : 554;
            String path = uri.getEncodedPath();
            if (path == null || path.isEmpty()) path = "/";
            boolean credentials = uri.getEncodedAuthority() != null
                    && uri.getEncodedAuthority().contains("@");
            return "host=" + host + " port=" + port + " path=" + path
                    + " credentials=" + (credentials ? "yes" : "no");
        } catch (Exception e) {
            return "URL no analizable";
        }
    }

    private static String safeMessage(Exception e) {
        String m = e.getMessage();
        return m == null ? "" : m.replace('\n', ' ').replace('\r', ' ');
    }
}
