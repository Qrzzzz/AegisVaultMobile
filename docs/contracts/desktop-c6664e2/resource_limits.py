"""Shared, bounded text-workflow resource contract."""

from __future__ import annotations

import json
from dataclasses import asdict, dataclass
from importlib.resources import files

from aegisvault.core.exceptions import ResourceLimitError, ValidationError
from aegisvault.core.protocol import HEADER_MAX_SIZE, TEXT_MAGIC


@dataclass(frozen=True)
class TextLimits:
    schema: int
    max_json_line_bytes: int
    max_plaintext_utf8_bytes: int
    max_plaintext_utf16_code_units: int
    max_encoded_text_utf8_bytes: int
    max_encoded_text_utf16_code_units: int
    max_agv1_header_bytes: int

    def to_dict(self) -> dict[str, int]:
        return asdict(self)


def _load_limits() -> TextLimits:
    raw = json.loads(files("aegisvault").joinpath("text_limits.json").read_text(encoding="utf-8"))
    limits = TextLimits(**raw)
    values = asdict(limits)
    if limits.schema != 1 or any(type(value) is not int or value <= 0 for value in values.values()):
        raise RuntimeError("Invalid text resource-limit contract")
    if limits.max_agv1_header_bytes != HEADER_MAX_SIZE:
        raise RuntimeError("Text resource-limit contract does not match AGV1 header limit")
    if limits.max_plaintext_utf16_code_units != limits.max_plaintext_utf8_bytes:
        raise RuntimeError("Plaintext UTF-16 and UTF-8 limits must share the ASCII boundary")
    if limits.max_encoded_text_utf16_code_units != limits.max_encoded_text_utf8_bytes:
        raise RuntimeError("Encoded-text UTF-16 and UTF-8 limits must share the ASCII boundary")
    package_bytes = (
        len(TEXT_MAGIC)
        + 4
        + limits.max_agv1_header_bytes
        + limits.max_plaintext_utf8_bytes
        + 16
    )
    maximum_token_bytes = 5 + 4 * ((package_bytes + 2) // 3)
    if maximum_token_bytes > limits.max_encoded_text_utf8_bytes:
        raise RuntimeError("Plaintext budget can produce an over-budget AGV1 token")
    if 6 * limits.max_encoded_text_utf16_code_units + 256 * 1024 > limits.max_json_line_bytes:
        raise RuntimeError("JSON line budget cannot contain a maximally escaped text request")
    return limits


TEXT_LIMITS = _load_limits()
MAX_JSON_LINE_BYTES = TEXT_LIMITS.max_json_line_bytes


def utf8_size(value: str) -> int:
    try:
        return len(value.encode("utf-8", errors="strict"))
    except UnicodeEncodeError as exc:
        raise ValidationError("Text contains invalid Unicode.", code="ipc.invalid_request") from exc


def require_plaintext(value: str) -> None:
    if utf8_size(value) > TEXT_LIMITS.max_plaintext_utf8_bytes:
        raise ResourceLimitError("Plaintext exceeds the text-workflow budget.")


def require_encoded_text(value: str) -> None:
    if utf8_size(value) > TEXT_LIMITS.max_encoded_text_utf8_bytes:
        raise ResourceLimitError("Encoded text exceeds the text-workflow budget.")


def require_base64_plaintext_budget(value: str, *, ignore_ascii_whitespace: bool) -> None:
    """Bound a valid Base64 result without first allocating the decoded payload."""

    try:
        raw = value.encode("ascii", errors="strict")
    except UnicodeEncodeError:
        return  # The normal Base64 validator retains its existing error code.
    whitespace = b"\t\n\r\f\v "
    count = 0
    tail = bytearray()
    for item in raw:
        if ignore_ascii_whitespace and item in whitespace:
            continue
        count += 1
        tail.append(item)
        if len(tail) > 2:
            del tail[0]
    if count == 0 or count % 4:
        return  # Invalid shape is classified by the Base64 decoder.
    padding = int(tail[-1:] == b"=") + int(bytes(tail[-2:-1]) == b"=")
    decoded_size = count // 4 * 3 - padding
    if decoded_size > TEXT_LIMITS.max_plaintext_utf8_bytes:
        raise ResourceLimitError("Decoded text exceeds the text-workflow budget.")
