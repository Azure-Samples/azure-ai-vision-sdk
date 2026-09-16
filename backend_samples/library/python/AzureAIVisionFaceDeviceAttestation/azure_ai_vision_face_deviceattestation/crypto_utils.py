"""
ECIES via google tink (tink-py), ECDSA via cryptography.

Keys use PEM in storage for compatibility with the Node backend.
ECIES imports the key coordinates into a single-key Tink keyset; ECDSA signs
and verifies directly with the cryptography EC keys.

ECIES key template parameters:
  KEM curve        NIST P-256
  KDF              HKDF-SHA256 (empty salt, empty info, 32-byte output key)
  DEM              AES-256-GCM (12-byte random IV, 16-byte tag)
  point format     UNCOMPRESSED (0x04 || X || Y, 65 bytes)
  output prefix    RAW (no 5-byte Tink key-ID prefix on the ciphertext)

These match Tink's `ECIES_P256_HKDF_HMAC_SHA256_AES256_GCM` template with
RAW prefix, and the on-wire blob is
`ephemeral_point(65) || iv(12) || ciphertext || tag(16)` — interoperable with
the Node Tink-format implementation and with Tink-Java / Tink-Swift mobile
clients.

ECDSA verifies are run twice on failure (DER first, then IEEE-P1363) so iOS
App Attest assertions encoded in either form are accepted, matching the Node
behavior.
"""

from __future__ import annotations

import base64
import logging
from typing import Any, Optional, Tuple

import tink
from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec, utils
from tink import cleartext_keyset_handle, hybrid
from tink.proto import (
    aes_gcm_pb2,
    common_pb2,
    ecies_aead_hkdf_pb2,
    tink_pb2,
)

from .base64_utils import decode_base64

log = logging.getLogger("crypto_utils")

# Register Tink primitives once at import.
hybrid.register()

_AES_GCM_TYPE_URL = "type.googleapis.com/google.crypto.tink.AesGcmKey"
_ECIES_PUB_TYPE_URL = "type.googleapis.com/google.crypto.tink.EciesAeadHkdfPublicKey"
_ECIES_PRIV_TYPE_URL = "type.googleapis.com/google.crypto.tink.EciesAeadHkdfPrivateKey"


# ---------- PEM helpers ----------

def _load_ec_pub(pem_or_obj) -> ec.EllipticCurvePublicKey:
    if isinstance(pem_or_obj, ec.EllipticCurvePublicKey):
        key = pem_or_obj
    else:
        if isinstance(pem_or_obj, str):
            pem_or_obj = pem_or_obj.encode("ascii")
        key = serialization.load_pem_public_key(pem_or_obj)
    if not isinstance(key, ec.EllipticCurvePublicKey) or not isinstance(key.curve, ec.SECP256R1):
        raise ValueError("Expected an EC P-256 public key")
    return key


def _load_ec_priv(pem: str) -> ec.EllipticCurvePrivateKey:
    key = serialization.load_pem_private_key(pem.encode("ascii"), password=None)
    if not isinstance(key, ec.EllipticCurvePrivateKey) or not isinstance(key.curve, ec.SECP256R1):
        raise ValueError("Expected an EC P-256 private key")
    return key


def _xy_bytes(pub: ec.EllipticCurvePublicKey) -> Tuple[bytes, bytes]:
    nums = pub.public_numbers()
    return nums.x.to_bytes(32, "big"), nums.y.to_bytes(32, "big")


def _d_bytes(priv: ec.EllipticCurvePrivateKey) -> bytes:
    return priv.private_numbers().private_value.to_bytes(32, "big")


# ---------- ECIES keyset builders ----------

def _aes_gcm_dem_template() -> tink_pb2.KeyTemplate:
    fmt = aes_gcm_pb2.AesGcmKeyFormat()
    fmt.key_size = 32  # AES-256
    tmpl = tink_pb2.KeyTemplate()
    tmpl.type_url = _AES_GCM_TYPE_URL
    tmpl.value = fmt.SerializeToString()
    tmpl.output_prefix_type = tink_pb2.RAW
    return tmpl


def _build_ecies_params() -> ecies_aead_hkdf_pb2.EciesAeadHkdfParams:
    params = ecies_aead_hkdf_pb2.EciesAeadHkdfParams()
    params.kem_params.curve_type = common_pb2.NIST_P256
    params.kem_params.hkdf_hash_type = common_pb2.SHA256
    params.kem_params.hkdf_salt = b""
    params.dem_params.aead_dem.CopyFrom(_aes_gcm_dem_template())
    params.ec_point_format = common_pb2.UNCOMPRESSED
    return params


def _build_ecies_pub_keyset(x: bytes, y: bytes) -> bytes:
    pub = ecies_aead_hkdf_pb2.EciesAeadHkdfPublicKey()
    pub.version = 0
    pub.params.CopyFrom(_build_ecies_params())
    pub.x = x
    pub.y = y

    key_data = tink_pb2.KeyData()
    key_data.type_url = _ECIES_PUB_TYPE_URL
    key_data.value = pub.SerializeToString()
    key_data.key_material_type = tink_pb2.KeyData.ASYMMETRIC_PUBLIC

    keyset = tink_pb2.Keyset()
    k = keyset.key.add()
    k.key_data.CopyFrom(key_data)
    k.status = tink_pb2.ENABLED
    k.key_id = 1
    k.output_prefix_type = tink_pb2.RAW
    keyset.primary_key_id = 1
    return keyset.SerializeToString()


