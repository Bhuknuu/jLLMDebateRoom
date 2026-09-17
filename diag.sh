#!/usr/bin/env bash
# diag.sh — Linux/macOS equivalent of diag-run.bat
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

LOG="$SCRIPT_DIR/run.log"

echo "=== STARTUP at $(date '+%d-%m-%Y %H:%M:%S') ===" > "$LOG"
java -Djava.awt.headless=true -cp out Courtroom >> "$LOG" 2>&1
echo "=== EXIT at $(date '+%H:%M:%S') code=$? ===" >> "$LOG"

echo "--- run.log tail ---"
tail -n 20 "$LOG"
