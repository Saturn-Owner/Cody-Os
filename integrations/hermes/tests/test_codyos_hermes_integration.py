import asyncio
import json
import os
import unittest

from codyos_hermes_integration import HermesBackendConfig, map_event_type_to_character_state, map_hermes_sse_to_codyos_event


class HermesIntegrationTests(unittest.TestCase):
    def test_env_config_without_secret_defaults_to_unconfigured(self):
        old = dict(os.environ)
        try:
            for key in list(os.environ):
                if key.startswith("CODYOS_HERMES_") or key.startswith("CODY_HOME_HERMES_") or key == "API_SERVER_KEY":
                    os.environ.pop(key, None)
            cfg = HermesBackendConfig.from_env()
            self.assertEqual(cfg.api_base, "http://127.0.0.1:8642")
            self.assertEqual(cfg.api_key, "")
        finally:
            os.environ.clear()
            os.environ.update(old)

    def test_event_state_mapping(self):
        self.assertEqual(map_event_type_to_character_state("tool.started"), "WORKING")
        self.assertEqual(map_event_type_to_character_state("approval.requested"), "APPROVAL_REQUIRED")
        self.assertEqual(map_event_type_to_character_state("voice.tts.started"), "SPEAKING")

    def test_maps_delta_payload(self):
        event = map_hermes_sse_to_codyos_event({"delta": "hi"})
        self.assertEqual(event["type"], "cody.message.delta")
        self.assertEqual(event["data"]["text"], "hi")
        self.assertEqual(event["state"], "THINKING")

    def test_maps_completed_messages_payload(self):
        event = map_hermes_sse_to_codyos_event({"completed": True, "messages": [{"role": "assistant", "content": "done"}]})
        self.assertEqual(event["type"], "cody.message.completed")
        self.assertEqual(event["data"]["text"], "done")
        self.assertEqual(event["state"], "SUCCESS")

    def test_forwards_approval_without_resolving(self):
        payload = {"event": "approval.requested", "request_id": "r1"}
        event = map_hermes_sse_to_codyos_event(payload)
        self.assertEqual(event["type"], "approval.requested")
        self.assertEqual(event["data"], payload)
        self.assertEqual(event["state"], "APPROVAL_REQUIRED")


if __name__ == "__main__":
    unittest.main()
