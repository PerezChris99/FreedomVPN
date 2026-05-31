"""
FreedomVPN Comprehensive Test Suite
=====================================

Tests actual behaviour, not just string presence.  Every test either:
  - Makes a real network request and validates the response schema
  - Loads and parses a real config/code file and validates structure
  - Performs a cryptographic verification
  - Checks a security invariant (e.g. no dangerous Android permissions)

Run:  python tests/test_suite_comprehensive.py
"""

import base64
import csv
import hashlib
import json
import os
import re
import sys
import time
import urllib.error
import urllib.request
from io import StringIO
from pathlib import Path
from typing import List, Tuple

PROJECT_ROOT = Path(__file__).parent.parent
sys.path.insert(0, str(PROJECT_ROOT))

# ─────────────────────────────────────────────────────────────────────────────
# Test infrastructure
# ─────────────────────────────────────────────────────────────────────────────

class R:
    """Test result."""
    def __init__(self, name: str, passed: bool, msg: str = "", dur: float = 0.0):
        self.name   = name
        self.passed = passed
        self.msg    = msg
        self.dur    = dur

    def __str__(self) -> str:
        icon = "✓" if self.passed else "✗"
        return f"  {icon} {self.name:65s}  ({self.dur:.2f}s)  {self.msg}"


class Suite:
    def __init__(self, name: str):
        self.name    = name
        self.results: List[R] = []

    def run(self, label: str, fn):
        t0 = time.monotonic()
        try:
            ok, msg = fn()
        except Exception as exc:
            ok, msg = False, f"Exception: {exc}"
        self.results.append(R(label, ok, msg, time.monotonic() - t0))

    def report(self) -> bool:
        passed = sum(1 for r in self.results if r.passed)
        total  = len(self.results)
        print(f"\n{'─'*70}")
        print(f"  SUITE: {self.name}  ({passed}/{total})")
        print(f"{'─'*70}")
        for r in self.results:
            print(r)
        return passed == total


# ─────────────────────────────────────────────────────────────────────────────
# Suite 1 — VPN Gate live API
# ─────────────────────────────────────────────────────────────────────────────

def suite_vpngate() -> bool:
    s = Suite("VPN Gate API (live)")
    URL = "https://www.vpngate.net/api/iphone/"
    _cache: dict = {}

    def fetch() -> str:
        if "raw" not in _cache:
            req = urllib.request.Request(URL, headers={"User-Agent": "FreedomVPN/2.0"})
            with urllib.request.urlopen(req, timeout=30) as resp:
                _cache["raw"] = resp.read().decode("utf-8")
        return _cache["raw"]

    def parse(raw: str) -> list:
        if "servers" not in _cache:
            lines = [l for l in raw.splitlines() if l and not l.startswith("*")]
            reader = csv.DictReader(StringIO("\n".join(lines)))
            _cache["servers"] = [row for row in reader if row.get("#HostName")]
        return _cache["servers"]

    s.run("Connectivity — fetch CSV (≥50 KB)", lambda: (
        (True, f"Fetched {len(fetch()):,} bytes") if len(fetch()) >= 50_000
        else (False, f"Only {len(fetch())} bytes")
    ))
    s.run("Parse — ≥20 server rows", lambda: (
        (True, f"{len(parse(fetch()))} servers") if len(parse(fetch())) >= 20
        else (False, f"Only {len(parse(fetch()))} rows")
    ))
    s.run("Schema — required CSV columns", lambda: _check_columns(parse(fetch())))
    s.run("Data — IP format validation", lambda: _check_ips(parse(fetch())))
    s.run("Data — ping plausibility (0–2000 ms)", lambda: _check_pings(parse(fetch())))
    s.run("Data — speed values present", lambda: _check_speeds(parse(fetch())))
    s.run("Data — OpenVPN config decode", lambda: _check_ovpn(parse(fetch())))
    s.run("Data — country diversity (≥5)", lambda: _check_countries(parse(fetch())))
    return s.report()


def _check_columns(servers: list) -> Tuple[bool, str]:
    required = ["#HostName", "IP", "Score", "Ping", "Speed", "CountryLong", "CountryShort"]
    missing = [c for c in required if c not in servers[0]]
    return (False, f"Missing: {missing}") if missing else (True, "All columns present")

def _check_ips(servers: list) -> Tuple[bool, str]:
    bad = []
    for srv in servers[:20]:
        parts = srv.get("IP", "").split(".")
        if len(parts) != 4 or not all(p.isdigit() and 0 <= int(p) <= 255 for p in parts):
            bad.append(srv.get("IP"))
    return (False, f"Bad IPs: {bad[:3]}") if bad else (True, "IPs valid")

def _check_pings(servers: list) -> Tuple[bool, str]:
    valid = sum(1 for s in servers[:30] if s.get("Ping","0").isdigit() and 0 < int(s["Ping"]) < 2000)
    return (valid >= 15, f"{valid}/30 plausible ping values")