def _build_ecies_priv_keyset(x: bytes, y: bytes, d: bytes) -> bytes:
    pub = ecies_aead_hkdf_pb2.EciesAeadHkdfPublicKey()
    pub.version = 0
    pub.params.CopyFrom(_build_ecies_params())
    pub.x = x
    pub.y = y

    priv = ecies_aead_hkdf_pb2.EciesAeadHkdfPrivateKey()
    priv.version = 0
    priv.public_key.CopyFrom(pub)
    priv.key_value = d

    key_data = tink_pb2.KeyData()
    key_data.type_url = _ECIES_PRIV_TYPE_URL
    key_data.value = priv.SerializeToString()
    key_data.key_material_type = tink_pb2.KeyData.ASYMMETRIC_PRIVATE

    keyset = tink_pb2.Keyset()
    k = keyset.key.add()
    k.key_data.CopyFrom(key_data)
    k.status = tink_pb2.ENABLED
    k.key_id = 1
    k.output_prefix_type = tink_pb2.RAW
    keyset.primary_key_id = 1
    return keyset.SerializeToString()


# ---------- public surface ----------

def generate_server_key_pair_ec() -> Optional[Tuple[str, str]]:
    """Generate a P-256 EC keypair. Returns (publicKeyPem, privateKeyPem) or None.

    We generate the keypair with the `cryptography` library so we can serialize
    to PEM (Tink-py does not expose an EC keypair → PEM helper).
    """
    try:
        priv = ec.generate_private_key(ec.SECP256R1())
        priv_pem = priv.private_bytes(
            encoding=serialization.Encoding.PEM,
            format=serialization.PrivateFormat.PKCS8,
            encryption_algorithm=serialization.NoEncryption(),
        ).decode("ascii")
        pub_pem = priv.public_key().public_bytes(
            encoding=serialization.Encoding.PEM,
            format=serialization.PublicFormat.SubjectPublicKeyInfo,
        ).decode("ascii")
        return pub_pem, priv_pem
    except Exception as e:
        log.error("generate_server_key_pair_ec failed: %s", e)
        return None


def encrypt_with_public_key_ec(data: str, public_key_pem: str) -> Optional[str]:
    """ECIES encrypt via Tink. Returns base64 Tink ECIES blob or None."""
    try:
        pub = _load_ec_pub(public_key_pem)
        x, y = _xy_bytes(pub)
        keyset_bytes = _build_ecies_pub_keyset(x, y)
        handle = tink.read_no_secret_keyset_handle(tink.BinaryKeysetReader(keyset_bytes))
        hybrid_encrypt = handle.primitive(hybrid.HybridEncrypt)
        ciphertext = hybrid_encrypt.encrypt(data.encode("utf-8"), b"")
        return base64.b64encode(ciphertext).decode("ascii")
    except Exception as e:
        log.error("encrypt_with_public_key_ec failed: %s", e)
        return None


def decrypt_with_private_key_ec(tink_b64: str, private_key_pem: str) -> Optional[str]:
    """ECIES decrypt via Tink. Returns the UTF-8 plaintext or None."""
    try:
        priv = _load_ec_priv(private_key_pem)
        x, y = _xy_bytes(priv.public_key())
        d = _d_bytes(priv)
        keyset_bytes = _build_ecies_priv_keyset(x, y, d)
        handle = cleartext_keyset_handle.read(tink.BinaryKeysetReader(keyset_bytes))
        hybrid_decrypt = handle.primitive(hybrid.HybridDecrypt)
        plaintext = hybrid_decrypt.decrypt(decode_base64(tink_b64), b"")
        return plaintext.decode("utf-8")
    except Exception as e:
        log.error("decrypt_with_private_key_ec failed: %s", e)
        return None


def sign_data_ec(data: str, private_key_pem: str) -> Optional[str]:
    """Sign UTF-8 bytes with ECDSA-SHA256 (DER). Returns base64 signature."""
    try:
        priv = _load_ec_priv(private_key_pem)
        sig = priv.sign(data.encode("utf-8"), ec.ECDSA(hashes.SHA256()))
        return base64.b64encode(sig).decode("ascii")
    except Exception as e:
        log.error("sign_data_ec failed: %s", e)
        return None


def verify_signature_ec(data: str, signature_b64: str, public_key_pem: str) -> bool:
    """Verify ECDSA-SHA256 DER signature over UTF-8 bytes of `data`."""
    try:
        sig = decode_base64(signature_b64)
        return verify_signature_ec_bytes(data.encode("utf-8"), sig, public_key_pem)
    except Exception as e:
        log.error("verify_signature_ec failed: %s", e)
        return False


def verify_signature_ec_bytes(
    data: bytes,
    signature: bytes,
    public_key_pem_or_obj: Any,
    *,
    encoding: str = "der",
) -> bool:
    """Verify ECDSA-SHA256 signature over `data` bytes.

    `encoding` is 'der' (Apple's documented format) or 'ieee-p1363' (raw r||s);
    P1363 signatures are converted to DER for cryptography verification.
    """
    try:
        pub = _load_ec_pub(public_key_pem_or_obj)
        if encoding == "ieee-p1363":
            if len(signature) != 64:
                return False
            signature = utils.encode_dss_signature(
                int.from_bytes(signature[:32], "big"), int.from_bytes(signature[32:], "big"),
            )
        pub.verify(signature, data, ec.ECDSA(hashes.SHA256()))
        return True
    except InvalidSignature:
        return False
    except Exception as e:
        log.error("verify_signature_ec_bytes failed: %s", e)
        return False
