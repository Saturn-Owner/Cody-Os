# Cody Home Gateway Protocol

Public base path is configured by `CODY_HOME_PUBLIC_BASE_PATH` at the reverse proxy/client layer. The FastAPI app signs internal paths: `/message`, `/voice`, and `/ws`.

## Pairing

`POST /pair`

```json
{"code":"123456","device_name":"Cody Home Device"}
```

Success `201` returns `device_id`, one-time `device_secret`, and `protocol_version`.

## HMAC

Canonical string:

```text
METHOD_UPPERCASE
PATH
TIMESTAMP
NONCE
SHA256_HEX_OF_EXACT_BODY_BYTES
```

Signature is HMAC-SHA256 hex with the device secret. Timestamp is Unix seconds parseable as float. Nonce must be unique during the configured nonce TTL.

## Message

`POST /message` JSON body:

```json
{"request_id":"optional-client-id","text":"hello"}
```

## Voice

`POST /voice` multipart body:

- `request_id` optional
- `input_format` optional
- `audio` required file

The exact multipart bytes are signed. Response is `audio/mpeg` when successful.
