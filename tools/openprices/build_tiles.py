#!/usr/bin/env python3
import argparse, collections, datetime as dt, gzip, hashlib, json, math, os, sys, time, urllib.request
from pathlib import Path
BASE = "https://prices.openfoodfacts.org"
UA = {"User-Agent": "Amni-Price price-pack builder (https://github.com/Amnibro/Amni-Price; amnibro7@gmail.com)"}
TILE_DEG = 2
def fetch(url, dest=None, retries=4):
    for i in range(retries):
        try:
            with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=120) as r:
                if dest is None: return r.read()
                tmp = Path(str(dest) + ".part"); tmp.write_bytes(r.read()); tmp.replace(dest); return dest
        except Exception as e:
            if i == retries - 1: raise
            time.sleep(min(120, 10 * 2 ** i))
def products(cache, max_age_days):
    p, part = cache / "products.json.gz", cache / "products.partial.json"
    if p.exists() and time.time() - p.stat().st_mtime < max_age_days * 86400: return json.load(gzip.open(p, "rt"))
    state = json.loads(part.read_text()) if part.exists() else {"done": [], "items": {}, "failed": 0}
    out, done = state["items"], set(state["done"])
    def page(q, order, n):
        d = json.loads(fetch(f"{BASE}/api/v1/products?size=100&{q}&order_by={order}&page={n}", retries=3))
        out.update({x["code"]: [x.get("product_name") or "", x.get("brands") or "", x.get("quantity") or ""] for x in d.get("items", []) if x.get("code")})
        return d.get("pages") or 0
    for q in ("price_count=1", "price_count=2", "price_count=3", "price_count__gte=4"):
        pages = page(q, "id", 1)
        plan = [("id", n) for n in range(2, min(pages, 500) + 1)] + [("-id", n) for n in range(1, max(0, pages - 500) + 2)]
        for order, n in plan:
            key = f"{q}|{order}|{n}"
            if key in done: continue
            try: page(q, order, n)
            except Exception as e: state["failed"] += 1; print(f"products {key} skipped: {e}", file=sys.stderr)
            done.add(key); state["done"] = sorted(done)
            if len(done) % 25 == 0: part.write_text(json.dumps(state))
            time.sleep(1.0)
    with gzip.open(p, "wt") as f: json.dump(out, f)
    part.unlink(missing_ok=True)
    print(f"products: {len(out)} codes, {state['failed']} pages skipped", file=sys.stderr)
    return out
def tile_of(lat, lon): return f"{math.floor(lat / TILE_DEG) * TILE_DEG}_{math.floor(lon / TILE_DEG) * TILE_DEG}"
def build(cache, out, days, per_pair, product_age):
    cache.mkdir(parents=True, exist_ok=True); out.mkdir(parents=True, exist_ok=True)
    for n in ("prices", "locations"): fetch(f"{BASE}/data/{n}.jsonl.gz", cache / f"{n}.jsonl.gz")
    locs = {}
    for l in map(json.loads, gzip.open(cache / "locations.jsonl.gz", "rt")):
        try: l["osm_lat"], l["osm_lon"] = float(l["osm_lat"]), float(l["osm_lon"])
        except (KeyError, TypeError, ValueError): continue
        if l.get("type") == "OSM" and -90 <= l["osm_lat"] <= 90 and -180 <= l["osm_lon"] <= 180: locs[l["id"]] = l
    names = products(cache, product_age)
    cutoff = (dt.date.today() - dt.timedelta(days=days)).isoformat()
    pairs = collections.defaultdict(list)
    for p in map(json.loads, gzip.open(cache / "prices.jsonl.gz", "rt")):
        if p.get("type") != "PRODUCT" or p.get("duplicate_of") or not p.get("product_code") or not p.get("date") or p["date"] < cutoff or p.get("location_id") not in locs or p.get("price") in (None, ""): continue
        cents = round(float(p["price"]) * 100)
        if cents <= 0 or cents > 10_000_00: continue
        pairs[(p["location_id"], p["product_code"])].append((p["date"], cents, bool(p.get("price_is_discounted")), p.get("currency") or "", p.get("product_name") or ""))
    tiles = collections.defaultdict(lambda: {"stores": {}, "products": {}, "prices": []})
    for (lid, code), obs in pairs.items():
        l = locs[lid]; t = tiles[tile_of(l["osm_lat"], l["osm_lon"])]
        si = t["stores"].setdefault(lid, len(t["stores"]))
        n = names.get(code, ["", "", ""]); pn = n[0] or next((o[4] for o in sorted(obs, reverse=True) if o[4]), "")
        pi = t["products"].setdefault(code, (len(t["products"]), pn, n[1], n[2]))[0]
        for d, c, s, cur, _ in sorted(set(obs), reverse=True)[:per_pair]: t["prices"].append([si, pi, c, d, 1 if s else 0, cur])
    generated = dt.datetime.now(dt.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    index = {"v": 1, "generated": generated, "tileDegrees": TILE_DEG, "source": "Open Prices (prices.openfoodfacts.org), Open Food Facts contributors", "license": "ODbL-1.0", "tiles": {}}
    for key, t in sorted(tiles.items()):
        stores = [[f"osm:{locs[lid]['osm_type'].lower()}:{locs[lid]['osm_id']}", locs[lid].get("osm_name") or locs[lid].get("osm_brand") or "Store", locs[lid].get("osm_address_city") or "", round(locs[lid]["osm_lat"], 6), round(locs[lid]["osm_lon"], 6)] for lid, _ in sorted(t["stores"].items(), key=lambda kv: kv[1])]
        prods = [[code, v[1], v[2], v[3]] for code, v in sorted(t["products"].items(), key=lambda kv: kv[1][0])]
        body = json.dumps({"v": 1, "tile": key, "generated": generated, "stores": stores, "products": prods, "prices": t["prices"]}, separators=(",", ":"), ensure_ascii=False).encode()
        data = gzip.compress(body, mtime=0, compresslevel=9)
        (out / f"tile_{key}.json.gz").write_bytes(data)
        index["tiles"][key] = {"stores": len(stores), "products": len(prods), "prices": len(t["prices"]), "bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()}
    for f in out.glob("tile_*.json.gz"):
        if f.name[5:-8] not in index["tiles"]: f.unlink()
    (out / "index.json").write_text(json.dumps(index, separators=(",", ":")))
    s = index["tiles"].values()
    print(f"{len(index['tiles'])} tiles, {sum(x['stores'] for x in s)} stores, {sum(x['prices'] for x in s)} prices, {sum(x['bytes'] for x in s) / 1e6:.1f} MB", file=sys.stderr)
if __name__ == "__main__":
    a = argparse.ArgumentParser()
    a.add_argument("--cache", default=os.path.expanduser("~/.cache/amni-price-packs/cache"))
    a.add_argument("--out", default=os.path.expanduser("~/.cache/amni-price-packs/out"))
    a.add_argument("--days", type=int, default=730)
    a.add_argument("--per-pair", type=int, default=4)
    a.add_argument("--product-age-days", type=int, default=7)
    o = a.parse_args()
    build(Path(o.cache), Path(o.out), o.days, o.per_pair, o.product_age_days)
