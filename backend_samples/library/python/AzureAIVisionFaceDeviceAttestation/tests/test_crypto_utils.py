import base64
import io
from datetime import datetime, timedelta, timezone

import pytest
import tink
from tink import cleartext_keyset_handle, signature as tink_signature
from tink.proto import ecdsa_pb2, tink_pb2
from asn1crypto import core
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec, padding, rsa, utils

from azure_ai_vision_face_deviceattestation.android import chain_checks
from azure_ai_vision_face_deviceattestation.android.types import AndroidPhaseFail
from azure_ai_vision_face_deviceattestation.ios import attestation_phases
from azure_ai_vision_face_deviceattestation.ios.types import IosPhaseFail
from azure_ai_vision_face_deviceattestation.base64_utils import decode_base64
from azure_ai_vision_face_deviceattestation.cert_utils import get_extension_value, validate_certificate_path
from azure_ai_vision_face_deviceattestation.cert_utils import (
    GOOGLE_HARDWARE_ATTESTATION_ROOT_CAS, compute_cert_thumbprint, pem_to_der,
)
from azure_ai_vision_face_deviceattestation.android.keymaster_ext import (
    KEYMASTER_EXT_OID_DOTTED,
    extract_attestation_challenge_from_cert,
    parse_key_description,
)
from azure_ai_vision_face_deviceattestation.ios.constants import NONCE_OID
from azure_ai_vision_face_deviceattestation.ios.parsers import extract_nonce_from_cred_cert, parse_assertion_auth_data
from azure_ai_vision_face_deviceattestation.crypto_utils import (
    decrypt_with_private_key_ec,
    encrypt_with_public_key_ec,
    sign_data_ec,
    verify_signature_ec,
    verify_signature_ec_bytes,
)


def test_app_attest_header_preserves_unsigned_counters_and_boundaries():
    for counter in [0, 0x01020304, 0x7fffffff, 0x80000000, 0xffffffff]:
        auth_data = b"\x42" * 32 + b"\xff" + counter.to_bytes(4, "big") + b"\xee"
        assert parse_assertion_auth_data(auth_data) == {
            "rpIdHash": b"\x42" * 32, "flags": 255, "signCount": counter,
        }
    with pytest.raises(ValueError) as error:
        parse_assertion_auth_data(bytes(36))
    assert str(error.value) == "authenticatorData is 36 bytes, < 37"


def test_app_attest_credential_layout_preserves_lengths_and_truncation_errors():
    for length in [0, 1, 255, 256, 32768, 65535]:
        auth_data = (b"\x42" * 32 + b"\xff" * 5 + b"\x61" * 16
                     + length.to_bytes(2, "big") + b"\xab" * length + b"\xee")
        assert attestation_phases.parse_attest_auth_data(auth_data) == {
            "rpIdHash": b"\x42" * 32, "flags": 255, "signCount": 4294967295,
            "aaguid": b"\x61" * 16, "credIdLen": length, "credentialId": b"\xab" * length,
        }
    for length in [0, 36, 37, 54, 55]:
        auth_data = bytes(54) + b"\x01" if length == 55 else bytes(length)
        with pytest.raises(IosPhaseFail) as error:
            attestation_phases.parse_attest_auth_data(auth_data)
        if length < 37:
            assert error.value.reason == "AUTHDATA_TOO_SHORT"
            assert str(error.value) == f"authData is {length} bytes, < 37"
        elif length < 55:
            assert error.value.reason == "AUTHDATA_MISSING_ATTESTED"
            assert str(error.value) == "authData missing attested credential data"
        else:
            assert error.value.reason == "AUTHDATA_TRUNCATED"
            assert str(error.value) == "authData truncated within credentialId"


def _base64url(data: bytes) -> str:
    return base64.urlsafe_b64encode(data).decode("ascii").rstrip("=")


