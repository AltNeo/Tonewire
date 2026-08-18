package com.tonewire.phone;

/** Allocates unique expected revisions before state-changing frames enter the writer queue. */
final class OptimisticRevision {
    private long current;

    synchronized long allocateExpected() { return current++; }
    synchronized long current() { return current; }
    synchronized void observe(long received) { current = Math.max(current, received); }
}
