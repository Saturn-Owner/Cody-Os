# Sicherheit

Cody Home ist frühe, experimentelle Self-Hosting-Software. Es gibt keinen
verwalteten Dienst, keine Telemetrie und keinen Anbieter, der deine Daten
sammelt — das bedeutet aber auch: Für die Sicherheit deiner eigenen Installation
bist du selbst verantwortlich. Bitte lies diese Hinweise, bevor du Cody Home
betreibst.

## Was Cody Home speichert und wo

- **Geräte-Zugangsdaten** (`device_id` / `device_secret`, beim Pairing erzeugt)
  werden nur auf dem Android-Gerät gespeichert, in `EncryptedSharedPreferences`
  (AES-256-GCM, Schlüsselmaterial im Android Keystore). Sie werden nicht geloggt
  und verlassen das Gerät nach dem Pairing nur noch indirekt als HMAC-Signatur;
  das Secret selbst wird nicht erneut übertragen.
- **Gateway-URLs** werden lokal pro Installation konfiguriert (siehe
  `android/gradle.properties.example`) und sind in diesem Repository weder
  fest verdrahtet noch mitgeliefert.
- **Signierschlüssel** für Release-Builds liegen vollständig in deiner
  Verantwortung. Dieses Repo enthält keinen Keystore und es sollte niemals einer
  hinzugefügt werden (siehe `.gitignore`).

## Authentifizierungsmodell

Jede Anfrage zwischen Android-Client und Gateway wird mit einer HMAC-SHA256-
Signatur über einen kanonischen String authentifiziert: Methode, Pfad,
Zeitstempel, Nonce und Hash des Bodys. Signiert wird mit dem Secret des
gepairten Geräts. Das Gateway muss eine Toleranz für Uhrzeitabweichungen und
Nonce-Prüfung gegen Replay-Angriffe erzwingen. Wenn du ein Gateway
implementierst oder auditierst, behandle das exakte Canonical-String-Format als
sicherheitskritisch.

## Schwachstelle melden

Dieses Projekt hat noch keinen eigenen Sicherheitskontakt und kein Bug-Bounty-
Programm. Wenn du ein Sicherheitsproblem findest, öffne bitte ein GitHub-Issue
mit klarer Sicherheitskennzeichnung. Falls Details nicht öffentlich gepostet
werden sollten, kontaktiere den Maintainer direkt. Bitte teste gefundene
Schwachstellen nicht gegen fremde Live-Installationen.

## Bekannte Einschränkungen

- Das ist **experimentelle Software** und wurde noch nicht unabhängig
  sicherheitsgeprüft.
- Wer das Gateway betreibt, ist selbst für TLS-Terminierung, Rate-Limits,
  Secret-Management und die Absicherung des Backends verantwortlich.
- Die Mikrofon-Pipeline der Referenz-Hardware (Echo Show 5, 1. Generation) hat
  ein bekanntes, noch untersuchtes Zuverlässigkeitsproblem. Das ist aktuell ein
  Funktionsproblem, kein bekanntes Sicherheitsproblem.
