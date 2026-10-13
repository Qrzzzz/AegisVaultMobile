"""Explicit Base64 utilities.

Base64 is encoding, not encryption. Keeping this in a separate module helps the
UI present it as a distinct workflow.
"""

from __future__ import annotations

import base64
import binascii
import re

from aegisvault.core.exceptions import ValidationError


def encode_text(text: str) -> str:
    return base64.b64encode(text.encode("utf-8")).decode("ascii")


ASCII_WHITESPACE = re.compile(rb"[\t\n\r\f\v ]+")


class Base64StreamDecoder:
    """Incrementally decode Base64 while retaining at most one partial quartet."""

    def __init__(self, *, strict: bool = True, ignore_ascii_whitespace: bool = False) -> None:
        self._strict = strict
        self._ignore_ascii_whitespace = ignore_ascii_whitespace
        self._buffer = b""
        self._finished = False

    def feed(self, encoded: bytes) -> bytes:
        """Decode complete quartets from one input block."""

        data = _prepare_base64(
            encoded,
            strict=self._strict,
            ignore_ascii_whitespace=self._ignore_ascii_whitespace,
        )
        if self._finished:
            if data:
                raise ValidationError("Base64 data follows padding.", code="base64.invalid_data")
            return b""

        data = self._buffer + data
        self._buffer = b""
        padding_index = data.find(b"=")
        if padding_index >= 0:
            padded_quartet_start = (padding_index // 4) * 4
            padded_quartet_end = padded_quartet_start + 4
            prefix = data[:padded_quartet_start]
            if len(data) < padded_quartet_end:
                self._buffer = data[padded_quartet_start:]
                return decode_bytes(prefix) if prefix else b""
            if len(data) != padded_quartet_end:
                raise ValidationError("Base64 data follows padding.", code="base64.invalid_data")
            decoded = decode_bytes(prefix) if prefix else b""
            decoded += decode_bytes(data[padded_quartet_start:padded_quartet_end])
            self._finished = True
            return decoded

        usable = (len(data) // 4) * 4
        self._buffer = data[usable:]
        return decode_bytes(data[:usable]) if usable else b""

    def finalize(self) -> bytes:
        """Validate that the stream ended on a complete Base64 quartet."""

        if self._finished:
            return b""
        if self._buffer:
            raise ValidationError("Input is not valid Base64 data.", code="base64.invalid_data")
        self._finished = True
        return b""


def decode_text(encoded: str, *, strict: bool = True, ignore_ascii_whitespace: bool = False) -> str:
    try:
        return decode_bytes(
            _ascii_bytes(encoded),
            strict=strict,
            ignore_ascii_whitespace=ignore_ascii_whitespace,
        ).decode("utf-8")
    except (ValidationError, binascii.Error, ValueError, UnicodeDecodeError) as exc:
        raise ValidationError("Input is not valid UTF-8 Base64 text.", code="base64.invalid_text") from exc


def encode_bytes(data: bytes) -> bytes:
    return base64.b64encode(data)


def decode_bytes(encoded: bytes, *, strict: bool = True, ignore_ascii_whitespace: bool = False) -> bytes:
    try:
        data = _prepare_base64(encoded, strict=strict, ignore_ascii_whitespace=ignore_ascii_whitespace)
        return base64.b64decode(data, validate=True)
    except (binascii.Error, ValueError) as exc:
        raise ValidationError("Input is not valid Base64 data.", code="base64.invalid_data") from exc


def _ascii_bytes(value: str) -> bytes:
    try:
        return value.encode("ascii")
    except UnicodeEncodeError as exc:
        raise ValidationError("Base64 input must be ASCII.", code="base64.invalid_text") from exc


def _prepare_base64(value: bytes, *, strict: bool, ignore_ascii_whitespace: bool) -> bytes:
    try:
        value.decode("ascii")
    except UnicodeDecodeError as exc:
        raise ValidationError("Base64 input must be ASCII.", code="base64.invalid_data") from exc
    if strict and ASCII_WHITESPACE.search(value):
        raise ValidationError("Base64 input contains whitespace in strict mode.", code="base64.invalid_data")
    if ignore_ascii_whitespace:
        return ASCII_WHITESPACE.sub(b"", value)
    return value