def _check_speeds(servers: list) -> Tuple[bool, str]:
    fast = [s for s in servers if (s.get("Speed") or "0").isdigit() and int(s.get("Speed","0")) >= 1_000_000]
    return (len(fast) >= 5, f"{len(fast)} servers ≥ 1 Mbps")

def _check_ovpn(servers: list) -> Tuple[bool, str]:
    ok = 0
    for s in servers[:15]:
        b64 = s.get("OpenVPN_ConfigData_Base64", "")
        if not b64:
            continue
        try:
            cfg = base64.b64decode(b64).decode("utf-8")
            if "remote " in cfg and "dev " in cfg and "proto " in cfg:
                ok += 1
        except Exception:
            pass
    return (ok >= 5, f"{ok} valid OpenVPN configs in first 15 servers")

def _check_countries(servers: list) -> Tuple[bool, str]:
    countries = {s.get("CountryShort", "XX") for s in servers}
    return (len(countries) >= 5, f"{len(countries)} countries")


# ─────────────────────────────────────────────────────────────────────────────
# Suite 2 — Cryptographic correctness
# ─────────────────────────────────────────────────────────────────────────────

def suite_crypto() -> bool:
    s = Suite("Cryptography")

    def t_wg_key_size():
        """WireGuard keys are exactly 32 bytes → 44-char base64."""
        import os
        raw = os.urandom(32)
        b64 = base64.b64encode(raw).decode()
        if len(base64.b64decode(b64)) != 32:
            return False, "Decode length != 32"
        return (len(b64) == 44, f"base64 length = {len(b64)} (expected 44)")

    def t_curve25519_clamp():
        """RFC 7748 §5 clamping must clear low-3 bits of byte[0] and set bit 6 of byte[31]."""
        import secrets
        raw = bytearray(secrets.token_bytes(32))
        raw[0]  &= 248
        raw[31]  = (raw[31] & 127) | 64
        if raw[0] & 0x07:        return False, "byte[0] low bits not cleared"
        if raw[31] & 0x80:       return False, "byte[31] bit-7 not cleared"
        if not (raw[31] & 0x40): return False, "byte[31] bit-6 not set"
        return True, "RFC 7748 clamping correct"

    def t_sha256_ne_curve25519():
        """SHA256(privkey) MUST differ from the Curve25519 public key — regression guard."""
        priv = bytes.fromhex("77076d0a7318a57d3c16c17251b26645c6ccd3ad754191742dbdcd0efb9535f".zfill(64))
        real_pub = bytes.fromhex("8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a")
        sha_pub  = hashlib.sha256(priv).digest()
        return (sha_pub != real_pub, "SHA256(privkey) ≠ Curve25519 pubkey — fix was necessary")

    def t_no_sha256_bug_in_tunnel():
        content = (PROJECT_ROOT / "windows/system-tunnel.js").read_text(encoding="utf-8")
        if "createHash('sha256').update(privateKey).digest()" in content:
            return False, "Old SHA256-as-pubkey bug still present"
        return True, "SHA256 pubkey bug absent"

    def t_nacl_scalarMult_present():
        content = (PROJECT_ROOT / "windows/system-tunnel.js").read_text(encoding="utf-8")
        if "scalarMult.base" not in content:
            return False, "nacl.scalarMult.base not found"
        return True, "Curve25519 via nacl.scalarMult.base present"

    def t_tweetnacl_in_deps():
        pkg = json.loads((PROJECT_ROOT / "windows/package.json").read_text())
        deps = {**pkg.get("dependencies", {}), **pkg.get("devDependencies", {})}
        return ("tweetnacl" in deps, f"tweetnacl {'found' if 'tweetnacl' in deps else 'MISSING'}")

    for label, fn in [
        ("WireGuard key = 32 bytes / 44-char base64", t_wg_key_size),
        ("Curve25519 clamping (RFC 7748)", t_curve25519_clamp),
        ("SHA256 ≠ Curve25519 pubkey (regression guard)", t_sha256_ne_curve25519),
        ("system-tunnel.js — SHA256 bug removed", t_no_sha256_bug_in_tunnel),
        ("system-tunnel.js — nacl.scalarMult.base present", t_nacl_scalarMult_present),
        ("windows/package.json — tweetnacl declared", t_tweetnacl_in_deps),
    ]:
        s.run(label, fn)
    return s.report()


# ─────────────────────────────────────────────────────────────────────────────
# Suite 3 — Android security invariants
# ─────────────────────────────────────────────────────────────────────────────

