# Hinweise zu Hermes-Plugins und Erweiterungen

Hermes unterstützt Plugins über:

- Verzeichnis-Plugins mit `plugin.yaml` und `__init__.py`, die `register(ctx)`
  bereitstellen
- pip-Pakete mit der Entry-Point-Gruppe `hermes_agent.plugins`
- Hooks wie `on_session_start`, `on_session_end`, `pre_tool_call`,
  `post_tool_call`, Streaming-Observer und API-Request-Hooks
- Tool-Registrierung über `PluginContext.register_tool()`

CodyOS Phase 1 benötigt kein Hermes-Plugin. Die API-Integration ist portabler
und sicherer, weil sie den vorhandenen Hermes-API-Server nutzt und Core-Patches
vermeidet.

Ein späteres optionales Plugin könnte detailliertere Model-/Router-Statusdaten
oder Notification-Hooks ergänzen, falls eine Hermes-Version diese nicht über
SSE/API bereitstellt.
