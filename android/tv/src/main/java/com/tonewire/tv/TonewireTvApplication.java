package com.tonewire.tv;

import android.app.Application;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;

import com.tonewire.tv.network.CommandDispatcher;
import com.tonewire.tv.network.TvNetworkServer;

/** Owns lightweight process-wide LAN resources; the equalizer service supplies its dispatcher. */
public final class TonewireTvApplication extends Application {
    private TvNetworkServer networkServer;

    @Override public void onCreate() {
        super.onCreate();
        networkServer = new TvNetworkServer(this);
        networkServer.start();
        Intent service = new Intent(this, TvEqualizerService.class);
        startForegroundService(service);
        bindService(service, engineConnection, BIND_AUTO_CREATE);
    }

    public TvNetworkServer getNetworkServer() { return networkServer; }
    public void setCommandDispatcher(CommandDispatcher dispatcher) { networkServer.setCommandDispatcher(dispatcher); }

    @Override public void onTerminate() {
        if (networkServer != null) networkServer.close();
        unbindService(engineConnection);
        super.onTerminate();
    }

    private final ServiceConnection engineConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            if (binder instanceof TvEqualizerController) setCommandDispatcher(
                    new com.tonewire.tv.network.EngineCommandDispatcher((TvEqualizerController) binder));
        }
        @Override public void onServiceDisconnected(ComponentName name) { }
    };
}
