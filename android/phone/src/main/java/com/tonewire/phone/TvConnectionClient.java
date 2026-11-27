package com.tonewire.phone;

import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import com.tonewire.protocol.AuthToken;
import com.tonewire.protocol.FrameDecoder;
import com.tonewire.protocol.ProtocolAuthenticator;
import com.tonewire.protocol.ProtocolCodec;
import com.tonewire.protocol.ProtocolMessage;
import com.tonewire.protocol.ProtocolMessages;
import com.tonewire.protocol.ProtocolTypes;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/** Persistent, authenticated v1 TCP client; all shared-protocol mapping lives here. */
public final class TvConnectionClient {
    private static final String TAG = "TonewirePhone";
    public interface Listener { void onState(PhoneState state); void onPairingRequired(); void onPairingAccepted(String token); void onError(String message); default void onTvIdentity(String tvId) { } default void onCommandRejected(String detail) { onError(detail); } }
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ExecutorService writerIo = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private final SecureRandom random = new SecureRandom();
    private final ConnectionGeneration generations = new ConnectionGeneration();
    private final Object connectionLock = new Object();
    private volatile Socket socket;
    private volatile BufferedWriter writer;
    private volatile boolean stopped;
    private String host;
    private int port;
    private String token;
    private final String deviceId;
    private String clientNonce;
    private String serverNonce;
    private String serverTvId = "";
    private volatile String pendingPairCode;
    private final RejectionResync rejectionResync = new RejectionResync();
    private final OptimisticRevision revisions = new OptimisticRevision();
    private final ConcurrentHashMap<Long, Long> mutationSentAt = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> mutationTypes = new ConcurrentHashMap<>();

    public TvConnectionClient(Listener listener, String deviceId) { this.listener = listener; this.deviceId = deviceId; }
    public void connect(String valueHost, int valuePort, String valueToken) {
        stopped = false; host = valueHost; port = valuePort; token = valueToken;
        long generation = generations.begin();
        Log.i(TAG, "connect requested gen=" + generation);
        closeCurrentSocket("replace");
        io.execute(() -> connectLoop(generation, 0));
    }
    public void disconnect() {
        stopped = true;
        long generation = generations.begin();
        Log.i(TAG, "disconnect gen=" + generation);
        closeCurrentSocket("disconnect");
        writerIo.shutdownNow();
        io.shutdownNow();
    }
    public void pair(String code) {
        pendingPairCode = code;
        send(ProtocolMessages.pairRequest(deviceId, code, nonce()));
    }
    public void requestState() { send(ProtocolMessages.stateGet(revisions.current())); }
    public void setEnabled(boolean enabled) { send(ProtocolMessages.eqEnable(revisions.allocateExpected(), enabled)); }
    public void setBand(int index, int millibels) { send(ProtocolMessages.eqBandSet(revisions.allocateExpected(), index, millibels)); }
    public void savePreset(String name) {
        send(new ProtocolMessage(ProtocolTypes.EQ_PRESET_SAVE, revisions.allocateExpected(), java.util.Collections.singletonMap("name", name)));
    }
    public void deletePreset(String id) {
        send(new ProtocolMessage(ProtocolTypes.EQ_PRESET_DELETE, revisions.allocateExpected(), java.util.Collections.singletonMap("preset_id", id)));
    }
    public void applyPreset(String presetId) { send(ProtocolMessages.presetApply(revisions.allocateExpected(), safeId(presetId))); }

