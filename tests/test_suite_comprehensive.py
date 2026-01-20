"""
FreedomVPN Comprehensive Test Suite
====================================

Tests all components across all platforms:
- VPN Gate Integration
- Server Selection
- Anti-Censorship Engine
- Leak Protection
- Statistics Engine
- Configuration Files
- Build Verification

Run with: python tests/test_suite_comprehensive.py
"""

import os
import sys
import json
import time
import urllib.request
import csv
import base64
from io import StringIO
from dataclasses import dataclass
from typing import List, Optional
from pathlib import Path

# Add project root to path
PROJECT_ROOT = Path(__file__).parent.parent
sys.path.insert(0, str(PROJECT_ROOT))


class TestResult:
    """Holds result of a single test"""
    def __init__(self, name: str, passed: bool, message: str = "", duration: float = 0):
        self.name = name
        self.passed = passed
        self.message = message
        self.duration = duration
    
    def __str__(self):
        status = "✓ PASS" if self.passed else "✗ FAIL"
        return f"{status} | {self.name} ({self.duration:.2f}s) - {self.message}"


class TestSuite:
    """Comprehensive test suite for FreedomVPN"""
    
    def __init__(self):
        self.results: List[TestResult] = []
        self.project_root = PROJECT_ROOT
    
    def add_result(self, result: TestResult):
        self.results.append(result)
        print(result)
    
    def run_test(self, name: str, test_func):
        """Run a single test and record the result"""
        start = time.time()
        try:
            result, message = test_func()
            duration = time.time() - start
            self.add_result(TestResult(name, result, message, duration))
        except Exception as e:
            duration = time.time() - start
            self.add_result(TestResult(name, False, f"Exception: {e}", duration))
    
    def print_summary(self):
        """Print test summary"""
        print("\n" + "=" * 70)
        print("TEST SUMMARY")
        print("=" * 70)
        
        passed = sum(1 for r in self.results if r.passed)
        failed = sum(1 for r in self.results if not r.passed)
        total = len(self.results)
        
        print(f"Total: {total} | Passed: {passed} | Failed: {failed}")
        print(f"Pass Rate: {(passed/total*100):.1f}%")
        
        if failed > 0:
            print("\nFailed Tests:")
            for r in self.results:
                if not r.passed:
                    print(f"  - {r.name}: {r.message}")
        
        print("=" * 70)
        return failed == 0


# =============================================================================
# VPN GATE TESTS
# =============================================================================

def test_vpngate_api_connectivity():
    """Test VPN Gate API is accessible"""
    url = "https://www.vpngate.net/api/iphone/"
    try:
        request = urllib.request.Request(
            url,
            headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'}
        )
        with urllib.request.urlopen(request, timeout=30) as response:
            data = response.read()
            if len(data) > 1000:
                return True, f"Fetched {len(data)} bytes"
            return False, "Response too small"
    except Exception as e:
        return False, str(e)


def test_vpngate_csv_parsing():
    """Test CSV parsing of VPN Gate response"""
    url = "https://www.vpngate.net/api/iphone/"
    try:
        request = urllib.request.Request(
            url,
            headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'}
        )
        with urllib.request.urlopen(request, timeout=30) as response:
            data = response.read().decode('utf-8')
        
        # Parse
        lines = data.strip().split('\n')
        data_lines = [line for line in lines if not line.startswith('*')]
        csv_content = '\n'.join(data_lines)
        reader = csv.reader(StringIO(csv_content))
        
        header = next(reader, None)
        servers = list(reader)
        
        if len(servers) > 10:
            return True, f"Parsed {len(servers)} servers"
        return False, f"Only {len(servers)} servers found"
    except Exception as e:
        return False, str(e)


