"""AegisVault v1 envelope format helpers."""

from __future__ import annotations

import base64
import binascii
import hashlib
import json
import struct
from datetime import UTC, datetime
from typing import Any, BinaryIO

from aegisvault.core.exceptions import ProtocolError

TEXT_PREFIX = "AGV1."
TEXT_MAGIC = b"AGVTEXT\x01"
FILE_MAGIC = b"AGVFILE\x01"
PROTOCOL_VERSION = 1
HEADER_MAX_SIZE = 64 * 1024
MIN_CHUNK_SIZE = 64 * 1024
MAX_CHUNK_SIZE = 16 * 1024 * 1024
MAX_CIPHERTEXT_CHUNK_SIZE = MAX_CHUNK_SIZE + 16
FLAG_LAST_CHUNK = 0x01
CHUNK_RECORD = struct.Struct(">BI")


def now_utc() -> str:
    return datetime.now(UTC).isoformat(timespec="seconds").replace("+00:00", "Z")


def canonical_json(data: dict[str, Any]) -> bytes:
    """Encode protocol JSON in a stable representation."""

    try:
        return json.dumps(
            data,
            ensure_ascii=False,
            sort_keys=True,
            separators=(",", ":"),
            allow_nan=False,
        ).encode("utf-8")
    except (TypeError, ValueError) as exc:
        raise ProtocolError("Invalid protocol header.", code="crypto.invalid_header") from exc


