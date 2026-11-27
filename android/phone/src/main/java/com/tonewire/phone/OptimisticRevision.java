package com.tonewire.phone;

/** Allocates unique expected revisions before state-changing frames enter the writer queue. */
final class OptimisticRevision {
    private long current;

    synchronized void resetForConnection() { current = 0; }
    synchronized long allocateExpected() { return current++; }
    synchronized long current() { return current; }
    synchronized void rejected(long authoritative) { current = authoritative; }
    synchronized void observe(long received) { current = Math.max(current, received); }
}
