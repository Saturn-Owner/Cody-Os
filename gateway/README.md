# Cody Home Gateway

Öffentlich nutzbares FastAPI-Transport-Gateway für CodyOS-Clients. Das Gateway ist nur
ein Adapter: Es enthält keinen Agenten, keine Memory, keinen Model-Provider und
keine Provider-Keys. Es leitet authentifizierte Geräteanfragen an ein
konfiguriertes Hermes-kompatibles Backend weiter.

## Funktionen

- `GET /health`
- `POST /pair`
- `POST /message`
- `POST /voice`
- `WS /ws`
- Geräte-Pairing und Zugangsdaten pro Gerät
- HMAC-SHA256-Signaturen für Anfragen
- Replay-Schutz über Zeitstempel und Nonce
- Versioniertes JSON-Event-Protokoll
- Turn-basierte Voice V1: Audio-Upload → STT → Hermes-Backend → TTS → MP3-Antwort

## Schnellstart

```bash
python -m venv .venv
. .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
# .env lokal bearbeiten; echte Secrets niemals committen
./scripts/start_gateway.sh
```

Einmaligen Pairing-Code erzeugen:

```bash
./scripts/create_pairing_code.sh
```

Tests ausführen:

```bash
./scripts/run_tests.sh
```

## Sicherheitshinweise

Niemals `.env`, `state/`, Geräte-Secrets, Pairing-Codes, Backend-Tokens,
persönliche Agentendaten, Domains, IPs, Sessions, Memory oder Workspace-Dateien
committen.