    private void connectLoop(long generation, int attempt) {
        Socket connected = null;
        try {
            if (stopped || !generations.isCurrent(generation)) return;
            connected = new Socket(); connected.connect(new InetSocketAddress(host, port), 4_000); connected.setTcpNoDelay(true);
            BufferedWriter connectedWriter = new BufferedWriter(new OutputStreamWriter(connected.getOutputStream(), StandardCharsets.US_ASCII));
            synchronized (connectionLock) {
                if (stopped || !generations.isCurrent(generation)) return;
                revisions.resetForConnection();
                rejectionResync.receivedSnapshot();
                mutationSentAt.clear();
                mutationTypes.clear();
                socket = connected;
                writer = connectedWriter;
                Log.i(TAG, "published socket gen=" + generation + " socket=" + socketId(connected));
            }
            clientNonce = nonce(); readLoop(connected, generation);
        } catch (Exception exception) {
            Log.i(TAG, "connect/read exit gen=" + generation + " socket=" + socketId(connected)
                    + " reason=" + exception.getClass().getSimpleName());
            if (!stopped && generations.isCurrent(generation)) {
                if (attempt == 0) error("Connection interrupted. Reconnecting to TV.");
                scheduleReconnect(generation, Math.min(attempt, 30));
            }
        } finally { closeOwnedSocket(connected, generation); }
    }

    private void readLoop(Socket connected, long generation) throws Exception {
        connected.setSoTimeout(10_000);
        InputStream input = connected.getInputStream();
        FrameDecoder decoder = new FrameDecoder();
        byte[] buffer = new byte[1024];
        boolean heartbeatPending = false;
        while (!stopped) {
            int count;
            try {
                count = input.read(buffer);
            } catch (SocketTimeoutException timeout) {
                if (heartbeatPending) throw timeout;
                requestState();
                heartbeatPending = true;
                continue;
            }
            if (count == -1) break;
            heartbeatPending = false;
            for (ProtocolMessage message : decoder.accept(buffer, 0, count)) {
                if (!generations.isCurrent(generation)) return;
                receive(message, generation);
            }
        }
        Log.i(TAG, "read loop ended socket=" + socketId(connected) + " stopped=" + stopped);
        if (!stopped) throw new IOException("TV closed the connection");
    }

    private void receive(ProtocolMessage message, long generation) {
        try {
            Log.i(TAG, "recv type=" + message.getType() + " revision=" + message.getRevision());
            if (ProtocolTypes.HELLO.equals(message.getType())) {
                serverNonce = message.requireField("nonce");
                serverTvId = message.getFields().getOrDefault("tv_id", "");
                if (token.isEmpty()) {
                    if (pendingPairCode != null) send(ProtocolMessages.pairRequest(deviceId, pendingPairCode, nonce()));
                    else main.post(listener::onPairingRequired);
                }
                else send(ProtocolAuthenticator.authToken(AuthToken.fromBase64(token), deviceId, clientNonce, serverNonce));
            } else if (ProtocolTypes.PAIR_ACCEPT.equals(message.getType())) {
                String pairingToken = message.requireField("token");
                pendingPairCode = null;
                token = pairingToken;
                send(ProtocolAuthenticator.authToken(AuthToken.fromBase64(token), deviceId, clientNonce, serverNonce));
                main.post(() -> listener.onPairingAccepted(pairingToken));
            } else if (ProtocolTypes.AUTH_TOKEN.equals(message.getType())) {
                if ("ok".equals(message.getFields().get("status"))) {
                    String authenticatedTvId = serverTvId;
                    main.post(() -> {
                        if (generations.isCurrent(generation)) listener.onTvIdentity(authenticatedTvId);
                    });
                    requestState();
                }
            } else if (ProtocolTypes.PAIR_REJECT.equals(message.getType())) { pendingPairCode = null; error(message.requireField("reason")); }
            else if (ProtocolTypes.STATE_SNAPSHOT.equals(message.getType())) {
                revisions.observe(message.getRevision());
                logAcknowledgement(message.getRevision());
                PhoneState state = stateFrom(message);
                rejectionResync.receivedSnapshot();
                main.post(() -> listener.onState(state));
            } else if (ProtocolTypes.EQ_BAND_ACK.equals(message.getType())) {
                revisions.observe(message.getRevision());
                logAcknowledgement(message.getRevision());
            } else if (ProtocolTypes.ERROR.equals(message.getType())) {
                String errorCode = message.getFields().get("code");
                if ("stale_revision".equals(errorCode) || "invalid_command".equals(errorCode)
                        || "engine_unavailable".equals(errorCode) || "unsupported_command".equals(errorCode)) {
                    revisions.rejected(message.getRevision());
                    boolean requestResync = rejectionResync.requestNow(errorCode);
                    if (requestResync && !"stale_revision".equals(errorCode)) {
                        String detail = message.getFields().getOrDefault("detail", "Command failed");
                        main.post(() -> listener.onCommandRejected(detail));
                    }
                    if (requestResync) {
                        Log.i(TAG, "command rejected; requesting state resync");
                        requestState();
                    } else if ("engine_unavailable".equals(errorCode)) {
                        error("The TV equalizer is starting or unavailable. Waiting for state.");
                    }
                } else if ("authentication_failed".equals(errorCode)) {
                    token = "";
                    main.post(listener::onPairingRequired);
                } else error(message.getFields().getOrDefault("detail", "The TV reported an error"));
            }
        } catch (Exception exception) { error("Received an invalid TV response"); }
    }

