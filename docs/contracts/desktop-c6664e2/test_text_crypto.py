from __future__ import annotations

import pytest

from aegisvault.core.crypto import decrypt_text, encrypt_text
from aegisvault.core.exceptions import AuthenticationError, ProtocolError
from aegisvault.core.kdf import SCRYPT_N_MAX, ScryptParams
from aegisvault.core.protocol import (
    HEADER_MAX_SIZE,
    TEXT_MAGIC,
    decode_token,
    encode_token,
    pack_envelope,
    unpack_envelope,
)


def fast_params() -> ScryptParams:
    return ScryptParams(salt=b"t" * 16, n=2**14)


def test_text_encrypt_decrypt_round_trip() -> None:
    token = encrypt_text("secret text", "passphrase", kdf_params=fast_params()).ciphertext
    assert token.startswith("AGV1.")
    assert decrypt_text(token, "passphrase").plaintext == "secret text"


def test_wrong_password_fails_authentication() -> None:
    token = encrypt_text("secret", "right", kdf_params=fast_params()).ciphertext
    with pytest.raises(AuthenticationError):
        decrypt_text(token, "wrong")


def test_tampered_text_fails_authentication_or_protocol() -> None:
    token = encrypt_text("secret", "right", kdf_params=fast_params()).ciphertext
    package = bytearray(decode_token(token))
    package[-1] ^= 0x01
    with pytest.raises((AuthenticationError, ProtocolError)):
        decrypt_text(encode_token(bytes(package)), "right")


def test_malicious_text_kdf_is_rejected_before_derivation() -> None:
    token = encrypt_text("secret", "right", kdf_params=fast_params()).ciphertext
    header, _header_bytes, ciphertext = unpack_envelope(TEXT_MAGIC, decode_token(token))
    header["kdf"]["n"] = SCRYPT_N_MAX * 2
    malicious = encode_token(pack_envelope(TEXT_MAGIC, header, ciphertext))
    with pytest.raises(ProtocolError):
        decrypt_text(malicious, "right")


def test_header_size_limit_is_enforced() -> None:
    with pytest.raises(ProtocolError):
        pack_envelope(TEXT_MAGIC, {"format": "aegisvault.text", "padding": "x" * HEADER_MAX_SIZE}, b"")
