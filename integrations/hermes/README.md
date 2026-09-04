# CodyOS Hermes Integration

Generic public integration layer between a user-owned Hermes installation and CodyOS/Cody Home Gateway.

This package does **not** contain a Hermes agent, memory, model provider, tools, skills, sessions, or personal data. It talks to a Hermes-compatible HTTP/SSE API that the user configures locally.

## Chosen architecture

Use Hermes' existing API surface:

- `GET /health`
- `GET /api/sessions`
- `POST /api/sessions`
- `POST /api/sessions/{session_id}/chat/stream`

This avoids a Hermes fork and avoids core patches. Hermes' official plugin system exists (`plugin.yaml` plus `register(ctx)`, or pip entry point `hermes_agent.plugins`), but CodyOS does not need a plugin for the first public integration because the API server already exposes the required agent invocation path.

## What is supported

- Dedicated CodyOS conversation/session
- Text requests
- Tool lifecycle events when Hermes emits them
- Task status events when Hermes emits them
- Model/router status events when Hermes emits them
- Notifications when Hermes emits them
- Approval events are forwarded only; CodyOS must not bypass Hermes approval logic
- Character-state mapping: `IDLE`, `THINKING`, `WORKING`, `SPEAKING`, `SUCCESS`, `ERROR`, `APPROVAL_REQUIRED`
- Voice pipeline can be initiated by the gateway; STT/TTS remain backend-side Hermes components

## Install

Copy this directory to:

```text
~/CodyHome-Public/integrations/hermes/
```

Install dependencies in the same Python environment as the gateway or add this directory to `PYTHONPATH`.

```bash
cp config.example.env .env
# edit .env locally; never commit real secrets
```

## Security

Do not publish:

- personal Hermes config
- SOUL/memory/workspace/sessions
- provider/API keys
- Google credentials
- device secrets or pairing codes
- private domains/IPs
