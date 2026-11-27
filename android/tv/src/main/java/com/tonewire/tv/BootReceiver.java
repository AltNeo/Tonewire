package com.tonewire.tv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Starts the lightweight foreground-service shell after boot on supported Android TVs. */
public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            TvStateRepository repository = new TvStateRepository(context, new android.os.Handler());
            if (repository.isAutostartEnabled()) {
                TvEqualizerService.requestStart(context, "boot");
            }
        }
    }
}
