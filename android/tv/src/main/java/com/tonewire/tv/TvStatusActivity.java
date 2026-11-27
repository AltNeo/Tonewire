package com.tonewire.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;

import com.tonewire.tv.network.TvNetworkServer;

/** Read-only TV control surface; all sound controls remain on the phone. */
public final class TvStatusActivity extends Activity {
    private final Handler handler = new Handler();
    private TvStatusLayout layout;
    private boolean pairingRequested;
    private TvNetworkServer server;
    private final Runnable statusUpdater =
            new Runnable() {
                @Override
                public void run() {
                    updateStatus();
                    handler.postDelayed(this, 1000L);
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        server = ((TonewireTvApplication) getApplication()).getNetworkServer();
        layout = new TvStatusLayout(this, TvIdentity.name(this));
        setContentView(layout.root);
        layout.pair.setOnClickListener(
                view -> {
                    pairingRequested = !pairingRequested;
                    if (pairingRequested) server.refreshPairingCode();
                    updateStatus();
                });
        layout.forget.setOnClickListener(
                view ->
                        new AlertDialog.Builder(this)
                                .setTitle("Forget all phones?")
                                .setMessage(
                                        "All phone connections will close. Pair again to control"
                                            + " this TV.")
                                .setNegativeButton("Cancel", null)
                                .setPositiveButton(
                                        "Forget",
                                        (dialog, which) -> {
                                            try {
                                                server.forgetAllPhones();
                                                pairingRequested = true;
                                                updateStatus();
                                            } catch (RuntimeException failure) {
                                                layout.error(
                                                        "Could not forget phones. Please retry.");
                                            }
                                        })
                                .show());
    }

    @Override
    protected void onResume() {
        super.onResume();
        TvEqualizerService.requestStart(this, "user launch");
        handler.post(statusUpdater);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(statusUpdater);
        super.onPause();
    }

    private void updateStatus() {
        if (server != null)
            layout.render(
                    TvSoundStatus.from(server.getLocalSnapshot()),
                    server.getStatus(),
                    server.getPairedPhoneCount(),
                    pairingRequested,
                    server.getPairingLockRemainingMillis());
    }
}
