package com.tonewire.tv;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.tonewire.tv.network.NetworkStatus;
import com.tonewire.tv.network.TvNetworkServer;

/** Leanback-safe pairing, status, and recovery screen; audio controls remain on the phone. */
public final class TvStatusActivity extends Activity {
    private static final int GRAPHITE = Color.rgb(18, 21, 20);
    private static final int LIME = Color.rgb(200, 255, 0);
    private final Handler handler = new Handler();
    private TextView codeView;
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

    @Override protected void onResume() { super.onResume(); handler.post(statusUpdater); }
    @Override protected void onPause() { handler.removeCallbacks(statusUpdater); super.onPause(); }

    private View createContent() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setGravity(Gravity.CENTER); root.setPadding(76, 48, 76, 48);
        root.setBackgroundColor(GRAPHITE);
        TextView brand = label("T O N E W I R E", 18, LIME); root.addView(brand);
        TextView title = label("Pair your phone", 42, Color.WHITE); title.setPadding(0, 22, 0, 8); root.addView(title);
        TextView instruction = label("Open Tonewire on your phone. It will find this TV on the same Wi-Fi network.", 22, Color.LTGRAY);
        instruction.setPadding(0, 0, 0, 26); root.addView(instruction);
        codeView = label("------", 56, LIME); codeView.setGravity(Gravity.CENTER); codeView.setLetterSpacing(0.14f);
        root.addView(codeView, new LinearLayout.LayoutParams(-1, -2));
        statusView = label("", 19, Color.WHITE); statusView.setGravity(Gravity.CENTER); statusView.setPadding(0, 14, 0, 8); root.addView(statusView);
        detailView = label("", 16, Color.LTGRAY); detailView.setGravity(Gravity.CENTER); root.addView(detailView);
        Button refresh = new Button(this); refresh.setText("NEW PAIRING CODE"); refresh.setTextColor(Color.BLACK); refresh.setTextSize(18); refresh.setBackgroundColor(LIME);
        refresh.setContentDescription("Generate a new six digit pairing code"); refresh.setFocusable(true); refresh.setOnClickListener(view -> { server.refreshPairingCode(); updateStatus(); });
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, -2); buttonParams.setMargins(0, 32, 0, 8); root.addView(refresh, buttonParams);
        TextView footer = label("Use the paired phone to tune sound while you watch. This screen never changes audio settings.", 16, Color.GRAY);
        footer.setGravity(Gravity.CENTER); footer.setPadding(0, 18, 0, 0); root.addView(footer);
        return root;
    }

    private void updateStatus() {
        if (server == null) return;
        NetworkStatus status = server.getStatus();
        codeView.setText(status.getPairingCode());
        long seconds = Math.max(0L, status.getPairingRemainingMillis() / 1000L);
        statusView.setText(status.isRunning() ? "READY TO PAIR  ·  code refreshes in " + seconds + "s" : "NETWORK SERVER OFFLINE");
        String clients = status.getConnectedClients() == 1 ? "1 phone connected" : status.getConnectedClients() + " phones connected";
        detailView.setText(status.getDetail() + "  ·  " + clients + (status.getPort() > 0 ? "  ·  port " + status.getPort() : ""));
    }

    private TextView label(String value, float size, int color) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color); view.setFocusable(false); return view;
    }
}
