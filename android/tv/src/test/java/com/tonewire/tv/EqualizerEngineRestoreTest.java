package com.tonewire.tv;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.media.audiofx.AudioEffect;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

/**
 * Behavioral tests for teardown restoration ordering and control safety.
 *
 * <p>These tests describe the intended protection: never leave a television louder than the user
 * left it while restoring, never write after control has been lost, always release the effect, and
 * surface rollback failures instead of swallowing them.</p>
 */
public final class EqualizerEngineRestoreTest {
    private static final int[] BOOSTED_ORIGINAL = {1500, 800, 1500, 300, 900};
    private static final int[] CUT_CURVE = {-100, -100, -100, -100, -100};

    @Test public void bypassesBeforeWritingAnyBandWhenOriginalWasDisabled() {
        FakeEffect effect = new FakeEffect();
        effect.enabled = false;
        effect.levels = BOOSTED_ORIGINAL.clone();
        EqualizerEngine engine = new EqualizerEngine(() -> effect);
        engine.open();
        engine.setEnabled(true);
        engine.apply(new EqPreset("Cut", CUT_CURVE));
        effect.mutations.clear();

        engine.restoreAndRelease();

        assertEquals("First teardown write must bypass, before any boosted band is restored",
                "enabled:false", effect.mutations.get(0));
        assertTrue(effect.released);
        assertTrue(effect.mutations.contains("band:0:1500"));
        assertTrue(effect.mutations.contains("band:2:1500"));
        int bypassAt = effect.mutations.indexOf("enabled:false");
        for (String mutation : effect.mutations) {
            if (mutation.startsWith("band:")) {
                assertTrue("Band restore must follow the bypass: " + effect.mutations,
                        effect.mutations.indexOf(mutation) > bypassAt);
            }
        }
    }

    @Test public void doesNotWriteAnyBandWhenControlWasLostWithoutCallback() {
        FakeEffect effect = new FakeEffect();
        effect.enabled = false;
        effect.levels = BOOSTED_ORIGINAL.clone();
        EqualizerEngine engine = new EqualizerEngine(() -> effect);
        engine.open();
        engine.setEnabled(true);
        engine.apply(new EqPreset("Cut", CUT_CURVE));
        effect.mutations.clear();
        // Simulate a missed OnControlStatusChange callback: cached control is still true.
        effect.control = false;

        try {
            engine.restoreAndRelease();
        } catch (IllegalStateException reported) {
            // Reporting the unverifiable rollback is acceptable; writing is not.
        }

        assertTrue("No effect writes are safe without live control: " + effect.mutations,
                effect.mutations.isEmpty());
        assertTrue(effect.released);
    }

    @Test public void stopsAfterProtectiveBypassWhenControlIsLost() {
        FakeEffect effect = new FakeEffect();
        effect.enabled = false;
        effect.levels = BOOSTED_ORIGINAL.clone();
        effect.afterMutation = mutation -> {
            if ("enabled:false".equals(mutation)) effect.control = false;
        };
        EqualizerEngine engine = new EqualizerEngine(() -> effect);
        engine.open();
        engine.setEnabled(true);
        engine.apply(new EqPreset("Cut", CUT_CURVE));
        effect.mutations.clear();

        try {
            engine.restoreAndRelease();
        } catch (IllegalStateException reported) {
            // A reported partial rollback is acceptable.
        }

        assertEquals("Only the protective bypass may run once control is gone: " + effect.mutations,
                1, effect.mutations.size());
        assertEquals("enabled:false", effect.mutations.get(0));
        assertTrue(effect.released);
    }

    @Test public void releasesTheEffectWhenOpenThrowsDuringCapabilityDiscovery() {
        FakeEffect effect = new FakeEffect();
        effect.failDiscovery = true;
        EqualizerEngine engine = new EqualizerEngine(() -> effect);

        assertThrows(RuntimeException.class, engine::open);

        assertTrue("A partially attached effect must still be released", effect.released);
        assertEquals(1, effect.releaseCount);
        assertTrue(effect.mutations.isEmpty());
    }

    @Test public void releasesTheEffectWhenOpenThrowsAfterTheOriginalSnapshot() {
        FakeEffect effect = new FakeEffect();
        effect.failListenerRegistration = true;
        EqualizerEngine engine = new EqualizerEngine(() -> effect);

        assertThrows(RuntimeException.class, engine::open);

        assertTrue("A failure after attach must not leak the effect", effect.released);
        assertEquals(1, effect.releaseCount);
    }

