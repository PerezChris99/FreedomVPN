#!/usr/bin/env python3
"""Dependency-free health load probe; never mutates peer state."""
import concurrent.futures, statistics, sys, time, urllib.request
def one(base):
    start=time.perf_counter()
    try:
        with urllib.request.urlopen(base.rstrip('/') + '/api/health', timeout=10) as r:
            r.read(); return r.status, time.perf_counter()-start
    except Exception:
        return 0, time.perf_counter()-start
def main():
    if len(sys.argv) not in (2,3,4): raise SystemExit('usage: api_load.py BASE_URL [REQUESTS=100] [CONCURRENCY=10]')
    base=sys.argv[1]; total=int(sys.argv[2]) if len(sys.argv)>2 else 100; workers=int(sys.argv[3]) if len(sys.argv)>3 else 10
    start=time.perf_counter()
    with concurrent.futures.ThreadPoolExecutor(max_workers=workers) as pool: results=list(pool.map(one,[base]*total))
    elapsed=time.perf_counter()-start; ok=[x for s,x in results if 200 <= s < 300]; errors=total-len(ok)
    print(f'requests={total} concurrency={workers} elapsed_seconds={elapsed:.3f} rps={total/elapsed:.2f} errors={errors}')
    if ok: print(f'latency_ms_p50={statistics.median(ok)*1000:.2f} latency_ms_max={max(ok)*1000:.2f}')
    if errors: raise SystemExit(1)
if __name__ == '__main__': main()
