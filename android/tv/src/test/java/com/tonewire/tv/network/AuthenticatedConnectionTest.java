package com.tonewire.tv.network;

import com.tonewire.protocol.ProtocolCodec;
import com.tonewire.protocol.ProtocolMessage;
import com.tonewire.protocol.ProtocolMessages;
import com.tonewire.protocol.ProtocolTypes;
import java.io.StringWriter;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;
import static org.junit.Assert.*;

public final class AuthenticatedConnectionTest {
    private final CommandDispatcher snapshots = command -> new ProtocolMessage(
            ProtocolTypes.STATE_SNAPSHOT,
            command.getRevision() + (ProtocolTypes.EQ_ENABLE.equals(command.getType()) ? 1 : 0),
            Collections.singletonMap("enabled", "false"));

    @Test public void pushesUseReceiversOwnRevisionAndDoNotAdvanceIt() throws Exception {
        StringWriter firstOut = new StringWriter(), secondOut = new StringWriter();
        AuthenticatedConnection first = new AuthenticatedConnection(firstOut);
        AuthenticatedConnection second = new AuthenticatedConnection(secondOut);
        for (int revision = 0; revision < 7; revision++) {
            assertTrue(first.dispatch(ProtocolMessages.eqEnable(revision, false), snapshots));
        }
        firstOut.getBuffer().setLength(0);
        first.push(snapshots);
        second.push(snapshots);
        assertEquals(7, ProtocolCodec.decode(firstOut.toString()).getRevision());
        assertEquals(0, ProtocolCodec.decode(secondOut.toString()).getRevision());
        secondOut.getBuffer().setLength(0);
        assertTrue(second.dispatch(ProtocolMessages.eqEnable(0, false), snapshots));
        assertEquals(1, ProtocolCodec.decode(secondOut.toString()).getRevision());
        secondOut.getBuffer().setLength(0);
        second.push(snapshots);
        assertEquals(1, ProtocolCodec.decode(secondOut.toString()).getRevision());
    }

    @Test public void staleCommandIsRejectedWithoutBroadcastTrigger() throws Exception {
        StringWriter output = new StringWriter();
        AuthenticatedConnection connection = new AuthenticatedConnection(output);
        assertTrue(connection.dispatch(ProtocolMessages.eqEnable(0, false), snapshots));
        output.getBuffer().setLength(0);
        assertFalse(connection.dispatch(ProtocolMessages.eqEnable(0, false), snapshots));
        assertEquals("stale_revision", ProtocolCodec.decode(output.toString()).requireField("code"));
    }

    @Test public void rejectedMutationDoesNotConsumeRevisionAndNextValidCommandSucceeds() throws Exception {
        StringWriter output = new StringWriter();
        AuthenticatedConnection connection = new AuthenticatedConnection(output);
        assertFalse(connection.dispatch(ProtocolMessages.eqEnable(0, false),
                command -> ProtocolMessages.error(command.getRevision(), "invalid_command", "unavailable")));
        output.getBuffer().setLength(0);
        assertTrue(connection.dispatch(ProtocolMessages.eqEnable(0, false), snapshots));
        assertEquals(1, ProtocolCodec.decode(output.toString()).getRevision());
        output.getBuffer().setLength(0);
        connection.dispatch(ProtocolMessages.eqEnable(99, false), snapshots);
        assertEquals(1, ProtocolCodec.decode(output.toString()).getRevision());
    }

    @Test public void stateReadTriggersRefreshButPingDoesNot() throws Exception {
        AuthenticatedConnection connection = new AuthenticatedConnection(new StringWriter());
        assertTrue(connection.dispatch(ProtocolMessages.stateGet(0), snapshots));
        assertFalse(connection.dispatch(ProtocolMessages.ping("abcdefghijklmnop"), snapshots));
    }

    @Test public void simultaneousRepliesAndPushesProduceWholeDecodableFrames() throws Exception {
        StringWriter output = new StringWriter();
        AuthenticatedConnection connection = new AuthenticatedConnection(output);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread replies = new Thread(() -> {
            try { start.await(); for (int i = 0; i < 100; i++) connection.dispatch(ProtocolMessages.eqEnable(i, false), snapshots); }
            catch (Throwable error) { failure.set(error); }
        });
        Thread pushes = new Thread(() -> {
            try { start.await(); for (int i = 0; i < 100; i++) connection.push(snapshots); }
            catch (Throwable error) { failure.set(error); }
        });
        replies.start(); pushes.start(); start.countDown(); replies.join(); pushes.join();
        assertNull(failure.get());
        String[] frames = output.toString().split("\n");
        assertEquals(200, frames.length);
        long last = 0;
        for (String frame : frames) {
            ProtocolMessage message = ProtocolCodec.decode(frame + "\n");
            assertEquals(ProtocolTypes.STATE_SNAPSHOT, message.getType());
            assertTrue(message.getRevision() >= last);
            last = message.getRevision();
        }
        assertEquals(100, last);
    }
}