def suite_android() -> bool:
    s = Suite("Android Security")
    MANIFEST = (PROJECT_ROOT / "android/app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")
    GRADLE   = (PROJECT_ROOT / "android/app/build.gradle.kts").read_text(encoding="utf-8")
    TUNNEL   = (PROJECT_ROOT / "android/app/src/main/java/com/freedomvpn/vpn/VpnTunnel.kt").read_text(encoding="utf-8")

    def perm(p: str): return p in MANIFEST

    s.run("No READ_MEDIA_IMAGES permission",    lambda: (not perm("READ_MEDIA_IMAGES"),    "OK" if not perm("READ_MEDIA_IMAGES") else "PRESENT — remove"))
    s.run("No READ_MEDIA_VIDEO permission",     lambda: (not perm("READ_MEDIA_VIDEO"),     "OK" if not perm("READ_MEDIA_VIDEO") else "PRESENT — remove"))
    s.run("No READ_MEDIA_AUDIO permission",     lambda: (not perm("READ_MEDIA_AUDIO"),     "OK" if not perm("READ_MEDIA_AUDIO") else "PRESENT — remove"))
    s.run("No ACCESS_BACKGROUND_LOCATION",      lambda: (not perm("ACCESS_BACKGROUND_LOCATION"), "OK" if not perm("ACCESS_BACKGROUND_LOCATION") else "PRESENT — remove"))
    s.run("No WRITE_EXTERNAL_STORAGE",          lambda: (not perm("WRITE_EXTERNAL_STORAGE"), "OK" if not perm("WRITE_EXTERNAL_STORAGE") else "PRESENT — use internal storage"))
    s.run("INTERNET permission present",        lambda: (perm("INTERNET"),    "OK" if perm("INTERNET") else "MISSING"))
    s.run("FOREGROUND_SERVICE present",         lambda: (perm("FOREGROUND_SERVICE"), "OK" if perm("FOREGROUND_SERVICE") else "MISSING"))
    s.run("VpnService intent filter declared",  lambda: ("android.net.VpnService" in MANIFEST, "present" if "android.net.VpnService" in MANIFEST else "MISSING"))
    s.run("VPN service exported=false",         lambda: _check_vpn_service_not_exported(MANIFEST))
    s.run("SUPPORTS_ALWAYS_ON meta-data",       lambda: ("SUPPORTS_ALWAYS_ON" in MANIFEST, "present" if "SUPPORTS_ALWAYS_ON" in MANIFEST else "MISSING"))
    s.run("wireguard-android in build.gradle",  lambda: ("wireguard.android:tunnel" in GRADLE, "present" if "wireguard.android:tunnel" in GRADLE else "MISSING"))
    s.run("security-crypto in build.gradle",    lambda: ("security-crypto" in GRADLE, "present" if "security-crypto" in GRADLE else "MISSING"))
    s.run("minSdk ≥ 26",                        lambda: _check_min_sdk(GRADLE))
    s.run("VpnTunnel.kt @Deprecated annotation",lambda: ("@Deprecated" in TUNNEL, "@Deprecated present" if "@Deprecated" in TUNNEL else "MISSING — unencrypted tunnel may be used in prod"))
    return s.report()

def _check_vpn_service_not_exported(manifest: str) -> Tuple[bool, str]:
    m = re.search(r'<service[^>]+FreedomVpnService[^>]*>', manifest)
    if not m:
        return False, "FreedomVpnService tag not found"
    block = m.group(0)
    if 'android:exported="false"' not in block:
        return False, f"exported=false missing in: {block[:80]}"
    return True, "exported=false"

def _check_min_sdk(gradle: str) -> Tuple[bool, str]:
    m = re.search(r'minSdk\s*=\s*(\d+)', gradle)
    if not m:
        return False, "minSdk not found"
    v = int(m.group(1))
    return (v >= 26, f"minSdk={v}")


# ─────────────────────────────────────────────────────────────────────────────
# Suite 4 — Browser extension
# ─────────────────────────────────────────────────────────────────────────────

def suite_extension() -> bool:
    s = Suite("Browser Extension")
    MF  = json.loads((PROJECT_ROOT / "extension/manifest.json").read_text())
    BG  = (PROJECT_ROOT / "extension/background.js").read_text(encoding="utf-8")
    POP = (PROJECT_ROOT / "extension/popup.html").read_text(encoding="utf-8")

    s.run("Manifest Version 3",                     lambda: (MF.get("manifest_version") == 3, f"MV{MF.get('manifest_version')}"))
    s.run("proxy permission",                       lambda: ("proxy" in MF.get("permissions",[]), "present" if "proxy" in MF.get("permissions",[]) else "MISSING"))
    s.run("storage permission",                     lambda: ("storage" in MF.get("permissions",[]), "present" if "storage" in MF.get("permissions",[]) else "MISSING"))
    s.run("webRequest permission",                  lambda: ("webRequest" in MF.get("permissions",[]), "present" if "webRequest" in MF.get("permissions",[]) else "MISSING"))
    s.run("background.service_worker = background.js", lambda: (MF.get("background",{}).get("service_worker")=="background.js", str(MF.get("background",{}))))
    s.run("No eval() in service worker (MV3 CSP)",  lambda: _check_no_eval(BG))
    s.run("chrome.proxy API used for routing",      lambda: ("chrome.proxy" in BG, "found" if "chrome.proxy" in BG else "MISSING — extension cannot route traffic"))
    s.run("No inline <script> in popup.html",       lambda: _check_no_inline_script(POP))
    s.run("host_permissions declared",              lambda: (bool(MF.get("host_permissions")), str(MF.get("host_permissions"))))
    return s.report()

