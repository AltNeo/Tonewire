package com.tonewire.phone;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Keystore-backed storage for long-lived pairing credentials. */
public final class TokenStore {
    private static final String ALIAS = "tonewire_pairing_token";
    private final SharedPreferences preferences;

    public TokenStore(Context context) {
        preferences = context.getSharedPreferences("tonewire.phone.token", Context.MODE_PRIVATE);
    }

    public void save(String token) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        preferences.edit().putString("value", Base64.encodeToString(cipher.doFinal(
                token.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP)).putString("iv",
                Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)).apply();
    }

    public String get() {
        String encrypted = preferences.getString("value", "");
        String iv = preferences.getString("iv", "");
        if (encrypted.isEmpty() || iv.isEmpty()) {
            return "";
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128,
                    Base64.decode(iv, Base64.NO_WRAP)));
            return new String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)),
                    StandardCharsets.UTF_8);
        } catch (Exception exception) {
            clear();
            return "";
        }
    }

    public void clear() { preferences.edit().remove("value").remove("iv").apply(); }

    /** Stable non-secret peer identifier; it must survive Activity/process recreation for HMAC lookup. */
    public String getOrCreateDeviceId() {
        String existing = preferences.getString("device_id", "");
        if (!existing.isEmpty()) return existing;
        String created = "phone_" + Long.toUnsignedString(new java.security.SecureRandom().nextLong(), 36);
        preferences.edit().putString("device_id", created).apply();
        return created;
    }

    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (!store.containsAlias(ALIAS)) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,
                    "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            generator.generateKey();
        }
        return ((KeyStore.SecretKeyEntry) store.getEntry(ALIAS, null)).getSecretKey();
    }
}
