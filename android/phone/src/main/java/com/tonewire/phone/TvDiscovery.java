package com.tonewire.phone;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;

/** DNS-SD discovery with a manual-IP path retained by the activity. */
public final class TvDiscovery {
    public interface Listener { void onFound(String name, String host, int port, String tvId); void onError(String message); }
    private final NsdManager manager;
    private NsdManager.DiscoveryListener discovery;
    private final Listener listener;

    public TvDiscovery(Context context, Listener listener) {
        manager = (NsdManager) context.getSystemService(Context.NSD_SERVICE);
        this.listener = listener;
    }

    public void start() {
        stop();
        discovery = new NsdManager.DiscoveryListener() {
            @Override public void onDiscoveryStarted(String type) { }
            @Override public void onDiscoveryStopped(String type) { }
            @Override public void onServiceLost(NsdServiceInfo service) { }
            @Override public void onStartDiscoveryFailed(String type, int code) { listener.onError("TV discovery unavailable"); }
            @Override public void onStopDiscoveryFailed(String type, int code) { }
            @Override public void onServiceFound(NsdServiceInfo service) {
                if (!"_tonewire._tcp.".equals(service.getServiceType())) { return; }
                manager.resolveService(service, new NsdManager.ResolveListener() {
                    @Override public void onResolveFailed(NsdServiceInfo ignored, int code) { }
                    @Override public void onServiceResolved(NsdServiceInfo resolved) {
                        if (resolved.getHost() != null) listener.onFound(resolved.getServiceName(),
                                resolved.getHost().getHostAddress(), resolved.getPort(),
                                resolved.getAttributes().containsKey("tv_id")
                                        ? new String(resolved.getAttributes().get("tv_id"), java.nio.charset.StandardCharsets.UTF_8) : "");
                    }
                });
            }
        };
        manager.discoverServices("_tonewire._tcp.", NsdManager.PROTOCOL_DNS_SD, discovery);
    }

    public void stop() {
        if (discovery != null) { try { manager.stopServiceDiscovery(discovery); } catch (Exception ignored) { } discovery = null; }
    }
}
