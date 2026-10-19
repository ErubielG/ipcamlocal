package com.ipcamlocal.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import android.util.Log;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class SecureCredentialsStore {
    private static final String KEYSTORE_ALIAS = "ipcam_credenciales_key";
    private static final String PREFS_NAME = "ipcam_secrets";
    private static final String TRANSFORMACION = "AES/GCM/NoPadding";
    private final SharedPreferences prefs;
    SecureCredentialsStore(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }
    void guardarSecreto(long perfilId, String password) {
        if (password == null || password.isEmpty()) { return; }
        try {
            SecretKey clave = obtenerOCrearClave();
            Cipher cipher = Cipher.getInstance(TRANSFORMACION);
            cipher.init(Cipher.ENCRYPT_MODE, clave);
            byte[] iv = cipher.getIV();
            byte[] cifrado = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
            prefs.edit()
                    .putString("iv_" + perfilId, Base64.encodeToString(iv, Base64.NO_WRAP))
                    .putString("datos_" + perfilId, Base64.encodeToString(cifrado, Base64.NO_WRAP))
                    .apply();
        } catch (Exception e) {
            Log.e("IPCam-C", "Error al cifrar credencial", e);
        }
    }
    String obtenerSecreto(long perfilId) {
        String ivB64 = prefs.getString("iv_" + perfilId, null);
        String datosB64 = prefs.getString("datos_" + perfilId, null);
        if (ivB64 == null || datosB64 == null) { return null; }
        try {
            SecretKey clave = obtenerOCrearClave();
            Cipher cipher = Cipher.getInstance(TRANSFORMACION);
            GCMParameterSpec spec = new GCMParameterSpec(
                    128, Base64.decode(ivB64, Base64.NO_WRAP)
            );
            cipher.init(Cipher.DECRYPT_MODE, clave, spec);
            byte[] plano = cipher.doFinal(Base64.decode(datosB64, Base64.NO_WRAP));
            return new String(plano, StandardCharsets.UTF_8);
        } catch (Exception e) {
            Log.e("IPCam-C", "Error al descifrar credencial", e);
            return null;
        }
    }
    void eliminarSecreto(long perfilId) {
        prefs.edit()
                .remove("iv_" + perfilId)
                .remove("datos_" + perfilId)
                .apply();
    }
    private SecretKey obtenerOCrearClave() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(KEYSTORE_ALIAS)) {
            KeyStore.SecretKeyEntry entrada =
                    (KeyStore.SecretKeyEntry) keyStore.getEntry(KEYSTORE_ALIAS, null);
            return entrada.getSecretKey();
        }
        KeyGenerator generador = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore"
        );
        KeyGenParameterSpec spec = new KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build();
        generador.init(spec);
        return generador.generateKey();
    }
}
