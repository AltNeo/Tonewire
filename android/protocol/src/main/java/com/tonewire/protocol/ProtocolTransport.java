package com.tonewire.protocol;

import java.io.IOException;

/** Adapter boundary for TCP, test fakes, or a future encrypted transport. */
public interface ProtocolTransport extends AutoCloseable {
    void send(ProtocolMessage message) throws IOException;
    void setListener(Listener listener);
    @Override void close() throws IOException;

    interface Listener {
        void onMessage(ProtocolMessage message);
        void onClosed(IOException cause);
    }
}
