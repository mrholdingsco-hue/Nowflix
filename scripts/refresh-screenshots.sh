#!/usr/bin/env bash
# Re-fetch the latest CI screenshots and redeploy the phone viewer to the same URL.
#
#   scripts/refresh-screenshots.sh
#
# Requires .env.local (VERCEL_TOKEN) at the repo root. Reads the ci-screenshots
# branch that verify.yml force-pushes on every CI run, bakes the PNGs into
# ci-viewer/, and ships them to the nowflix-ci-view Vercel project (Seoul icn1).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
VIEWER="$ROOT/ci-viewer"
SCOPE="team_Y9s7FtZdeu9VAIhowssjEFxr"
BRANCH="ci-screenshots"

# shellcheck disable=SC1091
set -a; source "$ROOT/.env.local"; set +a
: "${VERCEL_TOKEN:?VERCEL_TOKEN missing in .env.local}"

echo "==> Fetching $BRANCH from origin"
if ! git -C "$ROOT" fetch -f origin "$BRANCH":refs/remotes/origin/"$BRANCH" 2>/dev/null; then
  echo "!! ci-screenshots branch does not exist on origin yet — run CI first." >&2
  exit 1
fi
TREE="origin/$BRANCH"

echo "==> Extracting PNGs + metadata into $VIEWER/screenshots"
rm -rf "$VIEWER/screenshots"; mkdir -p "$VIEWER/screenshots"
rm -f "$VIEWER/meta.txt" "$VIEWER/kiosk-summary.txt"
git -C "$ROOT" ls-tree -r --name-only "$TREE" | grep -E '^screenshots/.*\.png$' | while read -r f; do
  git -C "$ROOT" show "$TREE:$f" > "$VIEWER/screenshots/$(basename "$f")"
done
git -C "$ROOT" show "$TREE:meta.txt"          > "$VIEWER/meta.txt"          2>/dev/null || true
git -C "$ROOT" show "$TREE:kiosk-summary.txt" > "$VIEWER/kiosk-summary.txt" 2>/dev/null || true

COUNT=$(find "$VIEWER/screenshots" -name '*.png' | wc -l | tr -d ' ')
echo "==> $COUNT screenshot(s):"
( cd "$VIEWER/screenshots" && ls -1 *.png 2>/dev/null | sed 's/^/    /' ) || true
if [ "$COUNT" -eq 0 ]; then
  echo "!! No PNGs on the branch — aborting deploy." >&2
  exit 1
fi

echo "==> Deploying to nowflix-ci-view (icn1)"
cd "$VIEWER"
vercel link --yes --project nowflix-ci-view --token "$VERCEL_TOKEN" --scope "$SCOPE" >/dev/null
vercel deploy --prod --yes --token "$VERCEL_TOKEN" --scope "$SCOPE"

echo "==> Live: https://nowflix-ci-view.vercel.app"
