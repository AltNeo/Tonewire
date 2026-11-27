package com.tonewire.tv.network;

import com.tonewire.protocol.ProtocolCodec;
import com.tonewire.protocol.ProtocolMessage;
import com.tonewire.protocol.ProtocolMessages;
import com.tonewire.protocol.ProtocolTypes;
import com.tonewire.protocol.RevisionTracker;
import java.io.IOException;
import java.io.Writer;

/** One connection owns its revision and serializes replies and unsolicited snapshots. */
final class AuthenticatedConnection {
    private final Writer output;
    private final RevisionTracker revisions = new RevisionTracker(0);

    AuthenticatedConnection(Writer output) { this.output = output; }

    synchronized void send(ProtocolMessage message) throws IOException {
        output.write(ProtocolCodec.encode(message));
        output.flush();
    }

    synchronized boolean dispatch(ProtocolMessage command, CommandDispatcher dispatcher) throws IOException {
        boolean mutation = isStateChanging(command);
        if (mutation && !revisions.accepts(command.getRevision())) {
            send(ProtocolMessages.error(revisions.getCurrentRevision(), "stale_revision", "Revision was already applied"));
            return false;
        }
        ProtocolMessage response = dispatcher.dispatch(command);
        if (mutation && !ProtocolTypes.ERROR.equals(response.getType())) revisions.advance();
        send(response);
        return !ProtocolTypes.ERROR.equals(response.getType())
                && (mutation || ProtocolTypes.STATE_GET.equals(command.getType()));
    }

    synchronized void push(CommandDispatcher dispatcher) throws IOException {
        // Never use a global revision or a sender's revision here.
        ProtocolMessage snapshot = dispatcher.dispatch(ProtocolMessages.stateGet(revisions.getCurrentRevision()));
        if (ProtocolTypes.STATE_SNAPSHOT.equals(snapshot.getType())) send(snapshot);
    }

    private static boolean isStateChanging(ProtocolMessage message) {
        return ProtocolTypes.EQ_ENABLE.equals(message.getType())
                || ProtocolTypes.EQ_BAND_SET.equals(message.getType())
                || ProtocolTypes.EQ_PRESET_APPLY.equals(message.getType())
                || ProtocolTypes.EQ_PRESET_SAVE.equals(message.getType())
                || ProtocolTypes.EQ_PRESET_DELETE.equals(message.getType());
    }
}
