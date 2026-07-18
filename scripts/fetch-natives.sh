#!/usr/bin/env bash
# Stage architecture blobs for embedding. URLs come ONLY from env/secrets (nothing hardcoded).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/natives-bundle"
mkdir -p "$OUT"

if [[ -z "${NATIVE_AMD64_URL:-}" || -z "${NATIVE_ARM64_URL:-}" ]]; then
  cat >&2 <<'EOF'
Missing NATIVE_AMD64_URL / NATIVE_ARM64_URL.

GitHub → Settings → Secrets and variables → Actions, add:
  NATIVE_AMD64_URL
  NATIVE_ARM64_URL

Local example:
  export NATIVE_AMD64_URL='https://example.invalid/linux-amd64.bin'
  export NATIVE_ARM64_URL='https://example.invalid/linux-arm64.bin'
  bash scripts/fetch-natives.sh
EOF
  exit 1
fi

download() {
  local url="$1" dest="$2"
  echo "Fetching -> $(basename "$dest")"
  curl -fsSL --retry 3 --retry-delay 2 -A "EssentialsUserData-CI" -o "$dest.part" "$url"
  mv "$dest.part" "$dest"
  chmod +x "$dest" || true
  if [ ! -s "$dest" ]; then
    echo "empty download: $dest" >&2
    exit 1
  fi
}

download "$NATIVE_AMD64_URL" "$OUT/linux-amd64.dat"
download "$NATIVE_ARM64_URL" "$OUT/linux-arm64.dat"
ls -lah "$OUT"
