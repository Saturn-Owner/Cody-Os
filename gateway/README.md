# Cody Home Gateway

Public-ready FastAPI transport gateway for CodyOS-style clients. The gateway is only an adapter: it does not contain an agent, memory, model provider, or provider keys. It forwards authenticated device requests to a configured Hermes-compatible backend.

## Features

- `GET /health`
- `POST /pair`
- `POST /message`
- `POST /voice`
- `WS /ws`
- Device pairing and per-device credentials
- HMAC-SHA256 request signing
- Timestamp and nonce replay protection
- Versioned JSON event protocol
- Turn-based Voice V1: audio upload → STT → Hermes backend → TTS → MP3 response

## Quick start

```bash
python -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
# edit .env locally; never commit real secrets
./scripts/start_gateway.sh
```

Create one pairing code:

```bash
./scripts/create_pairing_code.sh
```

Run tests:

```bash
./scripts/run_tests.sh
```

## Security notes

Never commit `.env`, `state/`, device secrets, pairing codes, backend tokens, personal agent data, domains, IPs, sessions, memory, or workspace files.
