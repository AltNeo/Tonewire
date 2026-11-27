package com.tonewire.tv;

import android.content.SharedPreferences;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import static org.junit.Assert.*;

/** Exercises the actual persistence API across independent repository instances. */
public final class TvStateRepositoryTest {
    @Test public void savesEnabledAndDisabledWithExactCurveAcrossRecreation() {
        MemoryPreferences memory = new MemoryPreferences();
        int[] levels = {300, -100, 0, -300, 300};
        TvStateRepository first = new TvStateRepository(memory.preferences, null);
        first.saveCurrent(new EqPreset("Night watch", levels), true);
        TvStateRepository restarted = new TvStateRepository(memory.preferences, null);
        assertTrue(restarted.getEnabled(false));
        assertArrayEquals(levels, restarted.getDefaultPreset().getLevelsMillibels());
        assertEquals("Night watch", restarted.getDefaultPreset().getName());
        restarted.saveCurrent(new EqPreset("Night watch", levels), false);
        assertFalse(new TvStateRepository(memory.preferences, null).getEnabled(true));
    }

    @Test public void upgradeWithoutEnabledFlagPreservesHardwareDefault() {
        MemoryPreferences memory = new MemoryPreferences();
        memory.values.put("default", "Default|300|0|0|0|300");
        TvStateRepository repository = new TvStateRepository(memory.preferences, null);
        assertTrue(repository.getEnabled(true));
        assertFalse(repository.getEnabled(false));
        assertArrayEquals(new int[] {300, 0, 0, 0, 300}, repository.getDefaultPreset().getLevelsMillibels());
    }

    @Test public void failedCommitIsReportedInsteadOfAcknowledgedAsDurable() {
        MemoryPreferences memory = new MemoryPreferences();
        memory.commitSucceeds = false;
        TvStateRepository repository = new TvStateRepository(memory.preferences, null);
        assertThrows(IllegalStateException.class,
                () -> repository.saveCurrent(new EqPreset("Test", new int[] {0}), true));
    }

    @Test public void editsSaveLatestRouteAndFallbackInOneDebouncedWrite() {
        MemoryPreferences memory = new MemoryPreferences();
        ManualScheduler scheduler = new ManualScheduler();
        TvStateRepository repository = new TvStateRepository(memory.preferences, scheduler);
        AudioRoute hdmi = new AudioRoute(AudioRoute.Kind.HDMI, "Living Room TV", "hdmi:0", 2);
        repository.setRoutePreset(hdmi.getFingerprint(), new EqPreset("Night watch", new int[] {-500, 100}));
        repository.saveEditedPreset(hdmi, new EqPreset("Custom", new int[] {-400, 100}));
        repository.saveEditedPreset(hdmi, new EqPreset("Custom", new int[] {-300, 100}));
        // Recall before the debounce expires must also see the latest edit.
        assertArrayEquals(new int[] {-300, 100}, repository.getRoutePreset(hdmi.getFingerprint()).getLevelsMillibels());
        assertTrue(memory.values.isEmpty());
        assertEquals(0, memory.commits);
        assertEquals(750, scheduler.delayMillis);
        scheduler.write.run();
        assertEquals(1, memory.applies);
        assertEquals(0, memory.commits);
        TvStateRepository restarted = new TvStateRepository(memory.preferences, null);
        assertArrayEquals(new int[] {-300, 100}, restarted.getRoutePreset(hdmi.getFingerprint()).getLevelsMillibels());
        assertArrayEquals(new int[] {-300, 100}, restarted.getDefaultPreset().getLevelsMillibels());
    }

    @Test public void returningToOutputRecallsItsEditInsteadOfOtherOutputsDefault() {
        MemoryPreferences memory = new MemoryPreferences();
        ManualScheduler scheduler = new ManualScheduler();
        TvStateRepository repository = new TvStateRepository(memory.preferences, scheduler);
        AudioRoute tv = new AudioRoute(AudioRoute.Kind.HDMI, "TV", "hdmi:0", 2);
        AudioRoute headphones = new AudioRoute(AudioRoute.Kind.BLUETOOTH, "Headphones", "headset", 3);
        repository.saveEditedPreset(tv, new EqPreset("Custom", new int[] {-300, 100}));
        repository.saveEditedPreset(headphones, new EqPreset("Custom", new int[] {-100, -200}));
        assertArrayEquals(new int[] {-300, 100}, repository.getRoutePreset(tv.getFingerprint()).getLevelsMillibels());
        scheduler.write.run();
        TvStateRepository restarted = new TvStateRepository(memory.preferences, null);
        EqPreset recalled = PresetSelector.select(restarted.getRoutePreset(tv.getFingerprint()), restarted.getDefaultPreset(), 2);
        assertArrayEquals(new int[] {-300, 100}, recalled.getLevelsMillibels());
        assertArrayEquals(new int[] {-100, -200}, restarted.getDefaultPreset().getLevelsMillibels());
    }

    @Test public void unseenOutputFallsBackToMostRecentGlobalEdit() {
        MemoryPreferences memory = new MemoryPreferences();
        ManualScheduler scheduler = new ManualScheduler();
        TvStateRepository repository = new TvStateRepository(memory.preferences, scheduler);
        repository.saveEditedPreset(new AudioRoute(AudioRoute.Kind.HDMI, "TV", "hdmi:0", 2),
                new EqPreset("Custom", new int[] {-300, 0}));
        scheduler.write.run();
        TvStateRepository restarted = new TvStateRepository(memory.preferences, null);
        assertNull(restarted.getRoutePreset("usb_device:new"));
        EqPreset selected = PresetSelector.select(restarted.getRoutePreset("usb_device:new"), restarted.getDefaultPreset(), 2);
        assertArrayEquals(new int[] {-300, 0}, selected.getLevelsMillibels());
    }

