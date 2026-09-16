"""Flow + reason-code tests for the attestation service (no real crypto material).

Covers the validation branches, the challenge happy path, session helpers, and
the config-driven well-known documents. The crypto-heavy success paths for
register/verify/token/digest require real device attestation material and are
exercised end-to-end by the backend sample, not here.
"""

from __future__ import annotations

import asyncio
import base64
import json

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from azure_ai_vision_face_deviceattestation import CertificateData, StorageError, UpdateResult
from azure_ai_vision_face_deviceattestation.cert_store import update_certificate_metadata
from azure_ai_vision_face_deviceattestation.crypto_utils import generate_server_key_pair_ec, encrypt_with_public_key_ec, decrypt_with_private_key_ec

from azure_ai_vision_face_deviceattestation import (
    AttestationChallengeRequest,
    AttestationConfig,
    AttestationRegisterRequest,
    AttestationVerifyRequest,
    LivenessDigestRequest,
    SessionRecord,
    SessionTokenRequest,
    create_attestation_service,
)

from .fakes import InMemoryStore

_SID = "12345678-1234-1234-1234-123456789abc"


def make_service():
    store = InMemoryStore()
    svc = create_attestation_service(
        AttestationConfig(ios_app_id="TEAM.bundle", android_package_name="com.example"),
        store,
    )
    return svc, store


async def test_challenge_missing_session_id():
    svc, _ = make_service()
    out = await svc.challenge(AttestationChallengeRequest(None, "client-1", "ios"))
    assert out.status == 400 and out.code == "MISSING_SESSION_ID" and out.ok is False


async def test_challenge_invalid_session_id():
    svc, _ = make_service()
    out = await svc.challenge(AttestationChallengeRequest("not-a-uuid", "client-1", "ios"))
    assert out.status == 400 and out.code == "INVALID_SESSION_ID"


async def test_challenge_missing_client_id():
    svc, _ = make_service()
    out = await svc.challenge(AttestationChallengeRequest(_SID, None, "ios"))
    assert out.status == 400 and out.code == "MISSING_CLIENT_ID"


async def test_challenge_invalid_system():
    svc, _ = make_service()
    out = await svc.challenge(AttestationChallengeRequest(_SID, "client-1", "windows"))
    assert out.status == 400 and out.code == "INVALID_SYSTEM"


async def test_challenge_session_not_found():
    svc, _ = make_service()
    out = await svc.challenge(AttestationChallengeRequest(_SID, "client-1", "ios"))
    assert out.status == 404 and out.code == "SESSION_NOT_FOUND"


async def test_challenge_happy_path_then_already_exists():
    svc, store = make_service()
    await store.set_session(_SID, SessionRecord(token="tok", data={}))

    out = await svc.challenge(AttestationChallengeRequest(_SID, "client-1", "iOS"))
    assert out.status == 200 and out.ok
    assert out.body["clientId"] == "client-1"
    assert out.body["system"] == "ios"
    assert len(out.body["challengeHash"]) == 64  # sha256 hex

    # challenge state is now persisted -> a second call conflicts
    out2 = await svc.challenge(AttestationChallengeRequest(_SID, "client-1", "ios"))
    assert out2.status == 409 and out2.code == "CHALLENGE_ALREADY_EXISTS"


async def test_register_invalid_json_body():
    svc, store = make_service()
    await store.set_session(_SID, SessionRecord(token="tok", data={"challengeHash": "x", "clientId": "c", "system": "ios"}))
    out = await svc.register(AttestationRegisterRequest(_SID, "c", "ios", None))
    assert out.status == 400 and out.code == "INVALID_JSON_BODY"


async def test_register_missing_body_fields():
    svc, store = make_service()
    await store.set_session(_SID, SessionRecord(token="tok", data={}))
    out = await svc.register(AttestationRegisterRequest(_SID, "c", "ios", {"payload": ""}))
    assert out.status == 400 and out.code == "MISSING_BODY_FIELDS"


async def test_verify_session_not_found():
    svc, _ = make_service()
    body = {"payload": '{"challengeHash":"x","encryptionPublicCert":"-----BEGIN CERTIFICATE-----\\nAA\\n-----END CERTIFICATE-----"}',
            "authPublicCert": "-----BEGIN CERTIFICATE-----\nAA\n-----END CERTIFICATE-----",
            "signature": "sig"}
    out = await svc.verify(AttestationVerifyRequest(_SID, "c", "ios", body))
    assert out.status == 404 and out.code == "SESSION_NOT_FOUND"


async def test_token_missing_encrypted_data():
    svc, store = make_service()
    await store.set_session(_SID, SessionRecord(token="tok", data={}))
    out = await svc.session_token(SessionTokenRequest(_SID, {"signature": "y"}))
    assert out.status == 400 and out.code == "MISSING_ENCRYPTED_DATA"


