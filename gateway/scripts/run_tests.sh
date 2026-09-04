#!/usr/bin/env bash
set -euo pipefail
PYTHON_BIN="${PYTHON:-}"
if [[ -z "$PYTHON_BIN" ]]; then
  if command -v python >/dev/null 2>&1; then PYTHON_BIN=python; else PYTHON_BIN=python3; fi
fi
"$PYTHON_BIN" -m py_compile cody_home_gateway/*.py cody_home_gateway/integrations/hermes/*.py tests/test_cody_home_gateway.py
"$PYTHON_BIN" -m unittest tests/test_cody_home_gateway.py
