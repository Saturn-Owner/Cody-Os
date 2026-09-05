# Event-Mapping

Bekannte Hermes-/CodyOS-Events:

- `cody.message.started` → `THINKING`
- `cody.message.delta` → `THINKING`
- `cody.message.completed` → `SUCCESS`
- `task.started` → `THINKING`
- `task.progress` → `WORKING`
- `task.completed` → `SUCCESS`
- `task.failed` → `ERROR`
- `tool.started` → `WORKING`
- `tool.completed` → `THINKING`
- `tool.failed` → `ERROR`
- `model.changed` → `THINKING`
- `notification` → `IDLE`
- `approval.requested` → `APPROVAL_REQUIRED`
- `approval.resolved` → `THINKING`
- `voice.transcription.started` → `THINKING`
- `voice.transcription.completed` → `THINKING`
- `voice.tts.started` → `SPEAKING`
- `voice.tts.completed` → `SPEAKING`
- `voice.audio.ready` → `SPEAKING`

Approval-Events sind nur Anzeige-/Transport-Events. Die eigentliche
Genehmigungslogik bleibt bei Hermes.
