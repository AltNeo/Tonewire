package com.tonewire.phone;

/** Serializes connection ownership so delayed work cannot affect a newer endpoint session. */
final class ConnectionGeneration {
    private long current;
    private long reconnectScheduled = -1L;

    synchronized long begin() {
        current++;
        reconnectScheduled = -1L;
        return current;
    }

    synchronized boolean isCurrent(long value) { return current == value; }
    synchronized long current() { return current; }

    synchronized boolean scheduleReconnect(long value) {
        if (current != value || reconnectScheduled == value) return false;
        reconnectScheduled = value;
        return true;
    }

    synchronized boolean beginScheduledReconnect(long value) {
        if (reconnectScheduled != value) return false;
        reconnectScheduled = -1L;
        return current == value;
    }
}
