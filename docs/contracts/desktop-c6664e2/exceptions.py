"""Application exception hierarchy.

The UI catches these exceptions and maps them to concise, localized messages.
Detailed diagnostics are kept in logs instead of being shown raw to users.
"""

from __future__ import annotations


class AppError(Exception):
    """Base class for expected application errors."""

    default_code = "app.error"

    def __init__(self, message: str | None = None, *, code: str | None = None) -> None:
        self.code = code or self.default_code
        super().__init__(message or self.code)


class ValidationError(AppError):
    """Raised when user input or operation parameters are invalid."""

    default_code = "validation.error"


class CryptoError(AppError):
    """Base class for cryptographic failures."""

    default_code = "crypto.error"


class AuthenticationError(CryptoError):
    """Raised when authentication fails, usually a wrong password or tampering."""

    default_code = "crypto.authentication_failed"


class ProtocolError(CryptoError):
    """Raised when encrypted data does not match the expected protocol."""

    default_code = "crypto.protocol_error"


class ResourceLimitError(AppError):
    """Raised when untrusted input asks for unsafe memory or CPU cost."""

    default_code = "resource.limit_exceeded"


class FileIOError(AppError):
    """Raised for filesystem and path handling errors."""

    default_code = "file.io_error"


class OperationCancelled(AppError):
    """Raised when a user cancels a long-running task."""

    default_code = "operation.cancelled"
