package com.tonewire.tv.network;

import android.content.Context;
import android.net.nsd.NsdManager;
import android.net.nsd.NsdServiceInfo;
import android.util.Log;

import com.tonewire.protocol.AuthToken;
import com.tonewire.protocol.FrameDecoder;
import com.tonewire.protocol.ProtocolAuthenticator;
import com.tonewire.protocol.ProtocolCodec;
import com.tonewire.protocol.ProtocolMessage;
import com.tonewire.protocol.ProtocolMessages;
import com.tonewire.protocol.ProtocolTypes;
import com.tonewire.protocol.RevisionTracker;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Small authenticated TCP listener advertised on the local network through DNS-SD. */
public final class TvNetworkServer implements AutoCloseable {
    private static final String TAG = "TonewireTvNet";
    public static final int DEFAULT_PORT = 49321;
    static final int PAIRING_IDLE_TIMEOUT_MILLIS = 6 * 60 * 1000;
    public interface StatusListener { void onNetworkStatus(NetworkStatus status); }
    private static final String SERVICE_TYPE = "_tonewire._tcp.";
    private final Context context;
    private final PairingCodeManager pairingCodes;
    private final SecureTokenStore tokenStore;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final AtomicInteger clients = new AtomicInteger();
    private final SecureRandom random = new SecureRandom();
    private volatile CommandDispatcher dispatcher = command -> ProtocolMessages.error(
            command.getRevision(), "engine_unavailable", "The audio service is not ready");
    private volatile StatusListener statusListener;
    private volatile ServerSocket serverSocket;
    private volatile NsdManager.RegistrationListener nsdListener;
    private volatile String detail = "Starting";

    public TvNetworkServer(Context context) {
        this.context = context.getApplicationContext();
        pairingCodes = new PairingCodeManager();
        tokenStore = new SecureTokenStore(this.context);
    }

    public synchronized void start() {
        if (serverSocket != null && !serverSocket.isClosed()) return;
        try {
            ServerSocket listener = new ServerSocket();
            listener.setReuseAddress(true);
            listener.bind(new InetSocketAddress(DEFAULT_PORT));
            serverSocket = listener;
            detail = "Advertising on local network";
            registerNsd(serverSocket.getLocalPort());
            executor.execute(this::acceptLoop);
        } catch (IOException exception) {
            detail = "Port " + DEFAULT_PORT + " unavailable; restart Tonewire TV";
            Log.w(TAG, "Unable to bind stable LAN port " + DEFAULT_PORT, exception);
            close();
        }
        notifyStatus();
    }

    public void setCommandDispatcher(CommandDispatcher commandDispatcher) {
        dispatcher = commandDispatcher == null ? dispatcher : commandDispatcher;
    }

    public void setStatusListener(StatusListener listener) {
        statusListener = listener;
        notifyStatus();
    }

    public NetworkStatus getStatus() {
        ServerSocket socket = serverSocket;
        long now = System.currentTimeMillis();
        return new NetworkStatus(socket != null && !socket.isClosed(), socket == null ? 0 : socket.getLocalPort(),
                clients.get(), pairingCodes.currentCode(now), pairingCodes.remainingMillis(now), detail);
    }

    public void refreshPairingCode() {
        pairingCodes.refresh(System.currentTimeMillis());
        notifyStatus();
    }

    private void acceptLoop() {
        while (serverSocket != null && !serverSocket.isClosed()) {
            try {
                Socket client = serverSocket.accept();
                Log.i(TAG, "accepted LAN client");
                clients.incrementAndGet();
                notifyStatus();
                executor.execute(() -> handleClient(client));
            } catch (IOException exception) {
                if (serverSocket != null && !serverSocket.isClosed()) detail = "Connection listener stopped";
                break;
            }
        }
        notifyStatus();
    }

