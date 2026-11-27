package com.tonewire.tv;

import android.media.audiofx.AudioEffect;
import android.media.audiofx.Equalizer;

/** Thin owner for the global session-0 effect. All calls must occur on the service thread. */
public final class EqualizerEngine {
    /** Minimal effect surface; keeps Android details out of restore-ordering tests. */
    interface Effect {
        boolean hasControl();
        boolean getEnabled();
        int setEnabled(boolean enabled);
        void setBandLevel(short band, short level);
        short getNumberOfBands();
        short[] getBandLevelRange();
        int getCenterFreq(short band);
        int[] getBandFreqRange(short band);
        short getNumberOfPresets();
        String getPresetName(short preset);
        short getBandLevel(short band);
        void setControlStatusListener(ControlListener listener);
        void release();

        interface ControlListener { void onControlStatusChange(boolean control); }
    }

    interface EffectFactory { Effect create(); }

    public static final class State {
        public final EqCapabilities capabilities;
        public final int[] levelsMillibels;
        public final boolean enabled;
        public final boolean hasControl;

        State(EqCapabilities capabilities, int[] levelsMillibels, boolean enabled, boolean hasControl) {
            this.capabilities = capabilities;
            this.levelsMillibels = levelsMillibels;
            this.enabled = enabled;
            this.hasControl = hasControl;
        }
    }

    private static final EffectFactory ANDROID_EFFECTS = new EffectFactory() {
        @Override public Effect create() { return new AndroidEffect(); }
    };

    private final EffectFactory effectFactory;
    private Effect equalizer;
    private EqCapabilities capabilities;
    private int[] originalLevels;
    private boolean originalEnabled;
    private volatile boolean hasControl;

    public EqualizerEngine() { this(ANDROID_EFFECTS); }

    EqualizerEngine(EffectFactory effectFactory) { this.effectFactory = effectFactory; }

    public State open() {
        try {
            equalizer = effectFactory.create();
            hasControl = equalizer.hasControl();
            originalEnabled = equalizer.getEnabled();
            capabilities = discover(equalizer);
            originalLevels = readLevels();
            equalizer.setControlStatusListener(new Effect.ControlListener() {
                @Override public void onControlStatusChange(boolean control) { hasControl = control; }
            });
            return snapshot();
        } catch (RuntimeException failure) {
            RuntimeException cleanupFailure = restoreInternal();
            if (cleanupFailure != null) failure.addSuppressed(cleanupFailure);
            throw failure;
        }
    }

    public State snapshot() {
        ensureOpen();
        hasControl = equalizer.hasControl();
        return new State(capabilities, readLevels(), equalizer.getEnabled(), hasControl);
    }

    public int setBandLevel(int band, int requestedMillibels) {
        ensureControl();
        if (band < 0 || band >= capabilities.getBandCount()) throw new IllegalArgumentException("invalid band");
        int level = EqMath.clampMillibels(requestedMillibels, capabilities.getMinimumMillibels(),
                capabilities.getMaximumMillibels());
        equalizer.setBandLevel((short) band, (short) level);
        return level;
    }

    public void setEnabled(boolean enabled) {
        ensureControl();
        int status = equalizer.setEnabled(enabled);
        if (status != AudioEffect.SUCCESS) {
            throw new IllegalStateException("Equalizer enable failed with status " + status);
        }
    }

    public void apply(EqPreset preset) {
        ensureOpen();
        int[] levels = preset.getLevelsMillibels();
        if (levels.length != capabilities.getBandCount()) {
            throw new IllegalArgumentException("preset band count does not match hardware");
        }
        for (int band = 0; band < levels.length; band++) setBandLevel(band, levels[band]);
    }

    /**
     * Restores the pre-attach hardware state and releases the effect. Cleanup always completes
     * before a failure is thrown so a caller cannot mistake a partial rollback for success.
     */
    public void restoreAndRelease() {
        RuntimeException failure = restoreInternal();
        if (failure != null) throw failure;
    }

