#!/bin/bash
#
# Scan all published Orbeon Docker images for CVEs using Docker Scout.
#
# Enumerates every tag of the three Orbeon repos on Docker Hub, scans each on
# both linux/amd64 and linux/arm64, saves SARIF + human-readable txt under
# docker/scout-reports/, and produces:
#   - summary.md       — internal triage view
#   - github-issue.md  — ready to paste into a GitHub issue
#
# Re-running the script skips tags that have both a SARIF and txt already on
# disk. Delete docker/scout-reports/<repo>/ to force a rescan.
#
# Rate limiting: when Docker Hub returns TOOMANYREQUESTS the script sleeps for
# RATE_LIMIT_WAIT_MIN minutes (default 30) and retries the same scan, looping
# until it goes through. The intent is to start the script once and let it run
# to completion even if rate limits trip multiple times.
#
# Requirements: docker, docker scout, python3, curl.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPORT_DIR="$SCRIPT_DIR/scout-reports"
SUMMARIZER="$SCRIPT_DIR/scout-summarize.py"

RATE_LIMIT_WAIT_MIN=${RATE_LIMIT_WAIT_MIN:-30}

REPOS=(
  "orbeon/orbeon-forms"
  "orbeon/postgres"
  "orbeon/db-to-s3-attachment-migration"
)

PLATFORMS=("linux/amd64" "linux/arm64")

mkdir -p "$REPORT_DIR"

slug() { echo "${1//\//_}"; }

fetch_tags() {
  local repo="$1"
  local url="https://hub.docker.com/v2/repositories/$repo/tags?page_size=100"
  while [[ -n "$url" && "$url" != "null" ]]; do
    local resp
    resp=$(curl -fsSL "$url")
    echo "$resp" | python3 -c "import json,sys; d=json.load(sys.stdin); [print(t['name']) for t in d.get('results', [])]"
    url=$(echo "$resp" | python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('next') or '')")
  done
}

scan_one() {
  local repo="$1" tag="$2" platform="$3"
  local image="docker.io/$repo:$tag"
  # Use registry:// so Docker Scout pulls directly from the registry instead
  # of the local image store — avoids the "content digest not found" failure
  # when scanning non-host-platform images without containerd image storage.
  local scout_ref="registry://$image"
  local platform_slug="${platform//\//-}"
  local outdir="$REPORT_DIR/$(slug "$repo")"
  mkdir -p "$outdir"
  local sarif="$outdir/${tag}__${platform_slug}.sarif"
  local txt="$outdir/${tag}__${platform_slug}.txt"

  if [[ -s "$sarif" && -s "$txt" ]]; then
    echo "    [skip] $tag ($platform) — already scanned"
    return 0
  fi

  local attempt=1
  while true; do
    if (( attempt == 1 )); then
      echo "    [scan-sarif] $image ($platform)"
    else
      echo "    [scan-sarif] $image ($platform)  (attempt $attempt)"
    fi
    if ! docker scout cves --platform="$platform" --format sarif --output "$sarif" "$scout_ref" >/dev/null 2>&1; then
      echo "    [warn] sarif scan failed for $image ($platform)"
      rm -f "$sarif"
    fi

    echo "    [scan-txt]"
    docker scout cves --platform="$platform" "$scout_ref" > "$txt" 2>&1 || true

    if grep -q 'TOOMANYREQUESTS' "$txt" 2>/dev/null; then
      local wait_secs=$(( RATE_LIMIT_WAIT_MIN * 60 ))
      local resume_at
      resume_at=$(date -v +"${RATE_LIMIT_WAIT_MIN}"M '+%H:%M:%S' 2>/dev/null || date -d "+${RATE_LIMIT_WAIT_MIN} minutes" '+%H:%M:%S' 2>/dev/null || echo "")
      echo "    [rate-limited] Docker Hub TOOMANYREQUESTS — sleeping ${RATE_LIMIT_WAIT_MIN} min${resume_at:+ (resume at $resume_at)} before retry"
      rm -f "$sarif" "$txt"
      sleep "$wait_secs"
      attempt=$(( attempt + 1 ))
      continue
    fi

    break
  done
}

main() {
  for repo in "${REPOS[@]}"; do
    echo "[repo] $repo"
    local tags
    tags=$(fetch_tags "$repo")
    while IFS= read -r tag; do
      [[ -z "$tag" ]] && continue
      echo "  [tag] $tag"
      for platform in "${PLATFORMS[@]}"; do
        scan_one "$repo" "$tag" "$platform"
      done
    done <<< "$tags"
  done

  if [[ -x "$SUMMARIZER" || -f "$SUMMARIZER" ]]; then
    echo "[summarize] writing summary.md and github-issue.md"
    python3 "$SUMMARIZER" "$REPORT_DIR"
  else
    echo "[summarize] skipped — $SUMMARIZER not found"
  fi

  echo "Done. Reports in $REPORT_DIR"
}

# Only run main when invoked directly, not when sourced
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
  main
fi
