'use strict';
const crypto = require('crypto');
const os = require('os');
const startedAt = process.hrtime.bigint();
const requests = new Map();
const buckets = [0.005,0.01,0.025,0.05,0.1,0.25,0.5,1,2.5,5,10];

function observe(method, route, status, seconds) {
  const k = JSON.stringify([method, route, status]);
  const v = requests.get(k) || { count: 0, sum: 0, bucket: new Array(buckets.length + 1).fill(0) };
  v.count += 1; v.sum += seconds;
  let i = buckets.findIndex(b => seconds <= b); if (i < 0) i = buckets.length;
  v.bucket[i] += 1; requests.set(k, v);
}
function middleware(req, res, next) {
  const start = process.hrtime.bigint();
  res.on('finish', () => {
    const seconds = Number(process.hrtime.bigint() - start) / 1e9;
    const route = req.route?.path ? (req.baseUrl || '') + req.route.path : 'unmatched';
    observe(req.method, route, res.statusCode, seconds);
  });
  next();
}
function esc(v) { return String(v).replace(/\\/g,'\\\\').replace(/"/g,'\\"').replace(/\n/g,'\\n'); }
function authorized(req) {
  const expected = process.env.METRICS_TOKEN; if (!expected) return false;
  const supplied = (req.get('authorization') || '').replace(/^Bearer\s+/i,'');
  const a = Buffer.from(supplied), b = Buffer.from(expected);
  return a.length === b.length && crypto.timingSafeEqual(a,b);
}
function render(db) {
  const mem = process.memoryUsage(), load = os.loadavg()[0] / Math.max(os.cpus().length,1);
  let peers = 0; try { peers = db.prepare('SELECT COUNT(*) AS count FROM peers').get().count; } catch {}
  const lines = [
    '# HELP freedomvpn_process_uptime_seconds Process uptime in seconds.',
    '# TYPE freedomvpn_process_uptime_seconds gauge',
    'freedomvpn_process_uptime_seconds ' + (Number(process.hrtime.bigint()-startedAt)/1e9),
    '# HELP freedomvpn_process_resident_memory_bytes Resident process memory in bytes.',
    '# TYPE freedomvpn_process_resident_memory_bytes gauge',
    'freedomvpn_process_resident_memory_bytes ' + mem.rss,
    '# HELP freedomvpn_nodejs_heap_used_bytes Node.js heap currently used in bytes.',
    '# TYPE freedomvpn_nodejs_heap_used_bytes gauge',
    'freedomvpn_nodejs_heap_used_bytes ' + mem.heapUsed,
    '# HELP freedomvpn_cpu_load_ratio One-minute host load divided by CPU count.',
    '# TYPE freedomvpn_cpu_load_ratio gauge',
    'freedomvpn_cpu_load_ratio ' + load,
    '# HELP freedomvpn_peers_total Current registered peer count.',
    '# TYPE freedomvpn_peers_total gauge',
    'freedomvpn_peers_total ' + peers,
    '# HELP freedomvpn_http_requests_total Total HTTP requests.',
    '# TYPE freedomvpn_http_requests_total counter',
    '# HELP freedomvpn_http_request_duration_seconds HTTP request duration in seconds.',
    '# TYPE freedomvpn_http_request_duration_seconds histogram'
  ];
  for (const [raw,v] of requests) {
    const x = JSON.parse(raw), labels = 'method="' + esc(x[0]) + '",route="' + esc(x[1]) + '",status="' + esc(x[2]) + '"';
    lines.push('freedomvpn_http_requests_total{' + labels + '} ' + v.count);
    let cumulative = 0;
    for (let i=buckets.length-1;i>=0;i--) {
      cumulative += v.bucket[i];
      lines.push('freedomvpn_http_request_duration_seconds_bucket{' + labels + ',le="' + buckets[i] + '"} ' + (v.count-cumulative));
    }
    lines.push('freedomvpn_http_request_duration_seconds_bucket{' + labels + ',le="+Inf"} ' + v.count);
    lines.push('freedomvpn_http_request_duration_seconds_sum{' + labels + '} ' + v.sum);
    lines.push('freedomvpn_http_request_duration_seconds_count{' + labels + '} ' + v.count);
  }
  return lines.join('\n') + '\n';
}
function router(db) {
  return (req,res) => {
    if (!authorized(req)) return res.status(process.env.METRICS_TOKEN ? 401 : 404).end();
    res.type('text/plain; version=0.0.4').send(render(db));
  };
}
module.exports = { middleware, router, render };