def test_vpngate_openvpn_configs():
    """Test OpenVPN config decoding"""
    url = "https://www.vpngate.net/api/iphone/"
    try:
        request = urllib.request.Request(
            url,
            headers={'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'}
        )
        with urllib.request.urlopen(request, timeout=30) as response:
            data = response.read().decode('utf-8')
        
        lines = data.strip().split('\n')
        data_lines = [line for line in lines if not line.startswith('*')]
        csv_content = '\n'.join(data_lines)
        reader = csv.reader(StringIO(csv_content))
        next(reader)  # skip header
        
        valid_configs = 0
        tested = 0
        for row in reader:
            if len(row) > 14 and row[14]:
                tested += 1
                try:
                    config = base64.b64decode(row[14]).decode('utf-8')
                    if 'remote' in config.lower() and 'dev' in config.lower():
                        valid_configs += 1
                except:
                    pass
            if tested >= 10:
                break
        
        if valid_configs >= 8:
            return True, f"{valid_configs}/10 configs valid"
        return False, f"Only {valid_configs}/10 valid"
    except Exception as e:
        return False, str(e)


# =============================================================================
# FILE STRUCTURE TESTS
# =============================================================================

def test_project_structure():
    """Verify essential project files exist"""
    required_files = [
        "README.md",
        "LICENSE",
        "tests/test_vpngate.py",
        "shared/config.js",
        "shared/anticensorship/CensorshipBypassEngine.js",
        "shared/anticensorship/LeakProtection.js",
        "shared/stats/DynamicStatsEngine.js",
        "shared/vpngate/parser.py",
        "shared/vpngate/server_selector.py",
        "web/package.json",
        "web/src/App.jsx",
        "windows/package.json",
        "windows/main.js",
        "android/app/build.gradle.kts",
        "extension/manifest.json",
    ]
    
    missing = []
    for file in required_files:
        path = PROJECT_ROOT / file
        if not path.exists():
            missing.append(file)
    
    if not missing:
        return True, f"All {len(required_files)} files present"
    return False, f"Missing: {', '.join(missing)}"


def test_web_package_json():
    """Validate web package.json"""
    try:
        with open(PROJECT_ROOT / "web/package.json", 'r', encoding='utf-8') as f:
            pkg = json.load(f)
        
        required = ['react', 'react-dom', 'react-router-dom']
        missing = [r for r in required if r not in pkg.get('dependencies', {})]
        
        if not missing:
            return True, f"Dependencies OK, version {pkg.get('version', 'unknown')}"
        return False, f"Missing deps: {missing}"
    except Exception as e:
        return False, str(e)


def test_windows_package_json():
    """Validate Windows Electron package.json"""
    try:
        with open(PROJECT_ROOT / "windows/package.json", 'r', encoding='utf-8') as f:
            pkg = json.load(f)
        
        if 'main' in pkg and 'scripts' in pkg:
            if 'start' in pkg['scripts']:
                return True, f"Electron config OK, version {pkg.get('version', 'unknown')}"
        return False, "Missing main entry or scripts"
    except Exception as e:
        return False, str(e)


def test_android_build_gradle():
    """Validate Android build configuration"""
    try:
        with open(PROJECT_ROOT / "android/app/build.gradle.kts", 'r', encoding='utf-8') as f:
            content = f.read()
        
        required = ['namespace', 'compileSdk', 'minSdk', 'compose']
        missing = [r for r in required if r not in content]
        
        if not missing:
            return True, "Kotlin DSL build config valid"
        return False, f"Missing: {missing}"
    except Exception as e:
        return False, str(e)


def test_extension_manifest():
    """Validate browser extension manifest"""
    try:
        with open(PROJECT_ROOT / "extension/manifest.json", 'r', encoding='utf-8') as f:
            manifest = json.load(f)
        
        required = ['manifest_version', 'name', 'version', 'permissions']
        missing = [r for r in required if r not in manifest]
        
        if not missing:
            return True, f"Manifest v{manifest.get('manifest_version')} OK"
        return False, f"Missing: {missing}"
    except Exception as e:
        return False, str(e)


# =============================================================================
# JAVASCRIPT MODULE TESTS
# =============================================================================

