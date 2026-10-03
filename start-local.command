#!/usr/bin/env bash
# Start the full local app. Uses an existing Ollama service or starts one temporarily.
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p .local
owned_ollama_pid=""
cleanup() { if [[ -n "$owned_ollama_pid" ]]; then kill "$owned_ollama_pid" 2>/dev/null || true; fi; }
trap cleanup EXIT
if ! curl -fsS --max-time 2 http://127.0.0.1:11434/api/version >/dev/null 2>&1; then
  ollama_bin="$(command -v ollama || true)"
  if [[ -z "$ollama_bin" && -x ../../work/ollama-runtime/ollama ]]; then
    ollama_bin="$(cd ../../work/ollama-runtime && pwd)/ollama"
    export OLLAMA_MODELS="$(cd ../../work/ollama-models && pwd)"
  fi
  if [[ -z "$ollama_bin" ]]; then echo "Install Ollama first; see README.md."; exit 1; fi
  OLLAMA_NO_CLOUD=1 OLLAMA_HOST=127.0.0.1:11434 OLLAMA_NUM_PARALLEL=1 "$ollama_bin" serve > .local/ollama.log 2>&1 &
  owned_ollama_pid=$!
  for attempt in {1..30}; do
    if curl -fsS --max-time 2 http://127.0.0.1:11434/api/version >/dev/null 2>&1; then break; fi
    sleep 1
  done
fi
export OLLAMA_MODEL="${OLLAMA_MODEL:-qwen3:4b}"
echo "SourceDesk: http://127.0.0.1:${PORT:-8787}"
echo "Local model: $OLLAMA_MODEL. Press Ctrl+C to stop."
bash ./run.sh
