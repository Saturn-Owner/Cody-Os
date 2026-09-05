# Geräte-Setup

## Unterstütztes Gerät

| | |
|---|---|
| Gerät | Amazon Echo Show 5, 1. Generation (2019) |
| Codename | `checkers` |
| Modell | H23K37 |
| SoC | MediaTek MT8163 |
| Zielsystem | LineageOS 18.1 (Android 11), inoffizieller Community-Build |

Aktuell wird kein anderes Echo-Show-Modell unterstützt. Unlock-Exploit,
Kernel und Device Tree beziehen sich auf genau diese Hardware-Revision.

> **Dieser Prozess entsperrt den Bootloader und ersetzt das originale Fire OS
> durch ein Community-LineageOS. Dabei besteht ein reales Risiko, das Gerät
> dauerhaft unbrauchbar zu machen; Garantieansprüche können verloren gehen und
> eine vollständige Rückkehr zum Stock-Zustand ist nicht immer möglich.** Nutze
> nur Hardware, deren Verlust du verschmerzen kannst. Die Unlock-Werkzeuge sind
> Drittanbieter-/Upstream-Projekte und werden hier nicht mitgeliefert.

## Bootloader Unlock

*(Noch auszufüllen.)* Der Referenzweg nutzt den Community-Exploit auf Basis von
`amonet` für diese Gerätefamilie. Dieses Repository enthält weder das Tool noch
gerätespezifische Binärdaten wie Unlock-Codes oder Bootloader-Payloads.
Stattdessen wird später auf die passenden Upstream-Anleitungen verlinkt.

## Recovery

*(Noch auszufüllen.)* Nach dem entsperrten Bootloader folgt üblicherweise ein
Custom Recovery wie TWRP, um LineageOS zu flashen. Die passenden Upstream-Links
werden ergänzt.

## LineageOS

*(Noch auszufüllen.)* Dieses Projekt zielt auf einen **inoffiziellen**
LineageOS-18.1-Build für `checkers`. Offiziellen LineageOS-Support für dieses
Gerät gibt es nicht. Links zu Device Tree, Kernel-Quellen und Builds werden
ergänzt.

## Cody-Home-Installation

Sobald LineageOS auf dem Gerät läuft, ist Cody Home selbst eine normale
APK-Installation — siehe [`../installation/`](../installation/).

## Bekannte Hardware-Einschränkung

Das physische Mikrofon funktioniert unter LineageOS auf dieser Hardware aktuell
nicht zuverlässig. Siehe [README](../../README.md#status) und
[`../architecture/`](../architecture/). Textbasierte Nutzung von Cody Home ist
davon nicht blockiert.
