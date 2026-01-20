/**
 * FreedomVPN - Cross-Platform Geolocation Service
 * 
 * Provides accurate location detection across:
 * - Web browsers (Navigator Geolocation API)
 * - Electron desktop apps
 * - Browser extensions
 * 
 * Features:
 * - High accuracy GPS/network location
 * - Permission handling
 * - Fallback to IP geolocation
 * - Distance calculations
 */

// IP-based geolocation fallback services
const GEO_IP_SERVICES = [
  { url: 'https://ipwho.is/', parser: (data) => ({ lat: data.latitude, lon: data.longitude, city: data.city, country: data.country, countryCode: data.country_code, accuracy: 10000 }) },
  { url: 'https://ipapi.co/json/', parser: (data) => ({ lat: data.latitude, lon: data.longitude, city: data.city, country: data.country_name, countryCode: data.country_code, accuracy: 10000 }) },
  { url: 'https://ip-api.com/json/', parser: (data) => ({ lat: data.lat, lon: data.lon, city: data.city, country: data.country, countryCode: data.countryCode, accuracy: 10000 }) },
];

/**
 * User location data
 */
class UserLocation {
  constructor(data) {
    this.latitude = data.lat || data.latitude || 0;
    this.longitude = data.lon || data.longitude || 0;
    this.accuracy = data.accuracy || 0; // meters
    this.altitude = data.altitude || null;
    this.altitudeAccuracy = data.altitudeAccuracy || null;
    this.heading = data.heading || null;
    this.speed = data.speed || null;
    this.city = data.city || 'Unknown';
    this.country = data.country || 'Unknown';
    this.countryCode = data.countryCode || 'XX';
    this.source = data.source || 'unknown'; // 'gps', 'network', 'ip', 'manual'
    this.timestamp = data.timestamp || Date.now();
  }

  get isHighAccuracy() {
    return this.accuracy < 100; // Less than 100m is considered high accuracy
  }

  get isMediumAccuracy() {
    return this.accuracy >= 100 && this.accuracy < 1000;
  }

  get isLowAccuracy() {
    return this.accuracy >= 1000;
  }

  get accuracyDescription() {
    if (this.accuracy < 10) return 'Precise (GPS)';
    if (this.accuracy < 100) return 'High (GPS/Network)';
    if (this.accuracy < 1000) return 'Medium (Network)';
    if (this.accuracy < 10000) return 'Low (Cell Tower)';
    return 'Approximate (IP-based)';
  }

  distanceTo(lat, lon) {
    // Haversine formula
    const R = 6371; // Earth's radius in km
    const dLat = (lat - this.latitude) * Math.PI / 180;
    const dLon = (lon - this.longitude) * Math.PI / 180;
    const a = Math.sin(dLat/2) * Math.sin(dLat/2) +
              Math.cos(this.latitude * Math.PI / 180) * Math.cos(lat * Math.PI / 180) *
              Math.sin(dLon/2) * Math.sin(dLon/2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
    return R * c;
  }

  toJSON() {
    return {
      latitude: this.latitude,
      longitude: this.longitude,
      accuracy: this.accuracy,
      accuracyDescription: this.accuracyDescription,
      city: this.city,
      country: this.country,
      countryCode: this.countryCode,
      source: this.source,
      timestamp: this.timestamp
    };
  }
}

/**
 * Permission status
 */
const PermissionStatus = {
  GRANTED: 'granted',
  DENIED: 'denied',
  PROMPT: 'prompt',
  UNAVAILABLE: 'unavailable'
};

/**
 * Cross-Platform Geolocation Service
 */
class GeolocationService {
  constructor() {
    this.currentLocation = null;
    this.watchId = null;
    this.listeners = new Set();
    this.permissionStatus = PermissionStatus.PROMPT;
  }

  /**
   * Check if geolocation is available
   */
  isAvailable() {
    if (typeof navigator !== 'undefined' && navigator.geolocation) {
      return true;
    }
    return false;
  }