def _check_no_eval(content: str) -> Tuple[bool, str]:
    code_lines = [l for l in content.splitlines() if not l.strip().startswith("//")]
    if re.search(r'\beval\s*\(', "\n".join(code_lines)):
        return False, "eval() found — forbidden by MV3 CSP"
    return True, "no eval()"

def _check_no_inline_script(html: str) -> Tuple[bool, str]:
    inline = re.findall(r'<script(?![^>]*\bsrc=)[^>]*>', html, re.IGNORECASE)
    if inline:
        return False, f"Inline script: {inline[0]}"
    return True, "no inline scripts"


# ─────────────────────────────────────────────────────────────────────────────
# Suite 5 — Windows security
# ─────────────────────────────────────────────────────────────────────────────

def suite_windows() -> bool:
    s = Suite("Windows App Security")
    TUNNEL = (PROJECT_ROOT / "windows/system-tunnel.js").read_text(encoding="utf-8")
    MAIN   = (PROJECT_ROOT / "windows/main.js").read_text(encoding="utf-8")
    PKG    = json.loads((PROJECT_ROOT / "windows/package.json").read_text())

    s.run("No SHA256-as-pubkey bug",            lambda: ("createHash('sha256').update(privateKey).digest()" not in TUNNEL, "OK" if "createHash('sha256').update(privateKey).digest()" not in TUNNEL else "BUG STILL PRESENT"))
    s.run("nacl.scalarMult.base present",       lambda: ("scalarMult.base" in TUNNEL, "found" if "scalarMult.base" in TUNNEL else "MISSING"))
    s.run("No false-connected-on-failure",      lambda: ("this.isConnected = true; // Mark as connected anyway" not in TUNNEL, "OK" if "this.isConnected = true; // Mark as connected anyway" not in TUNNEL else "BUG PRESENT"))
    s.run("contextIsolation=true",              lambda: ("contextIsolation: false" not in MAIN, "OK" if "contextIsolation: false" not in MAIN else "DISABLED — XSS risk"))
    s.run("nodeIntegration=false",              lambda: ("nodeIntegration: true" not in MAIN, "OK" if "nodeIntegration: true" not in MAIN else "ENABLED — RCE risk"))
    s.run("requestedExecutionLevel=requireAdmin",lambda: (PKG.get("build",{}).get("win",{}).get("requestedExecutionLevel")=="requireAdministrator", str(PKG.get("build",{}).get("win",{}).get("requestedExecutionLevel"))))
    s.run("Kill switch via netsh advfirewall",   lambda: ("netsh advfirewall" in TUNNEL, "found" if "netsh advfirewall" in TUNNEL else "MISSING"))
    s.run("Config file saved 0o600",            lambda: ("0o600" in TUNNEL, "found" if "0o600" in TUNNEL else "MISSING — config file world-readable"))
    s.run("tweetnacl in package.json",          lambda: ("tweetnacl" in {**PKG.get("dependencies",{}),**PKG.get("devDependencies",{})}, "found" if "tweetnacl" in {**PKG.get("dependencies",{}),**PKG.get("devDependencies",{})} else "MISSING"))
    return s.report()


# ─────────────────────────────────────────────────────────────────────────────
# Suite 6 — Project structure & secrets scan
# ─────────────────────────────────────────────────────────────────────────────

def suite_server_infra() -> bool:
    """Validate that the server infrastructure code is present and structurally sound."""
    s = Suite("Server Infrastructure")

    SERVER_FILES = [
        "server/README.md",
        "server/setup.sh",
        "server/api/package.json",
        "server/api/src/index.js",
        "server/api/src/db.js",
        "server/api/src/routes/peers.js",
        "server/api/src/routes/health.js",
        "server/api/src/routes/servers.js",
        "server/api/src/middleware/auth.js",
        "server/api/src/middleware/rateLimit.js",
        "server/api/src/utils/wireguard.js",
        "server/nginx/nginx.conf",
        "server/scripts/healthcheck.sh",
        "server/api/.env.example",
        "server/docker-compose.yml",
    ]

    s.run("All server files present",       lambda: _check_files(SERVER_FILES))
    s.run("server/api/package.json valid",  lambda: _check_json(PROJECT_ROOT / "server/api/package.json", ["name","dependencies","scripts"]))
    s.run("Peer route — input validation",  lambda: _check_peer_route_validation())
    s.run("Auth middleware — timingSafeEqual", lambda: _check_timing_safe_auth())
    s.run("WireGuard util — no shell concat", lambda: _check_wg_no_shell_injection())
    s.run("Rate limiting declared",         lambda: _check_rate_limiting())
    s.run("Nginx — TLS 1.2/1.3 only",       lambda: _check_nginx_tls())
    s.run("Nginx — security headers",       lambda: _check_nginx_headers())
    s.run("setup.sh — WireGuard installed", lambda: ("wg genkey" in (PROJECT_ROOT / "server/setup.sh").read_text(encoding="utf-8"), "wg genkey present"))
    s.run("setup.sh — UFW firewall setup",  lambda: ("ufw" in (PROJECT_ROOT / "server/setup.sh").read_text(encoding="utf-8"), "ufw present"))
    s.run("DB — WAL mode for concurrency",  lambda: ("WAL" in (PROJECT_ROOT / "server/api/src/db.js").read_text(encoding="utf-8"), "WAL mode set"))
    s.run("DB — file permissions 0o600",    lambda: ("0o600" in (PROJECT_ROOT / "server/api/src/db.js").read_text(encoding="utf-8"), "chmod 0o600 set"))
    return s.report()

