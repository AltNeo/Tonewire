package com.tonewire.phone;

/** Only a matching stable identity may move an already-selected TV endpoint. */
final class EndpointRediscovery {
    private EndpointRediscovery() { }

    static boolean shouldAdopt(String savedId, String savedHost, int savedPort,
            String foundId, String foundHost, int foundPort) {
        return savedId != null && !savedId.isEmpty() && savedId.equals(foundId)
                && foundHost != null && !foundHost.isEmpty() && foundPort > 0 && foundPort <= 65535
                && (!foundHost.equals(savedHost) || foundPort != savedPort);
    }
}
