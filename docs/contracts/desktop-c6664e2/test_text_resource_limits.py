from __future__ import annotations

import base64
from pathlib import Path

import pytest

from aegisvault.core.crypto import encrypt_text
from aegisvault.core.exceptions import ResourceLimitError
from aegisvault.core.kdf import ScryptParams
from aegisvault.resource_limits import TEXT_LIMITS, utf8_size
from aegisvault.services.crypto_service import CryptoService
from aegisvault.settings.models import AppSettings


def _utf16_code_units(value: str) -> int:
    return len(value.encode("utf-16-le")) // 2


def _boundary_sample(family: str) -> tuple[str, str, str]:
    maximum = TEXT_LIMITS.max_plaintext_utf8_bytes
    assert maximum == 1_507_294
    if family == "ascii":
        return "a" * (maximum - 1), "a" * maximum, "a" * (maximum + 1)
    if family == "bmp":
        return "你" * 502_431, "你" * 502_431 + "a", "你" * 502_431 + "aa"
    return "🔐" * 376_823 + "a", "🔐" * 376_823 + "aa", "🔐" * 376_823 + "aaa"


def test_text_limit_contract_closes_agv1_base64_and_json_budgets() -> None:
    limits = TEXT_LIMITS
    package_bytes = 8 + 4 + limits.max_agv1_header_bytes + limits.max_plaintext_utf8_bytes + 16
    maximum_agv1_token = 5 + 4 * ((package_bytes + 2) // 3)
    maximum_base64 = 4 * ((limits.max_plaintext_utf8_bytes + 2) // 3)

    assert maximum_agv1_token <= limits.max_encoded_text_utf8_bytes
    assert maximum_base64 <= limits.max_encoded_text_utf8_bytes
    assert 6 * limits.max_encoded_text_utf16_code_units + 256 * 1024 <= limits.max_json_line_bytes


@pytest.mark.parametrize("family", ["ascii", "bmp", "non_bmp"])
def test_base64_text_budget_round_trips_utf8_boundaries(family: str) -> None:
    before, at_limit, after = _boundary_sample(family)
    service = CryptoService(AppSettings())
    maximum = TEXT_LIMITS.max_plaintext_utf8_bytes
    assert [utf8_size(value) for value in (before, at_limit, after)] == [maximum - 1, maximum, maximum + 1]
    assert _utf16_code_units(at_limit) <= TEXT_LIMITS.max_plaintext_utf16_code_units

    for value in (before, at_limit):
        encoded = service.base64_encode_text(value)
        assert utf8_size(encoded) <= TEXT_LIMITS.max_encoded_text_utf8_bytes
        assert service.base64_decode_text(encoded) == value

    with pytest.raises(ResourceLimitError) as caught:
        service.base64_encode_text(after)
    assert caught.value.code == "resource.limit_exceeded"


def test_agv1_text_budget_round_trips_exact_ascii_boundary() -> None:
    plaintext = "a" * TEXT_LIMITS.max_plaintext_utf8_bytes
    service = CryptoService(AppSettings())

    token = service.encrypt_text(plaintext, "budget-password").ciphertext

    assert utf8_size(token) <= TEXT_LIMITS.max_encoded_text_utf8_bytes
    assert service.decrypt_text(token, "budget-password").plaintext == plaintext


def test_reverse_operations_reject_results_outside_plaintext_budget_before_work() -> None:
    over_budget = b"a" * (TEXT_LIMITS.max_plaintext_utf8_bytes + 1)
    service = CryptoService(AppSettings())
    encoded = base64.b64encode(over_budget).decode("ascii")
    token = encrypt_text(
        over_budget.decode("ascii"),
        "budget-password",
        kdf_params=ScryptParams(salt=b"b" * 16, n=2**14),
    ).ciphertext
    assert utf8_size(encoded) <= TEXT_LIMITS.max_encoded_text_utf8_bytes
    assert utf8_size(token) <= TEXT_LIMITS.max_encoded_text_utf8_bytes

    with pytest.raises(ResourceLimitError) as base64_error:
        service.base64_decode_text(encoded)
    with pytest.raises(ResourceLimitError) as agv1_error:
        service.decrypt_text(token, "budget-password")

    assert base64_error.value.code == agv1_error.value.code == "resource.limit_exceeded"


def test_text_limit_data_is_packaged_from_the_shared_source() -> None:
    root = Path(__file__).resolve().parents[1]
    project = (root / "src/AegisVault.App/AegisVault.App.csproj").read_text(encoding="utf-8")
    spec = (root / "scripts/aegisvault.spec").read_text(encoding="utf-8")
    assert "aegisvault\\text_limits.json" in project
    assert "src/aegisvault/text_limits.json" in spec