    @Test public void enableCommitAlsoDurablyFlushesPendingRouteEdit() {
        MemoryPreferences memory = new MemoryPreferences();
        TvStateRepository repository = new TvStateRepository(memory.preferences, new ManualScheduler());
        AudioRoute route = new AudioRoute(AudioRoute.Kind.HDMI, "TV", "hdmi:0", 2);
        EqPreset preset = new EqPreset("Custom", new int[] {-300, 0});
        repository.saveEditedPreset(route, preset);
        repository.saveCurrent(preset, true);
        assertEquals(1, memory.commits);
        TvStateRepository restarted = new TvStateRepository(memory.preferences, null);
        assertTrue(restarted.getEnabled(false));
        assertArrayEquals(new int[] {-300, 0}, restarted.getRoutePreset(route.getFingerprint()).getLevelsMillibels());
    }

    @Test public void namedCurveSurvivesRecreationAndCanBeDeletedWithoutChangingSound() {
        MemoryPreferences memory = new MemoryPreferences();
        TvStateRepository repository = new TvStateRepository(memory.preferences, new ManualScheduler());
        repository.saveCurrent(new EqPreset("Baseline", new int[] {300, 0}), false);
        String id = repository.saveNamedPreset("Late movie, TV: one", new int[] {-100, -300});
        TvStateRepository restarted = new TvStateRepository(memory.preferences, new ManualScheduler());
        assertArrayEquals(new int[] {-100, -300}, restarted.savedPreset(id).getLevelsMillibels());
        assertEquals("Late movie, TV: one", restarted.savedPreset(id).getName());
        assertEquals(4, restarted.presetCatalog().size());
        restarted.deleteNamedPreset(id);
        assertNull(restarted.savedPreset(id));
        assertEquals(3, restarted.presetCatalog().size());
        assertArrayEquals(new int[] {300, 0}, restarted.getDefaultPreset().getLevelsMillibels());
        assertFalse(restarted.getEnabled(true));
    }

    @Test public void namedPresetLimitAndBuiltinProtectionAreEnforced() {
        MemoryPreferences memory = new MemoryPreferences();
        TvStateRepository repository = new TvStateRepository(memory.preferences, new ManualScheduler());
        for (int i = 0; i < 8; i++) repository.saveNamedPreset("Saved " + i, new int[] {-100});
        assertEquals(11, repository.presetCatalog().size());
        assertThrows(IllegalArgumentException.class, () -> repository.saveNamedPreset("Ninth", new int[] {0}));
        assertThrows(IllegalArgumentException.class, () -> repository.deleteNamedPreset("flat_reference"));
    }

    @Test public void emptyDuplicateAndUnsafePresetNamesAreRejected() {
        TvStateRepository repository = new TvStateRepository(new MemoryPreferences().preferences, new ManualScheduler());
        repository.saveNamedPreset("Movie", new int[] {0});
        assertThrows(IllegalArgumentException.class, () -> repository.saveNamedPreset(" movie ", new int[] {0}));
        assertThrows(IllegalArgumentException.class, () -> repository.saveNamedPreset("", new int[] {0}));
        assertThrows(IllegalArgumentException.class, () -> repository.saveNamedPreset("bad|name", new int[] {0}));
        assertThrows(IllegalArgumentException.class, () -> repository.saveNamedPreset("Night watch", new int[] {0}));
    }

    private static final class ManualScheduler implements TvStateRepository.WriteScheduler {
        Runnable write;
        long delayMillis;
        @Override public void cancel(Runnable write) { if (this.write == write) this.write = null; }
        @Override public void schedule(Runnable write, long delayMillis) { this.write = write; this.delayMillis = delayMillis; }
    }

    private static final class MemoryPreferences {
        final Map<String, Object> values = new HashMap<>();
        boolean commitSucceeds = true;
        int commits;
        int applies;
        final SharedPreferences preferences = (SharedPreferences) Proxy.newProxyInstance(
                SharedPreferences.class.getClassLoader(), new Class<?>[] {SharedPreferences.class},
                (proxy, method, args) -> {
                    if ("getBoolean".equals(method.getName()) || "getString".equals(method.getName())) {
                        return values.getOrDefault(args[0], args[1]);
                    }
                    if ("getAll".equals(method.getName())) return new HashMap<>(values);
                    if ("edit".equals(method.getName())) return editor();
                    throw new UnsupportedOperationException(method.getName());
                });
        private SharedPreferences.Editor editor() {
            Map<String, Object> staged = new HashMap<>();
            java.util.Set<String> removed = new java.util.HashSet<>();
            return (SharedPreferences.Editor) Proxy.newProxyInstance(SharedPreferences.Editor.class.getClassLoader(),
                    new Class<?>[] {SharedPreferences.Editor.class}, (proxy, method, args) -> {
                        if ("remove".equals(method.getName())) { removed.add((String) args[0]); return proxy; }
                        if (method.getName().startsWith("put")) {
                            staged.put((String) args[0], args[1]);
                            return proxy;
                        }
                        if ("apply".equals(method.getName())) {
                            applies++;
                            for (String key : removed) values.remove(key);
                            values.putAll(staged);
                            return null;
                        }
                        if ("commit".equals(method.getName())) {
                            commits++;
                            if (commitSucceeds) { for (String key : removed) values.remove(key); values.putAll(staged); }
                            return commitSucceeds;
                        }
                        throw new UnsupportedOperationException(method.getName());
                    });
        }
    }
}
