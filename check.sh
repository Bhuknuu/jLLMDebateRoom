#!/usr/bin/env bash
# check.sh — Linux/macOS equivalent of check.bat
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

mkdir -p out
javac -d out Courtroom.java
if [ $? -ne 0 ]; then
    echo "[FAILED] Compilation error."
    exit 1
fi

java -cp out Courtroom --check
