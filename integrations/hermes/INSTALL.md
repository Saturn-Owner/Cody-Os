# CodyOS Hermes-Integration installieren

1. Installiere und konfiguriere deine eigene Hermes-Instanz.
2. Aktiviere den Hermes-API-Server gemäß der Dokumentation deiner
   Hermes-Version.
3. Erzeuge lokal ein Backend-API-Token.
4. Kopiere `config.example.env` nach `.env` und trage nur deine eigenen lokalen
   Werte ein.
5. Starte das Cody Home Gateway mit den passenden `CODY_HOME_HERMES_*`-
   Umgebungsvariablen oder importiere `codyos_hermes_integration` direkt in
   einem eigenen CodyOS-Dienst.

Für die API-basierte Integration ist kein Hermes-Core-Patch nötig.
