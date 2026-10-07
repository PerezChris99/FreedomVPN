"""FreedomVPN master regression runner.

This runner composes the maintained suite entry points instead of importing
legacy symbols that no longer exist. Structural checks and live/network checks
remain explicitly separate; passing this runner is not proof of production
network readiness.
"""

import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def run(label, command):
    print("\n" + "=" * 70)
    print(label)
    print("=" * 70)
    result = subprocess.run(command, cwd=ROOT)
    print(f"{label}: {'PASS' if result.returncode == 0 else 'FAIL'}")
    return result.returncode == 0


def main():
    suites = [
        ("VPN Gate regression", [sys.executable, "tests/test_vpngate.py"]),
        ("Comprehensive platform regression", [sys.executable, "tests/test_suite_comprehensive.py"]),
        ("Privacy/anonymity mechanism regression", [sys.executable, "tests/test_anonymity.py"]),
        ("Multi-hop regression", [sys.executable, "-m", "unittest", "discover", "-s", "tests", "-p", "test_multihop.py"]),
        ("Production-truth regression", [sys.executable, "tests/test_production_truth.py"]),
    ]

    results = [run(label, command) for label, command in suites]

    print("\n" + "=" * 70)
    print("MASTER TEST SUMMARY")
    print("=" * 70)
    for (label, _), ok in zip(suites, results):
        print(f"  {'PASS' if ok else 'FAIL'}  {label}")

    if all(results):
        print("\nAll automated regression suites passed.")
        print("This does not prove live VPN connectivity, anonymity, censorship bypass,")
        print("national-scale capacity, or production infrastructure readiness.")
        return 0

    print("\nOne or more automated regression suites failed.")
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
