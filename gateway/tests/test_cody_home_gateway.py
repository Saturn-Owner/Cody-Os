from concurrent.futures import ThreadPoolExecutor
import hashlib
import hmac
import json
from pathlib import Path
import tempfile
import time
import unittest

from cody_home_gateway.events import CodyHomeEvent, map_hermes_event_to_state, state_event
from cody_home_gateway.security import (
    DeviceStore,
    NonceStore,
    PairingStore,
    sign_request,
    verify_device_request,
)
from cody_home_gateway.hermes_client import HermesClient


NOW = 1_800_000_000.0


def body(payload: dict) -> bytes:
    return json.dumps(payload, separators=(",", ":"), sort_keys=True).encode("utf-8")


class CodyHomeGatewayTests(unittest.TestCase):
    def test_event_schema(self):
        event = CodyHomeEvent(type="connection.ready", request_id="r1", data={"ok": True}).to_dict()
        self.assertEqual(event["version"], 1)
        self.assertEqual(event["type"], "connection.ready")
        self.assertEqual(event["request_id"], "r1")

    def test_state_mapping(self):
        self.assertEqual(state_event("IDLE")["data"]["state"], "IDLE")
        self.assertEqual(map_hermes_event_to_state("tool.started"), "WORKING")
        self.assertEqual(map_hermes_event_to_state("approval.requested"), "APPROVAL_REQUIRED")

    def test_pairing_consumes_code_once(self):
        with tempfile.TemporaryDirectory() as tmp:
            pairing = PairingStore(Path(tmp) / "pairing.json")
            code = pairing.create_code(ttl_seconds=60)
            self.assertTrue(pairing.consume(code))
            self.assertFalse(pairing.consume(code))

    def test_device_auth_accepts_valid_signature(self):
        with tempfile.TemporaryDirectory() as tmp:
            devices = DeviceStore(Path(tmp) / "devices.json")
            device = devices.add("Echo")
            nonces = NonceStore()
            payload = {"text": "Hallo Cody"}
            raw = body(payload)
            signature = sign_request(
                secret=device.secret,
                method="POST",
                path="/message",
                timestamp=str(NOW),
                nonce="n1",
                body=raw,
            )
            ok, reason = verify_device_request(
                device_store=devices,
                nonce_store=nonces,
                device_id=device.device_id,
                signature=signature,
                method="POST",
                path="/message",
                timestamp=str(NOW),
                nonce="n1",
                body=raw,
                now=NOW,
            )
            self.assertTrue(ok)
            self.assertEqual(reason, "ok")

    def test_device_auth_survives_store_reload(self):
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "devices.json"
            devices = DeviceStore(path)
            device = devices.add("Echo")
            reloaded = DeviceStore(path)
            payload = {"text": "Hallo Cody"}
            raw = body(payload)
            signature = sign_request(
                secret=device.secret,
                method="POST",
                path="/message",
                timestamp=str(NOW),
                nonce="n1",
                body=raw,
            )
            ok, reason = verify_device_request(
                device_store=reloaded,
                nonce_store=NonceStore(),
                device_id=device.device_id,
                signature=signature,
                method="POST",
                path="/message",
                timestamp=str(NOW),
                nonce="n1",
                body=raw,
                now=NOW,
            )
            self.assertTrue(ok)
            self.assertEqual(reason, "ok")

    def test_device_auth_rejects_bad_signature(self):
        with tempfile.TemporaryDirectory() as tmp:
            devices = DeviceStore(Path(tmp) / "devices.json")
            device = devices.add("Echo")
            nonces = NonceStore()
            ok, reason = verify_device_request(
                device_store=devices,
                nonce_store=nonces,
                device_id=device.device_id,
                signature="bad",
                method="POST",
                path="/message",
                timestamp=str(NOW),
                nonce="n1",
                body=body({"text": "hi"}),
                now=NOW,
            )
            self.assertFalse(ok)
            self.assertEqual(reason, "invalid_signature")
            self.assertTrue(nonces.claim("n1", now=NOW))

    def test_device_auth_rejects_expired_request(self):
        with tempfile.TemporaryDirectory() as tmp:
            devices = DeviceStore(Path(tmp) / "devices.json")
            device = devices.add("Echo")
            ok, reason = verify_device_request(
                device_store=devices,
                nonce_store=NonceStore(),
                device_id=device.device_id,
                signature="bad",
                method="POST",
                path="/message",
                timestamp=str(NOW - 100),
                nonce="n1",
                body=body({}),
                now=NOW,
            )
            self.assertFalse(ok)
            self.assertEqual(reason, "request_expired")

    def test_nonce_replay_rejected(self):
        store = NonceStore()
        self.assertTrue(store.claim("same", now=NOW))
        self.assertFalse(store.claim("same", now=NOW))

    def test_parallel_nonce_replay_accepts_once(self):
        store = NonceStore()
        with ThreadPoolExecutor(max_workers=16) as executor:
            results = list(executor.map(lambda _: store.claim("same", now=NOW), range(16)))
        self.assertEqual(sum(1 for item in results if item), 1)

    def test_hermes_client_reads_existing_session_id(self):
        with tempfile.TemporaryDirectory() as tmp:
            session_file = Path(tmp) / "session_id"
            session_file.write_text("session-123\n", encoding="utf-8")
            client = HermesClient(
                base_url="http://127.0.0.1:1",
                api_key="test-key",
                session_id_file=session_file,
            )
            self.assertTrue(client.configured())


try:
    from fastapi.testclient import TestClient
    import cody_home_gateway.app as app_module
    from cody_home_gateway.app import app
except Exception:
    TestClient = None
    app = None


