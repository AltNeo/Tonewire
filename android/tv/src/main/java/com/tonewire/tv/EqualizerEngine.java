package com.tonewire.tv;

import android.media.audiofx.AudioEffect;
import android.media.audiofx.Equalizer;

/** Thin owner for the global session-0 effect. All calls must occur on the service thread. */
public final class EqualizerEngine {
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

    private Equalizer equalizer;
    private EqCapabilities capabilities;
    private int[] originalLevels;
    private boolean originalEnabled;
    private boolean hasControl;

    public State open() {
        try {
            equalizer = new Equalizer(1000, 0);
            hasControl = equalizer.hasControl();
            originalEnabled = equalizer.getEnabled();
            capabilities = discover(equalizer);
            originalLevels = readLevels();
            equalizer.setControlStatusListener(new AudioEffect.OnControlStatusChangeListener() {
                @Override public void onControlStatusChange(AudioEffect effect, boolean control) { hasControl = control; }
            });
            return snapshot();
        } catch (RuntimeException failure) {
            restoreAndRelease();
            throw failure;
        }
    }

    public State snapshot() {
        ensureOpen();
        return new State(capabilities, readLevels(), equalizer.getEnabled(), hasControl);
    }

    public int setBandLevel(int band, int requestedMillibels) {
        ensureOpen();
        if (band < 0 || band >= capabilities.getBandCount()) throw new IllegalArgumentException("invalid band");
        int level = EqMath.clampMillibels(requestedMillibels, capabilities.getMinimumMillibels(),
                capabilities.getMaximumMillibels());
        equalizer.setBandLevel((short) band, (short) level);
        return level;
    }

    public void setEnabled(boolean enabled) { ensureOpen(); equalizer.setEnabled(enabled); }

    public void apply(EqPreset preset) {
        ensureOpen();
        int[] levels = preset.getLevelsMillibels();
        if (levels.length != capabilities.getBandCount()) {
            throw new IllegalArgumentException("preset band count does not match hardware");
        }
        for (int band = 0; band < levels.length; band++) setBandLevel(band, levels[band]);
    }

    public void restoreAndRelease() {
        if (equalizer == null) return;
        try {
            if (hasControl && originalLevels != null) {
                for (int band = 0; band < originalLevels.length; band++) {
                    equalizer.setBandLevel((short) band, (short) originalLevels[band]);
                }
                equalizer.setEnabled(originalEnabled);
            }
        } catch (RuntimeException ignored) {
            // A dead effect cannot be restored; releasing remains mandatory.
        } finally {
            equalizer.release();
            equalizer = null;
            capabilities = null;
            originalLevels = null;
        }
    }

    private EqCapabilities discover(Equalizer effect) {
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
    private void ensureOpen() { if (equalizer == null) throw new IllegalStateException("effect is not open"); }
}
