# Cody-Home-Gateway-Protokoll

Der öffentliche Basispfad wird über `CODY_HOME_PUBLIC_BASE_PATH` auf Reverse-
Proxy-/Client-Ebene konfiguriert. Die FastAPI-App signiert die internen Pfade
`/message`, `/voice` und `/ws`.

## Pairing

`POST /pair`

```json
{"code":"123456","device_name":"Cody Home Device"}
```

Erfolg `201` liefert `device_id`, das einmalige `device_secret` und
`protocol_version`.

## HMAC

Kanonischer String:

```text
METHOD_UPPERCASE
PATH
TIMESTAMP
NONCE
SHA256_HEX_OF_EXACT_BODY_BYTES
```

Die Signatur ist HMAC-SHA256 in Hex-Encoding mit dem Geräte-Secret. Der
Zeitstempel sind Unix-Sekunden, parsebar als Float. Die Nonce muss innerhalb der
konfigurierten Nonce-TTL eindeutig sein.

## Message

`POST /message` JSON-Body:

```json
{"request_id":"optional-client-id","text":"hello"}
```

## Voice

`POST /voice` Multipart-Body:

- `request_id` optional
- `input_format` optional
- `audio` erforderliche Datei

Signiert werden die exakten Multipart-Bytes. Bei Erfolg ist die Antwort
`audio/mpeg`.
