// IP Address detection service
// Uses multiple free IP APIs with fallback

const IP_APIS = [
  'https://api.ipify.org?format=json',
  'https://ipapi.co/json/',
  'https://ip-api.com/json/',
]

export async function getPublicIP() {
  for (const api of IP_APIS) {
    try {
      const response = await fetch(api, { 
        timeout: 5000,
        cache: 'no-store' 
      })
      const data = await response.json()
      
      // Different APIs return IP in different fields
      const ip = data.ip || data.query
      if (ip) {
        return {
          ip,
          country: data.country || data.country_name || 'Unknown',
          city: data.city || 'Unknown',
          region: data.region || data.regionName || 'Unknown',
          isp: data.org || data.isp || 'Unknown',
          countryCode: data.country_code || data.countryCode || '',
        }
      }
    } catch (error) {
      console.warn(`Failed to fetch from ${api}:`, error)
      continue
    }
  }
  
  return {
    ip: 'Unable to detect',
    country: 'Unknown',
    city: 'Unknown',
    region: 'Unknown',
    isp: 'Unknown',
    countryCode: '',
  }
}

// Get country flag emoji from country code
export function getCountryFlag(countryCode) {
  if (!countryCode || countryCode.length !== 2) return '🌍'
  
  const codePoints = countryCode
    .toUpperCase()
    .split('')
    .map(char => 127397 + char.charCodeAt(0))
  
  return String.fromCodePoint(...codePoints)
}