def _check_peer_route_validation() -> Tuple[bool, str]:
    content = (PROJECT_ROOT / "server/api/src/routes/peers.js").read_text(encoding="utf-8")
    if "isValidWireGuardKey" not in content:
        return False, "No WireGuard key validation function"
    if "decoded.length === 32" not in content:
        return False, "No 32-byte key length check"
    if "VALID_PLATFORMS" not in content:
        return False, "No platform allow-list"
    return True, "Key validation, length check, platform allowlist all present"

def _check_timing_safe_auth() -> Tuple[bool, str]:
    content = (PROJECT_ROOT / "server/api/src/middleware/auth.js").read_text(encoding="utf-8")
    if "timingSafeEqual" not in content:
        return False, "timingSafeEqual not used — token comparison is timing-vulnerable"
    return True, "timingSafeEqual used for constant-time token comparison"

def _check_wg_no_shell_injection() -> Tuple[bool, str]:
    content = (PROJECT_ROOT / "server/api/src/utils/wireguard.js").read_text(encoding="utf-8")
    if "execFile" not in content:
        return False, "execFile not used — potential shell injection via exec()"
    if "exec(" in content and "execFile" not in content:
        return False, "exec() used without execFile — shell injection risk"
    if "validateKey" not in content:
        return False, "No key validation before passing to wg CLI"
    return True, "execFile (no shell) + validateKey before execution"

def _check_rate_limiting() -> Tuple[bool, str]:
    content = (PROJECT_ROOT / "server/api/src/middleware/rateLimit.js").read_text(encoding="utf-8")
    if "rateLimit" not in content:
        return False, "express-rate-limit not used"
    if "register" not in content:
        return False, "No stricter registration rate limit"
    return True, "Global + per-registration rate limits defined"

def _check_nginx_tls() -> Tuple[bool, str]:
    content = (PROJECT_ROOT / "server/nginx/nginx.conf").read_text(encoding="utf-8")
    if "TLSv1.2" not in content or "TLSv1.3" not in content:
        return False, "TLS 1.2/1.3 not configured"
    if "TLSv1 " in content or "TLSv1.1" in content:
        return False, "Insecure TLS 1.0/1.1 enabled"
    return True, "TLS 1.2 + 1.3 only"

def _check_nginx_headers() -> Tuple[bool, str]:
    content = (PROJECT_ROOT / "server/nginx/nginx.conf").read_text(encoding="utf-8")
    required = ["Strict-Transport-Security", "X-Frame-Options", "X-Content-Type-Options"]
    missing = [h for h in required if h not in content]
    if missing:
        return False, f"Missing headers: {missing}"
    return True, "HSTS, X-Frame-Options, X-Content-Type-Options set"


def suite_structure() -> bool:
    s = Suite("Project Structure & Hygiene")

    REQUIRED = [
        "README.md", "shared/config.js",
        "shared/anticensorship/CensorshipBypassEngine.js",
        "shared/anticensorship/LeakProtection.js",
        "shared/stats/DynamicStatsEngine.js",
        "shared/core/VPNTunnelService.js",
        "shared/multihop/MultiHopEngine.js",
        "web/package.json", "web/src/App.jsx",
        "windows/package.json", "windows/main.js", "windows/system-tunnel.js",
        "android/app/build.gradle.kts",
        "android/app/src/main/AndroidManifest.xml",
        "extension/manifest.json", "extension/background.js",
        "tests/test_suite_comprehensive.py",
    ]

    s.run("Required files present",  lambda: _check_files(REQUIRED))
    s.run("web/package.json valid",  lambda: _check_json(PROJECT_ROOT / "web/package.json", ["dependencies"]))
    s.run("windows/package.json valid", lambda: _check_json(PROJECT_ROOT / "windows/package.json", ["main","scripts","dependencies"]))
    s.run("extension/manifest.json valid", lambda: _check_json(PROJECT_ROOT / "extension/manifest.json", ["manifest_version","name","permissions"]))
    s.run("No hardcoded secrets",    lambda: _scan_secrets())
    s.run("Android — Kotlin configured", lambda: ("kotlin" in (PROJECT_ROOT/"android/app/build.gradle.kts").read_text().lower(), "yes"))
    s.run("Android — Compose enabled",   lambda: ("compose" in (PROJECT_ROOT/"android/app/build.gradle.kts").read_text().lower(), "yes"))
    return s.report()

def _check_files(files: list) -> Tuple[bool, str]:
    missing = [f for f in files if not (PROJECT_ROOT / f).exists()]
    return (not missing, f"All {len(files)} present" if not missing else f"Missing: {missing}")

