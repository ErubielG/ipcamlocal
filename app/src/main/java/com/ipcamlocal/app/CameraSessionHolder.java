package com.ipcamlocal.app;

import android.content.Context;

/** Keeps one RTSP session alive across Activity recreation/background use. */
final class CameraSessionHolder {
    private static CameraSession session;

    private CameraSessionHolder() {}

    static synchronized CameraSession get(Context context) {
        if (session == null || session.getState() == CameraSession.State.RELEASED) {
            session = new CameraSession(context.getApplicationContext(), null);
        }
        return session;
    }

    static synchronized void release() {
        if (session != null) {
            session.release();
            session = null;
        }
    }
}
