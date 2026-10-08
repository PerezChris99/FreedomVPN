# FreedomVPN production deployment baseline

These artifacts are provider-neutral. They do not claim that hosting, DNS, certificates,
secrets, signing credentials, or national capacity already exist.

## Gateway requirements
- Independent Linux WireGuard gateways with unique server keys/endpoints.
- Dedicated peer pools and fail-closed firewall policy.
- Private management network for metrics and administration.
- Health checks for API, WireGuard handshake, DNS, IPv6 and upstream reachability.
- Independent failure domains/providers for national-scale resilience.

## Promotion gate
Provision -> configure -> verify handshake -> verify bidirectional traffic -> verify DNS/IPv6
and kill switch -> API registration test -> health/load probe -> security checks -> promote.

## Recovery
Back up control-plane state to an encrypted independent location and test restores regularly.
Never back up client private WireGuard keys.

## External prerequisites
Hosting/capacity, DNS/certificates, secret management, release signing, target-network testing,
incident response and privacy/legal operations remain deployment-owned concerns.