def test_censorship_bypass_engine():
    """Validate CensorshipBypassEngine.js structure"""
    try:
        with open(PROJECT_ROOT / "shared/anticensorship/CensorshipBypassEngine.js", 'r', encoding='utf-8') as f:
            content = f.read()
        
        features = [
            'OBFUSCATION_METHODS',
            'TLS_CAMOUFLAGE',
            'DOMAIN_FRONTING',
            'ANTI_CENSORSHIP_SERVERS'
        ]
        found = [f for f in features if f in content]
        
        if len(found) >= 3:
            return True, f"Found {len(found)}/4 core features"
        return False, f"Only {len(found)}/4 features"
    except Exception as e:
        return False, str(e)


def test_leak_protection_module():
    """Validate LeakProtection.js structure"""
    try:
        with open(PROJECT_ROOT / "shared/anticensorship/LeakProtection.js", 'r', encoding='utf-8') as f:
            content = f.read()
        
        protections = ['WebRTC', 'DNS', 'IPv6', 'canvas', 'timezone']
        found = [p for p in protections if p.lower() in content.lower()]
        
        if len(found) >= 4:
            return True, f"Found {len(found)}/5 leak protections"
        return False, f"Only {len(found)}/5 protections"
    except Exception as e:
        return False, str(e)


def test_stats_engine():
    """Validate DynamicStatsEngine.js structure"""
    try:
        with open(PROJECT_ROOT / "shared/stats/DynamicStatsEngine.js", 'r', encoding='utf-8') as f:
            content = f.read()
        
        features = ['downloadSpeed', 'uploadSpeed', 'latency', 'moneySaved', 'compressionRatio']
        found = [f for f in features if f in content]
        
        if len(found) >= 4:
            return True, f"Found {len(found)}/5 stats features"
        return False, f"Only {len(found)}/5 features"
    except Exception as e:
        return False, str(e)


# =============================================================================
# PYTHON MODULE TESTS
# =============================================================================

def test_parser_module():
    """Test VPN Gate parser module"""
    try:
        sys.path.insert(0, str(PROJECT_ROOT / "shared/vpngate"))
        from parser import VpnGateServer, VpnGateParser
        
        # Test data class
        server = VpnGateServer(
            hostname="test",
            ip="1.2.3.4",
            score=1000,
            ping=50,
            speed=100_000_000,
            country_long="Japan",
            country_short="JP",
            num_vpn_sessions=10,
            uptime=3600000,
            total_users=100,
            total_traffic=1000000,
            log_type="none",
            operator="test",
            message="test",
            openvpn_config_base64="dGVzdA=="
        )
        
        if server.speed_mbps == 100.0 and server.quality_score > 0:
            return True, "Parser module functional"
        return False, "Parser calculations incorrect"
    except Exception as e:
        return False, str(e)


def test_server_selector_module():
    """Test server selector module"""
    try:
        sys.path.insert(0, str(PROJECT_ROOT / "shared/vpngate"))
        from server_selector import ServerSelector, Region, SortCriteria
        
        # Verify enums
        if Region.AFRICA.value == 'africa' and SortCriteria.SPEED.value == 'speed':
            return True, "Server selector module functional"
        return False, "Enum values incorrect"
    except Exception as e:
        return False, str(e)


# =============================================================================
# WEB APPLICATION TESTS
# =============================================================================

def test_web_react_components():
    """Verify React components exist"""
    components = [
        "web/src/App.jsx",
        "web/src/components/Navigation.jsx",
        "web/src/pages/Dashboard.jsx",
        "web/src/pages/Servers.jsx",
        "web/src/pages/Settings.jsx",
        "web/src/pages/Statistics.jsx",
        "web/src/context/VpnContext.jsx",
    ]
    
    missing = []
    for comp in components:
        path = PROJECT_ROOT / comp
        if not path.exists():
            missing.append(comp)
    
    if not missing:
        return True, f"All {len(components)} components present"
    return False, f"Missing: {', '.join(missing)}"


