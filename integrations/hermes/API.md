# API-Vertrag

Die Integration erwartet ein Hermes-kompatibles Backend mit:

## Health

`GET /health`

Wird genutzt, um die Erreichbarkeit des Backends zu prüfen.

## Sessions

`GET /api/sessions`

Liefert ein JSON-Objekt mit `data`; einzelne Einträge können `id` und `title`
enthalten.

`POST /api/sessions`

Anfrage:

```json
{"title":"CodyOS Home"}
```

Die Antwort kann `session_id`, `id` oder verschachtelt `session.id` enthalten.

## Streaming-Chat

`POST /api/sessions/{session_id}/chat/stream`

Headers:

```http
Authorization: Bearer <user-owned-hermes-api-token>
X-Hermes-Session-Key: codyos-home
```

Anfrage:

```json
{"message":"Hello"}
```

Antwort: Server-Sent-Events-Zeilen, die mit `data:` beginnen. Payloads werden
konservativ auf CodyOS-Events gemappt.