  /**
   * Check permission status
   */
  async checkPermission() {
    try {
      if (typeof navigator !== 'undefined' && navigator.permissions) {
        const result = await navigator.permissions.query({ name: 'geolocation' });
        this.permissionStatus = result.state;
        
        // Listen for permission changes
        result.onchange = () => {
          this.permissionStatus = result.state;
          this.notifyListeners({ type: 'permission', status: result.state });
        };
        
        return result.state;
      }
    } catch (e) {
      // Permissions API not supported
    }
    return PermissionStatus.PROMPT;
  }

  /**
   * Request location permission (triggers browser prompt)
   */
  async requestPermission() {
    try {
      // Requesting current position triggers the permission prompt
      await this.getCurrentLocation({ timeout: 5000 });
      this.permissionStatus = PermissionStatus.GRANTED;
      return PermissionStatus.GRANTED;
    } catch (error) {
      if (error.code === 1) { // PERMISSION_DENIED
        this.permissionStatus = PermissionStatus.DENIED;
        return PermissionStatus.DENIED;
      }
      return PermissionStatus.PROMPT;
    }
  }

  /**
   * Get current location with high accuracy
   * @param {Object} options - Location options
   * @returns {Promise<UserLocation>}
   */
  async getCurrentLocation(options = {}) {
    const defaultOptions = {
      enableHighAccuracy: true,
      timeout: 15000,
      maximumAge: 0
    };
    const opts = { ...defaultOptions, ...options };

    // Try browser geolocation first
    if (this.isAvailable()) {
      try {
        const position = await this.getBrowserLocation(opts);
        this.currentLocation = position;
        return position;
      } catch (error) {
        console.warn('[GeolocationService] Browser location failed:', error.message);
        // Fall through to IP geolocation
      }
    }

    // Fallback to IP geolocation
    console.log('[GeolocationService] Falling back to IP geolocation');
    const ipLocation = await this.getIPLocation();
    this.currentLocation = ipLocation;
    return ipLocation;
  }

  /**
   * Get location from browser Geolocation API
   */
  getBrowserLocation(options) {
    return new Promise((resolve, reject) => {
      navigator.geolocation.getCurrentPosition(
        async (position) => {
          const coords = position.coords;
          
          // Try to get city/country from reverse geocoding
          let cityCountry = { city: 'Unknown', country: 'Unknown', countryCode: 'XX' };
          try {
            cityCountry = await this.reverseGeocode(coords.latitude, coords.longitude);
          } catch (e) {
            // Ignore reverse geocoding errors
          }

          const location = new UserLocation({
            lat: coords.latitude,
            lon: coords.longitude,
            accuracy: coords.accuracy,
            altitude: coords.altitude,
            altitudeAccuracy: coords.altitudeAccuracy,
            heading: coords.heading,
            speed: coords.speed,
            city: cityCountry.city,
            country: cityCountry.country,
            countryCode: cityCountry.countryCode,
            source: coords.accuracy < 100 ? 'gps' : 'network',
            timestamp: position.timestamp
          });

          this.permissionStatus = PermissionStatus.GRANTED;
          resolve(location);
        },
        (error) => {
          if (error.code === 1) {
            this.permissionStatus = PermissionStatus.DENIED;
          }
          reject(error);
        },
        options
      );
    });
  }

  /**
   * Get location from IP address
   */
  async getIPLocation() {
    for (const service of GEO_IP_SERVICES) {
      try {
        const controller = new AbortController();
        const timeout = setTimeout(() => controller.abort(), 5000);
        
        const response = await fetch(service.url, { signal: controller.signal });
        clearTimeout(timeout);
        
        if (response.ok) {
          const data = await response.json();
          const parsed = service.parser(data);
          
          return new UserLocation({
            ...parsed,
            source: 'ip'
          });
        }
      } catch (e) {
        continue;
      }
    }
    
    // Return default location (Uganda) if all services fail
    return new UserLocation({
      lat: 0.3476,
      lon: 32.5825,
      accuracy: 100000,
      city: 'Kampala',
      country: 'Uganda',
      countryCode: 'UG',
      source: 'default'
    });
  }