def test_pem_helpers_preserve_der_and_reject_invalid_blocks():
    pem = GOOGLE_HARDWARE_ATTESTATION_ROOT_CAS[0]
    certificate = x509.load_pem_x509_certificate(pem.encode("ascii"))
    der = certificate.public_bytes(serialization.Encoding.DER)
    assert pem_to_der(pem) == der
    assert pem_to_der(pem.replace("\n", "\r\n")) == der
    assert compute_cert_thumbprint(pem) == certificate.fingerprint(hashes.SHA256()).hex()
    for malformed in ["AA==", "not PEM", pem + "\n" + pem,
                      pem.replace("CERTIFICATE", "PUBLIC KEY"),
                      pem.replace("END CERTIFICATE", "END PUBLIC KEY"),
                      "-----BEGIN CERTIFICATE-----\n!\n-----END CERTIFICATE-----",
                      "-----BEGIN CERTIFICATE-----\nMAA=\n-----END CERTIFICATE-----"]:
        with pytest.raises(ValueError):
            pem_to_der(malformed)
        assert compute_cert_thumbprint(malformed) is None


@pytest.mark.parametrize("scenario", [
    "valid", "untrusted", "non-ca", "key-usage", "path-length", "expired", "future",
    "expired-root", "critical-extension", "signature", "missing", "reordered", "extra",
    "no-key-usage", "non-tls-eku", "ca-non-tls-eku", "critical-issuer", "critical-root", "ca-leaf",
    "valid-rsa", "valid-rsa-pss", "name-constraints", "valid-name-constraints", "noncritical-attestation",
])
def test_certificate_path_policy(scenario, monkeypatch):
    def reject_network(*args, **kwargs):
        pytest.fail("Certificate path validation must not access the network")

    monkeypatch.setattr("socket.socket.connect", reject_network)
    now = datetime.now(timezone.utc)
    keys = [(rsa.generate_private_key(65537, 2048) if scenario.startswith("valid-rsa")
             else ec.generate_private_key(ec.SECP256R1())) for _ in range(3)]
    names = [x509.Name([x509.NameAttribute(x509.NameOID.COMMON_NAME, label)])
             for label in ["Path Root", "Path Issuer", "Path Leaf"]]

    def make_certificate(index, issuer_index):
        before = now + timedelta(hours=1) if scenario == "future" and index == 2 else now - timedelta(days=1)
        expired = (scenario == "expired" and index == 2) or (scenario == "expired-root" and index == 0)
        after = now - timedelta(hours=1) if expired else now + timedelta(days=1)
        builder = (x509.CertificateBuilder().subject_name(names[index]).issuer_name(names[issuer_index])
                   .public_key(keys[index].public_key()).serial_number(index + 1)
                   .not_valid_before(before).not_valid_after(after))
        if index < 2:
            ca = not (scenario == "non-ca" and index == 1)
            limit = (0 if scenario == "path-length" else 1) if index == 0 else None
            builder = builder.add_extension(x509.BasicConstraints(ca, limit), critical=True)
            if scenario != "no-key-usage":
                key_cert_sign = not (scenario == "key-usage" and index == 1)
                builder = builder.add_extension(x509.KeyUsage(
                    digital_signature=not key_cert_sign, content_commitment=False,
                    key_encipherment=False, data_encipherment=False, key_agreement=False,
                    key_cert_sign=key_cert_sign, crl_sign=False, encipher_only=False, decipher_only=False,
                ), critical=True)
            if scenario == "ca-non-tls-eku":
                builder = builder.add_extension(x509.ExtendedKeyUsage([x509.ExtendedKeyUsageOID.CODE_SIGNING]), True)
            if index == 1 and scenario in {"name-constraints", "valid-name-constraints"}:
                builder = builder.add_extension(x509.NameConstraints([x509.DNSName("allowed.example")], None), True)
        if (scenario == "critical-issuer" and index == 1) or (scenario == "critical-root" and index == 0):
            builder = builder.add_extension(x509.UnrecognizedExtension(x509.ObjectIdentifier("1.2.3.4"), b"\x05\x00"), True)
        if index == 2:
            if scenario in {"name-constraints", "valid-name-constraints"}:
                dns_name = "allowed.example" if scenario == "valid-name-constraints" else "blocked.example"
                builder = builder.add_extension(x509.SubjectAlternativeName([x509.DNSName(dns_name)]), False)
            if scenario == "noncritical-attestation":
                builder = builder.add_extension(x509.UnrecognizedExtension(x509.ObjectIdentifier(NONCE_OID), b"\x30\x00"), False)
                builder = builder.add_extension(x509.UnrecognizedExtension(x509.ObjectIdentifier(KEYMASTER_EXT_OID_DOTTED), b"\x30\x00"), False)
            if scenario == "ca-leaf":
                builder = builder.add_extension(x509.BasicConstraints(True, None), True)
            builder = builder.add_extension(x509.AuthorityInformationAccess([
                x509.AccessDescription(x509.AuthorityInformationAccessOID.CA_ISSUERS,
                                       x509.UniformResourceIdentifier("http://127.0.0.1:9/issuer.der"))
            ]), critical=False)
            if scenario == "critical-extension":
                builder = builder.add_extension(x509.UnrecognizedExtension(x509.ObjectIdentifier("1.2.3.4"), b"\x05\x00"), True)
            if scenario == "non-tls-eku":
                builder = builder.add_extension(x509.ExtendedKeyUsage([x509.ExtendedKeyUsageOID.CODE_SIGNING]), True)
        if scenario == "valid-rsa-pss":
            return builder.sign(keys[issuer_index], hashes.SHA256(), rsa_padding=padding.PSS(
                mgf=padding.MGF1(hashes.SHA256()), salt_length=32))
        return builder.sign(keys[issuer_index], hashes.SHA256())

    root, issuer, leaf = [make_certificate(index, max(0, index - 1)) for index in range(3)]
    chain = [certificate.public_bytes(serialization.Encoding.DER) for certificate in [leaf, issuer, root]]
    pins = [] if scenario == "untrusted" else [root.public_bytes(serialization.Encoding.PEM).decode("ascii")]
    if scenario == "signature":
        chain[0] = chain[0][:-1] + bytes([chain[0][-1] ^ 1])
    if scenario == "missing":
        del chain[1]
    if scenario == "reordered":
        chain[0], chain[1] = chain[1], chain[0]
    if scenario == "extra":
        chain.insert(1, chain[-1])
    expected_valid = scenario in {
        "valid", "no-key-usage", "non-tls-eku", "ca-non-tls-eku", "valid-rsa", "valid-rsa-pss",
        "valid-name-constraints", "noncritical-attestation",
    }
    assert validate_certificate_path(chain, pins) == expected_valid
    assert not validate_certificate_path([chain[-1]], pins)
    assert not validate_certificate_path([b"\x30", chain[-1]], pins)
    assert not validate_certificate_path([chain[0] + b"\x00", *chain[1:]], pins)
    assert not validate_certificate_path([], pins)

    monkeypatch.setattr(chain_checks, "GOOGLE_HARDWARE_ATTESTATION_ROOT_CAS", pins)
    if pins:
        monkeypatch.setattr(attestation_phases, "APPLE_APP_ATTEST_ROOT_CAS", pins)
    if expected_valid:
        assert chain_checks.verify_android_chain_signatures_and_root(chain)["rootCASubject"] == root.subject.rfc4514_string()
        attestation_phases.verify_ios_x5c_chain(chain[0], chain[1])
        direct_leaf = make_certificate(2, 0).public_bytes(serialization.Encoding.DER)
        attestation_phases.verify_ios_x5c_chain(direct_leaf, chain[-1])
    else:
        with pytest.raises(AndroidPhaseFail) as android_failure:
            chain_checks.verify_android_chain_signatures_and_root(chain)
        assert android_failure.value.reason == ("ROOT_CA_MISMATCH" if scenario == "untrusted" else "CHAIN_PATH_INVALID")
        with pytest.raises(IosPhaseFail) as ios_failure:
            attestation_phases.verify_ios_x5c_chain(chain[0], chain[1])
        assert ios_failure.value.reason == "CHAIN_PATH_INVALID"
    with pytest.raises(AndroidPhaseFail) as root_only_failure:
        chain_checks.verify_android_chain_signatures_and_root([chain[-1]])
    assert root_only_failure.value.reason == ("ROOT_CA_MISMATCH" if scenario == "untrusted" else "CHAIN_PATH_INVALID")


