# Device Setup

## Supported Device

| | |
|---|---|
| Device | Amazon Echo Show 5, 1st Generation (2019) |
| Codename | `checkers` |
| Model | H23K37 |
| SoC | MediaTek MT8163 |
| Target OS | LineageOS 18.1 (Android 11), unofficial community build |

No other Echo Show model or generation is currently supported. The unlock
exploit and kernel/device tree this relies on are specific to this exact
hardware revision.

> **This process unlocks the bootloader and replaces the stock Fire OS with
> a community-built LineageOS image. It carries real risk of permanently
> bricking the device, voids any warranty, and is not reversible back to a
> fully-functioning stock state in every case.** Only proceed on hardware
> you're willing to lose. None of the following is Cody Home's own tooling —
> all of it is third-party, upstream work this project depends on but does
> not maintain or vendor.

## Bootloader Unlock

*(To be completed.)* The reference unlock path uses the community
`amonet`-based exploit for this device family. This repository does not
include that tool or any device-specific binaries (unlock codes, bootloader
payloads) — these are per-device and/or third-party, and are documented
upstream, not here. This section will link to the exact upstream guide and
tool release once finalized.

## Recovery

*(To be completed.)* Installing a custom recovery (TWRP) is the usual next
step after an unlocked bootloader, used to flash the LineageOS build itself.
Will link to the upstream TWRP source/build for this device.

## LineageOS

*(To be completed.)* This project targets an **unofficial** LineageOS 18.1
build for `checkers` — there is no official LineageOS support for this
device. Will link to the upstream device tree, kernel source, and where to
obtain a build.

## Cody Home Installation

Once LineageOS is running on the device, installing Cody Home itself is just
installing an APK — see [`../installation/`](../installation/) for that part.

## Known hardware caveat

The physical microphone does not currently work reliably under LineageOS on
this hardware — see the main [README](../../README.md#status) and
[`../architecture/`](../architecture/) for what's been diagnosed so far. This
does not block installing or using Cody Home for text-based interaction.