def test_web_tailwind_config():
    """Verify Tailwind CSS configuration"""
    try:
        with open(PROJECT_ROOT / "web/tailwind.config.js", 'r', encoding='utf-8') as f:
            content = f.read()
        
        if 'content' in content and 'theme' in content:
            return True, "Tailwind configured"
        return False, "Missing content or theme config"
    except Exception as e:
        return False, str(e)


# =============================================================================
# WINDOWS APPLICATION TESTS  
# =============================================================================

def test_windows_electron_main():
    """Verify Electron main process"""
    try:
        with open(PROJECT_ROOT / "windows/main.js", 'r', encoding='utf-8') as f:
            content = f.read()
        
        features = ['BrowserWindow', 'ipcMain', 'createWindow', 'SERVERS']
        found = [f for f in features if f in content]
        
        if len(found) >= 3:
            return True, f"Found {len(found)}/4 Electron features"
        return False, f"Only {len(found)}/4 features"
    except Exception as e:
        return False, str(e)


def test_windows_preload():
    """Verify Electron preload script"""
    try:
        with open(PROJECT_ROOT / "windows/preload.js", 'r', encoding='utf-8') as f:
            content = f.read()
        
        if 'contextBridge' in content or 'exposeInMainWorld' in content:
            return True, "Preload script valid"
        return False, "Missing contextBridge"
    except Exception as e:
        return False, str(e)


def test_windows_uwp_structure():
    """Verify UWP project structure"""
    uwp_files = [
        "windows/FreedomVPN.Uwp/App.xaml",
        "windows/FreedomVPN.Uwp/MainWindow.xaml",
        "windows/FreedomVPN.Uwp/FreedomVPN.Uwp.csproj",
        "windows/FreedomVPN.Uwp/Services/VpnConnectionService.cs",
        "windows/FreedomVPN.Uwp/ViewModels/MainViewModel.cs",
    ]
    
    missing = []
    for f in uwp_files:
        path = PROJECT_ROOT / f
        if not path.exists():
            missing.append(f)
    
    if not missing:
        return True, f"All {len(uwp_files)} UWP files present"
    return False, f"Missing: {', '.join(missing)}"


# =============================================================================
# ANDROID APPLICATION TESTS
# =============================================================================

def test_android_manifest():
    """Verify Android manifest"""
    try:
        with open(PROJECT_ROOT / "android/app/src/main/AndroidManifest.xml", 'r', encoding='utf-8') as f:
            content = f.read()
        
        required = ['INTERNET', 'VPN_SERVICE', 'application']
        found = [r for r in required if r in content]
        
        if len(found) >= 2:
            return True, f"Found {len(found)}/3 manifest features"
        return False, f"Only {len(found)}/3 features"
    except Exception as e:
        return False, str(e)


def test_android_compose_ui():
    """Verify Android Compose UI files"""
    ui_path = PROJECT_ROOT / "android/app/src/main/java/com/freedomvpn/ui"
    if ui_path.exists():
        files = list(ui_path.rglob("*.kt"))
        if len(files) > 0:
            return True, f"Found {len(files)} Compose UI files"
    return False, "No UI files found"


# =============================================================================
# EXTENSION TESTS
# =============================================================================

def test_extension_files():
    """Verify extension files"""
    ext_files = [
        "extension/manifest.json",
        "extension/background.js",
        "extension/popup.html",
        "extension/popup.js",
        "extension/popup.css",
    ]
    
    missing = []
    for f in ext_files:
        path = PROJECT_ROOT / f
        if not path.exists():
            missing.append(f)
    
    if not missing:
        return True, f"All {len(ext_files)} extension files present"
    return False, f"Missing: {', '.join(missing)}"


def test_extension_background_script():
    """Verify extension background script"""
    try:
        with open(PROJECT_ROOT / "extension/background.js", 'r', encoding='utf-8') as f:
            content = f.read()
        
        features = ['chrome', 'proxy', 'storage', 'runtime']
        found = [f for f in features if f in content]
        
        if len(found) >= 2:
            return True, f"Found {len(found)}/4 extension APIs"
        return False, f"Only {len(found)}/4 APIs"
    except Exception as e:
        return False, str(e)


