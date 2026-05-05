#!/usr/bin/env bash
set -euo pipefail

VERSION="0.1.1"
TAG="v$VERSION"
TITLE="$TAG"
BINARY="hsr"
ASSET="hsr-linux-amd64"

cp "$BINARY" "$ASSET"

gh release create "$TAG" "$ASSET" \
  --title "$TITLE" \
  --target main \
  --generate-notes

echo "Published $TAG with asset $ASSET"
