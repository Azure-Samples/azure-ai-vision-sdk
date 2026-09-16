"""
Certificate utilities for path validation and pinning.

PEM <-> DER helpers, SHA-256 thumbprint, public-key extraction (P-256 only),
certificate-path verification, and pinned-CA membership.
"""

from __future__ import annotations

import hashlib
import logging
from dataclasses import dataclass
from datetime import datetime, timezone
from typing import Optional

from cryptography import x509
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.x509.verification import Criticality, ExtensionPolicy, Policy, PolicyBuilder, Store

log = logging.getLogger("cert_utils")


def _validate_ca_key_usage(
    policy: Policy, certificate: x509.Certificate, extension: Optional[x509.KeyUsage]
) -> None:
    if extension is not None and not extension.key_cert_sign:
        raise ValueError("Issuer key usage does not allow certificate signing")


def validate_certificate_path(chain_der: list[bytes], pinned_pems: list[str]) -> bool:
    """Validate an exact leaf-first attestation path offline using only a pinned root."""
    try:
        if len(chain_der) < 2 or not matches_pinned_ca(chain_der[-1], pinned_pems):
            return False
        certificates = [x509.load_der_x509_certificate(der) for der in chain_der]
        now = datetime.now(timezone.utc)
        for certificate in certificates:
            if not certificate.not_valid_before_utc <= now <= certificate.not_valid_after_utc:
                return False
        ca_policy = (ExtensionPolicy.webpki_defaults_ca()
                     .may_be_present(x509.KeyUsage, Criticality.AGNOSTIC, _validate_ca_key_usage)
                     .may_be_present(x509.AuthorityKeyIdentifier, Criticality.NON_CRITICAL, None)
                     .may_be_present(x509.ExtendedKeyUsage, Criticality.AGNOSTIC, None))
        leaf_policy = (ExtensionPolicy.webpki_defaults_ee()
                       .may_be_present(x509.AuthorityKeyIdentifier, Criticality.NON_CRITICAL, None)
                       .may_be_present(x509.SubjectAlternativeName, Criticality.AGNOSTIC, None)
                       .may_be_present(x509.ExtendedKeyUsage, Criticality.AGNOSTIC, None))
        verifier = (PolicyBuilder().store(Store([certificates[-1]])).time(now)
                    .extension_policies(ca_policy=ca_policy, ee_policy=leaf_policy)
                    .build_client_verifier())
        verified = verifier.verify(certificates[0], certificates[1:-1])
        return [certificate.public_bytes(serialization.Encoding.DER) for certificate in verified.chain] == chain_der
    except Exception as exception:
        log.debug("Certificate path validation failed: %s", exception)
        return False


def get_extension_value(cert_der: bytes, oid: str) -> Optional[bytes]:
    try:
        certificate = x509.load_der_x509_certificate(cert_der)
        extension = certificate.extensions.get_extension_for_oid(x509.ObjectIdentifier(oid))
        return extension.value.public_bytes()
    except (ValueError, x509.ExtensionNotFound, x509.DuplicateExtension):
        return None