  /**
   * Reverse geocode coordinates to city/country
   */
  async reverseGeocode(lat, lon) {
    try {
      const response = await fetch(
        `https://nominatim.openstreetmap.org/reverse?lat=${lat}&lon=${lon}&format=json`,
        { headers: { 'User-Agent': 'FreedomVPN/1.0' } }
      );
      
      if (response.ok) {
        const data = await response.json();
        return {
          city: data.address?.city || data.address?.town || data.address?.village || 'Unknown',
          country: data.address?.country || 'Unknown',
          countryCode: data.address?.country_code?.toUpperCase() || 'XX'
        };
      }
    } catch (e) {
      // Ignore errors
    }
    return { city: 'Unknown', country: 'Unknown', countryCode: 'XX' };
  }

  /**
   * Watch location changes
   */
  watchPosition(callback, options = {}) {
    if (!this.isAvailable()) {
      console.warn('[GeolocationService] Watch not available, using polling');
      return this.pollPosition(callback, options);
    }

    const defaultOptions = {
      enableHighAccuracy: true,
      timeout: 30000,
      maximumAge: 1000
    };
    const opts = { ...defaultOptions, ...options };

    this.watchId = navigator.geolocation.watchPosition(
      async (position) => {
        const coords = position.coords;
        
        let cityCountry = { city: 'Unknown', country: 'Unknown', countryCode: 'XX' };
        try {
          cityCountry = await this.reverseGeocode(coords.latitude, coords.longitude);
        } catch (e) {}

        const location = new UserLocation({
          lat: coords.latitude,
          lon: coords.longitude,
          accuracy: coords.accuracy,
          altitude: coords.altitude,
          altitudeAccuracy: coords.altitudeAccuracy,
          heading: coords.heading,
          speed: coords.speed,
          city: cityCountry.city,
          country: cityCountry.country,
          countryCode: cityCountry.countryCode,
          source: coords.accuracy < 100 ? 'gps' : 'network',
          timestamp: position.timestamp
        });

        this.currentLocation = location;
        callback(location);
      },
      (error) => {
        callback(null, error);
      },
      opts
    );

    return this.watchId;
  }

  /**
   * Poll position for platforms without watchPosition
   */
  pollPosition(callback, options = {}) {
    const interval = options.pollInterval || 10000;
    
    const poll = async () => {
      try {
        const location = await this.getCurrentLocation(options);
        callback(location);
      } catch (error) {
        callback(null, error);
      }
    };

    poll(); // Initial poll
    this.pollInterval = setInterval(poll, interval);
    return this.pollInterval;
  }

  /**
   * Stop watching position
   */
  clearWatch() {
    if (this.watchId !== null && this.isAvailable()) {
      navigator.geolocation.clearWatch(this.watchId);
      this.watchId = null;
    }
    if (this.pollInterval) {
      clearInterval(this.pollInterval);
      this.pollInterval = null;
    }
  }

  /**
   * Add location change listener
   */
  addListener(callback) {
    this.listeners.add(callback);
    return () => this.listeners.delete(callback);
  }

  /**
   * Notify all listeners
   */
  notifyListeners(event) {
    this.listeners.forEach(callback => {
      try {
        callback(event);
      } catch (e) {
        console.error('[GeolocationService] Listener error:', e);
      }
    });
  }

  /**
   * Get cached location
   */
  getCachedLocation() {
    return this.currentLocation;
  }

  /**
   * Check if user is in a high-risk region
   */
  isHighRiskRegion(countryCode) {
    const highRiskCountries = [
      'UG', // Uganda
      'CN', // China
      'IR', // Iran
      'RU', // Russia
      'BY', // Belarus
      'KP', // North Korea
      'SY', // Syria
      'VE', // Venezuela
      'CU', // Cuba
      'TR', // Turkey
      'EG', // Egypt
      'SA', // Saudi Arabia
      'AE', // UAE
      'PK', // Pakistan
      'VN', // Vietnam
      'TH', // Thailand
      'MM', // Myanmar
      'BD'  // Bangladesh
    ];
    return highRiskCountries.includes(countryCode?.toUpperCase());
  }
}

// Singleton instance
const geolocationService = new GeolocationService();

// Export for different environments
if (typeof module !== 'undefined' && module.exports) {
  module.exports = { GeolocationService, UserLocation, PermissionStatus, geolocationService };
}

if (typeof window !== 'undefined') {
  window.GeolocationService = GeolocationService;
  window.UserLocation = UserLocation;
  window.geolocationService = geolocationService;
}

export { GeolocationService, UserLocation, PermissionStatus, geolocationService };
