# API Contract

The integration expects a Hermes-compatible backend with:

## Health

`GET /health`

Used to verify backend reachability.

## Sessions

`GET /api/sessions`

Returns a JSON object with `data`, where each item may include `id` and `title`.

`POST /api/sessions`

Request:

```json
{"title":"CodyOS Home"}
```

Response may contain `session_id`, `id`, or nested `session.id`.

## Streaming chat

`POST /api/sessions/{session_id}/chat/stream`

Headers:

```http
Authorization: Bearer <user-owned-hermes-api-token>
X-Hermes-Session-Key: codyos-home
```

Request:

```json
{"message":"Hello"}
```

Response: server-sent events lines beginning with `data:`. Payloads are mapped conservatively into CodyOS events.
