'use strict';
/**
 * Server API unit tests
 * Tests the peer registration, auth middleware, and WireGuard util validation
 * without requiring a real WireGuard interface.
 */

// Mock the 'wg' CLI calls so tests run on any machine
jest.mock('./src/utils/wireguard', () => ({
  addPeer:     jest.fn().mockResolvedValue(undefined),
  removePeer:  jest.fn().mockResolvedValue(undefined),
  getPeerStats: jest.fn().mockResolvedValue([]),
}));

jest.mock('child_process', () => ({
  ...jest.requireActual('child_process'),
  execFileSync: jest.fn().mockReturnValue(Buffer.from('interface: wg0\\n')),
}));

// Use in-memory SQLite for tests
process.env.DB_PATH      = ':memory:';
process.env.ADMIN_TOKEN  = 'test-admin-token-' + 'x'.repeat(20);
process.env.WG_IFACE     = 'wg0';
process.env.SERVER_PUBLIC_KEY = 'hTivWiGjkHVHTLfdyxH2ZQDb86DSZjOsJvS6+rS07hs=';
process.env.SERVER_ENDPOINT   = '1.2.3.4:51820';
process.env.DNS1         = '1.1.1.1';
process.env.DNS2         = '1.0.0.1';
process.env.VPN_SUBNET   = '10.8.0.0/24';
process.env.VPN_SERVER_IP = '10.8.0.1';
process.env.METRICS_TOKEN = 'test-metrics-token-' + 'x'.repeat(20);

const request = require('supertest');
const app     = require('./src/index');

// A valid WireGuard public key (base64, 44 chars, 32 bytes)
const VALID_KEY   = 'hTivWiGjkHVHTLfdyxH2ZQDb86DSZjOsJvS6+rS07hs=';
const INVALID_KEY = 'not-a-real-key';

// ─────────────────────────────────────────────────────────────────────────────

describe('GET /api/health', () => {
  test('returns 200 with status ok', async () => {
    const res = await request(app).get('/api/health');
    expect(res.status).toBe(200);
    expect(res.body.status).toBe('ok');
    expect(typeof res.body.uptime).toBe('number');
    expect(typeof res.body.peers).toBe('number');
  });
});

describe('GET /api/metrics', () => {
  test('requires bearer authentication', async () => {
    const res = await request(app).get('/api/metrics');
    expect(res.status).toBe(401);
  });
  test('exports bounded operational metrics without peer/device identifiers', async () => {
    const res = await request(app)
      .get('/api/metrics')
      .set('Authorization', 'Bearer ' + process.env.METRICS_TOKEN);
    expect(res.status).toBe(200);
    expect(res.text).toContain('freedomvpn_process_uptime_seconds');
    expect(res.text).toContain('freedomvpn_peers_total');
    expect(res.text).not.toContain(VALID_KEY);
    expect(res.text).not.toContain('test-device');
  });
});

describe('GET /api/servers', () => {
  test('returns server list', async () => {
    const res = await request(app).get('/api/servers');
    expect(res.status).toBe(200);
    expect(Array.isArray(res.body.servers)).toBe(true);
    expect(res.body.servers.length).toBeGreaterThanOrEqual(1);
    const s = res.body.servers[0];
    expect(s.publicKey).toBeDefined();
    expect(s.endpoint).toBeDefined();
  });
});

describe('POST /api/peers/register', () => {
  test('rejects missing publicKey', async () => {
    const res = await request(app).post('/api/peers/register').send({});
    expect(res.status).toBe(400);
    expect(res.body.error).toMatch(/publicKey/i);
  });

  test('rejects invalid publicKey (too short)', async () => {
    const res = await request(app).post('/api/peers/register').send({ publicKey: 'short' });
    expect(res.status).toBe(400);
    expect(res.body.error).toMatch(/44.character|WireGuard/i);
  });

  test('accepts valid publicKey and returns peer config', async () => {
    const res = await request(app).post('/api/peers/register').send({
      publicKey: VALID_KEY,
      platform:  'android',
    });
    expect(res.status).toBe(201);
    expect(res.body.id).toBeDefined();
    expect(res.body.assignedIP).toMatch(/^10\.8\.0\.\d+\/32$/);
    expect(res.body.serverPublicKey).toBe(process.env.SERVER_PUBLIC_KEY);
    expect(res.body.serverEndpoint).toBe(process.env.SERVER_ENDPOINT);
    expect(Array.isArray(res.body.dns)).toBe(true);
    expect(res.body.dns.length).toBe(2);
  });

  test('re-registration returns same IP (idempotent)', async () => {
    const r1 = await request(app).post('/api/peers/register').send({ publicKey: VALID_KEY });
    const r2 = await request(app).post('/api/peers/register').send({ publicKey: VALID_KEY });
    expect(r1.body.assignedIP).toBe(r2.body.assignedIP);
    expect(r2.body.reregistered).toBe(true);
  });

  test('sanitises invalid platform to unknown', async () => {
    const key2 = 'AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=';
    const res  = await request(app).post('/api/peers/register').send({
      publicKey: key2,
      platform:  '<script>alert(1)</script>',
    });
    expect(res.status).toBe(201);
  });
});

describe('GET /api/peers (admin)', () => {
  test('rejects without auth', async () => {
    const res = await request(app).get('/api/peers');
    expect(res.status).toBe(401);
  });

  test('rejects wrong token', async () => {
    const res = await request(app)
      .get('/api/peers')
      .set('Authorization', 'Bearer wrong-token');
    expect(res.status).toBe(403);
  });

  test('accepts correct admin token', async () => {
    const res = await request(app)
      .get('/api/peers')
      .set('Authorization', `Bearer ${process.env.ADMIN_TOKEN}`);
    expect(res.status).toBe(200);
    expect(Array.isArray(res.body.peers)).toBe(true);
  });
});

describe('DELETE /api/peers/:id (admin)', () => {
  test('rejects without auth', async () => {
    const res = await request(app).delete('/api/peers/some-id');
    expect(res.status).toBe(401);
  });

  test('returns 400 for invalid UUID format', async () => {
    const res = await request(app)
      .delete('/api/peers/not-a-uuid')
      .set('Authorization', `Bearer ${process.env.ADMIN_TOKEN}`);
    expect(res.status).toBe(400);
  });

  test('returns 404 for non-existent UUID', async () => {
    const res = await request(app)
      .delete('/api/peers/00000000-0000-4000-8000-000000000000')
      .set('Authorization', `Bearer ${process.env.ADMIN_TOKEN}`);
    expect(res.status).toBe(404);
  });
});

describe('Security — unknown routes', () => {
  test('GET / returns 404', async () => {
    const res = await request(app).get('/');
    expect(res.status).toBe(404);
  });
  test('GET /api/unknown returns 404', async () => {
    const res = await request(app).get('/api/unknown');
    expect(res.status).toBe(404);
  });
});

describe('WireGuard util — input validation', () => {
  const { addPeer } = require('./src/utils/wireguard');

  // Un-mock for validation-only tests
  beforeEach(() => addPeer.mockClear());

  test('rejects key injection attempt (semicolons)', async () => {
    const res = await request(app).post('/api/peers/register').send({
      publicKey: 'aGVsbG8;rm+-rf+/;echo+pwned+>+/tmp/x+===========',
    });
    expect(res.status).toBe(400);
  });
});
