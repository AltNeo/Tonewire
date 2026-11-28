package com.tonewire.tv;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;

import com.tonewire.tv.network.TvNetworkServer;

/** Read-only TV control surface; all sound controls remain on the phone. */
public final class TvStatusActivity extends Activity {
    private static final long START_RETRY_DELAY_MILLIS = 300L;

    private final Handler handler = new Handler();
    private final Runnable startRetry =
            new Runnable() {
                @Override
                public void run() {
                    if (serviceStartRequested) return;
                    serviceStartRequested = true;
                    TvEqualizerService.requestStart(TvStatusActivity.this, "user launch (focus retry)");
                }
            };
    private TvStatusLayout layout;
    private boolean pairingRequested;
    private boolean serviceStartRequested;
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
        // Starting a foreground service from onResume can return null silently on API 31:
        // the activity may be resumed but not yet the focused/top app, and the platform
        // refuses the start without throwing. Waiting for window focus makes the start
        // reliable, and retrying covers the case where focus never arrives.
        requestServiceStartWhenFocused();
        handler.post(statusUpdater);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && !serviceStartRequested) {
            serviceStartRequested = true;
            TvEqualizerService.requestStart(this, "user launch");
        }
    }

    private void requestServiceStartWhenFocused() {
        if (hasWindowFocus()) {
            serviceStartRequested = true;
            TvEqualizerService.requestStart(this, "user launch");
        } else if (!serviceStartRequested) {
            // onWindowFocusChanged may have already fired before onResume, so retry shortly
            // rather than depending on a callback that will not come again.
            handler.postDelayed(startRetry, START_RETRY_DELAY_MILLIS);
        }
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(statusUpdater);
        handler.removeCallbacks(startRetry);
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