    private void handleClient(Socket socket) {
        try (Socket ignored = socket;
             BufferedWriter output = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8))) {
            // A user needs time to read the TV code and enter it on the phone. Keep this bounded.
            socket.setSoTimeout(PAIRING_IDLE_TIMEOUT_MILLIS);
            boolean authenticated = false;
            String serverNonce = nonce();
            RevisionTracker revisions = new RevisionTracker(0);
            write(output, ProtocolMessages.hello("tonewire_tv", "tv", serverNonce));
            FrameDecoder decoder = new FrameDecoder();
            byte[] buffer = new byte[2048];
            int count;
            while ((count = socket.getInputStream().read(buffer)) != -1) {
                java.util.List<ProtocolMessage> messages;
                try { messages = decoder.accept(buffer, 0, count); }
                catch (IllegalArgumentException exception) { write(output, ProtocolMessages.error(0, "invalid_frame", "Malformed protocol frame")); break; }
                for (ProtocolMessage message : messages) {
                Map<String, String> fields = message.getFields();
                if (ProtocolTypes.PAIR_REQUEST.equals(message.getType())) {
                    String code = fields.get("code");
                    String deviceId = fields.getOrDefault("device_id", "default");
                    if (!pairingCodes.matches(code, System.currentTimeMillis())) {
                        Log.i(TAG, "pair rejected: invalid code");
                        write(output, ProtocolMessages.pairReject("invalid_or_expired_code"));
                        continue;
                    }
                    String token = newToken();
                    try {
                        tokenStore.save(deviceId, token);
                    } catch (RuntimeException exception) {
                        Log.w(TAG, "pairing storage unavailable", exception);
                        write(output, ProtocolMessages.pairReject("storage_unavailable"));
                        continue;
                    }
                    Log.i(TAG, "pair accepted for peer");
                    Map<String, String> accept = new java.util.LinkedHashMap<>();
                    accept.put("peer_id", "tonewire_tv"); accept.put("token_id", tokenId(token)); accept.put("token", token);
                    write(output, new ProtocolMessage(ProtocolTypes.PAIR_ACCEPT, 0, accept));
                    continue;
                }
                    if (!authenticated) {
                    if (!ProtocolTypes.AUTH_TOKEN.equals(message.getType())) { write(output, ProtocolMessages.error(0, "authentication_required", "Send auth.token first")); break; }
                    String deviceId = fields.getOrDefault("peer_id", "default");
                    String storedToken;
                    try {
                        storedToken = tokenStore.load(deviceId);
                    } catch (RuntimeException exception) {
                        Log.w(TAG, "pairing storage unavailable", exception);
                        write(output, ProtocolMessages.error(0, "storage_unavailable", "Pairing storage unavailable"));
                        break;
                    }
                    authenticated = storedToken != null && ProtocolAuthenticator.verify(AuthToken.fromBase64(storedToken), message, serverNonce);
                    if (!authenticated) { Log.i(TAG, "authentication rejected"); write(output, ProtocolMessages.error(0, "authentication_failed", "Unknown pairing token")); break; }
                    Log.i(TAG, "authenticated LAN client");
                    write(output, new ProtocolMessage(ProtocolTypes.AUTH_TOKEN, 0,
                            java.util.Collections.singletonMap("status", "ok")));
                    continue;
                }
                long revision = message.getRevision();
                if (isStateChanging(message) && !revisions.accepts(revision)) {
                    write(output, ProtocolMessages.error(revision, "stale_revision", "Revision was already applied"));
                    continue;
                }
                if (isStateChanging(message)) revisions.advance();
                write(output, dispatcher.dispatch(message));
                }
            }
        } catch (IOException ignored) {
            // A disconnect is expected when the phone sleeps or changes Wi-Fi networks.
            Log.i(TAG, "LAN client disconnected");
        } finally {
            clients.decrementAndGet();
            notifyStatus();
        }
    }

    private void registerNsd(int port) {
        NsdServiceInfo info = new NsdServiceInfo();
        info.setServiceName("Tonewire TV"); info.setServiceType(SERVICE_TYPE); info.setPort(port);
        info.setAttribute("protocol", "1");
        NsdManager manager = context.getSystemService(NsdManager.class);
        nsdListener = new NsdManager.RegistrationListener() {
            @Override public void onServiceRegistered(NsdServiceInfo serviceInfo) { detail = "Ready for pairing"; notifyStatus(); }
            @Override public void onRegistrationFailed(NsdServiceInfo serviceInfo, int errorCode) { detail = "Manual IP available"; notifyStatus(); }
            @Override public void onServiceUnregistered(NsdServiceInfo serviceInfo) { }
            @Override public void onUnregistrationFailed(NsdServiceInfo serviceInfo, int errorCode) { }
        };
        manager.registerService(info, NsdManager.PROTOCOL_DNS_SD, nsdListener);
    }

    private void write(BufferedWriter output, ProtocolMessage message) throws IOException {
        output.write(ProtocolCodec.encode(message)); output.flush();
    }
    private String newToken() { byte[] bytes = new byte[32]; random.nextBytes(bytes); return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP | android.util.Base64.URL_SAFE); }
    private String nonce() { byte[] bytes = new byte[24]; random.nextBytes(bytes); return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP | android.util.Base64.URL_SAFE); }
    private static boolean isStateChanging(ProtocolMessage message) {
        return ProtocolTypes.EQ_ENABLE.equals(message.getType()) || ProtocolTypes.EQ_BAND_SET.equals(message.getType()) || ProtocolTypes.EQ_PRESET_APPLY.equals(message.getType());
    }
    private static String tokenId(String token) { return Integer.toHexString(token.hashCode()); }
    private void notifyStatus() { StatusListener listener = statusListener; if (listener != null) listener.onNetworkStatus(getStatus()); }

    @Override public synchronized void close() {
        if (nsdListener != null) { try { context.getSystemService(NsdManager.class).unregisterService(nsdListener); } catch (IllegalArgumentException ignored) { } nsdListener = null; }
        if (serverSocket != null) { try { serverSocket.close(); } catch (IOException ignored) { } serverSocket = null; }
    }
}
