package com.tonewire.phone;

/** Allows one immediate recovery read; a rejected recovery read must not loop. */
final class RejectionResync {
    private boolean waitingForSnapshot;
    synchronized boolean requestNow(String code) {
        if ("engine_unavailable".equals(code) || waitingForSnapshot) return false;
        waitingForSnapshot = true;
        return true;
    }
    synchronized void receivedSnapshot() { waitingForSnapshot = false; }
}
