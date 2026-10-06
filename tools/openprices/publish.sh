#!/usr/bin/env bash
set -euo pipefail
REPO=${AMNI_PRICE_DATA_REPO:-Amnibro/amni-price-data}
TAG=${AMNI_PRICE_DATA_TAG:-packs}
OUT=${AMNI_PRICE_PACKS_OUT:-$HOME/.cache/amni-price-packs/out}
DIR=$(cd "$(dirname "$0")" && pwd)
python3 "$DIR/build_tiles.py" --out "$OUT"
gh release view "$TAG" -R "$REPO" >/dev/null 2>&1 || gh release create "$TAG" -R "$REPO" --title "Price packs" --notes "Nightly price tiles built from Open Prices (prices.openfoodfacts.org). Data © Open Food Facts contributors, ODbL 1.0. Used by the Amni-Price app's Community prices." >/dev/null
OLD=$(mktemp); trap 'rm -f "$OLD"' EXIT
gh release download "$TAG" -R "$REPO" -p index.json -O "$OLD" --clobber 2>/dev/null || echo '{"tiles":{}}' > "$OLD"
mapfile -t CHANGED < <(python3 - "$OLD" "$OUT/index.json" <<'PY'
import json,sys
o=json.load(open(sys.argv[1])).get("tiles",{}); n=json.load(open(sys.argv[2]))["tiles"]
[print(f"tile_{k}.json.gz") for k,v in n.items() if o.get(k,{}).get("sha256")!=v["sha256"]]
PY
)
mapfile -t GONE < <(python3 - "$OLD" "$OUT/index.json" <<'PY'
import json,sys
o=json.load(open(sys.argv[1])).get("tiles",{}); n=json.load(open(sys.argv[2]))["tiles"]
[print(f"tile_{k}.json.gz") for k in o if k not in n]
PY
)
for ((i=0; i<${#CHANGED[@]}; i+=20)); do (cd "$OUT" && gh release upload "$TAG" -R "$REPO" --clobber "${CHANGED[@]:i:20}"); done
for f in "${GONE[@]}"; do gh release delete-asset "$TAG" "$f" -R "$REPO" -y >/dev/null || true; done
(cd "$OUT" && gh release upload "$TAG" -R "$REPO" --clobber index.json)
echo "published ${#CHANGED[@]} changed tiles, removed ${#GONE[@]}"