    @Test public void originalSnapshotIsNotReplacedByAnAppliedPreset() {
        FakeEffect effect = new FakeEffect();
        effect.enabled = false;
        effect.levels = BOOSTED_ORIGINAL.clone();
        EqualizerEngine engine = new EqualizerEngine(() -> effect);
        engine.open();
        engine.apply(new EqPreset("Cut", CUT_CURVE));
        engine.setEnabled(true);

        engine.restoreAndRelease();

        assertArrayEquals("The persisted preset must not replace the pre-attach baseline",
                BOOSTED_ORIGINAL, effect.levels);
        assertFalse("The pre-attach bypass state must be restored", effect.enabled);
        assertTrue(effect.mutations.contains("band:0:1500"));
        assertTrue(effect.mutations.contains("band:1:800"));
        assertTrue(effect.mutations.contains("band:2:1500"));
        assertTrue(effect.mutations.contains("band:3:300"));
        assertTrue(effect.mutations.contains("band:4:900"));
    }

    @Test public void stopsWritingBandsAfterControlIsLostDuringBandRestore() {
        FakeEffect effect = new FakeEffect();
        effect.enabled = false;
        effect.levels = BOOSTED_ORIGINAL.clone();
        EqualizerEngine engine = new EqualizerEngine(() -> effect);
        engine.open();
        engine.setEnabled(true);
        engine.apply(new EqPreset("Cut", CUT_CURVE));
        effect.mutations.clear();
        effect.afterMutation = mutation -> {
            if (mutation.startsWith("band:0:")) effect.control = false;
        };

        try {
            engine.restoreAndRelease();
        } catch (IllegalStateException reported) {
            // A reported partial rollback is acceptable.
        }

        int bandsWritten = 0;
        for (String mutation : effect.mutations) if (mutation.startsWith("band:")) bandsWritten++;
        assertEquals("Control loss during one band write must stop the remaining bands: "
                + effect.mutations, 1, bandsWritten);
        assertTrue(effect.released);
    }

    @Test public void reportsFailedDisableInsteadOfSilentlyLeavingTheEffectEnabled() {
        FakeEffect effect = new FakeEffect();
        effect.enabled = false;
        effect.levels = BOOSTED_ORIGINAL.clone();
        effect.disableStatus = AudioEffect.ERROR;
        EqualizerEngine engine = new EqualizerEngine(() -> effect);
        engine.open();
        engine.setEnabled(true);
        engine.apply(new EqPreset("Cut", CUT_CURVE));

        assertThrows("A failed protective bypass must not be reported as success",
                IllegalStateException.class, engine::restoreAndRelease);
        assertTrue("Release remains mandatory after a failed bypass", effect.released);
        assertEquals(1, effect.releaseCount);
    }

    @Test public void reportsFailedBandRestoreButStillReleases() {
        FakeEffect effect = new FakeEffect();
        effect.enabled = false;
        effect.levels = BOOSTED_ORIGINAL.clone();
        EqualizerEngine engine = new EqualizerEngine(() -> effect);
        engine.open();
        engine.setEnabled(true);
        engine.apply(new EqPreset("Cut", CUT_CURVE));
        effect.failBandIndex = 2;
        effect.mutations.clear();

        assertThrows("A partial rollback must be surfaced", IllegalStateException.class,
                engine::restoreAndRelease);
        assertTrue("Release is still required after a failed band restore", effect.released);
        assertEquals(1, effect.releaseCount);
        assertEquals("enabled:false", effect.mutations.get(0));
    }

    @Test public void rejectsSilentBypassNoOpBeforeWritingBands() {
        FakeEffect effect = new FakeEffect();
        effect.enabled = false;
        effect.levels = BOOSTED_ORIGINAL.clone();
        effect.disableNoOp = true;
        EqualizerEngine engine = new EqualizerEngine(() -> effect);
        engine.open();
        engine.setEnabled(true);
        engine.apply(new EqPreset("Cut", CUT_CURVE));
        effect.mutations.clear();

        assertThrows(IllegalStateException.class, engine::restoreAndRelease);

        assertFalse("No band may be rewritten while the bypass readback still says enabled",
                effect.mutations.stream().anyMatch(mutation -> mutation.startsWith("band:")));
        assertTrue(effect.released);
    }

    @Test public void rejectsSilentBandReadbackMismatch() {
        FakeEffect effect = new FakeEffect();
        effect.enabled = false;
        effect.levels = BOOSTED_ORIGINAL.clone();
        EqualizerEngine engine = new EqualizerEngine(() -> effect);
        engine.open();
        effect.levels = CUT_CURVE.clone();
        effect.enabled = true;
        effect.ignoreBandWrites = true;

        assertThrows(IllegalStateException.class, engine::restoreAndRelease);
        assertTrue("Readback mismatch must not prevent release", effect.released);
    }