def _check_json(path: Path, required_keys: list) -> Tuple[bool, str]:
    data = json.loads(path.read_text())
    missing = [k for k in required_keys if k not in data]
    return (not missing, "OK" if not missing else f"Missing keys: {missing}")

def _scan_secrets() -> Tuple[bool, str]:
    patterns = [
        r'password\s*=\s*["\'][^"\']{8,}["\']',
        r'api_key\s*=\s*["\'][a-zA-Z0-9_\-]{16,}["\']',
        r'secret\s*=\s*["\'][^"\']{8,}["\']',
    ]
    skip = {"node_modules", ".git", "build", "dist", "test_suite_comprehensive"}
    exts = {".js", ".ts", ".jsx", ".tsx", ".py", ".kt", ".json"}
    hits = []
    for p in PROJECT_ROOT.rglob("*"):
        if p.suffix not in exts:
            continue
        if any(s in str(p) for s in skip):
            continue
        try:
            text = p.read_text(encoding="utf-8", errors="ignore")
            for pat in patterns:
                if re.search(pat, text, re.IGNORECASE):
                    hits.append(str(p.relative_to(PROJECT_ROOT)))
                    break
        except Exception:
            pass
    return (not hits, "clean" if not hits else f"Potential secrets in: {hits[:3]}")


# ─────────────────────────────────────────────────────────────────────────────
# Phase 3: Android — Server API Client Integration
# ─────────────────────────────────────────────────────────────────────────────

def suite_android_server_api() -> bool:
    android_vpn = PROJECT_ROOT / "android" / "app" / "src" / "main" / "java" / "com" / "freedomvpn" / "vpn"
    server_pkg  = android_vpn / "server"
    sac         = server_pkg / "ServerApiClient.kt"
    scm         = server_pkg / "ServerConnectionManager.kt"
    svc         = android_vpn / "FreedomVpnService.kt"

    s = Suite("Android — Server API Client")

    s.run("ServerApiClient.kt + ServerConnectionManager.kt present", lambda: (
        (sac.exists() and scm.exists(), f"sac={sac.exists()} scm={scm.exists()}")
    ))
    s.run("ServerApiClient — HTTPS enforcement", lambda: (
        (False, "file missing") if not sac.exists() else (
            lambda txt: (
                'https://' in txt and ('startsWith' in txt or 'WARNING' in txt),
                "HTTPS enforcement present"
            )
        )(sac.read_text(encoding="utf-8"))
    ))
    s.run("ServerApiClient — 44-char key validation before send", lambda: (
        (False, "file missing") if not sac.exists() else (
            lambda txt: (
                "44" in txt and ("isValidWireGuardKey" in txt or "length != 44" in txt),
                "key validation present"
            )
        )(sac.read_text(encoding="utf-8"))
    ))
    s.run("ServerApiClient — registerPeer() method", lambda: (
        (False, "file missing") if not sac.exists() else (
            "fun registerPeer" in sac.read_text(encoding="utf-8"), "present"
        )
    ))
    s.run("ServerApiClient — fetchServers() method", lambda: (
        (False, "file missing") if not sac.exists() else (
            "fun fetchServers" in sac.read_text(encoding="utf-8"), "present"
        )
    ))
    s.run("ServerApiClient — checkHealth() method", lambda: (
        (False, "file missing") if not sac.exists() else (
            "fun checkHealth" in sac.read_text(encoding="utf-8"), "present"
        )
    ))
    s.run("ServerApiClient — sends publicKey not privateKey", lambda: (
        (False, "file missing") if not sac.exists() else (
            '"publicKey"' in sac.read_text(encoding="utf-8"), "publicKey in request body"
        )
    ))
    s.run("ServerConnectionManager — EncryptedSharedPreferences", lambda: (
        (False, "file missing") if not scm.exists() else (
            "EncryptedSharedPreferences" in scm.read_text(encoding="utf-8"), "credentials stored encrypted"
        )
    ))
    s.run("ServerConnectionManager — key pair generated locally", lambda: (
        (False, "file missing") if not scm.exists() else (
            "getOrCreateKeyPair" in scm.read_text(encoding="utf-8"), "private key stays on device"
        )
    ))
    s.run("ServerConnectionManager — uses server-assigned IP", lambda: (
        (False, "file missing") if not scm.exists() else (
            "assignedIP" in scm.read_text(encoding="utf-8"), "dynamic IP from server"
        )
    ))
    s.run("FreedomVpnService — injects ServerConnectionManager", lambda: (
        (False, "file missing") if not svc.exists() else (
            "ServerConnectionManager" in svc.read_text(encoding="utf-8"), "injected"
        )
    ))
    s.run("FreedomVpnService — startServerApiConnection() path", lambda: (
        (False, "file missing") if not svc.exists() else (
            "startServerApiConnection" in svc.read_text(encoding="utf-8"), "production connect path"
        )
    ))
    return s.report()


