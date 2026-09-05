from __future__ import annotations

import argparse
from pathlib import Path

from .config import load_config
from .security import PairingStore


def main() -> int:
    parser = argparse.ArgumentParser(description="Erzeugt einen Cody-Home-Pairing-Code.")
    cfg = load_config()
    parser.add_argument("--state-dir", default=str(cfg.state_dir))
    parser.add_argument("--ttl-seconds", type=int, default=cfg.pairing_ttl_seconds)
    args = parser.parse_args()
    store = PairingStore(Path(args.state_dir) / "pairing.json")
    code = store.create_code(ttl_seconds=args.ttl_seconds)
    print(code)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
