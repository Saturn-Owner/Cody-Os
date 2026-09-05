# Changelog

Alle wichtigen Änderungen an CodyOS werden in dieser Datei dokumentiert.

Das Projekt folgt semantischer Versionierung, solange das in der frühen
Entwicklungsphase sinnvoll möglich ist.

## v0.1.0-alpha — in Vorbereitung

Erste öffentliche Alpha-Version von CodyOS.

### Enthalten

- Cody Home Android-App für den Amazon Echo Show 5 (1. Generation, 2019),
  Codename `checkers`, Modell `H23K37`
- Jetpack-Compose-Dashboard mit Cody-Character, Statusanzeige und Ambient-Modus
- Sicheres Pairing zwischen Cody Home und Gateway
- HMAC-SHA256-Authentifizierung, Nonce- und Replay-Schutz
- Cody Home Gateway mit Health-, Pairing-, Message-, Voice- und WebSocket-Endpunkten
- Generische Hermes-Integration für nutzereigene Hermes-Backends
- Turn-basierte Voice-Pipeline: Aufnahme → Upload → STT → Hermes → TTS → Wiedergabe
- Deutsche öffentliche Projektdokumentation
- Transparenter Hinweis zur KI-unterstützten Entwicklung

### Getestet

- Android Debug- und Release-Build
- Gateway-Test-Suite
- Hermes-Integration-Test-Suite
- Import-Test der Hermes-Integration
- Secret-/Privacy-Scan des öffentlichen Repositories
- Voice-Pipeline mit vorab aufgenommener Audiodatei

### Bekannte Einschränkungen

- Das physische Mikrofon der Referenz-Hardware wird noch per Langzeittest
  untersucht. Die bisherige Messreihe läuft weiter und soll vor einer
  endgültigen Bewertung mindestens 20–24 Stunden durchgehende Uptime erreichen.
- CodyOS-ROM existiert noch nicht. Das aktuelle Projekt enthält Cody Home,
  Gateway, Dokumentation und Hermes-Integration.
- Hermes ist ein externes, self-hosted Backend und wird vom Nutzer selbst
  installiert, betrieben und konfiguriert.
