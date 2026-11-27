package com.tonewire.tv;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.ForegroundServiceStartNotAllowedException;
import android.content.Context;
import android.content.ComponentName;
import android.os.Build;
import com.tonewire.tv.network.EngineCommandDispatcher;
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
    private static final String CHANNEL_ID = "tonewire_service_state";
    private static final int NOTIFICATION_ID = 1;
    private static final String TAG = "TonewireEq";
    private static final long[] RETRY_DELAYS_MS = {500, 1000, 2000};
    private int openAttempts;
    private volatile boolean destroyed;
    private volatile String engineStatus = "Starting equalizer";
    private final Runnable openEngine = new Runnable() { @Override public void run() { startEngine(); } };

    /** Called only by the visible Activity or genuine BOOT_COMPLETED receiver. */
    public static boolean requestStart(Context context, String entryPoint) {
        try {
            ComponentName component = context.startForegroundService(new Intent(context, TvEqualizerService.class));
            if (component == null) {
                Log.e(TAG, "Service start returned null from " + entryPoint);
                return false;
            }
            Log.i(TAG, "Service start accepted from " + entryPoint + ": " + component);
            return true;
        } catch (ForegroundServiceStartNotAllowedException failure) {
            Log.e(TAG, "Foreground service start not allowed from " + entryPoint, failure);
            return false;
        } catch (SecurityException failure) {
            Log.e(TAG, "Foreground service permission denied from " + entryPoint, failure);
            return false;
        }
    }

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
        Log.i(TAG, "Service onCreate");
        createNotificationChannel();
        // API 31 has no matching type for an equalizer controller. API 34 requires specialUse.
        startForeground(NOTIFICATION_ID, createNotification(), FgsPolicy.typeForSdk(Build.VERSION.SDK_INT));
        ((TonewireTvApplication) getApplication()).setCommandDispatcher(new EngineCommandDispatcher(binder, TvIdentity.name(this)));
        engineThread = new HandlerThread("tonewire-eq", android.os.Process.THREAD_PRIORITY_AUDIO);
        engineThread.start();
        engineHandler = new Handler(engineThread.getLooper());
        engineHandler.post(openEngine);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        engineHandler.post(new Runnable() { @Override public void run() {
            if (state == null && openAttempts > RETRY_DELAYS_MS.length) {
                openAttempts = 0;
                startEngine();
            }
        } });
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    @Override
    public void onDestroy() {
        destroyed = true;
        ((TonewireTvApplication) getApplication()).setCommandDispatcher(null);
        if (engineHandler != null) {
            engineHandler.removeCallbacks(openEngine);
            engineHandler.post(new Runnable() {
                @Override public void run() {
                    stopRouteObserverSafely();
                    try {
                        if (stateRepository != null) stateRepository.flush();
                    } catch (RuntimeException failure) {
                        Log.e(TAG, "Could not flush equalizer state during shutdown", failure);
                    }
                    releaseEngine(engine);
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
                NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification createNotification() {
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(state == null ? engineStatus :
                        (!state.hasControl ? "Another app controls EQ" : state.enabled ? "EQ on" : "EQ off")
                                + " · " + TonewirePresets.displayName(activePresetName))
                .setContentIntent(PendingIntent.getActivity(this, 0,
                        new Intent(this, TvStatusActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE))
                .setOngoing(true)
                .build();
    }

    private void publishState() {
        ((TonewireTvApplication) getApplication()).getNetworkServer().publishState();
    }

    private void updateNotification() {
        if (!destroyed) getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, createNotification());
    }

    private void releaseEngine(EqualizerEngine target) {
        if (target == null) return;
        try {
            target.restoreAndRelease();
        } catch (RuntimeException failure) {
            Log.e(TAG, "Equalizer restoration could not be verified", failure);
        }
    }

    private void stopRouteObserverSafely() {
        if (routeObserver == null) return;
        try {
            routeObserver.stop();
        } catch (RuntimeException failure) {
            Log.e(TAG, "Could not stop equalizer route observer", failure);
        }
        routeObserver = null;
    }

    private void startEngine() {
        if (destroyed || state != null) return;
        if (stateRepository == null) stateRepository = new TvStateRepository(this, engineHandler);
        engine = new EqualizerEngine();
        EqualizerEngine.State opened;
        openAttempts++;
        try {
            opened = engine.open();
            Log.i(TAG, "Engine opened: " + opened.capabilities + "; enabled=" + opened.enabled
                    + "; levels=" + java.util.Arrays.toString(opened.levelsMillibels)
                    + "; control=" + opened.hasControl);
        } catch (RuntimeException failure) {
            Log.e(TAG, "Equalizer open attempt " + openAttempts + " failed", failure);
            releaseEngine(engine);
            engine = null;
            if (openAttempts <= RETRY_DELAYS_MS.length) {
                engineStatus = "Equalizer unavailable · retrying";
                engineHandler.postDelayed(openEngine, RETRY_DELAYS_MS[openAttempts - 1]);
            } else {
                engineStatus = "Equalizer unavailable · reopen Tonewire to retry";
            }
            updateNotification();
            return;
        }
        try {
            EqPreset saved = PresetSelector.select(null, stateRepository.getDefaultPreset(),
                    opened.capabilities.getBandCount());
            if (opened.hasControl && saved != null) {
                engine.apply(saved);
                activePresetName = saved.getName();
            }
            if (opened.hasControl) engine.setEnabled(stateRepository.getEnabled(opened.enabled));
            state = engine.snapshot();
            Log.i(TAG, "State restored: enabled=" + state.enabled + "; levels="
                    + java.util.Arrays.toString(state.levelsMillibels));
            routeObserver = new RouteObserver(this, engineHandler, new RouteObserver.Listener() {
                @Override public void onRouteSnapshot(RouteSnapshot snapshot) { onRoutesChanged(snapshot); }
            });
            routeObserver.start();
        } catch (RuntimeException failure) {
            Log.e(TAG, "Could not restore equalizer state", failure);
            stopRouteObserverSafely();
            releaseEngine(engine);
            engine = null;
            state = null;
            engineStatus = "Equalizer state could not be restored";
        }
        updateNotification();
        publishState();
    }

    private void onRoutesChanged(RouteSnapshot snapshot) {
        routes = snapshot;
        AudioRoute selected = snapshot.getSelectedRoute();
        if (selected == null || engine == null || state == null || stateRepository == null) {
            publishState();
            return;
        }
        try {
            state = engine.snapshot();
            if (state.hasControl) {
                EqPreset routePreset = PresetSelector.select(
                        stateRepository.getRoutePreset(selected.getFingerprint()),
                        stateRepository.getDefaultPreset(), state.capabilities.getBandCount());
                if (routePreset != null) {
                    engine.apply(routePreset);
                    activePresetName = routePreset.getName();
                    state = engine.snapshot();
                }
            }
        } catch (RuntimeException failure) {
            Log.e(TAG, "Route recall unavailable", failure);
            state = null;
            engineStatus = "Equalizer unavailable";
        }
        updateNotification();
        publishState();
    }

    /** Local binder intended for a same-process LAN server; never expose it outside this app. */
    public final class LocalBinder extends android.os.Binder implements TvEqualizerController {
        @Override public EqualizerEngine.State getState() {
            if (state == null || destroyed) return null;
            try {
                return call(new EngineCall<EqualizerEngine.State>() { @Override public EqualizerEngine.State run() {
                    EqualizerEngine.State previous = state;
                    state = engine.snapshot();
                    if (previous.hasControl != state.hasControl) updateNotification();
                    return state;
                } });
            } catch (RuntimeException failure) {
                Log.e(TAG, "Equalizer readback unavailable", failure);
                state = null;
                engineStatus = "Equalizer unavailable";
                updateNotification();
                return null;
            }
        }
        @Override public String getActivePresetName() { return activePresetName; }
        @Override public RouteSnapshot getRoutes() { return routes; }
        @Override public boolean supportsSavedPresets() { return true; }
        @Override public java.util.List<com.tonewire.protocol.PresetCatalog.Entry> getPresets() {
            if (state == null) return TonewirePresets.catalog();
            return call(() -> stateRepository.presetCatalog());
        }
        @Override public EqPreset resolvePreset(String id, EqCapabilities capabilities) {
            return call(() -> {
                EqPreset saved = stateRepository.savedPreset(id);
                return saved != null ? saved : TonewirePresets.create(id, capabilities);
            });
        }
        @Override public void savePreset(String name) {
            call(() -> { stateRepository.saveNamedPreset(name, engine.snapshot().levelsMillibels); return null; });
        }
        @Override public void deletePreset(String id) {
            call(() -> { stateRepository.deleteNamedPreset(id); return null; });
        }
        @Override public int setBandLevel(final int band, final int requestedMillibels) {
            return call(new EngineCall<Integer>() { @Override public Integer run() { activePresetName = "custom"; int level = engine.setBandLevel(band, requestedMillibels); persistCurrent(); return level; } });
        }
        @Override public void setEnabled(final boolean enabled) { call(new EngineCall<Void>() { @Override public Void run() { engine.setEnabled(enabled); state = engine.snapshot(); stateRepository.saveCurrent(new EqPreset(activePresetName, state.levelsMillibels), state.enabled); updateNotification(); return null; } }); }
        @Override public void applyPreset(final EqPreset preset) { call(new EngineCall<Void>() { @Override public Void run() { engine.apply(preset); activePresetName = preset.getName(); persistCurrent(); return null; } }); }
        @Override public void setRoutePreset(final String fingerprint, final EqPreset preset) { call(new EngineCall<Void>() { @Override public Void run() { stateRepository.setRoutePreset(fingerprint, preset); return null; } }); }
        @Override public void setAutostartEnabled(final boolean enabled) { call(new EngineCall<Void>() { @Override public Void run() { stateRepository.setAutostartEnabled(enabled); return null; } }); }
    }

    private void persistCurrent() { state = engine.snapshot(); stateRepository.saveEditedPreset(routes.getSelectedRoute(), new EqPreset(activePresetName, state.levelsMillibels)); updateNotification(); }
    private interface EngineCall<T> { T run(); }
    private <T> T call(final EngineCall<T> call) {
        if (engineHandler == null || engineThread == null || destroyed) throw new IllegalStateException("service is not ready");
        if (android.os.Looper.myLooper() == engineThread.getLooper()) {
            if (engine == null || state == null) throw new IllegalStateException("Equalizer is unavailable");
            return call.run();
        }
        final java.util.concurrent.atomic.AtomicReference<T> result = new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.concurrent.atomic.AtomicReference<RuntimeException> error = new java.util.concurrent.atomic.AtomicReference<>();
        final java.util.concurrent.CountDownLatch completed = new java.util.concurrent.CountDownLatch(1);
        engineHandler.post(new Runnable() { @Override public void run() { try { if (engine == null || state == null) throw new IllegalStateException("Equalizer is unavailable"); result.set(call.run()); } catch (RuntimeException failure) { error.set(failure); } finally { completed.countDown(); } } });
        try { completed.await(); } catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new IllegalStateException("interrupted", interrupted); }
        if (error.get() != null) throw error.get();
        return result.get();
    }
}
