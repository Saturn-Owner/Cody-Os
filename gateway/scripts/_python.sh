#!/usr/bin/env bash
if command -v python >/dev/null 2>&1; then
  command python "$@"
elif command -v python3 >/dev/null 2>&1; then
  command python3 "$@"
else
  echo "python or python3 is required" >&2
  exit 127
fi
