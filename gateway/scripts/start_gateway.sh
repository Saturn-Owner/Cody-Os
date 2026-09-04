#!/usr/bin/env bash
set -euo pipefail
PYTHON_BIN="${PYTHON:-}"
if [[ -z "$PYTHON_BIN" ]]; then
  if command -v python >/dev/null 2>&1; then PYTHON_BIN=python; else PYTHON_BIN=python3; fi
fi
exec "$PYTHON_BIN" -m uvicorn cody_home_gateway.app:app --host "${CODY_HOME_BIND_HOST:-127.0.0.1}" --port "${CODY_HOME_BIND_PORT:-8787}"
