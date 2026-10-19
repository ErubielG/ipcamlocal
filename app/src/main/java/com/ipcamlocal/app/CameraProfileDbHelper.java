package com.ipcamlocal.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

final class CameraProfileDbHelper extends SQLiteOpenHelper {
    private static final String DB_NAME = "ipcam_profiles.db";
    private static final int DB_VERSION = 3;
    private static final String TABLA = "camera_profiles";

    private static final String COL_ID = "id";
    private static final String COL_NOMBRE = "nombre";
    private static final String COL_HOST = "host";
    private static final String COL_PUERTO_RTSP = "puerto_rtsp";
    private static final String COL_RUTA_RTSP = "ruta_rtsp";
    private static final String COL_ENDPOINT_DEVICE = "endpoint_device";
    private static final String COL_ENDPOINT_ONVIF = "endpoint_onvif";
    private static final String COL_PROFILE_TOKEN = "profile_token";
    private static final String COL_USUARIO = "usuario";
    private static final String COL_ULTIMO_ESTADO = "ultimo_estado";
    private static final String COL_FECHA_ULTIMA_CONEXION = "fecha_ultima_conexion";

    private static final String COL_DEVICE_UUID = "device_uuid";
    private static final String COL_REMOTE_ENABLED = "remote_enabled";
    private static final String COL_REMOTE_VERIFIED = "remote_verified";
    private static final String COL_REMOTE_HOST = "remote_host";
    private static final String COL_REMOTE_PORT = "remote_port";
    private static final String COL_REMOTE_PATH = "remote_path";

    CameraProfileDbHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLA + " ("
                + COL_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, "
                + COL_NOMBRE + " TEXT NOT NULL, "
                + COL_HOST + " TEXT NOT NULL, "
                + COL_PUERTO_RTSP + " INTEGER, "
                + COL_RUTA_RTSP + " TEXT, "
                + COL_ENDPOINT_DEVICE + " TEXT, "
                + COL_ENDPOINT_ONVIF + " TEXT, "
                + COL_PROFILE_TOKEN + " TEXT, "
                + COL_USUARIO + " TEXT, "
                + COL_ULTIMO_ESTADO + " TEXT, "
                + COL_FECHA_ULTIMA_CONEXION + " INTEGER, "
                + COL_DEVICE_UUID + " TEXT, "
                + COL_REMOTE_ENABLED + " INTEGER DEFAULT 0, "
                + COL_REMOTE_VERIFIED + " INTEGER DEFAULT 0, "
                + COL_REMOTE_HOST + " TEXT, "
                + COL_REMOTE_PORT + " INTEGER DEFAULT 0, "
                + COL_REMOTE_PATH + " TEXT)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE " + TABLA + " ADD COLUMN " + COL_ENDPOINT_DEVICE + " TEXT");
            db.execSQL("ALTER TABLE " + TABLA + " ADD COLUMN " + COL_PROFILE_TOKEN + " TEXT");
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE " + TABLA + " ADD COLUMN " + COL_DEVICE_UUID + " TEXT");
            db.execSQL("ALTER TABLE " + TABLA + " ADD COLUMN " + COL_REMOTE_ENABLED + " INTEGER DEFAULT 0");
            db.execSQL("ALTER TABLE " + TABLA + " ADD COLUMN " + COL_REMOTE_VERIFIED + " INTEGER DEFAULT 0");
            db.execSQL("ALTER TABLE " + TABLA + " ADD COLUMN " + COL_REMOTE_HOST + " TEXT");
            db.execSQL("ALTER TABLE " + TABLA + " ADD COLUMN " + COL_REMOTE_PORT + " INTEGER DEFAULT 0");
            db.execSQL("ALTER TABLE " + TABLA + " ADD COLUMN " + COL_REMOTE_PATH + " TEXT");
        }
    }

    long insertar(CameraProfile perfil) {
        long id = getWritableDatabase().insert(TABLA, null, valoresDesdePerfil(perfil));
        perfil.setId(id);
        return id;
    }

    void actualizar(CameraProfile perfil) {
        getWritableDatabase().update(
                TABLA,
                valoresDesdePerfil(perfil),
                COL_ID + " = ?",
                new String[]{String.valueOf(perfil.getId())}
        );
    }

    void eliminar(long id) {
        getWritableDatabase().delete(
                TABLA,
                COL_ID + " = ?",
                new String[]{String.valueOf(id)}
        );
    }

    CameraProfile obtener(long id) {
        try (Cursor cursor = getReadableDatabase().query(
                TABLA, null, COL_ID + " = ?",
                new String[]{String.valueOf(id)}, null, null, null
        )) {
            if (cursor.moveToFirst()) return perfilDesdeCursor(cursor);
        }
        return null;
    }

    List<CameraProfile> listarTodos() {
        List<CameraProfile> resultado = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(
                TABLA, null, null, null, null, null,
                COL_NOMBRE + " COLLATE NOCASE ASC"
        )) {
            while (cursor.moveToNext()) resultado.add(perfilDesdeCursor(cursor));
        }
        return resultado;
    }

    private ContentValues valoresDesdePerfil(CameraProfile p) {
        ContentValues v = new ContentValues();
        v.put(COL_NOMBRE, p.getNombre());
        v.put(COL_HOST, p.getHost());
        v.put(COL_PUERTO_RTSP, p.getPuertoRtsp());
        v.put(COL_RUTA_RTSP, p.getRutaRtsp());
        v.put(COL_ENDPOINT_DEVICE, p.getEndpointDevice());
        v.put(COL_ENDPOINT_ONVIF, p.getEndpointOnvif());
        v.put(COL_PROFILE_TOKEN, p.getProfileToken());
        v.put(COL_USUARIO, p.getUsuario());
        v.put(COL_ULTIMO_ESTADO, p.getUltimoEstado());
        v.put(COL_FECHA_ULTIMA_CONEXION, p.getFechaUltimaConexion());

        v.put(COL_DEVICE_UUID, p.getDeviceUuid());
        v.put(COL_REMOTE_ENABLED, p.isRemoteEnabled() ? 1 : 0);
        v.put(COL_REMOTE_VERIFIED, p.isRemoteVerified() ? 1 : 0);
        v.put(COL_REMOTE_HOST, p.getRemoteHost());
        v.put(COL_REMOTE_PORT, p.getRemotePort());
        v.put(COL_REMOTE_PATH, p.getRemotePath());
        return v;
    }

    private CameraProfile perfilDesdeCursor(Cursor c) {
        CameraProfile p = new CameraProfile();
        p.setId(c.getLong(c.getColumnIndexOrThrow(COL_ID)));
        p.setNombre(c.getString(c.getColumnIndexOrThrow(COL_NOMBRE)));
        p.setHost(c.getString(c.getColumnIndexOrThrow(COL_HOST)));
        p.setPuertoRtsp(c.getInt(c.getColumnIndexOrThrow(COL_PUERTO_RTSP)));
        p.setRutaRtsp(c.getString(c.getColumnIndexOrThrow(COL_RUTA_RTSP)));
        p.setEndpointDevice(c.getString(c.getColumnIndexOrThrow(COL_ENDPOINT_DEVICE)));
        p.setEndpointOnvif(c.getString(c.getColumnIndexOrThrow(COL_ENDPOINT_ONVIF)));
        p.setProfileToken(c.getString(c.getColumnIndexOrThrow(COL_PROFILE_TOKEN)));
        p.setUsuario(c.getString(c.getColumnIndexOrThrow(COL_USUARIO)));
        p.setUltimoEstado(c.getString(c.getColumnIndexOrThrow(COL_ULTIMO_ESTADO)));
        p.setFechaUltimaConexion(c.getLong(c.getColumnIndexOrThrow(COL_FECHA_ULTIMA_CONEXION)));

        p.setDeviceUuid(c.getString(c.getColumnIndexOrThrow(COL_DEVICE_UUID)));
        p.setRemoteEnabled(c.getInt(c.getColumnIndexOrThrow(COL_REMOTE_ENABLED)) != 0);
        p.setRemoteVerified(c.getInt(c.getColumnIndexOrThrow(COL_REMOTE_VERIFIED)) != 0);
        p.setRemoteHost(c.getString(c.getColumnIndexOrThrow(COL_REMOTE_HOST)));
        p.setRemotePort(c.getInt(c.getColumnIndexOrThrow(COL_REMOTE_PORT)));
        p.setRemotePath(c.getString(c.getColumnIndexOrThrow(COL_REMOTE_PATH)));
        return p;
    }
}
