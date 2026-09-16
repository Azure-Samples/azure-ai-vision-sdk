import time

import pytest

from azure_ai_vision_face_deviceattestation.android import chain_checks
from azure_ai_vision_face_deviceattestation.android.integrity_checks import (
    verify_integrity_timestamp,
)
from azure_ai_vision_face_deviceattestation.android.keymaster_ext import KeyDescription
from azure_ai_vision_face_deviceattestation.android.types import AndroidPhaseFail


def test_play_integrity_timestamp_accepts_fresh_value():
    verify_integrity_timestamp({
        "requestDetails": {"timestampMillis": str(int(time.time() * 1000))},
    })


@pytest.mark.parametrize("verdict", [
    {},
    {"requestDetails": {}},
    {"requestDetails": {"timestampMillis": "not-a-timestamp"}},
])
def test_play_integrity_timestamp_rejects_missing_or_invalid_value(verdict):
    with pytest.raises(AndroidPhaseFail) as error:
        verify_integrity_timestamp(verdict)

    assert error.value.reason == "INTEGRITY_TIMESTAMP_INVALID"


@pytest.mark.parametrize("security_level", [1, 2])
def test_hardware_attestation_security_level_accepts_tee_or_strongbox(monkeypatch, security_level):
    description = KeyDescription(1, security_level, 2, 1, b"challenge")
    monkeypatch.setattr(chain_checks, "parse_key_description", lambda _: description)

    chain_checks.verify_attestation_security_level(b"certificate")


@pytest.mark.parametrize("description", [
    None,
    KeyDescription(1, 0, 2, 1, b"challenge"),
    KeyDescription(1, 3, 2, 1, b"challenge"),
])
def test_hardware_attestation_security_level_rejects_missing_software_or_unknown(monkeypatch, description):
    monkeypatch.setattr(chain_checks, "parse_key_description", lambda _: description)

    with pytest.raises(AndroidPhaseFail) as error:
        chain_checks.verify_attestation_security_level(b"certificate")

    expected_reason = "KEYMASTER_EXT_MISSING" if description is None else "ATTESTATION_SECURITY_LEVEL_INVALID"
    assert error.value.reason == expected_reason