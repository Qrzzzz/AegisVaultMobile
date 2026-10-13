"""Explicit offline historical text recovery. Never imported by the Android app.

Requires Python >=3.11 and cryptography >=42.0. No secret command arguments,
network, plaintext files, automatic format fallback or logging of data.
"""
import argparse
import base64
import binascii
from datetime import datetime, timezone
import getpass
import hashlib
import json
import math
import os
from pathlib import Path
import struct
import sys
import warnings

from cryptography.exceptions import InvalidTag
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.scrypt import Scrypt

PLAIN = 1_048_576
ENCODED = 2_097_152
OLD_METADATA = 16_384
OLD_PACKAGE = (PLAIN + 16) * 4 // 3 + 1 + OLD_METADATA


class MigrationError(Exception):
    """Only fixed category codes may be displayed to a user."""


def fail(code="PROTOCOL"):
    raise MigrationError(code)


def budget(size, maximum):
    if size > maximum:
        fail("RESOURCE")


def text_bytes(text):
    budget(len(text), PLAIN)
    try:
        raw = text.encode("utf-8", errors="strict")
    except UnicodeError:
        fail("UTF8")
    budget(len(raw), PLAIN)
    # Android also bounds UTF-16 units.
    budget(len(text.encode("utf-16-le")) // 2, PLAIN)
    return raw


def decode(value, maximum, *, url=False):
    if type(value) is not str:
        fail()
    budget(len(value), ENCODED)
    padding = len(value) - len(value.rstrip("="))
    budget(len(value) * 3 // 4 - padding, maximum)
    try:
        result = base64.b64decode(value + "=" * (-len(value) % 4) if url else value,
                                  altchars=b"-_" if url else None, validate=True)
    except (ValueError, binascii.Error):
        fail()
    budget(len(result), maximum)
    return result


def parse_old(raw):
    # Bound nesting/metadata before json.loads allocates objects. Only the literal
    # top-level ciphertext string is exempt from the metadata count.
    depth = 0
    metadata = 0
    ciphertext = False
    i = 0
    while i < len(raw):
        c = raw[i]
        if c in (123, 91):
            depth += 1
            if depth > 32:
                fail("RESOURCE")
        elif c in (125, 93):
            depth -= 1
        elif c == 34:
            start = i
            i += 1
            while i < len(raw) and raw[i] != 34:
                if raw[i] == 92:
                    i += 1
                i += 1
            if i >= len(raw):
                fail()
            end = i + 1
            metadata += 2 if ciphertext and depth == 1 else end - start
            j = end
            while j < len(raw) and raw[j] in b" \t\r\n":
                j += 1
            ciphertext = depth == 1 and raw[start:end] == b'"ciphertext"' and j < len(raw) and raw[j] == 58
            budget(metadata, OLD_METADATA)
            i += 1
            continue
        metadata += 1
        budget(metadata, OLD_METADATA)
        i += 1

    def unique(pairs):
        result = {}
        for key, value in pairs:
            if key in result:
                fail()
            result[key] = value
        return result

    def constant(_):
        fail()

    def finite(value):
        result = float(value)
        if not math.isfinite(result):
            fail()
        return result

    try:
        value = json.loads(raw.decode("utf-8", errors="strict"), object_pairs_hook=unique, parse_constant=constant, parse_float=finite)
    except (UnicodeError, ValueError, RecursionError):
        fail()
    if type(value) is not dict:
        fail()
    return value


def scrypt(password, salt, n, r, p, length):
    if any(type(v) is not int for v in (n, r, p, length)):
        fail()
    if length != 32 or not 16 <= len(salt) <= 64 or not 16384 <= n <= 1048576 or n & (n - 1) or not 1 <= r <= 16 or not 1 <= p <= 8:
        fail()
    budget(128 * n * r, 64 * 1024 * 1024)
    budget(n * r * p, 4194304)
    if password == "":
        fail("PASSWORD")
    return Scrypt(salt=salt, length=length, n=n, r=r, p=p).derive(text_bytes(password))


def recover(token, password, source_format):
    budget(len(token), ENCODED)
    try:
        # AK embeds the historical password as Unicode text. Only its ciphertext
        # is Base64/ASCII; rejecting the entire wrapper would lose valid data.
        budget(len(token.encode("utf-8", errors="strict")), ENCODED)
    except UnicodeError:
        fail()
    token = token.strip()
    try:
        if source_format == "android-json":
            if not token.startswith("AGV1."):
                fail()
            data = parse_old(decode(token[5:], OLD_PACKAGE, url=True))
            if type(data.get("v")) is not int or data["v"] != 1 or data.get("kind") != "text" or data.get("alg") != "AES-256-GCM":
                fail()
            kdf = data.get("kdf")
            if type(kdf) is not dict or kdf.get("name") != "scrypt" or type(kdf.get("salt_len")) is not int:
                fail()
            salt = decode(data.get("salt"), 64, url=True)
            if len(salt) != kdf["salt_len"]:
                fail()
            nonce = decode(data.get("nonce"), 12, url=True)
            ciphertext = decode(data.get("ciphertext"), PLAIN + 16, url=True)
            if len(nonce) != 12 or len(ciphertext) < 16:
                fail()
            key = scrypt(password, salt, kdf.get("n"), kdf.get("r"), kdf.get("p"), kdf.get("length"))
        elif source_format in ("aes", "ak"):
            if source_format == "ak":
                parts = token.split("#", 2)
                if len(parts) != 3 or parts[0] != "AK" or not parts[1] or not parts[2]:
                    fail()
                # The embedded password is inherently exposed by the old format.
                password, token = parts[1:]
            if not password:
                fail("PASSWORD")
            raw = decode(token, PLAIN + 28)
            nonce, ciphertext = raw[:12], raw[12:]
            key = hashlib.sha256(text_bytes(password)).digest()
        else:
            fail()
        if len(nonce) != 12 or len(ciphertext) < 16:
            fail()
        plain = AESGCM(key).decrypt(nonce, ciphertext, None)
        budget(len(plain), PLAIN)
        text = plain.decode("utf-8", errors="strict")
        text_bytes(text)
        return text
    except InvalidTag:
        fail("AUTH")
    except UnicodeError:
        fail("UTF8")


def migrate(token, password, new_password, source_format):
    if not new_password or new_password.isspace():
        fail("PASSWORD")
    plaintext = recover(token, password, source_format)
    plain = text_bytes(plaintext)
    salt, nonce = os.urandom(16), os.urandom(12)
    key = scrypt(new_password, salt, 32768, 8, 1, 32)
    b64 = lambda value: base64.b64encode(value).decode("ascii")
    header = {"format": "aegisvault.text", "version": 1, "algorithm": "AES-256-GCM",
              "created_at": datetime.now(timezone.utc).isoformat(timespec="seconds").replace("+00:00", "Z"),
              "nonce": b64(nonce), "kdf": {"type": "scrypt", "salt": b64(salt), "n": 32768, "r": 8, "p": 1, "length": 32}}
    aad = json.dumps(header, ensure_ascii=False, sort_keys=True, separators=(",", ":"), allow_nan=False).encode("utf-8")
    cipher = AESGCM(key).encrypt(nonce, plain, aad)
    if AESGCM(key).decrypt(nonce, cipher, aad) != plain:
        fail("AUTH")
    raw = b"AGVTEXT\x01" + struct.pack(">I", len(aad)) + aad + cipher
    result = "AGV1." + base64.urlsafe_b64encode(raw).decode("ascii").rstrip("=")
    budget(len(result), ENCODED)
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--format", choices=("android-json", "aes", "ak"), required=True)
    parser.add_argument("--input", type=Path, required=True, help="One original text token; keep a separate backup")
    parser.add_argument("--output", type=Path, required=True, help="New text token; must not already exist")
    args = parser.parse_args()
    try:
        # Refuse getpass's echoing fallback on redirected/noninteractive terminals.
        warnings.simplefilter("error", getpass.GetPassWarning)
        if args.output.exists() or args.input.resolve() == args.output.resolve():
            fail("OUTPUT_EXISTS")
        with args.input.open("rb") as source:
            raw = source.read(ENCODED + 1)
        budget(len(raw), ENCODED)
        token = raw.decode("utf-8", errors="strict")
        password = "" if args.format == "ak" else getpass.getpass("Old password (hidden): ")
        new_password = getpass.getpass("New password (hidden): ")
        if new_password != getpass.getpass("Repeat new password (hidden): "):
            fail("PASSWORD_MISMATCH")
        result = migrate(token, password, new_password, args.format)
        # All authentication/validation completes before any output is opened.
        with args.output.open("x", encoding="ascii") as target:
            target.write(result + "\n")
        print("Migration succeeded. Keep the original backup until both readers verify the new token.")
    except MigrationError as exc:
        print(f"Migration failed: {exc}", file=sys.stderr)
        return 1
    except getpass.GetPassWarning:
        print("Migration failed: HIDDEN_TERMINAL_REQUIRED", file=sys.stderr)
        return 1
    except (OSError, UnicodeError, ValueError):
        print("Migration failed: INPUT_OR_OUTPUT", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
