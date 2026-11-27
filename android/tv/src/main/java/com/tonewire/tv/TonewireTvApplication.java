package com.tonewire.tv;

import android.app.Application;

import com.tonewire.tv.network.CommandDispatcher;
import com.tonewire.tv.network.TvNetworkServer;

/** Owns LAN resources only. Boot and visible user launch own service startup. */
public final class TonewireTvApplication extends Application {
    private TvNetworkServer networkServer;

    @Override public void onCreate() {
        super.onCreate();
        networkServer = new TvNetworkServer(this);
        networkServer.start();
    }

    public TvNetworkServer getNetworkServer() { return networkServer; }
    public void setCommandDispatcher(CommandDispatcher dispatcher) { networkServer.setCommandDispatcher(dispatcher); }
}