# Pinned Google Hardware Attestation Root CAs (Android Key Attestation).
# https://developer.android.com/privacy-and-security/security-key-attestation#root_certificate
GOOGLE_HARDWARE_ATTESTATION_ROOT_CAS: list[str] = [
    """-----BEGIN CERTIFICATE-----
MIIFHDCCAwSgAwIBAgIJAPHBcqaZ6vUdMA0GCSqGSIb3DQEBCwUAMBsxGTAXBgNV
BAUTEGY5MjAwOWU4NTNiNmIwNDUwHhcNMjIwMzIwMTgwNzQ4WhcNNDIwMzE1MTgw
NzQ4WjAbMRkwFwYDVQQFExBmOTIwMDllODUzYjZiMDQ1MIICIjANBgkqhkiG9w0B
AQEFAAOCAg8AMIICCgKCAgEAr7bHgiuxpwHsK7Qui8xUFmOr75gvMsd/dTEDDJdS
Sxtf6An7xyqpRR90PL2abxM1dEqlXnf2tqw1Ne4Xwl5jlRfdnJLmN0pTy/4lj4/7
tv0Sk3iiKkypnEUtR6WfMgH0QZfKHM1+di+y9TFRtv6y//0rb+T+W8a9nsNL/ggj
nar86461qO0rOs2cXjp3kOG1FEJ5MVmFmBGtnrKpa73XpXyTqRxB/M0n1n/W9nGq
C4FSYa04T6N5RIZGBN2z2MT5IKGbFlbC8UrW0DxW7AYImQQcHtGl/m00QLVWutHQ
oVJYnFPlXTcHYvASLu+RhhsbDmxMgJJ0mcDpvsC4PjvB+TxywElgS70vE0XmLD+O
JtvsBslHZvPBKCOdT0MS+tgSOIfga+z1Z1g7+DVagf7quvmag8jfPioyKvxnK/Eg
sTUVi2ghzq8wm27ud/mIM7AY2qEORR8Go3TVB4HzWQgpZrt3i5MIlCaY504LzSRi
igHCzAPlHws+W0rB5N+er5/2pJKnfBSDiCiFAVtCLOZ7gLiMm0jhO2B6tUXHI/+M
RPjy02i59lINMRRev56GKtcd9qO/0kUJWdZTdA2XoS82ixPvZtXQpUpuL12ab+9E
aDK8Z4RHJYYfCT3Q5vNAXaiWQ+8PTWm2QgBR/bkwSWc+NpUFgNPN9PvQi8WEg5Um
AGMCAwEAAaNjMGEwHQYDVR0OBBYEFDZh4QB8iAUJUYtEbEf/GkzJ6k8SMB8GA1Ud
IwQYMBaAFDZh4QB8iAUJUYtEbEf/GkzJ6k8SMA8GA1UdEwEB/wQFMAMBAf8wDgYD
VR0PAQH/BAQDAgIEMA0GCSqGSIb3DQEBCwUAA4ICAQB8cMqTllHc8U+qCrOlg3H7
174lmaCsbo/bJ0C17JEgMLb4kvrqsXZs01U3mB/qABg/1t5Pd5AORHARs1hhqGIC
W/nKMav574f9rZN4PC2ZlufGXb7sIdJpGiO9ctRhiLuYuly10JccUZGEHpHSYM2G
tkgYbZba6lsCPYAAP83cyDV+1aOkTf1RCp/lM0PKvmxYN10RYsK631jrleGdcdkx
oSK//mSQbgcWnmAEZrzHoF1/0gso1HZgIn0YLzVhLSA/iXCX4QT2h3J5z3znluKG
1nv8NQdxei2DIIhASWfu804CA96cQKTTlaae2fweqXjdN1/v2nqOhngNyz1361mF
mr4XmaKH/ItTwOe72NI9ZcwS1lVaCvsIkTDCEXdm9rCNPAY10iTunIHFXRh+7KPz
lHGewCq/8TOohBRn0/NNfh7uRslOSZ/xKbN9tMBtw37Z8d2vvnXq/YWdsm1+JLVw
n6yYD/yacNJBlwpddla8eaVMjsF6nBnIgQOf9zKSe06nSTqvgwUHosgOECZJZ1Eu
zbH4yswbt02tKtKEFhx+v+OTge/06V+jGsqTWLsfrOCNLuA8H++z+pUENmpqnnHo
vaI47gC+TNpkgYGkkBT6B/m/U01BuOBBTzhIlMEZq9qkDWuM2cA5kW5V3FJUcfHn
w1IdYIg2Wxg7yHcQZemFQg==
-----END CERTIFICATE-----""",
    """-----BEGIN CERTIFICATE-----
MIICIjCCAaigAwIBAgIRAISp0Cl7DrWK5/8OgN52BgUwCgYIKoZIzj0EAwMwUjEc
MBoGA1UEAwwTS2V5IEF0dGVzdGF0aW9uIENBMTEQMA4GA1UECwwHQW5kcm9pZDET
MBEGA1UECgwKR29vZ2xlIExMQzELMAkGA1UEBhMCVVMwHhcNMjUwNzE3MjIzMjE4
WhcNMzUwNzE1MjIzMjE4WjBSMRwwGgYDVQQDDBNLZXkgQXR0ZXN0YXRpb24gQ0Ex
MRAwDgYDVQQLDAdBbmRyb2lkMRMwEQYDVQQKDApHb29nbGUgTExDMQswCQYDVQQG
EwJVUzB2MBAGByqGSM49AgEGBSuBBAAiA2IABCPaI3FO3z5bBQo8cuiEas4HjqCt
G/mLFfRT0MsIssPBEEU5Cfbt6sH5yOAxqEi5QagpU1yX4HwnGb7OtBYpDTB57uH5
Eczm34A5FNijV3s0/f0UPl7zbJcTx6xwqMIRq6NCMEAwDwYDVR0TAQH/BAUwAwEB
/zAOBgNVHQ8BAf8EBAMCAQYwHQYDVR0OBBYEFFIyuyz7RkOb3NaBqQ5lZuA0QepA
MAoGCCqGSM49BAMDA2gAMGUCMETfjPO/HwqReR2CS7p0ZWoD/LHs6hDi422opifH
EUaYLxwGlT9SLdjkVpz0UUOR5wIxAIoGyxGKRHVTpqpGRFiJtQEOOTp/+s1GcxeY
uR2zh/80lQyu9vAFCj6E4AXc+osmRg==
-----END CERTIFICATE-----""",
]


