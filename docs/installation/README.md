# Cody Home installieren

*(Noch auszufüllen.)* Sobald LineageOS auf dem Gerät läuft (siehe
[`../device-setup/`](../device-setup/)), läuft die Cody-Home-Installation grob so:

1. Kopiere [`../../android/gradle.properties.example`](../../android/gradle.properties.example)
   nach `android/gradle.properties` und trage deine eigenen Gateway-URLs ein.
2. Baue die APK (`./gradlew assembleDebug` oder `assembleRelease` aus
   `android/`).
3. Installiere sie auf dem Gerät (`adb install`, später ggf. über ein
   Recovery-flashbares Paket).
4. Beim ersten Start koppelst du die App mit einem Pairing-Code deines Gateways.

Eine genaue Schritt-für-Schritt-Anleitung mit Screenshots und Troubleshooting
wird ergänzt.
