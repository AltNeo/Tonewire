package com.tonewire.tv;

/** Named curve stored in millibels. A curve is only valid for its recorded band count. */
public final class EqPreset {
    private final String name;
    private final int[] levelsMillibels;

    public EqPreset(String name, int[] levelsMillibels) {
        this.name = name == null ? "Default" : name;
        this.levelsMillibels = levelsMillibels.clone();
    }

    public String getName() { return name; }
    public int[] getLevelsMillibels() { return levelsMillibels.clone(); }
}
