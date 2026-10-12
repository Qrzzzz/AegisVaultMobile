"""Application service facade for crypto workflows."""

from __future__ import annotations

import os
from pathlib import Path

from aegisvault.core import base64_tools
from aegisvault.core.crypto import decrypt_file, decrypt_text, encrypt_file, encrypt_text
from aegisvault.core.exceptions import (
    FileIOError,
    OperationCancelled,
    ValidationError,
)
from aegisvault.core.file_io import ensure_input_unchanged
from aegisvault.core.models import (
    CancelToken,
    FileProcessResult,
    ProgressCallback,
    ProgressEvent,
    TextDecryptResult,
    TextEncryptResult,
)
from aegisvault.resource_limits import (
    require_base64_plaintext_budget,
    require_encoded_text,
    require_plaintext,
)
from aegisvault.services.file_io import (
    atomic_binary_writer,
    base64_decoded_output_path,
    base64_encoded_output_path,
    decrypted_output_path,
    encrypted_output_path,
    ensure_input_file,
)
from aegisvault.settings.models import AppSettings


class CryptoService:
    """Coordinates output naming and AGV1 crypto or Base64 operations."""

    def __init__(self, settings: AppSettings) -> None:
        settings.validate()
        self.settings = settings

    def encrypt_text(self, plaintext: str, password: str) -> TextEncryptResult:
        self._require_password(password)
        require_plaintext(plaintext)
        result = encrypt_text(plaintext, password)
        require_encoded_text(result.ciphertext)
        return result

    def decrypt_text(self, ciphertext: str, password: str | None = None) -> TextDecryptResult:
        """Decrypt AGV1 only; a missing password never selects another format."""

        require_encoded_text(ciphertext)
        return decrypt_text(ciphertext, password if password is not None else "")

    def encrypt_file(
        self,
        input_path: Path,
        password: str,
        *,
        output_dir: Path | None = None,
        progress: ProgressCallback | None = None,
        cancel_token: CancelToken | None = None,
    ) -> FileProcessResult:
        self._require_password(password)
        input_path = ensure_input_file(input_path)
        out_dir = self._output_dir(output_dir)
        output_path = encrypted_output_path(input_path, out_dir, overwrite=self.settings.overwrite_outputs)
        return encrypt_file(
            input_path,
            output_path,
            password,
            overwrite=self.settings.overwrite_outputs,
            progress=progress,
            cancel_token=cancel_token,
        )

    def decrypt_file(
        self,
        input_path: Path,
        password: str,
        *,
        output_dir: Path | None = None,
        progress: ProgressCallback | None = None,
        cancel_token: CancelToken | None = None,
    ) -> FileProcessResult:
        """Decrypt an AGV1 container without format detection or fallback."""

        input_path = ensure_input_file(input_path)
        out_dir = self._output_dir(output_dir)
        output_path = decrypted_output_path(input_path, out_dir, overwrite=self.settings.overwrite_outputs)
        return decrypt_file(
            input_path,
            output_path,
            password,
            overwrite=self.settings.overwrite_outputs,
            progress=progress,
            cancel_token=cancel_token,
        )

    def base64_encode_text(self, text: str) -> str:
        require_plaintext(text)
        result = base64_tools.encode_text(text)
        require_encoded_text(result)
        return result

    def base64_decode_text(self, text: str, *, strict: bool = True, ignore_ascii_whitespace: bool = False) -> str:
        require_encoded_text(text)
        require_base64_plaintext_budget(text, ignore_ascii_whitespace=ignore_ascii_whitespace)
        result = base64_tools.decode_text(text, strict=strict, ignore_ascii_whitespace=ignore_ascii_whitespace)
        require_plaintext(result)
        return result

    def base64_encode_file(
        self,
        input_path: Path,
        *,
        output_dir: Path | None = None,
        progress: ProgressCallback | None = None,
        cancel_token: CancelToken | None = None,
    ) -> FileProcessResult:
        input_path = ensure_input_file(input_path)
        out_dir = self._output_dir(output_dir)
        output_path = base64_encoded_output_path(input_path, out_dir, overwrite=self.settings.overwrite_outputs)
        processed = 0
        with input_path.open("rb") as source, atomic_binary_writer(output_path, overwrite=self.settings.overwrite_outputs) as target:
            initial = os.fstat(source.fileno())
            original_size = initial.st_size
            self._emit(progress, 0.02, "preparing", input_path.name, processed_bytes=0, total_bytes=original_size)
            remainder = b""
            while True:
                self._check_cancel(cancel_token)
                chunk = source.read(1024 * 1024)
                if not chunk:
                    break
                processed += len(chunk)
                chunk = remainder + chunk
                usable = (len(chunk) // 3) * 3
                if usable:
                    target.write(base64_tools.encode_bytes(chunk[:usable]))
                remainder = chunk[usable:]
                self._emit(
                    progress,
                    0.05 + 0.9 * (processed / max(original_size, 1)),
                    "encoding",
                    input_path.name,
                    processed_bytes=processed,
                    total_bytes=original_size,
                )
            ensure_input_unchanged(source, initial, processed)
            if remainder:
                target.write(base64_tools.encode_bytes(remainder))
            self._check_cancel(cancel_token)
            self._emit(progress, 1.0, "done", output_path.name, processed_bytes=original_size, total_bytes=original_size)
            self._check_cancel(cancel_token)
            output_size = target.tell()
            ensure_input_unchanged(source, initial, processed)
        return FileProcessResult(input_path, output_path, original_size, output_size, "base64")

    def base64_decode_file(
        self,
        input_path: Path,
        *,
        output_dir: Path | None = None,
        progress: ProgressCallback | None = None,
        cancel_token: CancelToken | None = None,
    ) -> FileProcessResult:
        input_path = ensure_input_file(input_path)
        out_dir = self._output_dir(output_dir)
        output_path = base64_decoded_output_path(input_path, out_dir, overwrite=self.settings.overwrite_outputs)
        processed = 0
        with input_path.open("rb") as source, atomic_binary_writer(output_path, overwrite=self.settings.overwrite_outputs) as target:
            initial = os.fstat(source.fileno())
            original_size = initial.st_size
            self._emit(progress, 0.02, "preparing", input_path.name, processed_bytes=0, total_bytes=original_size)
            decoder = base64_tools.Base64StreamDecoder(strict=False, ignore_ascii_whitespace=True)
            while True:
                self._check_cancel(cancel_token)
                chunk = source.read(1024 * 1024)
                if not chunk:
                    break
                processed += len(chunk)
                decoded = decoder.feed(chunk)
                if decoded:
                    target.write(decoded)
                self._emit(
                    progress,
                    0.05 + 0.9 * (processed / max(original_size, 1)),
                    "decoding",
                    input_path.name,
                    processed_bytes=processed,
                    total_bytes=original_size,
                )
            ensure_input_unchanged(source, initial, processed)
            final = decoder.finalize()
            if final:
                target.write(final)
            self._check_cancel(cancel_token)
            self._emit(progress, 1.0, "done", output_path.name, processed_bytes=original_size, total_bytes=original_size)
            self._check_cancel(cancel_token)
            output_size = target.tell()
            ensure_input_unchanged(source, initial, processed)
        return FileProcessResult(input_path, output_path, original_size, output_size, "base64")

    def _output_dir(self, override: Path | None = None) -> Path | None:
        if override:
            return self._validated_output_dir(override)
        if self.settings.default_output_dir:
            return self._validated_output_dir(Path(self.settings.default_output_dir))
        return None

    def _validated_output_dir(self, value: Path) -> Path:
        directory = value.expanduser().resolve()
        if not directory.exists() or not directory.is_dir():
            raise FileIOError(f"Output directory is not available: {directory}", code="file.output_dir_invalid")
        return directory

    def _emit(
        self,
        progress: ProgressCallback | None,
        percent: float,
        stage: str,
        detail: str = "",
        *,
        processed_bytes: int | None = None,
        total_bytes: int | None = None,
    ) -> None:
        if progress:
            progress(ProgressEvent(max(0.0, min(1.0, percent)), stage, detail, processed_bytes, total_bytes))

    def _check_cancel(self, cancel_token: CancelToken | None) -> None:
        if cancel_token and cancel_token.cancelled:
            raise OperationCancelled("Operation was cancelled.", code="operation.cancelled")

    def _require_password(self, password: str | None) -> None:
        if not isinstance(password, str) or password == "":
            raise ValidationError("Password is required.", code="validation.password_required")
