#!/usr/bin/env bash
if command -v python >/dev/null 2>&1; then
  command python "$@"
elif command -v python3 >/dev/null 2>&1; then
  command python3 "$@"
else
  echo "python oder python3 wird benötigt" >&2
  exit 127
fi
