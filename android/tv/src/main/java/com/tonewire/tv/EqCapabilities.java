package com.tonewire.tv;

import java.util.Arrays;

/** Immutable, runtime-discovered Equalizer capabilities exposed to TV clients. */
public final class EqCapabilities {
    private final int minimumMillibels;
    private final int maximumMillibels;
    private final int[] centerFrequenciesMillihertz;
    private final int[][] bandRangesMillihertz;
    private final String[] presetNames;

    public EqCapabilities(int minimumMillibels, int maximumMillibels, int[] centers,
            int[][] ranges, String[] presetNames) {
        this.minimumMillibels = minimumMillibels;
        this.maximumMillibels = maximumMillibels;
        this.centerFrequenciesMillihertz = centers.clone();
        this.bandRangesMillihertz = copyRanges(ranges);
        this.presetNames = presetNames.clone();
    }

    public int getBandCount() { return centerFrequenciesMillihertz.length; }
    public int getMinimumMillibels() { return minimumMillibels; }
    public int getMaximumMillibels() { return maximumMillibels; }
    public int[] getCenterFrequenciesMillihertz() { return centerFrequenciesMillihertz.clone(); }
    public int[][] getBandRangesMillihertz() { return copyRanges(bandRangesMillihertz); }
    public String[] getPresetNames() { return presetNames.clone(); }

    private static int[][] copyRanges(int[][] source) {
        int[][] copy = new int[source.length][];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i].clone();
        }
        return copy;
    }

    @Override
    public String toString() {
        return "EqCapabilities{" + getBandCount() + " bands, " + minimumMillibels + ".."
                + maximumMillibels + " mB, presets=" + Arrays.toString(presetNames) + "}";
    }
}
