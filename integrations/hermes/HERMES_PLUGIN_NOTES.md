# Hermes Plugin / Extension Notes

Hermes supports plugins via:

- directory plugins with `plugin.yaml` and `__init__.py` exposing `register(ctx)`
- pip packages exposing the `hermes_agent.plugins` entry-point group
- hooks such as `on_session_start`, `on_session_end`, `pre_tool_call`, `post_tool_call`, streaming observers, and API request hooks
- plugin tool registration via `PluginContext.register_tool()`

CodyOS Phase 1 does not require a Hermes plugin. The API integration is safer and more portable because it uses the existing Hermes API server and avoids core patches.

A future optional plugin could add richer model/router status or notification hooks if Hermes does not expose those through SSE/API in a given version.