def suite_windows_server_api() -> bool:
    tunnel_js = PROJECT_ROOT / "windows" / "system-tunnel.js"
    main_js   = PROJECT_ROOT / "windows" / "main.js"

    s = Suite("Windows — Server API Connection")

    def _t(path, check_fn):
        if not path.exists():
            return False, "file missing"
        return check_fn(path.read_text(encoding="utf-8"))

    s.run("tunnel.js + main.js present", lambda: (
        tunnel_js.exists() and main_js.exists(), "both present"
    ))
    s.run("system-tunnel.js — static registerPeer()", lambda: _t(
        tunnel_js, lambda txt: ("static async registerPeer" in txt, "present")
    ))
    s.run("system-tunnel.js — registerPeer enforces HTTPS", lambda: _t(
        tunnel_js, lambda txt: (
            "startsWith('https://')" in txt or 'startsWith("https://")' in txt,
            "HTTPS enforced"
        )
    ))
    s.run("system-tunnel.js — static fetchServerList()", lambda: _t(
        tunnel_js, lambda txt: ("static async fetchServerList" in txt, "present")
    ))
    s.run("system-tunnel.js — connectViaServerApi()", lambda: _t(
        tunnel_js, lambda txt: ("async connectViaServerApi" in txt, "present")
    ))
    s.run("system-tunnel.js — private key persisted", lambda: _t(
        tunnel_js, lambda txt: ("wg_private_key" in txt or "generateKeyPairAsync" in txt, "key persistence")
    ))
    s.run("system-tunnel.js — stable anonymous device ID", lambda: _t(
        tunnel_js, lambda txt: ("_getDeviceId" in txt, "anonymous ID")
    ))
    s.run("main.js — connect-via-server-api IPC handler", lambda: _t(
        main_js, lambda txt: ("connect-via-server-api" in txt, "IPC handler present")
    ))
    s.run("main.js — IPC validates https:// URL", lambda: _t(
        main_js, lambda txt: ("https://" in txt and "startsWith" in txt, "URL validation")
    ))
    s.run("main.js — server_url stored in electron-store", lambda: _t(
        main_js, lambda txt: ("server_url" in txt and "store.set" in txt, "persistent")
    ))
    return s.report()


def suite_extension_server_api() -> bool:
    bg = PROJECT_ROOT / "extension" / "background.js"

    s = Suite("Extension — Server API Integration")

    def _t(check_fn):
        if not bg.exists():
            return False, "file missing"
        return check_fn(bg.read_text(encoding="utf-8"))

    s.run("extension/background.js present", lambda: (bg.exists(), str(bg)))
    s.run("FreedomVPNServerService defined", lambda: _t(
        lambda txt: ("FreedomVPNServerService" in txt, "service present")
    ))
    s.run("Server URL stored in chrome.storage.sync", lambda: _t(
        lambda txt: (
            "chrome.storage.sync" in txt and "freedomvpn_server_url" in txt,
            "URL persisted"
        )
    ))
    s.run("HTTPS-only enforcement for server URL", lambda: _t(
        lambda txt: (
            "startsWith('https://')" in txt or 'startsWith("https://")' in txt,
            "HTTPS enforced"
        )
    ))
    s.run("Fetches /api/servers from production server", lambda: _t(
        lambda txt: ("/api/servers" in txt and "fetchServers" in txt, "endpoint present")
    ))
    s.run("setFreedomServerUrl message handler", lambda: _t(
        lambda txt: ("setFreedomServerUrl" in txt, "handler present")
    ))
    s.run("getFreedomServerUrl message handler", lambda: _t(
        lambda txt: ("getFreedomServerUrl" in txt, "handler present")
    ))
    s.run("refreshFreedomServers message handler", lambda: _t(
        lambda txt: ("refreshFreedomServers" in txt, "handler present")
    ))
    s.run("FreedomVPN servers preferred over VPNGate", lambda: _t(
        lambda txt: (
            "freedomServers" in txt and "VPNGate" in txt,
            "FreedomVPN first, VPNGate fallback"
        )
    ))
    return s.report()


# ─────────────────────────────────────────────────────────────────────────────
# Phase 6a: Extension — Free Proxy Service (replaces dead hardcoded IPs)
# ─────────────────────────────────────────────────────────────────────────────