@pytest.mark.parametrize("critical", [False, True])
@pytest.mark.parametrize("oid,prefix,extract", [
    (NONCE_OID, "3024a1220420", extract_nonce_from_cred_cert),
    (KEYMASTER_EXT_OID_DOTTED,
     "30340201010a01010201020a01010420", extract_attestation_challenge_from_cert),
])
def test_extension_lookup_ignores_embedded_oid(critical, oid, prefix, extract):
    oid_der = core.ObjectIdentifier(oid).dump()
    nonce = bytes(range(32))
    payload = bytes.fromhex(prefix) + nonce
    if oid == KEYMASTER_EXT_OID_DOTTED:
        payload += bytes.fromhex("040030003000")
    private_key = ec.generate_private_key(ec.SECP256R1())
    name = x509.Name([x509.NameAttribute(x509.NameOID.COMMON_NAME, "oid-test")])
    now = datetime.now(timezone.utc)
    decoy = oid_der + bytes([0x04, len(payload)]) + payload
    builder = (x509.CertificateBuilder().subject_name(name).issuer_name(name)
               .public_key(private_key.public_key()).serial_number(1)
               .not_valid_before(now - timedelta(days=1)).not_valid_after(now + timedelta(days=1))
               .add_extension(x509.UnrecognizedExtension(x509.ObjectIdentifier("1.2.3.4"), decoy), False))
    missing = builder.sign(private_key, hashes.SHA256()).public_bytes(serialization.Encoding.DER)
    assert get_extension_value(missing, oid) is None
    assert extract(missing) is None
    assert extract(b"\x30\x00") is None

    wrong_tag = bytearray(payload)
    wrong_tag[2] = 0x05
    nested_or_negative = (bytes.fromhex("3026a1240420") + nonce + b"\x05\x00"
                          if oid == NONCE_OID else payload[:4] + b"\xff" + payload[5:])
    for malformed in [b"", b"\x30", payload[:-1], bytes(wrong_tag), payload + b"\x05\x00", nested_or_negative]:
        malformed_cert = (builder.add_extension(
            x509.UnrecognizedExtension(x509.ObjectIdentifier(oid), malformed), critical)
            .sign(private_key, hashes.SHA256()).public_bytes(serialization.Encoding.DER))
        assert extract(malformed_cert) is None

    builder = builder.add_extension(x509.UnrecognizedExtension(x509.ObjectIdentifier(oid), payload), critical)
    cert = builder.sign(private_key, hashes.SHA256()).public_bytes(serialization.Encoding.DER)
    assert get_extension_value(cert, oid) == payload
    assert extract(cert) == nonce
    if oid == KEYMASTER_EXT_OID_DOTTED:
        description = parse_key_description(cert)
        assert description is not None
        assert (description.attestationVersion, description.attestationSecurityLevel,
                description.keyMintVersion, description.keyMintSecurityLevel) == (1, 1, 2, 1)

    oid_prefix, last_arc = oid.rsplit(".", 1)
    other_oid = f"{oid_prefix}.{int(last_arc) + 1}"
    other_oid_der = core.ObjectIdentifier(other_oid).dump()
    duplicate = (builder.add_extension(
        x509.UnrecognizedExtension(x509.ObjectIdentifier(other_oid), payload),
        critical).sign(private_key, hashes.SHA256()).public_bytes(serialization.Encoding.DER))
    duplicate = duplicate.replace(other_oid_der, oid_der)
    assert extract(duplicate) is None


