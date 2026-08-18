package com.tonewire.protocol;

/** Small helper for rejecting replayed or stale state-changing commands. */
public final class RevisionTracker {
    private long currentRevision;
    public RevisionTracker(long initialRevision) { if (initialRevision < 0) throw new IllegalArgumentException("negative revision"); currentRevision = initialRevision; }
    public long getCurrentRevision() { return currentRevision; }
    public boolean accepts(long expectedRevision) { return expectedRevision == currentRevision; }
    public long advance() { if (currentRevision == Long.MAX_VALUE) throw new IllegalStateException("revision exhausted"); return ++currentRevision; }
    public void updateFromSnapshot(long revision) { if (revision < currentRevision) throw new IllegalArgumentException("stale snapshot"); currentRevision = revision; }
}