    static PhoneState stateFrom(ProtocolMessage message) {
        String rawLevels = message.requireField("levels_mb");
        String[] levels = rawLevels.isEmpty() ? new String[0] : rawLevels.split(",");
        String[] centers = message.getFields().getOrDefault("centers_hz", "").split(",");
        String[] minimums = message.requireField("min_mb").split(",");
        String[] maximums = message.requireField("max_mb").split(",");
        ArrayList<PhoneState.Band> bands = new ArrayList<>();
        for (int i = 0; i < levels.length; i++) {
            if (centers.length != levels.length || (minimums.length != 1 && minimums.length != levels.length)
                    || (maximums.length != 1 && maximums.length != levels.length)) continue;
            int minimum = Integer.parseInt(minimums[minimums.length == 1 ? 0 : i]);
            int maximum = Integer.parseInt(maximums[maximums.length == 1 ? 0 : i]);
            bands.add(new PhoneState.Band(i, Integer.parseInt(centers[i]), minimum, maximum,
                    Integer.parseInt(levels[i])));
        }
        return new PhoneState(PhoneState.Connection.CONNECTED, engineStatus(message.getFields().getOrDefault("engine", "ok")), message.getFields().getOrDefault("tv_name", "TV"),
                message.getFields().getOrDefault("route_name", message.requireField("route_id")), message.getFields().getOrDefault("preset_name", "Custom"),
                Boolean.parseBoolean(message.requireField("enabled")), message.getRevision(), bands,
                java.util.Arrays.asList(message.getFields().getOrDefault("features", "").split(",")).contains("presets"),
                com.tonewire.protocol.PresetCatalog.decode(message.getFields().getOrDefault("presets", "")));
    }
    static boolean engineReady(PhoneState state) {
        return state.connection == PhoneState.Connection.CONNECTED && "Live local control".equals(state.status);
    }

    private static String engineStatus(String engine) {
        switch (engine) {
            case "ok": return "Live local control";
            case "no_control": return "Another app controls the equalizer. Changes cannot be applied.";
            case "unavailable": return "The TV equalizer is unavailable. Changes cannot be applied.";
            case "route_ambiguous": return "The active output is unclear. Check the TV's connected outputs.";
            default: return "The TV cannot confirm equalizer status. Changes may not apply.";
        }
    }

    private void send(ProtocolMessage message) {
        long generation = generations.current();
        try {
            writerIo.execute(() -> sendOnWriter(message, generation));
        } catch (RejectedExecutionException ignored) {
            Log.i(TAG, "drop send after shutdown type=" + message.getType());
        }
    }

