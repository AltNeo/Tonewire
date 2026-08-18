package com.tonewire.probe;

import android.app.Activity;
import android.graphics.Color;
import android.media.audiofx.AudioEffect;
import android.media.audiofx.Equalizer;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.widget.TextView;

public final class ToneProbeActivity extends Activity {
    private static final String TAG = "TonewireProbe";

    private Equalizer equalizer;
    private TextView reportView;
    private final StringBuilder report = new StringBuilder();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureReportView();
        append("Tonewire capability probe");
        append("No EQ parameters will be changed.");
        append("");
        printEffectDescriptors();
        probeGlobalEqualizer();
    }

    @Override
    protected void onDestroy() {
        if (equalizer != null) {
            equalizer.release();
            equalizer = null;
        }
        super.onDestroy();
    }

    private void configureReportView() {
        reportView = new TextView(this);
        reportView.setBackgroundColor(Color.rgb(13, 16, 14));
        reportView.setTextColor(Color.rgb(217, 255, 107));
        reportView.setTextSize(16);
        reportView.setGravity(Gravity.START | Gravity.TOP);
        reportView.setPadding(48, 36, 48, 36);
        setContentView(reportView);
    }

    private void printEffectDescriptors() {
        AudioEffect.Descriptor[] descriptors = AudioEffect.queryEffects();
        append("Effect descriptors: " + (descriptors == null ? 0 : descriptors.length));
        if (descriptors == null) {
            return;
        }

        for (AudioEffect.Descriptor descriptor : descriptors) {
            if ("Equalizer".equals(descriptor.name)
                    || "Dynamic Bass Boost".equals(descriptor.name)
                    || "Virtualizer".equals(descriptor.name)) {
                append(
                        "Effect: " + descriptor.name
                                + " / " + descriptor.implementor
                                + " / " + descriptor.connectMode
                );
            }
        }
    }

    private void probeGlobalEqualizer() {
        try {
            equalizer = new Equalizer(1000, 0);
            append("GLOBAL_EQ_CREATED=true");
            append("GLOBAL_EQ_HAS_CONTROL=" + equalizer.hasControl());
            append("GLOBAL_EQ_ENABLED_INITIAL=" + equalizer.getEnabled());

            short[] levelRange = equalizer.getBandLevelRange();
            append("GLOBAL_EQ_LEVEL_MIN_MB=" + levelRange[0]);
            append("GLOBAL_EQ_LEVEL_MAX_MB=" + levelRange[1]);

            short bandCount = equalizer.getNumberOfBands();
            append("GLOBAL_EQ_BAND_COUNT=" + bandCount);
            for (short band = 0; band < bandCount; band++) {
                int[] frequencyRange = equalizer.getBandFreqRange(band);
                append(
                        "BAND"
                                + "|index=" + band
                                + "|center_mhz=" + equalizer.getCenterFreq(band)
                                + "|min_mhz=" + frequencyRange[0]
                                + "|max_mhz=" + frequencyRange[1]
                                + "|level_mb=" + equalizer.getBandLevel(band)
                );
            }

            append("GLOBAL_EQ_PRESET_COUNT=" + equalizer.getNumberOfPresets());
            append("GLOBAL_EQ_PROBE_SUCCESS=true");
        } catch (Throwable error) {
            append("GLOBAL_EQ_PROBE_SUCCESS=false");
            append("GLOBAL_EQ_ERROR=" + error);
            Log.e(TAG, "Probe failed", error);
        }
    }

    private void append(String line) {
        Log.i(TAG, line);
        report.append(line).append('\n');
        reportView.setText(report.toString());
    }
}