# =============================================================================
# INTEGRATION TESTS
# =============================================================================

def test_shared_config():
    """Verify shared configuration"""
    try:
        with open(PROJECT_ROOT / "shared/config.js", 'r', encoding='utf-8') as f:
            content = f.read()
        
        if 'FreedomVPN' in content or 'config' in content.lower():
            return True, "Shared config present"
        return False, "Config appears empty"
    except Exception as e:
        return False, str(e)


def test_documentation():
    """Verify documentation exists"""
    docs = [
        "docs/ANDROID_SETUP.md",
        "docs/WINDOWS_SETUP.md",
        "docs/VPNGATE_INTEGRATION.md",
    ]
    
    found = 0
    for doc in docs:
        if (PROJECT_ROOT / doc).exists():
            found += 1
    
    if found >= 2:
        return True, f"Found {found}/3 documentation files"
    return False, f"Only {found}/3 docs"


# =============================================================================
# MAIN TEST RUNNER
# =============================================================================

def main():
    print("=" * 70)
    print("FREEDOMVPN COMPREHENSIVE TEST SUITE")
    print("=" * 70)
    print(f"Project Root: {PROJECT_ROOT}")
    print(f"Python Version: {sys.version}")
    print(f"Test Started: {time.strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 70)
    print()
    
    suite = TestSuite()
    
    # VPN Gate Tests
    print("\n--- VPN GATE INTEGRATION TESTS ---\n")
    suite.run_test("VPN Gate API Connectivity", test_vpngate_api_connectivity)
    suite.run_test("VPN Gate CSV Parsing", test_vpngate_csv_parsing)
    suite.run_test("OpenVPN Config Decoding", test_vpngate_openvpn_configs)
    
    # Project Structure Tests
    print("\n--- PROJECT STRUCTURE TESTS ---\n")
    suite.run_test("Project Structure", test_project_structure)
    suite.run_test("Web package.json", test_web_package_json)
    suite.run_test("Windows package.json", test_windows_package_json)
    suite.run_test("Android build.gradle.kts", test_android_build_gradle)
    suite.run_test("Extension manifest.json", test_extension_manifest)
    
    # JavaScript Module Tests
    print("\n--- JAVASCRIPT MODULE TESTS ---\n")
    suite.run_test("CensorshipBypassEngine", test_censorship_bypass_engine)
    suite.run_test("LeakProtection", test_leak_protection_module)
    suite.run_test("DynamicStatsEngine", test_stats_engine)
    
    # Python Module Tests
    print("\n--- PYTHON MODULE TESTS ---\n")
    suite.run_test("Parser Module", test_parser_module)
    suite.run_test("Server Selector Module", test_server_selector_module)
    
    # Web Application Tests
    print("\n--- WEB APPLICATION TESTS ---\n")
    suite.run_test("React Components", test_web_react_components)
    suite.run_test("Tailwind CSS Config", test_web_tailwind_config)
    
    # Windows Application Tests
    print("\n--- WINDOWS APPLICATION TESTS ---\n")
    suite.run_test("Electron Main Process", test_windows_electron_main)
    suite.run_test("Electron Preload Script", test_windows_preload)
    suite.run_test("UWP Project Structure", test_windows_uwp_structure)
    
    # Android Application Tests
    print("\n--- ANDROID APPLICATION TESTS ---\n")
    suite.run_test("Android Manifest", test_android_manifest)
    suite.run_test("Compose UI Files", test_android_compose_ui)
    
    # Extension Tests
    print("\n--- BROWSER EXTENSION TESTS ---\n")
    suite.run_test("Extension Files", test_extension_files)
    suite.run_test("Background Script", test_extension_background_script)
    
    # Integration Tests
    print("\n--- INTEGRATION TESTS ---\n")
    suite.run_test("Shared Configuration", test_shared_config)
    suite.run_test("Documentation", test_documentation)
    
    # Summary
    all_passed = suite.print_summary()
    return 0 if all_passed else 1


if __name__ == "__main__":
    exit_code = main()
    sys.exit(exit_code)