def test_decode_base64_accepts_standard_and_urlsafe_unpadded():
    value = b"\xfb\xff\xef\xfa"

    assert decode_base64(base64.b64encode(value).decode("ascii")) == value
    assert decode_base64(_base64url(value)) == value


def test_verify_signature_accepts_urlsafe_unpadded_signature():
    private_key = ec.generate_private_key(ec.SECP256R1())
    public_key_pem = private_key.public_key().public_bytes(
        serialization.Encoding.PEM,
        serialization.PublicFormat.SubjectPublicKeyInfo,
    ).decode("ascii")
    payload = '{"challengeHash":"test"}'
    signature = private_key.sign(payload.encode("utf-8"), ec.ECDSA(hashes.SHA256()))

    assert verify_signature_ec(payload, _base64url(signature), public_key_pem)


def test_decrypt_accepts_urlsafe_unpadded_ciphertext():
    private_key = ec.generate_private_key(ec.SECP256R1())
    private_key_pem = private_key.private_bytes(
        serialization.Encoding.PEM,
        serialization.PrivateFormat.PKCS8,
        serialization.NoEncryption(),
    ).decode("ascii")
    public_key_pem = private_key.public_key().public_bytes(
        serialization.Encoding.PEM,
        serialization.PublicFormat.SubjectPublicKeyInfo,
    ).decode("ascii")
    ciphertext = encrypt_with_public_key_ec("payload", public_key_pem)

    assert ciphertext is not None
    assert decrypt_with_private_key_ec(
        _base64url(base64.b64decode(ciphertext)),
        private_key_pem,
    ) == "payload"


