"""Production-truth regression tests for FreedomVPN.

These tests deliberately check that UI/application state cannot claim VPN
protection from simulated data or unverified tunnel setup. They are static
regressions, not live network verification.
"""

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path):
    return (ROOT / path).read_text(encoding="utf-8")


def test_windows_has_no_demo_connection():
    text = read("windows/main.js").lower()
    forbidden = ["demo mode: simulating", "simulated vpn ip", "connected (demo mode)"]
    assert not any(item in text for item in forbidden), "demo connection path remains"


def test_windows_connection_requires_verification():
    text = read("windows/main.js")
    assert "result.verified !== true" in text
    assert "verified: true" in text


def test_windows_multihop_does_not_create_fake_connected_hops():
    text = read("windows/main.js").lower()
    assert "connected: true" not in text[text.find("// multi-hop ipc handlers"):]
    assert "simulated" not in text[text.find("// multi-hop ipc handlers"):]


def test_windows_does_not_generate_fake_vpn_ip():
    text = read("windows/main.js")
    assert "Math.random()" not in text
    assert "generateVpnIP(server)" not in text


def test_android_has_no_placeholder_production_endpoint():
    text = read("android/app/src/main/java/com/freedomvpn/vpn/server/ServerApiClient.kt")
    assert "YOUR_SERVER_DOMAIN_HERE" not in text
    gradle = read("android/app/build.gradle.kts")
    assert "serverBaseUrl" in gradle
    assert "serverCertPins" in gradle


def test_android_release_requires_deployment_config():
    text = read("android/app/build.gradle.kts")
    assert "Release builds require" in text
    assert "serverCertPins" in text


def test_readme_does_not_make_absolute_anonymity_claims():
    text = read("README.md").lower()
    assert "untraceable" not in text
    assert "unblockable" not in text
    assert "100% anonymous" not in text
    assert "all rights reserved" in text


def main():
    tests = [
        test_windows_has_no_demo_connection,
        test_windows_connection_requires_verification,
        test_windows_multihop_does_not_create_fake_connected_hops,
        test_windows_does_not_generate_fake_vpn_ip,
        test_android_has_no_placeholder_production_endpoint,
        test_android_release_requires_deployment_config,
        test_readme_does_not_make_absolute_anonymity_claims,
    ]
    failures = 0
    for test in tests:
        try:
            test()
            print(f"[PASS] {test.__name__}")
        except Exception as exc:
            failures += 1
            print(f"[FAIL] {test.__name__}: {exc}")
    print(f"Production-truth tests: {len(tests)-failures}/{len(tests)} passed")
    return failures == 0


if __name__ == "__main__":
    raise SystemExit(0 if main() else 1)
