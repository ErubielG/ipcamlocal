package com.ipcamlocal.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

public class BackgroundCameraService extends Service {
    static final String ACTION_STOP = "com.ipcamlocal.app.STOP_BACKGROUND_CAMERA";
    private static final String CHANNEL_ID = "ipcam_background_stream";
    private static final int NOTIFICATION_ID = 1402;

    @Override
    public void onCreate() {
        super.onCreate();
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            CameraSessionHolder.get(this).disconnect();
            stopForegroundCompat();
            stopSelf();
            return START_NOT_STICKY;
        }

        // Touch the holder so the service and Activity reference the same session.
        CameraSessionHolder.get(this);
        startForeground(NOTIFICATION_ID, buildNotification());
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Cámara en segundo plano",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Mantiene activa la transmisión RTSP cuando IPCamLocal está minimizada.");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent openIntent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent openPending = PendingIntent.getActivity(
                this, 10, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Intent stopIntent = new Intent(this, BackgroundCameraService.class)
                .setAction(ACTION_STOP);
        PendingIntent stopPending = PendingIntent.getService(
                this, 11, stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);

        return builder
                .setSmallIcon(android.R.drawable.ic_menu_camera)
                .setContentTitle("IPCamLocal · Segundo plano")
                .setContentText("La transmisión RTSP continúa activa")
                .setContentIntent(openPending)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_media_pause,
                        "Detener",
                        stopPending
                ).build())
                .build();
    }

    private void stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE);
        } else {
            stopForeground(true);
        }
    }
}
