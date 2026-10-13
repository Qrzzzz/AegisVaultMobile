from __future__ import annotations

import base64
import os
from pathlib import Path

import pytest

from aegisvault.core.exceptions import FileIOError, OperationCancelled
from aegisvault.core.models import CancelToken, ProgressEvent
from aegisvault.services.crypto_service import CryptoService
from aegisvault.settings.models import AppSettings

CHUNK = 1024 * 1024


@pytest.mark.parametrize("direction", ["encode", "decode"])
@pytest.mark.parametrize("change", ["truncate", "grow", "same_size", "done_truncate"])
@pytest.mark.parametrize("overwrite", [False, True])
def test_changed_input_is_never_published(tmp_path: Path, direction: str, change: str, overwrite: bool) -> None:
    payload = b"A" * (3 * CHUNK)
    source = tmp_path / ("input.bin" if direction == "encode" else "input.b64")
    source.write_bytes(payload if direction == "encode" else base64.b64encode(payload))
    output_dir = tmp_path / "output"
    output_dir.mkdir()
    previous = output_dir / ("input.bin.b64" if direction == "encode" else "input")
    if overwrite:
        previous.write_bytes(b"previous output")
    initial_files = set(output_dir.iterdir())
    events = []
    changed = False

    def progress(event: ProgressEvent) -> None:
        nonlocal changed
        events.append(event)
        stage = "done" if change == "done_truncate" else ("encoding" if direction == "encode" else "decoding")
        if changed or event.stage != stage:
            return
        changed = True
        # A real second handle mutates the real source after the first chunk.
        with source.open("r+b") as other:
            if change in {"truncate", "done_truncate"}:
                other.truncate(CHUNK)
            elif change == "grow":
                other.seek(0, 2)
                other.write(b"AAAA")
            else:
                other.seek(0)
                other.write(b"BBBB")
        if change == "same_size":
            # Ensure a detectable timestamp change even on coarse filesystems.
            stat = source.stat()
            os.utime(source, ns=(stat.st_atime_ns, stat.st_mtime_ns + 2_000_000_000))

    operation = getattr(CryptoService(AppSettings(overwrite_outputs=overwrite)), f"base64_{direction}_file")
    with pytest.raises(FileIOError) as caught:
        operation(source, output_dir=output_dir, progress=progress)
    assert caught.value.code == "file.input_changed"
    assert changed
    assert set(output_dir.iterdir()) == initial_files
    if overwrite:
        assert previous.read_bytes() == b"previous output"
    if change != "done_truncate":
        assert all(event.stage != "done" for event in events)


@pytest.mark.parametrize("size", [0, 1, 2, 3 * CHUNK + 5])
def test_static_input_roundtrip_has_exact_byte_counts(tmp_path: Path, size: int) -> None:
    source = tmp_path / "source.bin"
    payload = (bytes(range(256)) * (size // 256 + 1))[:size]
    source.write_bytes(payload)
    service = CryptoService(AppSettings())
    encoded_events: list[ProgressEvent] = []
    encoded = service.base64_encode_file(source, progress=encoded_events.append)
    assert encoded.output_path.read_bytes() == base64.b64encode(payload)
    decoded_events: list[ProgressEvent] = []
    decoded = service.base64_decode_file(encoded.output_path, progress=decoded_events.append)
    assert decoded.output_path.read_bytes() == payload
    for result, events in [(encoded, encoded_events), (decoded, decoded_events)]:
        assert result.original_size == result.input_path.stat().st_size
        assert result.output_size == result.output_path.stat().st_size
        assert events[-1].stage == "done"
        assert events[-1].processed_bytes == events[-1].total_bytes == result.original_size
        assert all(a.percent <= b.percent for a, b in zip(events, events[1:], strict=False))
    assert not list(tmp_path.glob(".*.tmp"))


@pytest.mark.parametrize("direction", ["encode", "decode"])
@pytest.mark.parametrize("stage", ["preparing", "streaming", "done"])
def test_cancel_still_rolls_back_output(tmp_path: Path, direction: str, stage: str) -> None:
    source = tmp_path / ("source.bin" if direction == "encode" else "source.b64")
    source.write_bytes(b"AAAA" * CHUNK)
    token = CancelToken()

    def progress(event: ProgressEvent) -> None:
        cancel_stage = ("encoding" if direction == "encode" else "decoding") if stage == "streaming" else stage
        if event.stage == cancel_stage:
            token.cancel()

    with pytest.raises(OperationCancelled):
        getattr(CryptoService(AppSettings()), f"base64_{direction}_file")(source, progress=progress, cancel_token=token)
    assert set(tmp_path.iterdir()) == {source}


@pytest.mark.parametrize("direction", ["encode", "decode"])
def test_source_size_is_bound_to_opened_handle(tmp_path: Path, monkeypatch: pytest.MonkeyPatch, direction: str) -> None:
    source = tmp_path / ("source.bin" if direction == "encode" else "source.b64")
    source.write_bytes(b"AAAA" * CHUNK)
    real_open = Path.open
    replaced = False

    def open_after_change(path: Path, *args, **kwargs):
        nonlocal replaced
        if path == source and args == ("rb",) and not replaced:
            replaced = True
            with real_open(path, "wb") as other:
                other.write(b"YWJj")
        return real_open(path, *args, **kwargs)

    monkeypatch.setattr(Path, "open", open_after_change)
    result = getattr(CryptoService(AppSettings()), f"base64_{direction}_file")(source)
    assert replaced and result.original_size == 4
    assert result.output_path.read_bytes() == (b"WVdKag==" if direction == "encode" else b"abc")