@dataclass
class CertExpirationInfo:
    is_valid: bool
    not_before: datetime
    not_after: datetime
    is_expired: bool
    is_not_yet_valid: bool


def pem_to_der(pem_cert: str) -> bytes:
    if not pem_cert.lstrip().startswith("-----BEGIN CERTIFICATE-----") or pem_cert.count("-----BEGIN ") != 1:
        raise ValueError("Expected a single CERTIFICATE PEM block")
    certificate = x509.load_pem_x509_certificate(pem_cert.encode("ascii"))
    return certificate.public_bytes(serialization.Encoding.DER)


def compute_cert_thumbprint(pem_cert: str) -> Optional[str]:
    """SHA-256 hex (lowercase) of the cert DER. None on malformed PEM."""
    try:
        der = pem_to_der(pem_cert)
        return hashlib.sha256(der).hexdigest().lower()
    except Exception as e:
        log.error("compute_cert_thumbprint failed: %s", e)
        return None


def _load_cert(pem_or_der: bytes | str) -> Optional[x509.Certificate]:
    try:
        if isinstance(pem_or_der, str):
            return x509.load_pem_x509_certificate(pem_or_der.encode("utf-8"))
        if pem_or_der.startswith(b"-----BEGIN"):
            return x509.load_pem_x509_certificate(pem_or_der)
        return x509.load_der_x509_certificate(pem_or_der)
    except Exception as e:
        log.error("Failed to load cert: %s", e)
        return None


def validate_certificate(pem_cert: str) -> Optional[dict]:
    """Returns {valid, subject, issuer, validFrom, validTo} or None on parse failure.

    `valid` is False when current time is outside [notBefore, notAfter].
    """
    cert = _load_cert(pem_cert)
    if cert is None:
        return None
    now = datetime.now(timezone.utc)
    not_before = cert.not_valid_before_utc
    not_after = cert.not_valid_after_utc
    if now < not_before or now > not_after:
        return {"valid": False}
    return {
        "valid": True,
        "subject": cert.subject.rfc4514_string(),
        "issuer": cert.issuer.rfc4514_string(),
        "validFrom": not_before,
        "validTo": not_after,
    }


# 5-minute clock-skew tolerance, matches Node side. The issuer is expected to
# pre-date notBefore to cover real-world latency; we only need to absorb our
# own NTP drift here.
_CERT_VALIDITY_CLOCK_SKEW_MS = 5 * 60 * 1000


def validate_certificate_expiration(pem_cert: str) -> Optional[CertExpirationInfo]:
    cert = _load_cert(pem_cert)
    if cert is None:
        return None
    not_before = cert.not_valid_before_utc
    not_after = cert.not_valid_after_utc
    now = datetime.now(timezone.utc)
    skew_ms = _CERT_VALIDITY_CLOCK_SKEW_MS
    is_expired = (now.timestamp() * 1000) - skew_ms > (not_after.timestamp() * 1000)
    is_not_yet_valid = (now.timestamp() * 1000) + skew_ms < (not_before.timestamp() * 1000)
    return CertExpirationInfo(
        is_valid=not (is_expired or is_not_yet_valid),
        not_before=not_before,
        not_after=not_after,
        is_expired=is_expired,
        is_not_yet_valid=is_not_yet_valid,
    )


def extract_public_key_from_cert(pem_cert: str) -> Optional[str]:
    """Extract EC P-256 public key as PEM. Returns None if not P-256 EC."""
    cert = _load_cert(pem_cert)
    if cert is None:
        return None
    try:
        pubkey = cert.public_key()
        if not isinstance(pubkey, ec.EllipticCurvePublicKey):
            log.error("Certificate does not contain EC public key")
            return None
        if not isinstance(pubkey.curve, ec.SECP256R1):
            log.error("Invalid EC curve: %s. Only P-256 supported.", pubkey.curve.name)
            return None
        return pubkey.public_bytes(
            encoding=serialization.Encoding.PEM,
            format=serialization.PublicFormat.SubjectPublicKeyInfo,
        ).decode("ascii")
    except Exception as e:
        log.error("extract_public_key_from_cert failed: %s", e)
        return None


def matches_pinned_ca(cert_der: bytes, pinned_pems: list[str]) -> bool:
    """Byte-by-byte comparison against pinned CAs (not just hash)."""
    for pem in pinned_pems:
        try:
            if cert_der == pem_to_der(pem):
                return True
        except Exception:
            continue
    return False
