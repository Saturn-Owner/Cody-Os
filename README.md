# CodyOS

**Open-Source KI-Smart-Display-Plattform für persönliche Agenten.**

> **Status: FRÜHE ENTWICKLUNG / EXPERIMENTELL.** Dieses Hobbyprojekt ist aktiv
> in Arbeit und noch kein fertiges Produkt. Rechne mit Ecken, fehlenden Teilen
> und möglichen Breaking Changes. Lies den Abschnitt [Status](#status), bevor du
> Zeit hineinsteckst.

CodyOS ist das Gesamtprojekt. Die erste funktionierende Komponente ist
**Cody Home**: eine Smart-Display-Erfahrung für einen jailbroken Echo Show 5
mit LineageOS. Cody Home spricht über ein sicheres Gateway mit deinem eigenen
self-hosted KI-Backend — keine Provider-Keys auf dem Display, keine gebündelten
persönlichen Agentendaten, deine Hardware und dein Backend.

CodyOS enthält **keinen** persönlichen Hermes-Agenten. Hermes ist ein externes,
self-hosted Backend, das Nutzer selbst betreiben und konfigurieren. Dieses Repo
enthält ein Gateway und eine optionale generische Hermes-Integration, aber
keinen Hermes-Core, keine Memory, keine Sessions und keine Provider-Zugangsdaten.

## Architektur

```text
Echo Show 5
↓
Cody Home Android
↓
Cody Home Gateway
↓
Hermes Integration
↓
Eigene Hermes-Instanz des Nutzers
```

Ein CodyOS-ROM ist ein späteres Langfristziel und aktuell noch nicht enthalten.

## Wie es aussieht

Cody Home zeigt ein Dashboard mit Uhrzeit, Verbindungsstatus des Agenten,
aktueller Aktivität und einer animierten Figur („Cody“), die den Zustand
widerspiegelt — zuhören, denken, sprechen oder ruhen. Nach Inaktivität übernimmt
ein abgedunkelter Ambient-Bildschirm.

## Funktionen

- Native Android-App (Kotlin, Jetpack Compose) — kein WebView, keine Hybrid-Shell
- Animierter Cody-Begleiter mit klaren **Character States** (idle, listening,
  thinking, working, speaking, success, error, offline)
- **Ambient-Modus** — dunkler und reduzierter nach Inaktivität, wacht per Touch auf
- **Sicheres Pairing** — einmaliger Pairing-Code, Zugangsdaten in
  `EncryptedSharedPreferences` (AES-256-GCM, geschützt durch Android Keystore)
- **HMAC-SHA256-Authentifizierung** für jeden Gateway-Aufruf
- **HTTPS- und WebSocket-Gateway-Protokoll** mit automatischem Reconnect und Backoff
- **Hermes-Integration** — optionale Bridge zum eigenen Hermes-Backend des Nutzers
  (siehe [`integrations/hermes/`](integrations/hermes/))
- Textanfragen an deinen Agenten mit gestreamten Status-Updates
- Voice-Pipeline Ende-zu-Ende implementiert (Aufnahme → Upload → STT → Agent →
  TTS → Wiedergabe) — siehe [Status](#status) zur aktuellen Hardware-Einschränkung

## Status

Dieses Projekt ist **noch nicht bereit für den allgemeinen Einsatz**. Aktuell gilt:

- Cody Home Android, Pairing-Flow und Gateway-Protokoll (HMAC-Auth,
  Textanfragen, Reconnect-Logik) funktionieren und wurden gegen ein echtes
  Gateway getestet.
- Die Voice-Pipeline ist vollständig implementiert und wurde
  **Ende-zu-Ende mit einer vorab aufgenommenen Audiodatei** verifiziert —
  Upload, Speech-to-Text, Agentenantwort, Text-to-Speech und Wiedergabe über
  den Echo-Lautsprecher funktionieren.
- **Das physische Mikrofon der Referenz-Hardware funktioniert unter LineageOS
  noch nicht zuverlässig.** Die Ursache liegt tief im Audio-Stack (siehe
  [`docs/architecture/`](docs/architecture/), sobald veröffentlicht) — es wirkt
  nicht wie ein App-Bug, sondern wie ein Hardware-/Treiberproblem dieses
  LineageOS-Ports. Das wird weiter untersucht.
- Das Gateway, mit dem die App zum KI-Agenten spricht, ist enthalten in
  [`gateway/`](gateway/).
- Die Hermes-Integration ist als generischer Erweiterungspunkt enthalten in
  [`integrations/hermes/`](integrations/hermes/).

## Repository-Struktur

```
CodyOS/
├── android/           Native Cody-Home-Android-App für den Echo
├── gateway/           FastAPI-Gateway für authentifizierten Gerätetransport
├── integrations/
│   └── hermes/         Optionale generische Bridge zum eigenen Hermes-Backend
├── docs/
│   ├── device-setup/    Unlock/Flash der Referenz-Hardware
│   ├── installation/    Installation von Cody Home nach dem Geräte-Setup
│   ├── architecture/     Zusammenspiel der Komponenten
│   └── screenshots/
├── assets/branding/
└── scripts/
```

## Unterstütztes Gerät

Amazon Echo Show 5, 1. Generation (2019) — Codename `checkers`, Modell
`H23K37`, mit einem inoffiziellen LineageOS-18.1-Build. Siehe
[`docs/device-setup/README.md`](docs/device-setup/README.md), bevor du Cody Home
installierst: Dafür muss der Bootloader entsperrt und ein Community-LineageOS
geflasht werden. Das ist kein offiziell unterstützter und nicht risikofrei
rückgängig machbarer Prozess.

## Erste Schritte

1. Gerät vorbereiten — siehe [`docs/device-setup/`](docs/device-setup/).
2. Cody Home installieren — siehe [`docs/installation/`](docs/installation/).
3. Eigenes Gateway eintragen — kopiere
   [`android/gradle.properties.example`](android/gradle.properties.example) nach
   `android/gradle.properties` und trage vor dem Build deine Gateway-URLs ein.

## Sicherheit

Siehe [`SECURITY.md`](SECURITY.md) für das Authentifizierungsmodell, gespeicherte
Daten und Hinweise zum Melden von Schwachstellen.

## KI-gestützte Entwicklung

CodyOS wurde zu großen Teilen mit KI-Unterstützung entwickelt. Details zu
genutzten Werkzeugen, Einsatzbereichen und manueller Prüfung stehen in
[`AI_DEVELOPMENT.md`](AI_DEVELOPMENT.md).

## Lizenz

Apache License 2.0. Siehe [`LICENSE`](LICENSE).