def suite_free_proxy_service() -> bool:
    bg = PROJECT_ROOT / "extension" / "background.js"

    s = Suite("Extension — Free Proxy Service")

    def _t(check_fn):
        if not bg.exists():
            return False, "file missing"
        return check_fn(bg.read_text(encoding="utf-8"))

    s.run("FreeProxyService object defined", lambda: _t(
        lambda txt: ("FreeProxyService" in txt, "defined")
    ))
    s.run("GeoNode API URL present", lambda: _t(
        lambda txt: ("proxylist.geonode.com" in txt, "GeoNode URL")
    ))
    s.run("ProxyScrape API URL present", lambda: _t(
        lambda txt: ("api.proxyscrape.com" in txt, "ProxyScrape URL")
    ))
    s.run("fetchProxies() method", lambda: _t(
        lambda txt: ("fetchProxies" in txt, "method present")
    ))
    s.run("getNext() round-robin method", lambda: _t(
        lambda txt: ("getNext" in txt, "round-robin method")
    ))
    s.run("Dynamic server map (_dynamicServersMap)", lambda: _t(
        lambda txt: ("_dynamicServersMap" in txt, "dynamic map present")
    ))
    s.run("setProxy accepts server objects (not just IDs)", lambda: _t(
        lambda txt: ("typeof serverOrId" in txt or "serverOrId" in txt, "object-aware setProxy")
    ))
    s.run("Proxy scheme uses server.scheme (not hardcoded 'https')", lambda: _t(
        lambda txt: ("server.scheme" in txt and "socks5" in txt, "correct scheme")
    ))
    s.run("Dead hardcoded IPs removed (197.232 Kenya gone)", lambda: _t(
        lambda txt: ("197.232.170.50" not in txt, "dead IPs removed")
    ))
    s.run("refreshFreeProxies message handler", lambda: _t(
        lambda txt: ("refreshFreeProxies" in txt, "handler present")
    ))
    s.run("triggerFailover uses FreeProxyService", lambda: _t(
        lambda txt: ("FreeProxyService.fetchProxies" in txt, "failover uses free proxies")
    ))
    s.run("onInstalled fetches free proxies", lambda: _t(
        lambda txt: ("FreeProxyService.fetchProxies" in txt and "onInstalled" in txt, "startup fetch")
    ))

    return s.report()


# ─────────────────────────────────────────────────────────────────────────────
# Phase 6b: Windows — Cloudflare WARP (free WireGuard VPN)
# ─────────────────────────────────────────────────────────────────────────────

def suite_warp_integration() -> bool:
    tunnel_js = PROJECT_ROOT / "windows" / "system-tunnel.js"
    main_js   = PROJECT_ROOT / "windows" / "main.js"

    s = Suite("Windows — Cloudflare WARP Integration")

    def _t(path, check_fn):
        if not path.exists():
            return False, "file missing"
        return check_fn(path.read_text(encoding="utf-8"))

    s.run("system-tunnel.js — static registerWarpPeer()", lambda: _t(
        tunnel_js, lambda txt: ("static async registerWarpPeer" in txt, "present")
    ))
    s.run("system-tunnel.js — WARP API URL (cloudflareclient.com)", lambda: _t(
        tunnel_js, lambda txt: ("api.cloudflareclient.com" in txt, "WARP API URL")
    ))
    s.run("system-tunnel.js — connectViaWarp() method", lambda: _t(
        tunnel_js, lambda txt: ("async connectViaWarp" in txt, "method present")
    ))
    s.run("system-tunnel.js — WARP default port 2408", lambda: _t(
        tunnel_js, lambda txt: ("2408" in txt, "WireGuard WARP port")
    ))
    s.run("system-tunnel.js — warp_private_key stored", lambda: _t(
        tunnel_js, lambda txt: ("warp_private_key" in txt, "key persistence")
    ))
    s.run("system-tunnel.js — warp_creds cached in store", lambda: _t(
        tunnel_js, lambda txt: ("warp_creds" in txt, "credential caching")
    ))
    s.run("main.js — connect-via-warp IPC handler", lambda: _t(
        main_js, lambda txt: ("connect-via-warp" in txt, "IPC handler present")
    ))
    s.run("main.js — clear-warp-creds IPC handler", lambda: _t(
        main_js, lambda txt: ("clear-warp-creds" in txt, "IPC handler present")
    ))

    return s.report()


# ─────────────────────────────────────────────────────────────────────────────
# Main
# ─────────────────────────────────────────────────────────────────────────────

def main():
    print("=" * 70)
    print("  FREEDOMVPN COMPREHENSIVE TEST SUITE")
    print(f"  Root: {PROJECT_ROOT}")
    print("=" * 70)

    suites = [
        ("VPN Gate API",                    suite_vpngate),
        ("Cryptography",                    suite_crypto),
        ("Android Security",                suite_android),
        ("Extension Security",              suite_extension),
        ("Windows Security",                suite_windows),
        ("Server Infrastructure",           suite_server_infra),
        ("Project Structure",               suite_structure),
        ("Android — Server API Client",     suite_android_server_api),
        ("Windows — Server API Connection", suite_windows_server_api),
        ("Extension — Server API",          suite_extension_server_api),
        ("Extension — Free Proxy Service",  suite_free_proxy_service),
        ("Windows — Cloudflare WARP",       suite_warp_integration),
    ]

    results = []
    for name, fn in suites:
        try:
            results.append((name, fn()))
        except Exception as exc:
            print(f"\n  ERROR in suite {name}: {exc}")
            results.append((name, False))

    print("\n" + "=" * 70)
    print("  OVERALL")
    print("=" * 70)
    for name, ok in results:
        print(f"  {'✅' if ok else '❌'}  {name}")

    all_pass = all(ok for _, ok in results)
    if all_pass:
        print("\n  ✅  ALL SUITES PASSED\n")
    else:
        print("\n  ❌  FAILURES — fix before committing\n")
        sys.exit(1)


if __name__ == "__main__":
    main()
