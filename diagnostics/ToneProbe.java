import android.media.audiofx.AudioEffect;
import android.media.audiofx.Equalizer;

public final class ToneProbe {
    private ToneProbe() {
    }

    public static void main(String[] args) {
        System.out.println("TONEWIRE_PROBE_VERSION=1");
        printEffectDescriptors();
        probeGlobalEqualizer();
    }

    private static void printEffectDescriptors() {
        System.out.println("EFFECT_DESCRIPTORS_BEGIN");
        AudioEffect.Descriptor[] descriptors = AudioEffect.queryEffects();
        if (descriptors == null) {
            System.out.println("EFFECT_DESCRIPTORS_UNAVAILABLE");
            return;
        }

        for (AudioEffect.Descriptor descriptor : descriptors) {
            System.out.println(
                    "EFFECT"
                            + "|name=" + descriptor.name
                            + "|implementor=" + descriptor.implementor
                            + "|type=" + descriptor.type
                            + "|uuid=" + descriptor.uuid
                            + "|mode=" + descriptor.connectMode
            );
        }
        System.out.println("EFFECT_DESCRIPTORS_END");
    }

    private static void probeGlobalEqualizer() {
        Equalizer equalizer = null;
        try {
            equalizer = new Equalizer(1000, 0);
            System.out.println("GLOBAL_EQ_CREATED=true");
            System.out.println("GLOBAL_EQ_HAS_CONTROL=" + equalizer.hasControl());
            System.out.println("GLOBAL_EQ_ENABLED_INITIAL=" + equalizer.getEnabled());

            short[] levelRange = equalizer.getBandLevelRange();
            System.out.println("GLOBAL_EQ_LEVEL_MIN_MB=" + levelRange[0]);
            System.out.println("GLOBAL_EQ_LEVEL_MAX_MB=" + levelRange[1]);

            short bandCount = equalizer.getNumberOfBands();
            System.out.println("GLOBAL_EQ_BAND_COUNT=" + bandCount);
            for (short band = 0; band < bandCount; band++) {
                int[] frequencyRange = equalizer.getBandFreqRange(band);
                System.out.println(
                        "BAND"
                                + "|index=" + band
                                + "|center_mhz=" + equalizer.getCenterFreq(band)
                                + "|min_mhz=" + frequencyRange[0]
                                + "|max_mhz=" + frequencyRange[1]
                                + "|level_mb=" + equalizer.getBandLevel(band)
                );
            }

            short presetCount = equalizer.getNumberOfPresets();
            System.out.println("GLOBAL_EQ_PRESET_COUNT=" + presetCount);
            for (short preset = 0; preset < presetCount; preset++) {
                System.out.println(
                        "PRESET|index=" + preset + "|name=" + equalizer.getPresetName(preset)
                );
            }
            System.out.println("GLOBAL_EQ_PROBE_SUCCESS=true");
        } catch (Throwable error) {
            System.out.println("GLOBAL_EQ_PROBE_SUCCESS=false");
            System.out.println("GLOBAL_EQ_ERROR=" + error);
            error.printStackTrace(System.out);
        } finally {
            if (equalizer != null) {
                equalizer.release();
            }
        }
    }
}
