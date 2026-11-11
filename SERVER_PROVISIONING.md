# Server and protocol provisioning

The server owns the custom-panel login, password, subdomain/management port and
public server IP. SSH retains its separate credentials and SSH port.

The New Protocol dialog only asks for server and protocol (except SSH). The admin
calls POST /panel/api/inbounds/provision, then stores the returned inbound ID and
protocol port. Ports are chosen on the hosting panel; management ports and protocol
ports are separate. The panel creates default transport settings, keys and secrets.
For TLS protocols, set Certificate path and Private key path in New Server or the
server manager, for example /etc/letsencrypt/live/example.com/fullchain.pem and
/etc/letsencrypt/live/example.com/privkey.pem. Both paths refer to files on the
hosting server; no private key is uploaded or read by the admin backend. The panel
checks that the files are readable and form a valid certificate/key pair before
creating a TLS inbound. If both fields are empty, its web certificate/key are used.
The panel hostname remains the TLS SNI, so the certificate must cover that hostname.
Changing these server defaults affects newly provisioned inbounds; existing inbounds
keep their configured paths. ShadowTLS uses
www.cloudflare.com:443 as its default handshake cover; it remains editable in the panel.

Retries return the same managed inbound by a stable tag. An admin database row lock
serializes additions on each server and returns HTTP 409 for duplicates, including
concurrent requests. Existing protocols are excluded from the frontend selector.
Only a successful panel response saves a new local protocol. If the remote creation
succeeds but the local transaction fails, retrying recovers the same inbound.

Hibernate adds server connection fields, inboundId and the native endpoint marker.
At startup ServerConnectionMigration copies a legacy connection into each server
that lacks one, preferring protocols previously managed by 3x-ui. Old protocol columns
remain for migration and SSH. If legacy protocols used different panels, review the
chosen server connection; one server now has exactly one custom-panel connection.
Existing client configurations are retained. New clients for every non-SSH protocol
use the custom-panel client API, including Naive and WireGuard. Managed protocols use
the stored inbound ID; legacy protocol records fall back to their configured port.

Update the custom server panel, the admin backend and its frontend together before
using this workflow. No tests or application builds were run during this change.

## Bulk client creation

Mass Actions uses one authenticated panel session and JSON batches of up to 200
clients. New identities are sent to /panel/api/clients/bulkCreate with the target
inbound ID. Existing identities that still need attachment are sent to bulkAttach,
also in batches of 200. There are no per-client create/attach or credential-fetch
HTTP calls. Final inbound settings and canonical identities are read in bulk and
used by the same config renderers as individual additions, including AmneziaWG.

Users already configured locally are skipped. Each local result is saved in its
own transaction under a user-row lock; failures retain per-user reporting. Retrying
a partially completed operation reuses existing remote credentials and attachments.
SSH retains its independent per-user provisioning path. No API changes or database
schema migration are needed on the server panel for this optimization.

## Individual and bulk removal

The delete button on a user's protocol card and Mass Actions both completely remove
that client's panel identity through bulkDel with keepTraffic=false. The panel
removes the client's traffic and inbound memberships; this is not a detach-only
operation, including when that identity previously had multiple memberships.
Other, separately identified clients of the admin user are not selected by this
operation. Confirmation dialogs explicitly describe the complete panel cleanup.

Mass removal sends batches of 200 using one authenticated session. Individual
removal uses the same path with one user. Current names and stored credentials
resolve legacy panel accounts after username changes. Missing remote identities
are successful no-ops for retry purposes. The final panel list must confirm the
client is absent before local configs are removed. Errors retain local data and
are displayed in the UI. The protocol-card list refreshes from the backend after
individual removal. SSH keeps its independent behavior.

Locally orphaned configs without an owning user remain untouched because their
remote identity cannot be confirmed by this action. No tests or builds were run.