async def test_token_server_keys_not_generated():
    svc, store = make_service()
    await store.set_session(_SID, SessionRecord(token="tok", data={}))
    out = await svc.session_token(SessionTokenRequest(_SID, {"encryptedData": "x", "signature": "y"}))
    assert out.status == 409 and out.code == "SERVER_KEYS_NOT_GENERATED"


async def test_digest_session_not_found():
    svc, _ = make_service()
    out = await svc.liveness_digest(LivenessDigestRequest(_SID, {"encryptedData": "x", "signature": "y"}))
    assert out.status == 404 and out.code == "SESSION_NOT_FOUND"


async def test_session_helpers():
    svc, _ = make_service()
    assert await svc.session_exists(_SID) is False
    assert await svc.save_session(_SID, "tok") == _SID
    assert await svc.session_exists(_SID) is True


async def test_get_liveness_outcome():
    svc, store = make_service()
    await store.set_session(_SID, SessionRecord(token="tok", data={"digestCompleted": True, "digest": "abc123"}))
    outcome = await svc.get_liveness_outcome(_SID)
    assert outcome.completed is True and outcome.client_digest == "abc123"

    empty = await svc.get_liveness_outcome("00000000-0000-0000-0000-000000000000")
    assert empty.completed is False


def test_well_known_from_config():
    svc, _ = make_service()
    aasa = svc.apple_app_site_association()
    assert aasa["applinks"]["details"][0]["appIDs"] == ["TEAM.bundle"]
    links = svc.android_asset_links()
    assert links[0]["target"]["package_name"] == "com.example"


async def test_snapshot_cas_rejects_stale_versions_and_recreation():
    _, store = make_service()
    record = SessionRecord("token", {"nested": {"counter": 0}})
    assert await store.set_session(_SID, record)
    assert not await store.set_session(_SID, record)
    snapshot = await store.get_session(_SID)
    snapshot.value.data["nested"]["counter"] = 2
    assert (await store.get_session(_SID)).value.data["nested"]["counter"] == 0
    assert await store.update_session(_SID, snapshot.version, snapshot.value) == UpdateResult.APPLIED
    assert await store.update_session(_SID, snapshot.version, record) == UpdateResult.CONFLICT
    del store.sessions[_SID]
    assert await store.update_session(_SID, snapshot.version, record) == UpdateResult.MISSING_OR_EXPIRED
    assert await store.set_session(_SID, record)
    assert await store.update_session(_SID, snapshot.version, record) == UpdateResult.CONFLICT


async def test_certificate_counters_cannot_regress():
    _, store = make_service()
    await store.set_certificate("cert", CertificateData("client", "ios", "cert", "pem", "", "", {}))
    lower = await store.get_certificate("cert")
    higher = await store.get_certificate("cert")
    assert await update_certificate_metadata(store, "cert", {"lastAssertionSignCount": 2}, higher)
    assert not await update_certificate_metadata(store, "cert", {"lastAssertionSignCount": 1}, lower)
    assert (await store.get_certificate("cert")).value.metadata["lastAssertionSignCount"] == 2


async def test_concurrent_signed_tokens_and_storage_failure(monkeypatch):
    svc, store = make_service()
    public_key, private_key = generate_server_key_pair_ec()
    data = {"serverKeyGenerated": True, "certRegistered": True, "challengeHash": "challenge",
            "clientId": "client", "system": "android", "clientAuthPublicKey": public_key,
            "clientEncryptionPublicKey": public_key, "serverEncryptionPrivateKey": private_key}
    encrypted = encrypt_with_public_key_ec(json.dumps(data), public_key)
    signer = serialization.load_pem_private_key(private_key.encode(), password=None)
    signature = base64.b64encode(signer.sign(encrypted.encode(), ec.ECDSA(hashes.SHA256()))).decode()
    request = SessionTokenRequest(_SID, {"encryptedData": encrypted, "signature": signature})
    await store.set_session(_SID, SessionRecord("secret-token", data))
    original_read = store.get_session
    arrived = 0
    both_read = asyncio.Event()

    async def synchronized_read(sid):
        nonlocal arrived
        snapshot = await original_read(sid)
        arrived += 1
        if arrived == 2:
            both_read.set()
        await both_read.wait()
        return snapshot

    monkeypatch.setattr(store, "get_session", synchronized_read)
    responses = await asyncio.gather(svc.session_token(request), svc.session_token(request))
    assert sorted(response.status for response in responses) == [200, 409]
    winner = next(response for response in responses if response.ok)
    assert json.loads(decrypt_with_private_key_ec(winner.body["encryptedData"], private_key))["token"] == "secret-token"

    async def failed_write(*args):
        raise StorageError("Injected storage failure")

    store.sessions.clear()
    await store.set_session(_SID, SessionRecord("secret-token", data))
    monkeypatch.setattr(store, "update_session", failed_write)
    result = await svc.session_token(request)
    assert result.status == 503
    assert "encryptedData" not in result.body
    assert not (await store.get_session(_SID)).value.data.get("authCompleted")
