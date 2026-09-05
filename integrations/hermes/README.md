# CodyOS Hermes Integration

Generische öffentliche Integrationsschicht zwischen einer nutzereigenen
Hermes-Installation und dem Cody Home Gateway.

Dieses Paket enthält **keinen** Hermes-Agenten, keine Memory, keinen
Model-Provider, keine Tools, Skills, Sessions oder persönlichen Daten. Es spricht
mit einer Hermes-kompatiblen HTTP/SSE-API, die der Nutzer lokal konfiguriert.

## Gewählte Architektur

Genutzt wird die vorhandene Hermes-API:

- `GET /health`
- `GET /api/sessions`
- `POST /api/sessions`
- `POST /api/sessions/{session_id}/chat/stream`

So vermeiden wir einen Hermes-Fork und Core-Patches. Hermes besitzt ein
offizielles Plugin-System (`plugin.yaml` plus `register(ctx)` oder pip
Entry-Point `hermes_agent.plugins`), aber für die erste öffentliche Integration
braucht CodyOS kein Plugin, weil der API-Server den nötigen Agenten-Aufrufpfad
bereits bereitstellt.

## Unterstützt

- eigene CodyOS-Conversation/-Session
- Textanfragen
- Tool-Lifecycle-Events, wenn Hermes sie ausgibt
- Task-Status-Events, wenn Hermes sie ausgibt
- Model-/Router-Status-Events, wenn Hermes sie ausgibt
- Notifications, wenn Hermes sie ausgibt
- Approval-Events werden nur weitergereicht; CodyOS darf Hermes'
  Approval-Logik nicht umgehen
- Character-State-Mapping: `IDLE`, `THINKING`, `WORKING`, `SPEAKING`,
  `SUCCESS`, `ERROR`, `APPROVAL_REQUIRED`
- Voice-Pipeline kann vom Gateway gestartet werden; STT/TTS bleiben
  Backend-seitige Hermes-Komponenten

## Installation

Kopiere dieses Verzeichnis nach:

```text
~/CodyHome-Public/integrations/hermes/
```

Installiere Abhängigkeiten in derselben Python-Umgebung wie das Gateway oder
füge dieses Verzeichnis zu `PYTHONPATH` hinzu.

```bash
cp config.example.env .env
# .env lokal bearbeiten; echte Secrets niemals committen
```

## Sicherheit

Nicht veröffentlichen:

- persönliche Hermes-Konfiguration
- SOUL/memory/workspace/sessions
- Provider-/API-Keys
- Google-Zugangsdaten
- Geräte-Secrets oder Pairing-Codes
- private Domains/IPs
