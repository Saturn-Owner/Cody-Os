# Security

Cody Home is early-stage, experimental, self-hosted software. There is no
managed service, no telemetry, and no vendor collecting your data — which
also means there's no one but you responsible for keeping your own
deployment secure. Please read this before running it.

## What Cody Home stores, and where

- **Device credentials** (`device_id` / `device_secret`, issued during
  pairing) are stored on the Android device only, inside
  `EncryptedSharedPreferences` (AES-256-GCM, key material in the Android
  Keystore). They are never logged, and never leave the device except as
  an HMAC signature — the raw secret itself is never transmitted after
  pairing.
- **Gateway URLs** are configured locally per deployment (see
  `android/gradle.properties.example`) and are never hardcoded or shipped
  in this repository.
- **Signing keys** for release builds are entirely your own responsibility
  — this repo never contains a keystore, and none should ever be added to
  it (see `.gitignore`).

## Authentication model

Every request between the Android client and the Gateway is authenticated
with an HMAC-SHA256 signature over a canonical string (method, path,
timestamp, nonce, and a hash of the body), signed with the paired device's
own secret. There is a clock-skew tolerance and nonce usage is expected to
be enforced Gateway-side to prevent replay. If you're implementing or
auditing a Gateway, treat the exact canonical-string format as
security-critical — a loose implementation here is the most likely place
for a real vulnerability to hide.

## Reporting a vulnerability

This project doesn't yet have a dedicated security contact or bug bounty.
If you find a security issue, please open a GitHub issue marked clearly as
a security concern, or reach out to the maintainer directly if the issue
involves something that shouldn't be posted publicly (e.g. a flaw that
would let one paired device impersonate another, or a Gateway
implementation bug). Please don't test found vulnerabilities against
anyone's live deployment but your own.

## Known limitations (be aware before you self-host)

- This is **experimental software**. It has not had an independent
  security review.
- The Gateway is not part of this repository yet — if you build or run
  one, you are responsible for its own security posture (TLS termination,
  rate limiting, secret management, etc.).
- The microphone pipeline on the reference hardware (Echo Show 5, 1st
  gen) has a known, still-under-investigation reliability issue — see the
  main README. This is a functional bug, not a known security issue, but
  is noted here for completeness given it touches audio capture.