@pytest.mark.parametrize("encoding", ["der", "ieee-p1363"])
def test_ecdsa_interoperates_with_tink(encoding):
    tink_signature.register()
    template = tink_pb2.KeyTemplate()
    template.CopyFrom(tink_signature.signature_key_templates.ECDSA_P256)
    template.output_prefix_type = tink_pb2.RAW
    key_format = ecdsa_pb2.EcdsaKeyFormat.FromString(template.value)
    key_format.params.encoding = ecdsa_pb2.IEEE_P1363 if encoding == "ieee-p1363" else ecdsa_pb2.DER
    template.value = key_format.SerializeToString()
    handle = tink.new_keyset_handle(template)
    keyset_buffer = io.BytesIO()
    cleartext_keyset_handle.write(tink.BinaryKeysetWriter(keyset_buffer), handle)
    keyset = tink_pb2.Keyset.FromString(keyset_buffer.getvalue())
    private_proto = ecdsa_pb2.EcdsaPrivateKey.FromString(keyset.key[0].key_data.value)
    private_key = ec.derive_private_key(int.from_bytes(private_proto.key_value, "big"), ec.SECP256R1())
    public_key = private_key.public_key()
    public_pem = public_key.public_bytes(serialization.Encoding.PEM,
                                         serialization.PublicFormat.SubjectPublicKeyInfo).decode("ascii")
    private_pem = private_key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,
                                           serialization.NoEncryption()).decode("ascii")
    payload = ' { "challengeHash": "test" }\n'
    data = payload.encode("utf-8")
    signature = handle.primitive(tink_signature.PublicKeySign).sign(data)
    for key in [public_key, public_pem]:
        assert verify_signature_ec_bytes(data, signature, key, encoding=encoding)
        assert not verify_signature_ec_bytes(data + b"!", signature, key, encoding=encoding)
        assert not verify_signature_ec_bytes(data, signature + b"\x00", key, encoding=encoding)
    assert not verify_signature_ec_bytes(data, signature, ec.generate_private_key(ec.SECP256R1()).public_key(), encoding=encoding)
    if encoding == "der":
        assert verify_signature_ec(payload, _base64url(signature), public_pem)
    signed = sign_data_ec(payload, private_pem)
    assert signed is not None
    signature = base64.b64decode(signed)
    if encoding == "ieee-p1363":
        first, second = utils.decode_dss_signature(signature)
        signature = first.to_bytes(32, "big") + second.to_bytes(32, "big")
    handle.public_keyset_handle().primitive(tink_signature.PublicKeyVerify).verify(signature, data)


def test_ecdsa_rejects_invalid_keys_and_signatures():
    data = b"payload"
    public_key = ec.generate_private_key(ec.SECP256R1()).public_key()
    for malformed in [b"", b"\x00" * 63, b"\x00" * 64, b"\x00" * 65, b"\xff" * 64]:
        assert not verify_signature_ec_bytes(data, malformed, public_key, encoding="ieee-p1363")
        assert not verify_signature_ec_bytes(data, malformed, public_key)
    for private_key in [ec.generate_private_key(ec.SECP384R1()), rsa.generate_private_key(65537, 2048)]:
        private_pem = private_key.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,
                                               serialization.NoEncryption()).decode("ascii")
        assert sign_data_ec("payload", private_pem) is None
        signature = (private_key.sign(data, ec.ECDSA(hashes.SHA256()))
                     if isinstance(private_key, ec.EllipticCurvePrivateKey) else b"invalid")
        public_key = private_key.public_key()
        public_pem = public_key.public_bytes(serialization.Encoding.PEM,
                                             serialization.PublicFormat.SubjectPublicKeyInfo).decode("ascii")
        assert not verify_signature_ec_bytes(data, signature, public_key)
        assert not verify_signature_ec_bytes(data, signature, public_pem)
    assert sign_data_ec("payload", "invalid PEM") is None
    assert not verify_signature_ec("payload", "!", "invalid PEM")
