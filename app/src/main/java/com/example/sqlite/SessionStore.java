package com.example.sqlite;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import org.json.JSONObject;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Cifra la sesión local usando una clave AES protegida por Android Keystore. */
public class SessionStore {
    private static final String PREFS = "warehouse_session_secure";
    private static final String DATA = "encrypted_session";
    private static final String KEY_ALIAS = "warehouse_session_aes_key";
    private static final int IV_LENGTH = 12;
    private final SharedPreferences prefs;

    public SessionStore(Context context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        context.getSharedPreferences("warehouse_session", Context.MODE_PRIVATE).edit().clear().apply();
    }

    public void save(long id, String username, String name, String role) {
        try {
            JSONObject data = new JSONObject();
            data.put("id", id);
            data.put("username", username);
            data.put("name", name);
            data.put("role", role);
            byte[] encrypted = encrypt(data.toString().getBytes(StandardCharsets.UTF_8));
            prefs.edit().putString(DATA, Base64.encodeToString(encrypted, Base64.NO_WRAP)).apply();
        } catch (Exception e) {
            clear();
            throw new IllegalStateException("No se pudo proteger la sesión local", e);
        }
    }

    public boolean active() { return read() != null && id() > 0; }
    public long id() { try { return read().getLong("id"); } catch (Exception e) { return -1; } }
    public String username() { return value("username"); }
    public String name() { return value("name"); }
    public String role() { return value("role"); }
    public void clear() { prefs.edit().remove(DATA).apply(); }

    private String value(String field) {
        try { JSONObject data = read(); return data == null ? "" : data.optString(field, ""); }
        catch (Exception e) { clear(); return ""; }
    }

    private JSONObject read() {
        try {
            String value = prefs.getString(DATA, null);
            if (value == null) return null;
            return new JSONObject(new String(decrypt(Base64.decode(value, Base64.NO_WRAP)), StandardCharsets.UTF_8));
        } catch (Exception e) {
            clear();
            return null;
        }
    }

    private byte[] encrypt(byte[] plain) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, secretKey());
        byte[] iv = cipher.getIV();
        byte[] ciphertext = cipher.doFinal(plain);
        return ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array();
    }

    private byte[] decrypt(byte[] payload) throws Exception {
        if (payload.length <= IV_LENGTH) throw new IllegalArgumentException("Sesión cifrada inválida");
        byte[] iv = new byte[IV_LENGTH];
        byte[] ciphertext = new byte[payload.length - IV_LENGTH];
        ByteBuffer.wrap(payload).get(iv).get(ciphertext);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), new GCMParameterSpec(128, iv));
        return cipher.doFinal(ciphertext);
    }

    private SecretKey secretKey() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        java.security.Key existing = store.getKey(KEY_ALIAS, null);
        if (existing instanceof SecretKey) return (SecretKey) existing;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }
}


