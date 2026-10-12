"""Password based key derivation.

AGV1 uses scrypt, a memory-hard KDF available in the ``cryptography`` package,
with bounded memory and CPU costs for offline password guessing resistance
on desktop machines.
"""

from __future__ import annotations

import base64
import binascii
import os
from dataclasses import dataclass
from typing import Any

from cryptography.hazmat.primitives.kdf.scrypt import Scrypt

from aegisvault.core.exceptions import ProtocolError, ResourceLimitError, ValidationError

KDF_TYPE_SCRYPT = "scrypt"
SCRYPT_N_MIN = 2**14
SCRYPT_N_MAX = 2**20
SCRYPT_R_MIN = 1
SCRYPT_R_MAX = 16
SCRYPT_P_MIN = 1
SCRYPT_P_MAX = 8
SCRYPT_SALT_MIN = 16
SCRYPT_SALT_MAX = 64
SCRYPT_KEY_LENGTH = 32
SCRYPT_MEMORY_MAX_BYTES = 256 * 1024 * 1024
SCRYPT_WORK_MAX = 4 * 1024 * 1024


@dataclass(frozen=True)
class ScryptParams:
    """Serializable scrypt parameters."""

    salt: bytes
    n: int = 2**15
    r: int = 8
    p: int = 1
    length: int = SCRYPT_KEY_LENGTH


def make_scrypt_params(*, n: int = 2**15, r: int = 8, p: int = 1, length: int = 32) -> ScryptParams:
    """Create fresh scrypt parameters with a random salt."""

    params = ScryptParams(salt=os.urandom(16), n=n, r=r, p=p, length=length)
    validate_scrypt_params(params)
    return params


def derive_key(password: str, params: ScryptParams) -> bytes:
    """Derive an AES key from a user password."""

    if not isinstance(password, str) or password == "":
        raise ValidationError("Password is required.", code="validation.password_required")
    validate_scrypt_params(params)
    kdf = Scrypt(salt=params.salt, length=params.length, n=params.n, r=params.r, p=params.p)
    return kdf.derive(password.encode("utf-8"))


def validate_scrypt_params(params: ScryptParams) -> None:
    """Validate KDF parameters before deriving keys or serializing headers."""

    if not isinstance(params, ScryptParams):
        raise ProtocolError("Invalid scrypt parameters.", code="crypto.invalid_kdf")
    if not isinstance(params.salt, bytes):
        raise ProtocolError("Invalid salt type.", code="crypto.invalid_kdf")
    if any(type(value) is not int for value in (params.n, params.r, params.p, params.length)):
        raise ProtocolError("Invalid scrypt parameter type.", code="crypto.invalid_kdf")
    if params.length != SCRYPT_KEY_LENGTH:
        raise ProtocolError("AES-256 requires a 32-byte key.", code="crypto.invalid_kdf")
    if not (SCRYPT_SALT_MIN <= len(params.salt) <= SCRYPT_SALT_MAX):
        raise ProtocolError("Invalid salt length.", code="crypto.invalid_kdf")
    if not (SCRYPT_N_MIN <= params.n <= SCRYPT_N_MAX) or params.n & (params.n - 1) != 0:
        raise ProtocolError("Invalid scrypt N parameter.", code="crypto.invalid_kdf")
    if not (SCRYPT_R_MIN <= params.r <= SCRYPT_R_MAX):
        raise ProtocolError("Invalid scrypt r parameter.", code="crypto.invalid_kdf")
    if not (SCRYPT_P_MIN <= params.p <= SCRYPT_P_MAX):
        raise ProtocolError("Invalid scrypt p parameter.", code="crypto.invalid_kdf")
    memory_bytes = 128 * params.n * params.r
    work_units = params.n * params.r * params.p
    if memory_bytes > SCRYPT_MEMORY_MAX_BYTES:
        raise ResourceLimitError(
            "Scrypt parameters exceed the memory safety budget.",
            code="resource.limit_exceeded",
        )
    if work_units > SCRYPT_WORK_MAX:
        raise ResourceLimitError(
            "Scrypt parameters exceed the work safety budget.",
            code="resource.limit_exceeded",
        )


def params_to_header(params: ScryptParams) -> dict[str, Any]:
    """Convert KDF parameters to a JSON-serializable protocol mapping."""

    validate_scrypt_params(params)
    return {
        "type": KDF_TYPE_SCRYPT,
        "salt": base64.b64encode(params.salt).decode("ascii"),
        "n": params.n,
        "r": params.r,
        "p": params.p,
        "length": params.length,
    }


def params_from_header(data: dict[str, Any]) -> ScryptParams:
    """Parse and validate KDF parameters from a protocol header."""

    if not isinstance(data, dict):
        raise ProtocolError("Invalid KDF parameters.", code="crypto.invalid_kdf")
    if data.get("type") != KDF_TYPE_SCRYPT:
        raise ProtocolError("Unsupported KDF.", code="crypto.unsupported_kdf")
    try:
        salt_value = data["salt"]
        n = data["n"]
        r = data["r"]
        p = data["p"]
        length = data["length"]
        if not isinstance(salt_value, str) or any(type(value) is not int for value in (n, r, p, length)):
            raise TypeError
        salt = base64.b64decode(salt_value, validate=True)
    except (KeyError, TypeError, ValueError, binascii.Error) as exc:
        raise ProtocolError("Invalid KDF parameters.", code="crypto.invalid_kdf") from exc
    params = ScryptParams(salt=salt, n=n, r=r, p=p, length=length)
    validate_scrypt_params(params)
    return params