def _unique_object(pairs: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in pairs:
        if key in result:
            raise ValueError("Duplicate JSON object key")
        result[key] = value
    return result


def _reject_non_finite_number(_value: str) -> None:
    raise ValueError("Non-finite JSON number")


def parse_header(raw: bytes) -> dict[str, Any]:
    try:
        data = json.loads(
            raw.decode("utf-8"),
            object_pairs_hook=_unique_object,
            parse_constant=_reject_non_finite_number,
        )
    except (UnicodeDecodeError, json.JSONDecodeError, ValueError) as exc:
        raise ProtocolError("Invalid protocol header.", code="crypto.invalid_header") from exc
    if not isinstance(data, dict):
        raise ProtocolError("Protocol header must be a JSON object.", code="crypto.invalid_header")
    return data


def header_digest(header_bytes: bytes) -> bytes:
    return hashlib.sha256(header_bytes).digest()


def encode_token(package: bytes) -> str:
    encoded = base64.urlsafe_b64encode(package).decode("ascii").rstrip("=")
    return TEXT_PREFIX + encoded


def decode_token(token: str) -> bytes:
    if not token.startswith(TEXT_PREFIX):
        raise ProtocolError("Only AGV1 text tokens are supported.", code="protocol.unsupported_format")
    payload = token[len(TEXT_PREFIX) :]
    if not payload:
        raise ProtocolError("Invalid text token encoding.", code="crypto.invalid_encoding")
    padding = "=" * (-len(payload) % 4)
    try:
        encoded = (payload + padding).encode("ascii")
        return base64.b64decode(encoded, altchars=b"-_", validate=True)
    except (UnicodeEncodeError, ValueError, binascii.Error) as exc:
        raise ProtocolError("Invalid text token encoding.", code="crypto.invalid_encoding") from exc


def pack_envelope(magic: bytes, header: dict[str, Any], payload: bytes) -> bytes:
    header_bytes = canonical_json(header)
    if len(header_bytes) > HEADER_MAX_SIZE:
        raise ProtocolError("Protocol header is too large.", code="crypto.header_too_large")
    return magic + struct.pack(">I", len(header_bytes)) + header_bytes + payload


def unpack_envelope(magic: bytes, package: bytes) -> tuple[dict[str, Any], bytes, bytes]:
    if not package.startswith(magic):
        raise ProtocolError("Only AGV1 encrypted data is supported.", code="protocol.unsupported_format")
    offset = len(magic)
    if len(package) < offset + 4:
        raise ProtocolError("Encrypted data is truncated.", code="crypto.truncated")
    header_len = struct.unpack(">I", package[offset : offset + 4])[0]
    offset += 4
    if header_len <= 0 or header_len > HEADER_MAX_SIZE:
        raise ProtocolError("Invalid protocol header length.", code="crypto.invalid_header")
    end = offset + header_len
    if len(package) < end:
        raise ProtocolError("Encrypted data is truncated.", code="crypto.truncated")
    header_bytes = package[offset:end]
    return parse_header(header_bytes), header_bytes, package[end:]


def read_exact(stream: BinaryIO, size: int) -> bytes:
    if size < 0 or size > MAX_CIPHERTEXT_CHUNK_SIZE:
        raise ProtocolError("Chunk record exceeds safety limits.", code="crypto.invalid_chunk")
    data = stream.read(size)
    if len(data) != size:
        raise ProtocolError("Encrypted data is truncated.", code="crypto.truncated")
    return data


def write_file_header(stream: BinaryIO, header: dict[str, Any]) -> bytes:
    header_bytes = canonical_json(header)
    if len(header_bytes) > HEADER_MAX_SIZE:
        raise ProtocolError("Protocol header is too large.", code="crypto.header_too_large")
    stream.write(FILE_MAGIC)
    stream.write(struct.pack(">I", len(header_bytes)))
    stream.write(header_bytes)
    return header_bytes


def read_file_header(stream: BinaryIO) -> tuple[dict[str, Any], bytes]:
    magic = stream.read(len(FILE_MAGIC))
    if magic != FILE_MAGIC:
        raise ProtocolError("Only AGV1 encrypted files are supported.", code="protocol.unsupported_format")
    header_len = struct.unpack(">I", read_exact(stream, 4))[0]
    if header_len <= 0 or header_len > HEADER_MAX_SIZE:
        raise ProtocolError("Invalid protocol header length.", code="crypto.invalid_header")
    header_bytes = read_exact(stream, header_len)
    return parse_header(header_bytes), header_bytes


def validate_common_header(header: dict[str, Any], *, kind: str) -> None:
    format_name = header.get("format")
    version = header.get("version")
    algorithm = header.get("algorithm")
    if type(format_name) is not str or format_name != f"aegisvault.{kind}":
        raise ProtocolError("Encrypted data type does not match this operation.", code="crypto.format_mismatch")
    if type(version) is not int or version != PROTOCOL_VERSION:
        raise ProtocolError("Unsupported protocol version.", code="crypto.unsupported_version")
    if type(algorithm) is not str or algorithm != "AES-256-GCM":
        raise ProtocolError("Unsupported encryption algorithm.", code="crypto.unsupported_algorithm")


def validate_text_header(header: dict[str, Any]) -> None:
    """Validate required, non-cryptographic fields of an AGV1 text header."""

    validate_common_header(header, kind="text")
    _validate_created_at(header.get("created_at"))
    if not isinstance(header.get("kdf"), dict):
        raise ProtocolError("Invalid KDF header.", code="crypto.invalid_header")
    if not isinstance(header.get("nonce"), str):
        raise ProtocolError("Invalid nonce header.", code="crypto.invalid_header")


def validate_file_header(header: dict[str, Any]) -> int:
    """Validate required AGV1 file fields and return the chunk size."""

    validate_common_header(header, kind="file")
    if type(header.get("mode")) is not str or header.get("mode") != "chunked":
        raise ProtocolError("Unsupported file encryption mode.", code="crypto.unsupported_mode")
    _validate_created_at(header.get("created_at"))
    if not isinstance(header.get("kdf"), dict):
        raise ProtocolError("Invalid KDF header.", code="crypto.invalid_header")
    if not isinstance(header.get("nonce_prefix"), str):
        raise ProtocolError("Invalid nonce-prefix header.", code="crypto.invalid_header")
    metadata = header.get("metadata")
    if not isinstance(metadata, dict):
        raise ProtocolError("Invalid file metadata.", code="crypto.invalid_header")
    original_suffix = metadata.get("original_suffix")
    original_size = metadata.get("original_size")
    if type(original_suffix) is not str or len(original_suffix) > 4096 or "\x00" in original_suffix:
        raise ProtocolError("Invalid original file suffix.", code="crypto.invalid_header")
    if type(original_size) is not int or not (0 <= original_size <= 0x7FFFFFFFFFFFFFFF):
        raise ProtocolError("Invalid original file size.", code="crypto.invalid_header")
    return validate_chunk_size(header.get("chunk_size"))


def _validate_created_at(value: Any) -> None:
    if type(value) is not str or not value or len(value) > 128 or "\x00" in value:
        raise ProtocolError("Invalid creation timestamp.", code="crypto.invalid_header")


def validate_chunk_size(value: Any) -> int:
    if type(value) is not int:
        raise ProtocolError("Invalid chunk size.", code="crypto.invalid_chunk_size")
    chunk_size = value
    if not (MIN_CHUNK_SIZE <= chunk_size <= MAX_CHUNK_SIZE):
        raise ProtocolError("Chunk size is outside safety limits.", code="crypto.invalid_chunk_size")
    return chunk_size


def validate_chunk_record(flags: int, ciphertext_len: int, chunk_size: int) -> None:
    if flags not in (0, FLAG_LAST_CHUNK):
        raise ProtocolError("Invalid chunk flags.", code="crypto.invalid_chunk")
    if ciphertext_len < 16:
        raise ProtocolError("Invalid chunk length.", code="crypto.invalid_chunk")
    if ciphertext_len > chunk_size + 16 or ciphertext_len > MAX_CIPHERTEXT_CHUNK_SIZE:
        raise ProtocolError("Chunk length exceeds safety limits.", code="crypto.invalid_chunk")


def chunk_aad(digest: bytes, index: int, flags: int) -> bytes:
    return b"AGV1-FILE-CHUNK|" + digest + struct.pack(">QB", index, flags)


def chunk_nonce(prefix: bytes, index: int) -> bytes:
    if len(prefix) != 8:
        raise ProtocolError("Invalid nonce prefix.", code="crypto.invalid_nonce")
    if type(index) is not int or not (0 <= index <= 0xFFFFFFFF):
        raise ProtocolError("File has too many chunks.", code="crypto.too_many_chunks")
    return prefix + struct.pack(">I", index)
