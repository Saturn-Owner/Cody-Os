# CodyOS v0.1.0-alpha — Release Notes

Dies sind die vorbereiteten Release Notes für die erste öffentliche
Alpha-Version von CodyOS. Es wurde noch kein Git-Tag und kein GitHub-Release
erstellt.

## Status

`v0.1.0-alpha` ist eine frühe, experimentelle Version. Sie richtet sich an
technisch erfahrene Nutzer, die CodyOS auf eigener Hardware und mit einem
eigenen Backend testen möchten.

CodyOS enthält keinen persönlichen Hermes-Agenten. Hermes ist ein externes,
self-hosted Backend, das vom Nutzer selbst installiert, betrieben und
konfiguriert wird.

Ein CodyOS-ROM existiert noch nicht. Das aktuelle Release umfasst Cody Home,
das Cody Home Gateway, die generische Hermes-Integration und Dokumentation.

## Aktuelle Features

- Cody Home Android-App für ein Smart-Display-Erlebnis auf dem Echo Show 5
- Animierter Cody-Character mit Zuständen wie IDLE, LISTENING, THINKING,
  WORKING, SPEAKING, SUCCESS, ERROR und OFFLINE
- Ambient-Modus nach Inaktivität
- Sichere Gerätekopplung über Pairing-Code
- Verschlüsselte Speicherung der Gerätedaten auf Android
- Cody Home Gateway mit:
  - `GET /health`
  - `POST /pair`
  - `POST /message`
  - `POST /voice`
  - `WS /ws`
- HMAC-SHA256-Authentifizierung für signierte Geräteanfragen
- Nonce-, Ablaufzeit- und Replay-Schutz
- WebSocket-Events für Status-, Nachrichten- und Task-Updates
- Generische Hermes-Integration für nutzereigene Hermes-Backends
- Turn-basierte Voice-Pipeline: Aufnahme → STT → Hermes → TTS → MP3-Antwort

## Unterstütztes Gerät

Aktuell getestete Referenz-Hardware:

- Amazon Echo Show 5, 1. Generation (2019)
- Codename: `checkers`
- Modell: `H23K37`
- Inoffizielles LineageOS 18.1 auf Android-11-Basis

Andere Geräte sind möglich, aber für diese Alpha-Version nicht dokumentiert
oder getestet.

## Getestete Komponenten

- Android Debug-Build
- Android Release-Build
- Cody Home Gateway Tests
- Hermes-Integration Tests
- Import-Test der Hermes-Integration
- Secret-/Privacy-Scan
- Textkommunikation über Gateway → Hermes → Gateway
- Voice-Pipeline mit vorab aufgenommener Audiodatei

## Bekannte Einschränkungen

- Die physische Mikrofonfunktion der Referenz-Hardware wird noch untersucht.
  Ein Langzeittest des Mic-Monitors läuft weiter. Eine endgültige Bewertung
  erfolgt erst nach mindestens 20–24 Stunden durchgehender Uptime.
- Voice V1 ist turn-basiert, nicht realtime.
- Es gibt noch kein Wakeword.
- Es gibt noch kein CodyOS-ROM.
- Hermes ist nicht Bestandteil von CodyOS, sondern ein externes Backend.
- Nutzer müssen ihr eigenes Hermes-Backend betreiben und konfigurieren.

## Sicherheit

- Keine Provider-Keys auf dem Display
- Keine persönlichen Agentendaten im Repository
- Keine produktiven Pairing-Codes oder Device-Secrets im Repository
- `.env`, lokale Konfigurationen, Keystores und Build-Artefakte bleiben
  ausgeschlossen

## Hinweis zur KI-unterstützten Entwicklung

CodyOS wurde zu großen Teilen mit KI-Unterstützung entwickelt. Details stehen
in [`../AI_DEVELOPMENT.md`](../AI_DEVELOPMENT.md).
