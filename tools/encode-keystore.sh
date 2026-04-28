#!/usr/bin/env sh
set -e

if [ -z "$1" ]; then
  echo "Usage: ./tools/encode-keystore.sh path/to/release.keystore"
  exit 1
fi

base64 -w 0 "$1" > keystore-base64.txt 2>/dev/null || base64 "$1" | tr -d '\n' > keystore-base64.txt
echo "Created keystore-base64.txt"
