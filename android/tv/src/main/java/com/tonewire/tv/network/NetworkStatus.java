package com.tonewire.tv.network;

/** Immutable snapshot suitable for the TV status screen. */
public final class NetworkStatus {
    private final boolean running;
    private final int port;
    private final int connectedClients;
    private final String pairingCode;
    private final long pairingRemainingMillis;
    private final String detail;

    public NetworkStatus(boolean running, int port, int connectedClients, String pairingCode,
            long pairingRemainingMillis, String detail) {
        this.running = running;
        this.port = port;
        this.connectedClients = connectedClients;
        this.pairingCode = pairingCode;
        this.pairingRemainingMillis = pairingRemainingMillis;
        this.detail = detail;
    }

    public boolean isRunning() { return running; }
    public int getPort() { return port; }
    public int getConnectedClients() { return connectedClients; }
    public String getPairingCode() { return pairingCode; }
    public long getPairingRemainingMillis() { return pairingRemainingMillis; }
    public String getDetail() { return detail; }
}
