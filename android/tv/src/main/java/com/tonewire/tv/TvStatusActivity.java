package com.tonewire.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.ScrollView;

import com.tonewire.tv.network.NetworkStatus;
import com.tonewire.tv.network.TvNetworkServer;

/** Leanback-safe pairing, status, and recovery screen; audio controls remain on the phone. */
public final class TvStatusActivity extends Activity {
    private static final int GRAPHITE = Color.rgb(18, 21, 20);
    private static final int LIME = Color.rgb(200, 255, 0);
    private final Handler handler = new Handler();
    private TextView soundView;
    private TextView outputView;
    private TextView presetView;
    private TextView healthView;
    private TextView codeView;
    private Button pairButton;
    private boolean pairingRequested;
    private TextView statusView;
    private TextView detailView;
    private TvNetworkServer server;
    private final Runnable statusUpdater = new Runnable() {
        @Override public void run() { updateStatus(); handler.postDelayed(this, 1000L); }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        server = ((TonewireTvApplication) getApplication()).getNetworkServer();
        setContentView(createContent());
    }

    @Override protected void onResume() { super.onResume(); TvEqualizerService.requestStart(this, "user launch"); handler.post(statusUpdater); }
    @Override protected void onPause() { handler.removeCallbacks(statusUpdater); super.onPause(); }

    private View createContent() {
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(GRAPHITE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setGravity(Gravity.CENTER_VERTICAL); root.setPadding(76, 32, 76, 32);
        scroll.addView(root);
        root.addView(label("T O N E W I R E", 14, LIME));
        root.addView(label(TvIdentity.name(this), 26, Color.WHITE));
        soundView = label("EQ starting", 32, Color.WHITE); root.addView(soundView);
        outputView = label("", 18, Color.LTGRAY); root.addView(outputView);
        presetView = label("", 18, Color.LTGRAY); root.addView(presetView);
        healthView = label("", 16, LIME); root.addView(healthView);
        detailView = label("", 15, Color.LTGRAY); detailView.setPadding(0, 8, 0, 10); root.addView(detailView);
        codeView = label("------", 36, LIME); codeView.setLetterSpacing(.14f); root.addView(codeView);
        statusView = label("", 15, Color.LTGRAY); root.addView(statusView);
        pairButton = new Button(this); pairButton.setText("PAIR A PHONE"); pairButton.setTextSize(15);
        pairButton.setContentDescription("Pair a phone"); pairButton.setFocusable(true);
        pairButton.setOnClickListener(view -> {
            pairingRequested = !pairingRequested;
            if (pairingRequested) server.refreshPairingCode();
            updateStatus();
        });
        root.addView(pairButton, new LinearLayout.LayoutParams(-1, -2));
        Button forget = new Button(this); forget.setText("FORGET ALL PHONES"); forget.setTextSize(15);
        forget.setContentDescription("Forget all paired phones"); forget.setFocusable(true);
        forget.setOnClickListener(view -> new AlertDialog.Builder(this).setTitle("Forget all phones?")
                .setMessage("All phone connections will close. Pair again to control this TV.")
                .setNegativeButton("Cancel", null).setPositiveButton("Forget", (dialog, which) -> {
                    try { server.forgetAllPhones(); pairingRequested = true; updateStatus(); }
                    catch (RuntimeException failure) { healthView.setText("Could not forget phones. Please retry."); }
                }).show());
        root.addView(forget, new LinearLayout.LayoutParams(-1, -2));
        return scroll;
    }

    private void updateStatus() {
        if (server == null) return;
        TvSoundStatus sound = TvSoundStatus.from(server.getLocalSnapshot());
        soundView.setText(sound.eq);
        outputView.setText("Output · " + sound.output);
        presetView.setText("Preset · " + sound.preset);
        healthView.setText(sound.health);
        NetworkStatus status = server.getStatus();
        int phones = status.getConnectedClients();
        detailView.setText((phones == 1 ? "1 phone connected" : phones + " phones connected")
                + (status.isRunning() ? " · port " + status.getPort() : " · network server offline"));
        boolean showCode = TvSoundStatus.showPairingCode(pairingRequested, server.getPairedPhoneCount());
        codeView.setVisibility(showCode ? View.VISIBLE : View.GONE);
        statusView.setVisibility(showCode ? View.VISIBLE : View.GONE);
        pairButton.setText(pairingRequested ? "HIDE PAIRING CODE" : "PAIR A PHONE");
        if (showCode) {
            codeView.setText(status.getPairingCode());
            long lock = server.getPairingLockRemainingMillis();
            statusView.setText(lock > 0 ? "Pairing locked · try again in " + ((lock + 999) / 1000) + "s"
                    : "Enter this code on your phone · refreshes in " + status.getPairingRemainingMillis() / 1000 + "s");
        }
    }

    private TextView label(String value, float size, int color) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); view.setFocusable(false); return view;
    }
}
