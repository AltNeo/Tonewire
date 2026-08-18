package com.tonewire.tv;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;

/**
 * Foreground owner of the session-0 Equalizer. The binder is the hook used by the LAN server.
 */
public final class TvEqualizerService extends Service {
    private static final String CHANNEL_ID = "tonewire_service";
    private static final int NOTIFICATION_ID = 1;
    private static final String TAG = "TonewireEq";
    private final LocalBinder binder = new LocalBinder();
    private HandlerThread engineThread;
    private Handler engineHandler;
    private EqualizerEngine engine;
    private TvStateRepository stateRepository;
    private RouteObserver routeObserver;
    private volatile EqualizerEngine.State state;
    private volatile RouteSnapshot routes = new RouteSnapshot(java.util.Collections.<AudioRoute>emptyList());
    private volatile String activePresetName = "custom";

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, createNotification());
        engineThread = new HandlerThread("tonewire-eq", android.os.Process.THREAD_PRIORITY_AUDIO);
        engineThread.start();
        engineHandler = new Handler(engineThread.getLooper());
        engineHandler.post(new Runnable() {
            @Override public void run() { startEngine(); }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        if (engineHandler != null) {
            engineHandler.post(new Runnable() {
                @Override public void run() {
                    if (routeObserver != null) routeObserver.stop();
                    if (stateRepository != null) stateRepository.flush();
                    if (engine != null) engine.restoreAndRelease();
                }
            });
        }
        if (engineThread != null) engineThread.quitSafely();
        super.onDestroy();
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_MIN);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification createNotification() {
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notification_text))
                .setOngoing(true)
                .build();
    }

    private void startEngine() {
        stateRepository = new TvStateRepository(this, engineHandler);
        engine = new EqualizerEngine();
        try {
            state = engine.open();
            EqPreset saved = PresetSelector.select(null, stateRepository.getDefaultPreset(),
                    state.capabilities.getBandCount());
            if (saved != null) {
                engine.apply(saved);
                activePresetName = saved.getName();
            }
            state = engine.snapshot();
            routeObserver = new RouteObserver(this, engineHandler, new RouteObserver.Listener() {
                @Override public void onRouteSnapshot(RouteSnapshot snapshot) { onRoutesChanged(snapshot); }
            });
            routeObserver.start();
        } catch (RuntimeException failure) {
            Log.e(TAG, "Could not open global Equalizer", failure);
            if (engine != null) engine.restoreAndRelease();
            stopSelf();
        }
    }

    private void onRoutesChanged(RouteSnapshot snapshot) {
        routes = snapshot;
        AudioRoute selected = snapshot.getSelectedRoute();
        if (selected == null || engine == null || stateRepository == null) return;
        EqPreset routePreset = PresetSelector.select(
                stateRepository.getRoutePreset(selected.getFingerprint()),
                stateRepository.getDefaultPreset(), state.capabilities.getBandCount());
        if (routePreset != null) {
            engine.apply(routePreset);
            activePresetName = routePreset.getName();
            state = engine.snapshot();
        }
    }

    /** Local binder intended for a same-process LAN server; never expose it outside this app. */
    public final class LocalBinder extends android.os.Binder implements TvEqualizerController {
        @Override public EqualizerEngine.State getState() { return state; }
        @Override public String getActivePresetName() { return activePresetName; }
        @Override public RouteSnapshot getRoutes() { return routes; }
        @Override public int setBandLevel(final int band, final int requestedMillibels) {
            return call(new EngineCall<Integer>() { @Override public Integer run() { activePresetName = "custom"; int level = engine.setBandLevel(band, requestedMillibels); persistCurrent(); return level; } });
        }
        @Override public void setEnabled(final boolean enabled) { call(new EngineCall<Void>() { @Override public Void run() { engine.setEnabled(enabled); state = engine.snapshot(); return null; } }); }
        @Override public void applyPreset(final EqPreset preset) { call(new EngineCall<Void>() { @Override public Void run() { engine.apply(preset); activePresetName = preset.getName(); persistCurrent(); return null; } }); }
        @Override public void setRoutePreset(final String fingerprint, final EqPreset preset) { call(new EngineCall<Void>() { @Override public Void run() { stateRepository.setRoutePreset(fingerprint, preset); return null; } }); }
        @Override public void setAutostartEnabled(final boolean enabled) { call(new EngineCall<Void>() { @Override public Void run() { stateRepository.setAutostartEnabled(enabled); return null; } }); }
    }

    private void persistCurrent() { state = engine.snapshot(); stateRepository.setDefaultPreset(new EqPreset("Default", state.levelsMillibels)); }
    private interface EngineCall<T> { T run(); }
    private <T> T call(final EngineCall<T> call) {
        if (engineHandler == null || engineThread == null) throw new IllegalStateException("service is not ready");
        if (android.os.Looper.myLooper() == engineThread.getLooper()) return call.run();
        final java.util.concurrent.atomic.AtomicReference<T> result = new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.concurrent.atomic.AtomicReference<RuntimeException> error = new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.concurrent.CountDownLatch completed = new java.util.concurrent.CountDownLatch(1);
        engineHandler.post(new Runnable() { @Override public void run() { try { result.set(call.run()); } catch (RuntimeException failure) { error.set(failure); } finally { completed.countDown(); } } });
        try { completed.await(); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException("interrupted", interrupted); }
        if (error.get() != null) throw error.get();
        return result.get();
    }
}
