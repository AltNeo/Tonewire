package com.tonewire.tv;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Starts the lightweight foreground-service shell after boot on the Android 9 target. */
public final class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            TvStateRepository repository = new TvStateRepository(context, new android.os.Handler());
            if (repository.isAutostartEnabled()) {
                context.startForegroundService(new Intent(context, TvEqualizerService.class));
            }
        }
    }
}
