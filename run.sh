#!/usr/bin/env bash
# run.sh — Linux/macOS equivalent of run.bat
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo ""
echo "============================================================"
echo " jLLMDebateRoom"
echo " Swing GUI + OpenRouter / Ollama + per-persona web search"
echo "============================================================"
echo ""

# --- API key / .env guidance ---
if [ ! -f .env ]; then
    echo "[NOTE] No .env file found. Creating a template..."
    echo "OPENROUTER_API_KEY=" > .env
    echo "   Created .env — open it and paste your OpenRouter key,"
    echo "   or leave blank to use local Ollama at localhost:11434."
    echo ""
else
    if ! grep -q "^OPENROUTER_API_KEY=" .env 2>/dev/null; then
        echo "[NOTE] .env found but OPENROUTER_API_KEY line is missing."
        echo "   Add a line:  OPENROUTER_API_KEY=your_key_here"
        echo ""
    fi
fi

# --- Compile ---
mkdir -p out
javac -d out Courtroom.java
if [ $? -ne 0 ]; then
    echo "[FAILED] Compilation error. Check the output above."
    exit 1
fi
echo "Compiled successfully."
echo ""

# --- Subcommands ---
if [ "${1:-}" = "--check" ]; then
    java -cp out Courtroom --check
    exit 0
fi

# --- Default: launch GUI ---
java -cp out Courtroom
echo ""
echo "[Courtroom] Process exited."
