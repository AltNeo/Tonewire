package com.tonewire.tv.network;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;
import android.content.SharedPreferences;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public final class SecureTokenStoreTest {
    @Test public void ciphertextPayloadStartsWithTheGeneratedGcmIv() {
        byte[] iv = new byte[SecureTokenStore.GCM_IV_LENGTH_BYTES];
        byte[] encrypted = {4, 5, 6};
        assertArrayEquals(new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 5, 6},
                SecureTokenStore.packCiphertext(iv, encrypted));
    }
    @Test public void forgetClearsEveryPairedCredentialAndReportsStorageFailure() {
        Map<String, String> values = new HashMap<>();
        values.put("token.phone1", "encrypted1"); values.put("token.phone2", "encrypted2");
        boolean[] succeed = {true};
        SharedPreferences preferences = (SharedPreferences) Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),
                new Class<?>[] {SharedPreferences.class}, (proxy, method, args) -> {
                    if ("getAll".equals(method.getName())) return new HashMap<>(values);
                    if ("edit".equals(method.getName())) return Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),
                            new Class<?>[] {SharedPreferences.Editor.class}, (editor, editMethod, editArgs) -> {
                                if ("clear".equals(editMethod.getName())) return editor;
                                if ("commit".equals(editMethod.getName())) {
                                    if (succeed[0]) values.clear();
                                    return succeed[0];
                                }
                                throw new UnsupportedOperationException();
                            });
                    throw new UnsupportedOperationException();
                });
        SecureTokenStore store = new SecureTokenStore(preferences);
        assertEquals(2, store.count());
        store.clear(); assertEquals(0, store.count());
        succeed[0] = false;
        assertThrows(IllegalStateException.class, store::clear);
    }
}
