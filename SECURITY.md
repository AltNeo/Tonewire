# Security

## Threat model

Tonewire v0.1 is designed for a trusted home LAN. Pairing uses a short-lived
code and later connections use a random token with HMAC authentication. Tokens
are stored with Android Keystore-backed encryption.

The local TCP service should not be exposed directly to the public internet.
Use a firewall, keep the TV and phone on a trusted network, and disable network
ADB after development.

## Implemented protections

Pairing locks globally for 30 seconds after five wrong codes and rotates the code.
Refreshing the displayed code does not bypass the lock. The counter is in memory;
a process restart resets it, so this is a home-LAN guard rather than a hostile-LAN
rate-limiting service. Missing or malformed device/peer identifiers are rejected.
The TV's confirmed Forget all phones action durably clears credentials and closes
existing connections. Only successful mutations advance a connection's revision;
rejections report the authoritative revision for resynchronization.

## Known limitations

The following hardening work remains before treating an untrusted or hostile
LAN as in scope:

- The TV server currently binds to all local interfaces rather than selecting a
  single LAN interface.
- Post-authentication command frames do not have a per-message integrity tag.
- Route fingerprints should avoid persisting Bluetooth MAC addresses in
  plaintext preferences.

Please do not report these known v0.1 limitations as new vulnerabilities unless
you can demonstrate impact beyond the trusted-home-LAN threat model. Reports
for authentication bypasses, token disclosure, remote code execution, or
unexpected internet exposure are welcome through a private security report.
