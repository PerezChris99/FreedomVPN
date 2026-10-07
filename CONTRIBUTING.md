# Contributing to FreedomVPN

Thank you for contributing to FreedomVPN.

## Development workflow

The development branch is `perez`. Changes should be developed there and reviewed before they are merged into `main`.

1. Create or use an authorized feature branch from `perez`.
2. Keep commits small and descriptive.
3. Add regression tests for behavior changes.
4. Do not commit credentials, private keys, production secrets, or generated build artifacts.
5. Push the branch and open a pull request targeting `perez`.
6. Promote changes to `main` only after review and required checks pass.

## Security-sensitive changes

Changes involving VPN routing, tunnel state, cryptography, authentication, certificate pinning, firewall rules, peer management, or privacy logging require extra review.

Never represent simulated state as real network protection.

## Testing

Run the applicable suites before opening a pull request:

- `cd server/api && npm test`
- `python tests/run_all_tests.py`
- Android Gradle checks where an Android SDK is available.
- Windows runtime validation on a real Windows host for tunnel-related changes.

Static tests do not prove that a real VPN tunnel works. Live network validation remains separate.

## Pull requests

A good pull request should explain what changed, why it changed, security/privacy implications, tests executed and their results, infrastructure prerequisites, and known limitations.

## Copyright

Contributions are accepted through the project's review process. Copyright and licensing remain subject to the terms established by the copyright holder and any written contribution agreement that may apply.