    private RuntimeException restoreInternal() {
        if (equalizer == null) return null;
        Effect effect = equalizer;
        int[] levels = originalLevels;
        boolean restoreEnabled = originalEnabled;
        RuntimeException failure = null;
        try {
            if (levels != null) {
                if (!effect.hasControl()) throw new IllegalStateException("Equalizer control lost before restore");
                if (!restoreEnabled) {
                    int disableStatus = effect.setEnabled(false);
                    if (disableStatus != AudioEffect.SUCCESS) {
                        throw new IllegalStateException("Equalizer bypass failed with status " + disableStatus);
                    }
                    if (effect.getEnabled()) {
                        throw new IllegalStateException("Equalizer bypass did not take effect");
                    }
                }
                for (int band = 0; band < levels.length; band++) {
                    if (!effect.hasControl()) {
                        throw new IllegalStateException("Equalizer control lost before band " + band);
                    }
                    effect.setBandLevel((short) band, (short) levels[band]);
                }
                if (restoreEnabled) {
                    if (!effect.hasControl()) throw new IllegalStateException("Equalizer control lost before enable");
                    int enableStatus = effect.setEnabled(true);
                    if (enableStatus != AudioEffect.SUCCESS) {
                        throw new IllegalStateException("Equalizer enable failed with status " + enableStatus);
                    }
                }
                for (int band = 0; band < levels.length; band++) {
                    if (!effect.hasControl()) {
                        throw new IllegalStateException("Equalizer control lost during readback");
                    }
                    if (effect.getBandLevel((short) band) != (short) levels[band]) {
                        throw new IllegalStateException("Equalizer band " + band + " readback mismatch");
                    }
                }
                if (effect.getEnabled() != restoreEnabled) {
                    throw new IllegalStateException("Equalizer enabled readback mismatch");
                }
            }
        } catch (RuntimeException restoreFailure) {
            failure = restoreFailure;
        } finally {
            try {
                effect.release();
            } catch (RuntimeException releaseFailure) {
                if (failure == null) failure = releaseFailure;
                else failure.addSuppressed(releaseFailure);
            } finally {
                equalizer = null;
                capabilities = null;
                originalLevels = null;
                originalEnabled = false;
                hasControl = false;
            }
        }
        return failure;
    }

    private EqCapabilities discover(Effect effect) {
        short[] range = effect.getBandLevelRange();
        int count = effect.getNumberOfBands();
        int[] centers = new int[count];
        int[][] ranges = new int[count][2];
        for (short band = 0; band < count; band++) {
            centers[band] = effect.getCenterFreq(band);
            int[] frequencyRange = effect.getBandFreqRange(band);
            ranges[band][0] = frequencyRange[0];
            ranges[band][1] = frequencyRange[1];
        }
        int presetCount = effect.getNumberOfPresets();
        String[] presets = new String[presetCount];
        for (short preset = 0; preset < presetCount; preset++) presets[preset] = effect.getPresetName(preset);
        return new EqCapabilities(range[0], range[1], centers, ranges, presets);
    }

    private int[] readLevels() {
        int[] levels = new int[capabilities.getBandCount()];
        for (short band = 0; band < levels.length; band++) levels[band] = equalizer.getBandLevel(band);
        return levels;
    }
    private void ensureControl() {
        ensureOpen();
        if (!equalizer.hasControl()) throw new IllegalStateException("Another app controls the equalizer");
    }
    private void ensureOpen() { if (equalizer == null) throw new IllegalStateException("effect is not open"); }

    /** Android adapter; this is the only place a real session-0 effect is created. */
    private static final class AndroidEffect implements Effect {
        private final Equalizer equalizer;

        AndroidEffect() { equalizer = new Equalizer(1000, 0); }

        @Override public boolean hasControl() { return equalizer.hasControl(); }
        @Override public boolean getEnabled() { return equalizer.getEnabled(); }
        @Override public int setEnabled(boolean enabled) { return equalizer.setEnabled(enabled); }
        @Override public void setBandLevel(short band, short level) { equalizer.setBandLevel(band, level); }
        @Override public short getNumberOfBands() { return equalizer.getNumberOfBands(); }
        @Override public short[] getBandLevelRange() { return equalizer.getBandLevelRange(); }
        @Override public int getCenterFreq(short band) { return equalizer.getCenterFreq(band); }
        @Override public int[] getBandFreqRange(short band) { return equalizer.getBandFreqRange(band); }
        @Override public short getNumberOfPresets() { return equalizer.getNumberOfPresets(); }
        @Override public String getPresetName(short preset) { return equalizer.getPresetName(preset); }
        @Override public short getBandLevel(short band) { return equalizer.getBandLevel(band); }

        @Override public void setControlStatusListener(final ControlListener listener) {
            equalizer.setControlStatusListener(new AudioEffect.OnControlStatusChangeListener() {
                @Override public void onControlStatusChange(AudioEffect effect, boolean control) {
                    listener.onControlStatusChange(control);
                }
            });
        }

        @Override public void release() { equalizer.release(); }
    }
}
