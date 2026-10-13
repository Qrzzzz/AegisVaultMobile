from __future__ import annotations

import pytest

from aegisvault.core import base64_tools
from aegisvault.core.exceptions import ValidationError


def test_strict_mode_rejects_whitespace() -> None:
    with pytest.raises(ValidationError):
        base64_tools.decode_text("aG Vs bG8=", strict=True)


def test_relaxed_mode_allows_crlf() -> None:
    assert base64_tools.decode_text("aGVs\r\nbG8=", strict=False, ignore_ascii_whitespace=True) == "hello"


def test_non_ascii_input_fails() -> None:
    with pytest.raises(ValidationError):
        base64_tools.decode_text("aGVsbG8=你好", strict=False, ignore_ascii_whitespace=True)


def test_padding_error_fails() -> None:
    with pytest.raises(ValidationError) as caught:
        base64_tools.decode_text("aGVsbG8", strict=True)
    assert caught.value.code == "base64.invalid_text"