    @Test public void restoresOriginallyEnabledStateAndVerifiesIt() {
        FakeEffect effect = new FakeEffect();
        effect.enabled = true;
        effect.levels = BOOSTED_ORIGINAL.clone();
        EqualizerEngine engine = new EqualizerEngine(() -> effect);
        engine.open();
        engine.setEnabled(false);
        engine.apply(new EqPreset("Cut", CUT_CURVE));
        effect.mutations.clear();

        engine.restoreAndRelease();

        assertArrayEquals(BOOSTED_ORIGINAL, effect.levels);
        assertTrue("The originally enabled effect must be re-enabled", effect.enabled);
        assertEquals("band:0:1500", effect.mutations.get(0));
        assertTrue(effect.mutations.contains("enabled:true"));
        assertTrue(effect.released);
    }

    @Test public void openFailureKeepsOriginalExceptionAndSuppressesCleanupFailure() {
        FakeEffect effect = new FakeEffect();
        effect.enabled = false;
        effect.levels = BOOSTED_ORIGINAL.clone();
        effect.failListenerRegistration = true;
        effect.disableStatus = AudioEffect.ERROR;
        EqualizerEngine engine = new EqualizerEngine(() -> effect);

        IllegalStateException original = assertThrows(IllegalStateException.class, engine::open);

        assertEquals("listener registration failed", original.getMessage());
        assertEquals("Cleanup failure must be suppressed on the original open exception", 1,
                original.getSuppressed().length);
        assertTrue(original.getSuppressed()[0].getMessage().contains("bypass failed"));
        assertTrue(effect.released);
    }

    @Test public void reportsReleaseFailureAndResetsTheEngine() {
        FakeEffect effect = new FakeEffect();
        effect.enabled = false;
        effect.levels = BOOSTED_ORIGINAL.clone();
        effect.failRelease = true;
        EqualizerEngine engine = new EqualizerEngine(() -> effect);
        engine.open();
        engine.setEnabled(true);
        engine.apply(new EqPreset("Cut", CUT_CURVE));

        assertThrows(IllegalStateException.class, engine::restoreAndRelease);
        assertTrue(effect.released);
        assertEquals(1, effect.releaseCount);
        assertThrows(IllegalStateException.class, engine::snapshot);
        engine.restoreAndRelease();
    }

    @Test public void reportsFailedUserEnableCommandAndReadbackStaysTruthful() {
        FakeEffect effect = new FakeEffect();
        effect.levels = BOOSTED_ORIGINAL.clone();
        EqualizerEngine engine = new EqualizerEngine(() -> effect);
        engine.open();
        effect.enableStatus = AudioEffect.ERROR;

        assertThrows(IllegalStateException.class, () -> engine.setEnabled(true));
        assertFalse("A failed enable must not be acknowledged as applied", effect.enabled);
        assertFalse("Snapshot must continue to report the real bypassed state",
                engine.snapshot().enabled);
    }

    private static final class FakeEffect implements EqualizerEngine.Effect {
        final List<String> mutations = new ArrayList<>();
        int[] levels = new int[5];
        boolean enabled;
        boolean control = true;
        boolean released;
        int releaseCount;
        int disableStatus = AudioEffect.SUCCESS;
        int enableStatus = AudioEffect.SUCCESS;
        boolean disableNoOp;
        boolean ignoreBandWrites;
        boolean failRelease;
        int failBandIndex = -1;
        boolean failDiscovery;
        boolean failListenerRegistration;
        MutationHook afterMutation;

        interface MutationHook { void afterMutation(String mutation); }

        @Override public boolean hasControl() { return control; }
        @Override public boolean getEnabled() { return enabled; }

        @Override public int setEnabled(boolean enabled) {
            int status = enabled ? enableStatus : disableStatus;
            if (status == AudioEffect.SUCCESS && !(enabled == false && disableNoOp)) {
                this.enabled = enabled;
            }
            record("enabled:" + enabled);
            return status;
        }

        @Override public void setBandLevel(short band, short level) {
            if (band == failBandIndex) throw new IllegalStateException("band write failed");
            if (!ignoreBandWrites) levels[band] = level;
            record("band:" + band + ":" + level);
        }

        @Override public short getNumberOfBands() { return (short) levels.length; }
        @Override public short[] getBandLevelRange() {
            if (failDiscovery) throw new IllegalStateException("discovery failed");
            return new short[] {-1500, 1500};
        }
        @Override public int getCenterFreq(short band) { return 60_000 * (band + 1); }
        @Override public int[] getBandFreqRange(short band) { return new int[] {20_000, 200_000}; }
        @Override public short getNumberOfPresets() { return 0; }
        @Override public String getPresetName(short preset) { return ""; }
        @Override public short getBandLevel(short band) { return (short) levels[band]; }

        @Override public void setControlStatusListener(ControlListener listener) {
            if (failListenerRegistration) throw new IllegalStateException("listener registration failed");
        }

        @Override public void release() {
            released = true;
            releaseCount++;
            if (failRelease) throw new IllegalStateException("release failed");
        }

        private void record(String mutation) {
            mutations.add(mutation);
            if (afterMutation != null) afterMutation.afterMutation(mutation);
        }
    }
}
