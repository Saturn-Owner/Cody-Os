# Hinweise zum Public Gateway

Dieses Verzeichnis ist für die öffentliche CodyOS-Gateway-Komponente gedacht.

Das Gateway spricht mit einer generischen Hermes-kompatiblen HTTP-API, die über
Umgebungsvariablen konfiguriert wird. Persönliche Hermes-Daten wie SOUL, Memory,
Sessions, Workspace, Google-Zugangsdaten, Provider-Keys, private Domains/IPs,
Geräte-Secrets, Caddy-Konfiguration und systemd-Secrets sind absichtlich nicht
enthalten.

Voice V1 ist turn-basiert und nutzt vorhandene Backend-seitige Hermes-
Komponenten, wenn sie verfügbar sind:

- STT: `tools.transcription_tools.transcribe_audio()`
- Cody-Anfrage: `HermesClient.stream_message()`
- TTS: `tools.tts_tool.text_to_speech_tool()` mit Provider `edge`

Wenn diese Hermes-Module zur Laufzeit nicht verfügbar sind, liefert `/voice`
einen serverseitigen Fehler, bis der Host äquivalente Backend-Komponenten
integriert.
