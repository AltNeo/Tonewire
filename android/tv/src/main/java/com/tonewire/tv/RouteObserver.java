package com.tonewire.tv;

import android.content.Context;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Handler;

import java.util.ArrayList;
import java.util.List;

/** Observes connected output devices without polling. */
public final class RouteObserver {
    public interface Listener { void onRouteSnapshot(RouteSnapshot snapshot); }

    private final AudioManager audioManager;
    private final Handler handler;
    private final Listener listener;
    private final AudioDeviceCallback callback = new AudioDeviceCallback() {
        @Override public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) { dispatch(); }
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) { dispatch(); }
    };

    public RouteObserver(Context context, Handler handler, Listener listener) {
        audioManager = context.getSystemService(AudioManager.class);
        this.handler = handler;
        this.listener = listener;
    }
    public void start() { audioManager.registerAudioDeviceCallback(callback, handler); dispatch(); }
    public void stop() { audioManager.unregisterAudioDeviceCallback(callback); }

    private void dispatch() {
        AudioDeviceInfo[] devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS);
        List<AudioRoute> routes = new ArrayList<>();
        for (AudioDeviceInfo device : devices) {
            AudioRoute route = AudioRoute.from(device);
            if (route.getKind() != AudioRoute.Kind.UNKNOWN) routes.add(route);
        }
        listener.onRouteSnapshot(new RouteSnapshot(routes));
    }
}
