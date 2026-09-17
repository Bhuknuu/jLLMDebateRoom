#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

mkdir -p out
javac -d out CourtroomCLI.java
java -cp out CourtroomCLI "${1:-qwen2.5}"
