package com.tonewire.tv.network;

import com.tonewire.protocol.ProtocolMessage;

/** Bridge implemented by the audio-service owner for authenticated protocol commands. */
public interface CommandDispatcher {
    ProtocolMessage dispatch(ProtocolMessage command);
}
