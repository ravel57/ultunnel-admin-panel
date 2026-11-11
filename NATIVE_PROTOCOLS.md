# Native protocols in the admin panel

The protocol selector supports TUIC v5, MASQUE, AnyTLS, ShadowTLS v3 with Shadowsocks,
and TrustTunnel, alongside the existing AmneziaWG and Hysteria 2 protocols.
Hysteria 2 preserves Gecko packet-size ranges from the inbound's Salamander mask.

Set the custom panel login, password, management subdomain/port and public IP
on the server. New Protocol now provisions the selected inbound automatically and
stores its returned ID and port. See SERVER_PROVISIONING.md for setup and migration.

Full native outbound settings are stored in the database and exported through the
existing sing-box configuration endpoint. ShadowTLS exports two connected outbounds:
Shadowsocks with UDP-over-TCP v2, and a ShadowTLS v3 transport. Source proxy IDs are
included only in admin responses, to retain the added state after refreshing.

MASQUE is exported as sing-box JSON; it has no standard share URI. Other new protocols
also expose share links (TrustTunnel uses the v2 `tt://` format). TLS verification
stays enabled. Use a certificate whose name matches the inbound SNI. Clients must
include the respective protocol implementation; MASQUE, TrustTunnel and Gecko require
the ultunnel sing-box fork. TrustTunnel ICMP is not implemented by the 3x-ui listener.

The application uses Hibernate `ddl-auto: update` to create the native-config table
and persisted Gecko fields on startup. No build or tests were run for these changes.
