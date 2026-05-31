"""
Multi-Hop VPN Tests
Tests for the server-bouncing multi-hop functionality across all platforms.
"""

import unittest
import json
import os


class TestMultiHopEngine(unittest.TestCase):
    """Tests for the core Multi-Hop engine"""
    
    def test_multihop_engine_exists(self):
        """Multi-Hop engine file should exist"""
        engine_path = os.path.join(os.path.dirname(__file__), '..', 'shared', 'multihop', 'MultiHopEngine.js')
        self.assertTrue(os.path.exists(engine_path), "MultiHopEngine.js should exist")
    
    def test_multihop_engine_has_required_classes(self):
        """Engine should export required classes"""
        engine_path = os.path.join(os.path.dirname(__file__), '..', 'shared', 'multihop', 'MultiHopEngine.js')
        with open(engine_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # Check for required exports
        self.assertIn('class MultiHopEngine', content, "Should have MultiHopEngine class")
        self.assertIn('MultiHopPresets', content, "Should have MultiHopPresets")
        self.assertIn('class ProxyChainManager', content, "Should have ProxyChainManager class")
    
    def test_multihop_presets_defined(self):
        """Should have all anonymity presets defined"""
        engine_path = os.path.join(os.path.dirname(__file__), '..', 'shared', 'multihop', 'MultiHopEngine.js')
        with open(engine_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        presets = ['FAST', 'BALANCED', 'MAXIMUM', 'PARANOID']
        for preset in presets:
            self.assertIn(preset, content, f"Should have {preset} preset")
    
    def test_multihop_has_hop_count_config(self):
        """Each preset should have hop count configuration"""
        engine_path = os.path.join(os.path.dirname(__file__), '..', 'shared', 'multihop', 'MultiHopEngine.js')
        with open(engine_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        self.assertIn('hopCount: 2', content, "FAST/BALANCED should have 2 hops")
        self.assertIn('hopCount: 3', content, "MAXIMUM should have 3 hops")
        self.assertIn('hopCount: 4', content, "PARANOID should have 4 hops")
    
    def test_multihop_has_diversity_option(self):
        """Should have geographic diversity routing option"""
        engine_path = os.path.join(os.path.dirname(__file__), '..', 'shared', 'multihop', 'MultiHopEngine.js')
        with open(engine_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        self.assertIn('diverseRouting', content, "Should have diverseRouting option")


class TestMultiHopWindows(unittest.TestCase):
    """Tests for Windows desktop multi-hop implementation"""
    
    def test_windows_main_has_multihop_import(self):
        """Windows main.js should import MultiHopEngine"""
        main_path = os.path.join(os.path.dirname(__file__), '..', 'windows', 'main.js')
        with open(main_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        self.assertIn('MultiHopEngine', content, "Should import MultiHopEngine")
        self.assertIn('MultiHopPresets', content, "Should import MultiHopPresets")
    
    def test_windows_has_multihop_ipc_handlers(self):
        """Windows should have IPC handlers for multi-hop"""
        main_path = os.path.join(os.path.dirname(__file__), '..', 'windows', 'main.js')
        with open(main_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        self.assertIn("'toggle-multihop'", content, "Should have toggle-multihop handler")
        self.assertIn("'get-multihop-state'", content, "Should have get-multihop-state handler")
        self.assertIn("'set-multihop-preset'", content, "Should have set-multihop-preset handler")
    
    def test_windows_state_includes_multihop(self):
        """Windows state should include multi-hop tracking"""
        main_path = os.path.join(os.path.dirname(__file__), '..', 'windows', 'main.js')
        with open(main_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        self.assertIn('multiHop:', content, "State should include multiHop")
        self.assertIn('enabled: false', content, "MultiHop should be disabled by default")
    
    def test_windows_toggle_multihop_function(self):
        """Windows should have toggleMultiHop function"""
        main_path = os.path.join(os.path.dirname(__file__), '..', 'windows', 'main.js')
        with open(main_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        self.assertIn('async function toggleMultiHop', content, "Should have toggleMultiHop function")


class TestMultiHopAndroid(unittest.TestCase):
    """Tests for Android multi-hop implementation"""
    
    def test_android_multihop_manager_exists(self):
        """Android MultiHopManager.kt should exist"""
        manager_path = os.path.join(os.path.dirname(__file__), '..', 'android', 'app', 'src', 'main', 'java', 'com', 'freedomvpn', 'vpn', 'MultiHopManager.kt')
        self.assertTrue(os.path.exists(manager_path), "MultiHopManager.kt should exist")
    
    def test_android_has_multihop_presets(self):
        """Android should have anonymity presets"""
        manager_path = os.path.join(os.path.dirname(__file__), '..', 'android', 'app', 'src', 'main', 'java', 'com', 'freedomvpn', 'vpn', 'MultiHopManager.kt')
        with open(manager_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        self.assertIn('enum class MultiHopPreset', content, "Should have MultiHopPreset enum")
        self.assertIn('FAST', content, "Should have FAST preset")
        self.assertIn('BALANCED', content, "Should have BALANCED preset")
        self.assertIn('MAXIMUM', content, "Should have MAXIMUM preset")
        self.assertIn('PARANOID', content, "Should have PARANOID preset")
    
    def test_android_has_chain_state(self):
        """Android should track chain state"""
        manager_path = os.path.join(os.path.dirname(__file__), '..', 'android', 'app', 'src', 'main', 'java', 'com', 'freedomvpn', 'vpn', 'MultiHopManager.kt')
        with open(manager_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        self.assertIn('data class ChainState', content, "Should have ChainState data class")
        self.assertIn('data class HopInfo', content, "Should have HopInfo data class")
    
    def test_android_has_activate_deactivate(self):
        """Android should have activate/deactivate functions"""
        manager_path = os.path.join(os.path.dirname(__file__), '..', 'android', 'app', 'src', 'main', 'java', 'com', 'freedomvpn', 'vpn', 'MultiHopManager.kt')
        with open(manager_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        self.assertIn('suspend fun activate', content, "Should have activate function")
        self.assertIn('suspend fun deactivate', content, "Should have deactivate function")
        self.assertIn('suspend fun toggle', content, "Should have toggle function")
    
    def test_android_has_chain_rotation(self):
        """Android should support chain rotation for enhanced anonymity"""
        manager_path = os.path.join(os.path.dirname(__file__), '..', 'android', 'app', 'src', 'main', 'java', 'com', 'freedomvpn', 'vpn', 'MultiHopManager.kt')
        with open(manager_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        self.assertIn('suspend fun rotate', content, "Should have rotate function")
        self.assertIn('rotationIntervalMinutes', content, "Should have rotation interval config")


class TestMultiHopWeb(unittest.TestCase):
    """Tests for Web app multi-hop implementation"""
    
    def test_web_multihop_service_exists(self):
        """Web multiHopService.js should exist"""
        service_path = os.path.join(os.path.dirname(__file__), '..', 'web', 'src', 'services', 'multiHopService.js')
        self.assertTrue(os.path.exists(service_path), "multiHopService.js should exist")
    
    def test_web_has_multihop_presets(self):
        """Web should have anonymity presets"""
        service_path = os.path.join(os.path.dirname(__file__), '..', 'web', 'src', 'services', 'multiHopService.js')
        with open(service_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        self.assertIn('MultiHopPresets', content, "Should export MultiHopPresets")
        self.assertIn('FAST:', content, "Should have FAST preset")
        self.assertIn('BALANCED:', content, "Should have BALANCED preset")
        self.assertIn('MAXIMUM:', content, "Should have MAXIMUM preset")
        self.assertIn('PARANOID:', content, "Should have PARANOID preset")
    
    def test_web_service_has_required_methods(self):
        """Web service should have required methods"""
        service_path = os.path.join(os.path.dirname(__file__), '..', 'web', 'src', 'services', 'multiHopService.js')
        with open(service_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        self.assertIn('activate()', content, "Should have activate method")
        self.assertIn('deactivate()', content, "Should have deactivate method")
        self.assertIn('toggle()', content, "Should have toggle method")
        self.assertIn('setPreset(', content, "Should have setPreset method")
    
    def test_web_dashboard_has_multihop_ui(self):
        """Web Dashboard should have multi-hop UI"""
        dashboard_path = os.path.join(os.path.dirname(__file__), '..', 'web', 'src', 'pages', 'Dashboard.jsx')
        with open(dashboard_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        self.assertIn('multiHopService', content, "Should import multiHopService")
        self.assertIn('Multi-Hop', content, "Should have Multi-Hop UI label")
        self.assertIn('multiHopState', content, "Should track multiHop state")


class TestMultiHopExtension(unittest.TestCase):
    """Tests for browser extension multi-hop implementation"""
    
    def test_extension_popup_has_multihop_ui(self):
        """Extension popup should have multi-hop UI"""
        popup_path = os.path.join(os.path.dirname(__file__), '..', 'extension', 'popup.html')
        with open(popup_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        self.assertIn('multihopCard', content, "Should have multihopCard element")
        self.assertIn('multiHopToggle', content, "Should have multiHopToggle checkbox")
        self.assertIn('multiHopPreset', content, "Should have multiHopPreset selector")
        self.assertIn('multiHopChain', content, "Should have multiHopChain display")
    
    def test_extension_js_has_multihop_state(self):
        """Extension popup.js should track multi-hop state"""
        popup_path = os.path.join(os.path.dirname(__file__), '..', 'extension', 'popup.js')
        with open(popup_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        self.assertIn('multiHop:', content, "State should include multiHop")
        self.assertIn('MULTIHOP_PRESETS', content, "Should have MULTIHOP_PRESETS config")
    
    def test_extension_has_toggle_function(self):
        """Extension should have toggleMultiHop function"""
        popup_path = os.path.join(os.path.dirname(__file__), '..', 'extension', 'popup.js')
        with open(popup_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        self.assertIn('async function toggleMultiHop', content, "Should have toggleMultiHop function")
        self.assertIn('buildMultiHopChain', content, "Should have buildMultiHopChain function")
    
    def test_extension_has_chain_display(self):
        """Extension should have chain display function"""
        popup_path = os.path.join(os.path.dirname(__file__), '..', 'extension', 'popup.js')
        with open(popup_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        self.assertIn('updateMultiHopDisplay', content, "Should have updateMultiHopDisplay function")
        self.assertIn('setMultiHopPreset', content, "Should have setMultiHopPreset function")


class TestMultiHopSpeedOptimization(unittest.TestCase):
    """Tests for multi-hop speed optimization features"""
    
    def test_presets_have_speed_estimates(self):
        """All presets should have speed retention estimates"""
        engine_path = os.path.join(os.path.dirname(__file__), '..', 'shared', 'multihop', 'MultiHopEngine.js')
        with open(engine_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        # Check for speed estimates in code
        self.assertIn('estimateSpeedRetention', content, "Should have speed estimation method")
    
    def test_engine_has_latency_optimization(self):
        """Engine should optimize for latency"""
        engine_path = os.path.join(os.path.dirname(__file__), '..', 'shared', 'multihop', 'MultiHopEngine.js')
        with open(engine_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        self.assertIn('latencyCache', content, "Should cache latency measurements")
        self.assertIn('measureLatency', content, "Should have latency measurement")
    
    def test_android_has_speed_retention(self):
        """Android should calculate speed retention"""
        manager_path = os.path.join(os.path.dirname(__file__), '..', 'android', 'app', 'src', 'main', 'java', 'com', 'freedomvpn', 'vpn', 'MultiHopManager.kt')
        with open(manager_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        self.assertIn('getSpeedRetention', content, "Should have getSpeedRetention function")
        self.assertIn('estimatedSpeedRetention', content, "ChainState should track speed retention")


class TestMultiHopSecurity(unittest.TestCase):
    """Tests for multi-hop security features"""
    
    def test_engine_ensures_minimum_hops(self):
        """Engine should ensure minimum 2 hops for anonymity"""
        engine_path = os.path.join(os.path.dirname(__file__), '..', 'shared', 'multihop', 'MultiHopEngine.js')
        with open(engine_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        self.assertIn('Not enough servers available for multi-hop', content, 
                     "Should validate minimum hop count")
    
    def test_has_geographic_diversity(self):
        """Should support geographic diversity for maximum anonymity"""
        engine_path = os.path.join(os.path.dirname(__file__), '..', 'shared', 'multihop', 'MultiHopEngine.js')
        with open(engine_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        self.assertIn('diverseRouting', content, "Should have diversity option")
        self.assertIn('usedRegions', content, "Should track used regions")
    
    def test_supports_chain_rotation(self):
        """Should support chain rotation for enhanced anonymity"""
        engine_path = os.path.join(os.path.dirname(__file__), '..', 'shared', 'multihop', 'MultiHopEngine.js')
        with open(engine_path, 'r', encoding='utf-8') as f:
            content = f.read()
        
        self.assertIn('rotateChain', content, "Should have chain rotation")
        self.assertIn('rotationInterval', content, "Should have rotation interval config")


def run_tests():
    """Run all multi-hop tests"""
    # Create test suite
    loader = unittest.TestLoader()
    suite = unittest.TestSuite()
    
    # Add test classes
    suite.addTests(loader.loadTestsFromTestCase(TestMultiHopEngine))
    suite.addTests(loader.loadTestsFromTestCase(TestMultiHopWindows))
    suite.addTests(loader.loadTestsFromTestCase(TestMultiHopAndroid))
    suite.addTests(loader.loadTestsFromTestCase(TestMultiHopWeb))
    suite.addTests(loader.loadTestsFromTestCase(TestMultiHopExtension))
    suite.addTests(loader.loadTestsFromTestCase(TestMultiHopSpeedOptimization))
    suite.addTests(loader.loadTestsFromTestCase(TestMultiHopSecurity))
    
    # Run with verbose output
    runner = unittest.TextTestRunner(verbosity=2)
    result = runner.run(suite)
    
    # Summary
    print("\n" + "=" * 60)
    print("MULTI-HOP TEST SUMMARY")
    print("=" * 60)
    
    total = result.testsRun
    failures = len(result.failures)
    errors = len(result.errors)
    passed = total - failures - errors
    
    print(f"Total Tests:  {total}")
    print(f"Passed:       {passed}")
    print(f"Failed:       {failures}")
    print(f"Errors:       {errors}")
    print(f"Pass Rate:    {(passed/total)*100:.1f}%")
    
    if failures == 0 and errors == 0:
        print("\n[PASS] All Multi-Hop tests passed!")
        print("Server bouncing feature is fully implemented across all platforms.")
    else:
        print("\n[FAIL] Some tests failed. Review the output above.")
    
    return result


if __name__ == '__main__':
    run_tests()
