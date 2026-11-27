package com.tonewire.tv.network;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Keystore-encrypted persistence for pairing tokens. */
public final class SecureTokenStore {
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "tonewire_pairing_tokens";
    private static final String PREFERENCES = "tonewire.secure_tokens";
    private static final String TOKEN_PREFIX = "token.";
    static final int GCM_IV_LENGTH_BYTES = 12;
    private final SharedPreferences preferences;

    public SecureTokenStore(Context context) {
        this(context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE));
    }

    SecureTokenStore(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    public synchronized void clear() {
        if (!preferences.edit().clear().commit()) throw new IllegalStateException("Could not forget paired phones");
    }

    public synchronized int count() {
        int count = 0;
        for (String key : preferences.getAll().keySet()) if (key.startsWith(TOKEN_PREFIX)) count++;
        return count;
    }

    public synchronized void save(String deviceId, String token) {
        preferences.edit().putString(TOKEN_PREFIX + safeId(deviceId), encrypt(token)).apply();
    }

    public synchronized boolean matches(String deviceId, String token) {
        String stored = preferences.getString(TOKEN_PREFIX + safeId(deviceId), null);
        if (stored == null || token == null) return false;
        String clear = decrypt(stored);
        return clear != null && constantTimeEquals(clear, token);
    }

    /** Reads a token only to create an in-memory verifier for an incoming connection. */
    public synchronized String load(String deviceId) {
        String stored = preferences.getString(TOKEN_PREFIX + safeId(deviceId), null);
        return stored == null ? null : decrypt(stored);
    }

    private String encrypt(String value) {
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            // AndroidKeyStore owns IV generation for encryption; API 28 rejects caller-provided IVs.
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
            byte[] nonce = cipher.getIV();
            if (nonce == null || nonce.length != GCM_IV_LENGTH_BYTES) {
                throw new IllegalStateException("AndroidKeyStore returned an invalid GCM IV");
            }
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(packCiphertext(nonce, encrypted), Base64.NO_WRAP);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to protect pairing token", exception);
        }
    }

    private String decrypt(String stored) {
        try {
            byte[] all = Base64.decode(stored, Base64.NO_WRAP);
            if (all.length <= GCM_IV_LENGTH_BYTES) return null;
            byte[] nonce = java.util.Arrays.copyOfRange(all, 0, GCM_IV_LENGTH_BYTES);
            byte[] encrypted = java.util.Arrays.copyOfRange(all, GCM_IV_LENGTH_BYTES, all.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, nonce));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            return null;
        }
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore store = KeyStore.getInstance(KEYSTORE);
        store.load(null);
        if (store.containsAlias(KEY_ALIAS)) return ((SecretKey) store.getKey(KEY_ALIAS, null));
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
        return generator.generateKey();
    }

    private static String safeId(String id) {
        if (!TvNetworkServer.validPeerId(id)) throw new IllegalArgumentException("invalid peerId");
        return id;
    }
    static byte[] packCiphertext(byte[] nonce, byte[] encrypted) {
        if (nonce == null || nonce.length != GCM_IV_LENGTH_BYTES || encrypted == null || encrypted.length == 0) {
            throw new IllegalArgumentException("invalid encrypted token payload");
        }
        return ByteBuffer.allocate(nonce.length + encrypted.length).put(nonce).put(encrypted).array();
    }
    private static boolean constantTimeEquals(String first, String second) {
        return java.security.MessageDigest.isEqual(first.getBytes(StandardCharsets.UTF_8),
                second.getBytes(StandardCharsets.UTF_8));
    }
}
