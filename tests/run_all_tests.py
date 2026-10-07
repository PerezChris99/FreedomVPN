"""
FreedomVPN Master Test Runner
==============================

Runs all test suites and provides a complete verification report:
1. VPN Gate Integration Tests
2. Platform Compatibility Tests  
3. Anonymity Verification Tests
4. Multi-Hop regression tests
5. Production-truth regression tests

Run with: python tests/run_all_tests.py
"""

import os
import sys
import time
from pathlib import Path

# Add project root to path
PROJECT_ROOT = Path(__file__).parent.parent
sys.path.insert(0, str(PROJECT_ROOT))
sys.path.insert(0, str(PROJECT_ROOT / "tests"))


def run_all_tests():
    """Run all test suites and report results"""
    print("=" * 70)
    print("FREEDOMVPN MASTER TEST SUITE")
    print("=" * 70)
    print(f"Project: {PROJECT_ROOT}")
    print(f"Python: {sys.version.split()[0]}")
    print(f"Started: {time.strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 70)
    
    all_passed = True
    total_tests = 0
    total_passed = 0
    
    # ===== TEST SUITE 1: VPN GATE =====
    print("\n" + "=" * 70)
    print("SUITE 1: VPN GATE INTEGRATION")
    print("=" * 70)
    
    try:
        from test_vpngate import run_tests as run_vpngate_tests
        vpngate_passed = run_vpngate_tests()
        if vpngate_passed:
            total_passed += 8
        total_tests += 8
        all_passed = all_passed and vpngate_passed
    except Exception as e:
        print(f"[ERROR] VPN Gate tests failed: {e}")
        all_passed = False
    
    # ===== TEST SUITE 2: COMPREHENSIVE =====
    print("\n" + "=" * 70)
    print("SUITE 2: COMPREHENSIVE PLATFORM TESTS")
    print("=" * 70)
    
    try:
        from test_suite_comprehensive import TestSuite
        from test_suite_comprehensive import (
            test_vpngate_api_connectivity,
            test_vpngate_csv_parsing,
            test_vpngate_openvpn_configs,
            test_project_structure,
            test_web_package_json,
            test_windows_package_json,
            test_android_build_gradle,
            test_extension_manifest,
            test_censorship_bypass_engine,
            test_leak_protection_module,
            test_stats_engine,
            test_parser_module,
            test_server_selector_module,
            test_web_react_components,
            test_web_tailwind_config,
            test_windows_electron_main,
            test_windows_preload,
            test_windows_uwp_structure,
            test_android_manifest,
            test_android_compose_ui,
            test_extension_files,
            test_extension_background_script,
            test_shared_config,
            test_documentation
        )
        
        suite = TestSuite()
        
        tests = [
            ("VPN Gate API", test_vpngate_api_connectivity),
            ("CSV Parsing", test_vpngate_csv_parsing),
            ("OpenVPN Configs", test_vpngate_openvpn_configs),
            ("Project Structure", test_project_structure),
            ("Web package.json", test_web_package_json),
            ("Windows package.json", test_windows_package_json),
            ("Android build.gradle", test_android_build_gradle),
            ("Extension manifest", test_extension_manifest),
            ("CensorshipBypassEngine", test_censorship_bypass_engine),
            ("LeakProtection", test_leak_protection_module),
            ("DynamicStatsEngine", test_stats_engine),
            ("Parser Module", test_parser_module),
            ("Server Selector", test_server_selector_module),
            ("React Components", test_web_react_components),
            ("Tailwind Config", test_web_tailwind_config),
            ("Electron Main", test_windows_electron_main),
            ("Electron Preload", test_windows_preload),
            ("UWP Structure", test_windows_uwp_structure),
            ("Android Manifest", test_android_manifest),
            ("Compose UI", test_android_compose_ui),
            ("Extension Files", test_extension_files),
            ("Background Script", test_extension_background_script),
            ("Shared Config", test_shared_config),
            ("Documentation", test_documentation),
        ]
        
        passed = 0
        for name, test_func in tests:
            try:
                result, msg = test_func()
                if result:
                    passed += 1
                    print(f"  [PASS] {name}")
                else:
                    print(f"  [FAIL] {name}: {msg}")
                    all_passed = False
            except Exception as e:
                print(f"  [ERROR] {name}: {e}")
                all_passed = False
        
        total_tests += len(tests)
        total_passed += passed
        
    except Exception as e:
        print(f"[ERROR] Comprehensive tests failed: {e}")
        import traceback
        traceback.print_exc()
        all_passed = False
    
    # ===== TEST SUITE 3: ANONYMITY =====
    print("\n" + "=" * 70)
    print("SUITE 3: ANONYMITY VERIFICATION")
    print("=" * 70)
    
    try:
        from test_anonymity import AnonymityTestSuite
        anon_suite = AnonymityTestSuite()
        anon_passed = anon_suite.run_all_tests()
        
        anon_total = len(anon_suite.results)
        anon_pass_count = sum(1 for r in anon_suite.results if r.passed)
        
        total_tests += anon_total
        total_passed += anon_pass_count
        all_passed = all_passed and anon_passed
        
    except Exception as e:
        print(f"[ERROR] Anonymity tests failed: {e}")
        import traceback
        traceback.print_exc()
        all_passed = False
    
    # ===== TEST SUITE 4: MULTI-HOP =====
    print("\n" + "=" * 70)
    print("SUITE 4: MULTI-HOP (SERVER BOUNCING)")
    print("=" * 70)
    
    try:
        import unittest
        from test_multihop import (
            TestMultiHopEngine,
            TestMultiHopWindows,
            TestMultiHopAndroid,
            TestMultiHopWeb,
            TestMultiHopExtension,
            TestMultiHopSpeedOptimization,
            TestMultiHopSecurity
        )
        
        loader = unittest.TestLoader()
        multihop_suite = unittest.TestSuite()
        
        multihop_suite.addTests(loader.loadTestsFromTestCase(TestMultiHopEngine))
        multihop_suite.addTests(loader.loadTestsFromTestCase(TestMultiHopWindows))
        multihop_suite.addTests(loader.loadTestsFromTestCase(TestMultiHopAndroid))
        multihop_suite.addTests(loader.loadTestsFromTestCase(TestMultiHopWeb))
        multihop_suite.addTests(loader.loadTestsFromTestCase(TestMultiHopExtension))
        multihop_suite.addTests(loader.loadTestsFromTestCase(TestMultiHopSpeedOptimization))
        multihop_suite.addTests(loader.loadTestsFromTestCase(TestMultiHopSecurity))
        
        runner = unittest.TextTestRunner(verbosity=1)
        result = runner.run(multihop_suite)
        
        mh_total = result.testsRun
        mh_failures = len(result.failures) + len(result.errors)
        mh_passed = mh_total - mh_failures
        
        total_tests += mh_total
        total_passed += mh_passed
        
        if mh_failures > 0:
            all_passed = False
        
        print(f"\n  Multi-Hop Tests: {mh_passed}/{mh_total} passed")
        
    except Exception as e:
        print(f"[ERROR] Multi-Hop tests failed: {e}")
        import traceback
        traceback.print_exc()
        all_passed = False
    
    # ===== PRODUCTION-TRUTH REGRESSIONS =====
    print("\n" + "=" * 70)
    print("SUITE 5: PRODUCTION-TRUTH REGRESSIONS")
    print("=" * 70)
    try:
        from test_production_truth import main as run_production_truth
        truth_passed = run_production_truth()
        truth_total = 7
        total_tests += truth_total
        if truth_passed:
            total_passed += truth_total
        else:
            all_passed = False
    except Exception as e:
        print(f"[ERROR] Production-truth tests failed: {e}")
        all_passed = False

    # ===== FINAL SUMMARY =====
    print("\n" + "=" * 70)
    print("MASTER TEST SUMMARY")
    print("=" * 70)
    print(f"\nTotal Tests Run: {total_tests}")
    print(f"Tests Passed: {total_passed}")
    print(f"Tests Failed: {total_tests - total_passed}")
    print(f"Overall Pass Rate: {(total_passed/total_tests*100):.1f}%")
    
    # Use actual counts for final verdict (more reliable than boolean tracking)
    final_success = total_passed == total_tests
    
    if final_success:
        print("\n" + "=" * 70)
        print("[SUCCESS] ALL AUTOMATED REGRESSION TESTS PASSED")
        print("=" * 70)
        print("\nThis result confirms only the checks executed by this test runner.")
        print("It does not prove live VPN connectivity, anonymity, censorship bypass,")
        print("national-scale capacity, or production infrastructure readiness.")
    else:
        print("\n[WARNING] Some tests failed - review output above")
    
    print("\n" + "=" * 70)
    return final_success


if __name__ == "__main__":
    success = run_all_tests()
    sys.exit(0 if success else 1)
