package com.tonewire.tv.network;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

public final class SecureTokenStoreTest {
    @Test public void ciphertextPayloadStartsWithTheGeneratedGcmIv() {
        byte[] iv = new byte[SecureTokenStore.GCM_IV_LENGTH_BYTES];
        byte[] encrypted = {4, 5, 6};
        assertArrayEquals(new byte[] {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 4, 5, 6},
                SecureTokenStore.packCiphertext(iv, encrypted));
    }
}
