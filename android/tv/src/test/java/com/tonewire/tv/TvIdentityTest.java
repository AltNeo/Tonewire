package com.tonewire.tv;

import android.content.SharedPreferences;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.Test;
import static org.junit.Assert.*;

public final class TvIdentityTest {
    @Test public void randomIdentityIsPersistedOnceAndSurvivesRecreation() {
        Memory memory = new Memory();
        String created = TvIdentity.id(memory.preferences());
        assertEquals(created, UUID.fromString(created).toString());
        assertEquals(created, TvIdentity.id(memory.preferences()));
        assertEquals(1, memory.commits);
    }
    @Test public void separateInstallationsHaveDifferentIdentities() {
        assertNotEquals(TvIdentity.id(new Memory().preferences()), TvIdentity.id(new Memory().preferences()));
    }
    @Test public void failedPersistenceDoesNotAdvertiseEphemeralIdentity() {
        Memory memory = new Memory(); memory.succeeds = false;
        assertThrows(IllegalStateException.class, () -> TvIdentity.id(memory.preferences()));
    }
    private static final class Memory {
        final Map<String, String> values = new HashMap<>();
        int commits;
        boolean succeeds = true;
        SharedPreferences preferences() {
            return (SharedPreferences) Proxy.newProxyInstance(SharedPreferences.class.getClassLoader(),
                    new Class<?>[] {SharedPreferences.class}, (proxy, method, args) -> {
                        if ("getString".equals(method.getName())) return values.getOrDefault(args[0], (String) args[1]);
                        if ("edit".equals(method.getName())) {
                            Map<String, String> staged = new HashMap<>();
                            return Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),
                                    new Class<?>[] {SharedPreferences.Editor.class}, (editor, editMethod, editArgs) -> {
                                        if ("putString".equals(editMethod.getName())) {
                                            staged.put((String) editArgs[0], (String) editArgs[1]); return editor;
                                        }
                                        if ("commit".equals(editMethod.getName())) {
                                            commits++; if (succeeds) values.putAll(staged); return succeeds;
                                        }
                                        throw new UnsupportedOperationException(editMethod.getName());
                                    });
                        }
                        throw new UnsupportedOperationException(method.getName());
                    });
        }
    }
}