class _FakeHermes:
    async def stream_message(self, text: str, *, request_id: str):
        yield {"type": "cody.message.started", "data": {}}
        yield {"type": "cody.message.delta", "data": {"text": "Hallo Echo"}}
        yield {"type": "cody.message.completed", "data": {"text": "Hallo Echo"}}


def _multipart_body(boundary: str, *, request_id: str = "voice-test", filename: str = "voice.m4a", content_type: str = "audio/mp4", audio: bytes = b"fake-audio") -> bytes:
    prefix = (
        f"--{boundary}\r\n"
        "Content-Disposition: form-data; name=\"request_id\"\r\n\r\n"
        f"{request_id}\r\n"
        f"--{boundary}\r\n"
        "Content-Disposition: form-data; name=\"input_format\"\r\n\r\n"
        "m4a\r\n"
        f"--{boundary}\r\n"
        f"Content-Disposition: form-data; name=\"audio\"; filename=\"{filename}\"\r\n"
        f"Content-Type: {content_type}\r\n\r\n"
    ).encode("utf-8")
    suffix = f"\r\n--{boundary}--\r\n".encode("utf-8")
    return prefix + audio + suffix


def _auth_headers(device, raw: bytes, *, path: str = "/voice", nonce: str = "voice-nonce") -> dict:
    timestamp = str(time.time())
    canonical = "\n".join([
        "POST",
        path,
        timestamp,
        nonce,
        hashlib.sha256(raw).hexdigest(),
    ]).encode("utf-8")
    return {
        "X-Cody-Device-Id": device.device_id,
        "X-Cody-Timestamp": timestamp,
        "X-Cody-Nonce": nonce,
        "X-Cody-Signature": hmac.new(device.secret.encode("utf-8"), canonical, hashlib.sha256).hexdigest(),
    }


@unittest.skipIf(TestClient is None, "FastAPI test client not installed")
class CodyHomeGatewayAppTests(unittest.TestCase):
    def test_health_endpoint(self):
        client = TestClient(app)
        response = client.get("/health")
        self.assertEqual(response.status_code, 200)
        payload = response.json()
        self.assertEqual(payload["service"], "cody-home-gateway")

    def test_message_requires_device_auth(self):
        client = TestClient(app)
        response = client.post("/message", json={"text": "hi"})
        self.assertEqual(response.status_code, 401)

    def test_pair_without_body_returns_400(self):
        client = TestClient(app)
        response = client.post("/pair")
        self.assertEqual(response.status_code, 400)
        self.assertEqual(response.json()["error"], "json_body_required")


    def test_voice_requires_device_auth(self):
        client = TestClient(app)
        response = client.post("/voice", data=b"")
        self.assertEqual(response.status_code, 401)

    def test_voice_rejects_unsupported_audio_format(self):
        client = TestClient(app)
        old_devices = app_module.DEVICE_STORE
        old_nonces = app_module.NONCE_STORE
        with tempfile.TemporaryDirectory() as tmp:
            try:
                app_module.DEVICE_STORE = DeviceStore(Path(tmp) / "devices.json")
                app_module.NONCE_STORE = NonceStore()
                device = app_module.DEVICE_STORE.add("Voice Unit Test")
                raw = _multipart_body("b1", filename="voice.exe", content_type="application/x-msdownload")
                headers = _auth_headers(device, raw, nonce="voice-bad-format")
                headers["Content-Type"] = "multipart/form-data; boundary=b1"
                response = client.post("/voice", content=raw, headers=headers)
            finally:
                app_module.DEVICE_STORE = old_devices
                app_module.NONCE_STORE = old_nonces
        self.assertEqual(response.status_code, 415)
        self.assertEqual(response.json()["error"], "unsupported_audio_format")

    def test_voice_multipart_hmac_returns_mp3_and_cleans_temp(self):
        import cody_home_gateway.voice as voice_module
        client = TestClient(app)
        old_devices = app_module.DEVICE_STORE
        old_nonces = app_module.NONCE_STORE
        old_hermes = app_module.HERMES
        old_transcribe = voice_module._transcribe_audio
        old_tts = voice_module._text_to_speech
        with tempfile.TemporaryDirectory() as tmp:
            try:
                app_module.DEVICE_STORE = DeviceStore(Path(tmp) / "devices.json")
                app_module.NONCE_STORE = NonceStore()
                device = app_module.DEVICE_STORE.add("Voice Unit Test")
                raw = _multipart_body("b2", audio=b"RIFFfake")
                headers = _auth_headers(device, raw, nonce="voice-ok")
                headers["Content-Type"] = "multipart/form-data; boundary=b2"
                app_module.HERMES = _FakeHermes()
                voice_module._transcribe_audio = lambda path: {"success": True, "transcript": "Sag Hallo Echo", "provider": "local"}
                def fake_tts(text, output_path):
                    with open(output_path, "wb") as fh:
                        fh.write(b"ID3fake-mp3")
                    return {"success": True, "file_path": output_path, "provider": "edge"}
                voice_module._text_to_speech = fake_tts
                response = client.post("/voice", content=raw, headers=headers)
            finally:
                app_module.DEVICE_STORE = old_devices
                app_module.NONCE_STORE = old_nonces
                app_module.HERMES = old_hermes
                voice_module._transcribe_audio = old_transcribe
                voice_module._text_to_speech = old_tts

        self.assertEqual(response.status_code, 200)
        self.assertEqual(response.headers["content-type"], "audio/mpeg")
        self.assertIn("voice.audio.ready", response.headers["x-cody-voice-events"])
        self.assertTrue(response.content.startswith(b"ID3"))
        tmp_dir = app_module.STATE_DIR / "voice_tmp"
        leftovers = [p for p in tmp_dir.iterdir() if p.name.startswith("voice_")] if tmp_dir.exists() else []
        self.assertEqual(leftovers, [])


if __name__ == "__main__":
    unittest.main()
