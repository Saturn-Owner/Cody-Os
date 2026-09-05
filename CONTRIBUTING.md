# Beitragen

Danke, dass du CodyOS verbessern möchtest. Das Projekt ist noch experimentell,
deshalb sind kleine, gut nachvollziehbare Beiträge besonders hilfreich.

## Grundregeln

- Keine Secrets, Tokens, Pairing-Codes, Keystores, privaten URLs oder
  persönlichen Agentendaten committen.
- Technische Schnittstellen wie API-Pfade, Env-Variablen, Eventnamen,
  Protokollfelder und öffentliche Klassen nicht ohne Migrationsplan ändern.
- CodyOS enthält keinen persönlichen Hermes-Agenten. Beiträge sollen generisch
  bleiben und mit einer eigenen Hermes-Instanz des Nutzers funktionieren.
- Sicherheitsrelevante Änderungen bitte klein halten und in der Beschreibung
  klar erklären.

## Vor einem Pull Request

Bitte lokal prüfen:

```bash
cd android
./gradlew assembleDebug assembleRelease
```

```bash
cd gateway
./scripts/run_tests.sh
```

```bash
cd integrations/hermes
python -m unittest -v tests/test_codyos_hermes_integration.py
```

## Sprache

Öffentliche Dokumentation ist auf Deutsch. Technische Namen und stabile
Schnittstellen bleiben Englisch, wenn sie Teil des Protokolls oder Codes sind.