    /** Runs only on writerIo; never performs network I/O on the UI or reader thread. */
    private void sendOnWriter(ProtocolMessage message, long generation) {
        try {
            if (stopped || !generations.isCurrent(generation)) {
                Log.i(TAG, "drop stale send gen=" + generation + " type=" + message.getType());
                return;
            }
            BufferedWriter current = writer;
            if (current != null && socket != null && !socket.isClosed()) {
                synchronized (current) {
                    if (stopped || !generations.isCurrent(generation) || current != writer
                            || socket == null || socket.isClosed()) {
                        Log.i(TAG, "drop replaced send gen=" + generation + " type=" + message.getType());
                        return;
                    }
                    current.write(ProtocolCodec.encode(message));
                    current.flush();
                    if (ProtocolTypes.EQ_BAND_SET.equals(message.getType()) || ProtocolTypes.EQ_ENABLE.equals(message.getType())) {
                        long acknowledgedRevision = message.getRevision() + 1L;
                        mutationSentAt.put(acknowledgedRevision, SystemClock.elapsedRealtime());
                        mutationTypes.put(acknowledgedRevision, message.getType());
                        Log.i(TAG, "TW_SENT revision=" + message.getRevision() + " type=" + message.getType()
                                + " elapsed_ms=" + SystemClock.elapsedRealtime());
                    } else Log.i(TAG, "send type=" + message.getType() + " revision=" + message.getRevision());
                }
            } else {
                Log.i(TAG, "send unavailable gen=" + generation + " socket=" + socketId(socket));
                error("Connection interrupted. Reconnecting to TV.");
                scheduleReconnectCurrent(generation);
            }
        } catch (Exception exception) {
            Log.i(TAG, "send failed gen=" + generation + " socket=" + socketId(socket)
                    + " reason=" + exception.getClass().getSimpleName());
            if (generations.isCurrent(generation) && !stopped) {
                error("Connection interrupted. Reconnecting to TV.");
                scheduleReconnectCurrent(generation);
            }
        }
    }
    private String nonce() { byte[] bytes = new byte[18]; random.nextBytes(bytes); return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private String safeId(String value) { return value.toLowerCase().replace(' ', '_').replaceAll("[^a-z0-9_-]", ""); }
    private void error(String message) { main.post(() -> listener.onError(message)); }
    private void logAcknowledgement(long acknowledgedRevision) {
        Long sentAt = mutationSentAt.remove(acknowledgedRevision);
        String type = mutationTypes.remove(acknowledgedRevision);
        if (sentAt != null) {
            Log.i(TAG, "TW_ACK revision=" + acknowledgedRevision + " type=" + type
                    + " elapsed_ms=" + (SystemClock.elapsedRealtime() - sentAt));
        }
    }
    private void scheduleReconnectCurrent(long generation) {
        if (!generations.isCurrent(generation)) return;
        closeCurrentSocket("send_failed");
        if (!stopped) scheduleReconnect(generation, 0);
    }

    private void scheduleReconnect(long generation, int attempt) {
        if (!generations.scheduleReconnect(generation)) return;
        int delay = 500 << Math.min(attempt, 4);
        main.postDelayed(() -> {
            if (generations.beginScheduledReconnect(generation) && !stopped) {
                io.execute(() -> connectLoop(generation, Math.min(attempt + 1, 30)));
            }
        }, delay);
    }

    private void closeCurrentSocket(String reason) {
        Socket toClose;
        synchronized (connectionLock) {
            toClose = socket;
            socket = null;
            writer = null;
        }
        Log.i(TAG, "close current reason=" + reason + " socket=" + socketId(toClose));
        try { if (toClose != null) toClose.close(); } catch (Exception ignored) { }
    }

    /** An obsolete attempt can close only its own socket, never a newer session's socket. */
    private void closeOwnedSocket(Socket owner, long generation) {
        if (owner == null) {
            Log.i(TAG, "close owner skipped gen=" + generation + " socket=none");
            return;
        }
        boolean wasCurrent;
        synchronized (connectionLock) {
            wasCurrent = socket == owner;
            if (wasCurrent) {
                socket = null;
                writer = null;
            }
        }
        Log.i(TAG, "close owner gen=" + generation + " socket=" + socketId(owner) + " current=" + wasCurrent);
        try { owner.close(); } catch (Exception ignored) { }
    }

    private static String socketId(Socket value) {
        return value == null ? "none" : Integer.toHexString(System.identityHashCode(value));
    }
}
