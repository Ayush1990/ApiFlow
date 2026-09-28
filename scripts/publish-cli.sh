#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/packages/apiflow-cli"

echo "Publishing @apiflow/cli from $(pwd)"
npm pack --dry-run
echo ""
read -r -p "Bump and publish to npm? [y/N] " confirm
if [[ "${confirm,,}" != "y" ]]; then
  echo "Aborted."
  exit 0
fi

npm version patch
npm publish --access public
echo "Published $(node -p "require('./package.json').version")"
