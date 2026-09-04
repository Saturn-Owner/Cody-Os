# CodyOS

**Open, self-hosted AI surfaces for personal agents.**

> **Status: EARLY DEVELOPMENT / EXPERIMENTAL.** This is a hobby project in
> active progress, not a finished product. Expect rough edges, incomplete
> pieces, and breaking changes. See [Status](#status) below before you invest
> time in it.

CodyOS is the broader project. Its first working component is **Cody Home**:
a smart-display experience for a jailbroken, LineageOS-based Echo Show 5.
Cody Home talks to your own self-hosted AI backend through a secure Gateway —
no provider keys on the display, no bundled personal agent data, your hardware
and your backend.

CodyOS does **not** include anyone's personal Hermes agent. Hermes is an
external, self-hosted backend that users configure themselves. The code here
contains a Gateway and an optional generic Hermes integration, not Hermes core,
not memory, not sessions, and not provider credentials.

## Architecture

```text
Echo Show 5
↓
Cody Home Android
↓
Cody Home Gateway
↓
Hermes Integration
↓
User's own Hermes instance
```

Future CodyOS ROM work is a long-term goal and is not included yet.

## What it looks like

Cody Home shows a dashboard with the time, your agent's connection status,
current activity, and an animated character ("Cody") that reflects what's
happening — listening, thinking, speaking, idle. A dimmed ambient screen takes
over after a period of inactivity.

## Features

- Native Android app (Kotlin, Jetpack Compose) — no WebView, no hybrid shell
- Animated Cody companion with distinct **character states** (idle, listening,
  thinking, working, speaking, success, error, offline)
- **Ambient mode** — dims and simplifies after inactivity, wakes on touch
- **Secure pairing** — one-time pairing code exchange, credentials stored in
  `EncryptedSharedPreferences` (AES-256-GCM, Android Keystore-backed)
- **HMAC-SHA256 request authentication** on every call to the Gateway
- **HTTPS + WebSocket Gateway protocol**, with automatic reconnect and backoff
- **Hermes integration** — optional bridge to a user's own Hermes-style backend
  (see [`integrations/hermes/`](integrations/hermes/))
- Text requests to your agent, with streamed status updates
- Voice pipeline implemented end-to-end (record → upload → STT → agent → TTS →
  playback) — see [Status](#status) for the current hardware caveat

## Status

This project is **not** ready for general use. Concretely, right now:

- Cody Home Android, pairing flow, and Gateway protocol (HMAC auth, text
  requests, reconnect logic) work and have been tested against a real
  Gateway.
- The voice pipeline is fully implemented and has been verified working
  **end-to-end when fed a pre-recorded audio file** — upload, speech-to-text,
  agent response, text-to-speech, and playback on the Echo's speaker all work.
- **The physical microphone on the reference hardware does not yet reliably
  work.** This has been traced deep into the audio stack (see
  [`docs/architecture/`](docs/architecture/) once published) — it is not an
  application bug, but appears to be a hardware/driver-level issue specific to
  this LineageOS port. This is under active investigation.
- The Gateway (the AI-agent-facing backend this app talks to) is included in
  [`gateway/`](gateway/).
- The Hermes integration is included as a generic extension point in
  [`integrations/hermes/`](integrations/hermes/).

## Repository layout

```
CodyOS/
├── android/           Cody Home native Android client for the Echo
├── gateway/           FastAPI Gateway for authenticated device transport
├── integrations/
│   └── hermes/         Optional generic bridge for a user's own Hermes backend
├── docs/
│   ├── device-setup/    Unlocking/flashing the reference hardware
│   ├── installation/    Installing Cody Home once the device is ready
│   ├── architecture/     How the pieces fit together
│   └── screenshots/
├── assets/branding/
└── scripts/
```

## Supported device

Amazon Echo Show 5, 1st generation (2019) — codename `checkers`, model
`H23K37`, running an unofficial LineageOS 18.1 build. See
[`docs/device-setup/README.md`](docs/device-setup/README.md) for what's
involved before you can install Cody Home at all — this requires unlocking
the bootloader and flashing a community LineageOS build; it is not a
supported or reversible-without-risk process.

## Getting started

1. Prepare the device — see [`docs/device-setup/`](docs/device-setup/).
2. Install Cody Home — see [`docs/installation/`](docs/installation/).
3. Point it at your own Gateway — copy
   [`android/gradle.properties.example`](android/gradle.properties.example) to
   `android/gradle.properties` and fill in your Gateway's URLs before building.

## Security

See [`SECURITY.md`](SECURITY.md) for the authentication model, what's stored
where, and how to report a vulnerability.

## License

Apache License 2.0. See [`LICENSE`](LICENSE).
