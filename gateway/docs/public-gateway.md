# Public Gateway Notes

This directory is intended to be copied into `~/CodyHome-Public/gateway/`.

The gateway talks to a generic Hermes-compatible HTTP API configured by environment variables. It intentionally excludes personal Hermes data such as SOUL, memory, sessions, workspace, Google credentials, provider keys, private domains/IPs, device secrets, Caddy config, and systemd secrets.

Voice V1 is turn-based and uses existing backend-side Hermes components when available:

- STT: `tools.transcription_tools.transcribe_audio()`
- Cody request: `HermesClient.stream_message()`
- TTS: `tools.tts_tool.text_to_speech_tool()` with provider `edge`

If those Hermes modules are not available in the runtime, `/voice` will return a server-side error until the host integrates equivalent backend components.
