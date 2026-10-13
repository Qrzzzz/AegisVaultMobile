"""Actual frozen desktop CryptoService oracle; public artificial test inputs only."""
import argparse
import base64
import hashlib
import importlib.util
import json
from pathlib import Path
import platform
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent
SHA = "c6664e2b7e6a11b7dfb55f0881136131e1d2bc74"


def input_text(case):
    return case["text"] if "text" in case else case["repeat"] * case["count"]


def normalized(value):
    if len(value) <= 10000:
        return {"kind": "ok", "text": value}
    raw = value.encode("utf-8")
    return {"kind": "ok", "utf8_bytes": len(raw), "sha256": hashlib.sha256(raw).hexdigest()}


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--desktop-root", type=Path, required=True)
    p.add_argument("--exchange-dir", type=Path, required=True)
    p.add_argument("--phase", choices=("prepare", "verify"), required=True)
    args = p.parse_args()
    hashes = {}
    for path in sorted((args.desktop_root / "src").rglob("*.py")):
        rel = path.relative_to(args.desktop_root).as_posix()
        original = subprocess.check_output(["git", "-C", str(ROOT.parent / "桌面版"), "show", f"{SHA}:{rel}"])
        assert path.read_bytes() == original, "Desktop source differs from frozen Git blob"
        hashes[rel] = hashlib.sha256(original).hexdigest()
    snapshot = ROOT / "docs/contracts/desktop-c6664e2"
    for rel, digest in json.loads((snapshot / "text-semantics-manifest.json").read_text())["git_blob_sha256"].items():
        assert hashlib.sha256((snapshot / Path(rel).name).read_bytes()).hexdigest() == digest
    sys.path.insert(0, str(args.desktop_root / "src"))
    from aegisvault.services.crypto_service import CryptoService
    from aegisvault.settings.models import AppSettings
    import cryptography
    service = CryptoService(AppSettings())
    args.exchange_dir.mkdir(parents=True, exist_ok=True)
    fixture = ROOT / "app/src/test/resources/text-semantics-1.3.json"
    receipt = {"desktop_sha": SHA, "python": sys.version, "platform": platform.platform(),
               "cryptography": cryptography.__version__, "source_sha256": hashes}

    def execute(case):
        text = input_text(case)
        try:
            if case["op"] == "encode":
                result = service.base64_encode_text(text)
            elif case["op"] == "decode":
                result = service.base64_decode_text(text, strict=case["strict"], ignore_ascii_whitespace=case["ignore"])
            else:
                token = service.encrypt_text(text, case["password"]).ciphertext
                assert service.decrypt_text(token, case["password"]).plaintext == text
                return {"kind": "ok", "text": text, "token": token}
            return normalized(result)
        except Exception as exc:
            code = getattr(exc, "code", type(exc).__name__)
            if code == "resource.limit_exceeded": category = "RESOURCE"
            elif code == "validation.password_required": category = "PASSWORD_REQUIRED"
            elif code == "ipc.invalid_request" or isinstance(exc, UnicodeError): category = "INPUT_INVALID"
            elif code in ("base64.invalid_text", "base64.invalid_data"): category = "ENCODING_INVALID"
            else: raise
            return {"kind": "error", "category": category, "desktop_code": code}

    if args.phase == "prepare":
        corpus = {"", " ", "\t\n\r\f\v ", "YQ==", "YWI=", "Zm9v", "aGVs\r\nbG8=", " YQ== ",
                  "gA==", "wK8=", "7aCA", "Zh==", "Zm9=", "你好", "YQ==\u00a0", "YQ==\u3000", "YQ==\u0085",
                  "YQ==\u200b", "YQ==\x00", "_w==", "-w==", "/w==", "+w==", "%%%%", "😀", "\ud800", "\udc00"}
        for ws in "\t\n\r\f\v ":
            corpus.update((ws + "YQ==", "YQ==" + ws, "Y" + ws + "Q==", ws * 4))
        for prefix in ("", "Y", "YQ", "YWI", "Zm9v"):
            for padding in range(9):
                for suffix in ("", "YQ==", "=", "?"):
                    corpus.add(prefix + "=" * padding + suffix)
        corpus.add(base64.b64encode("中文 é😀 \n".encode()).decode())
        cases = []
        for text in sorted(corpus):
            for strict in (True, False):
                for ignore in (False, True):
                    cases.append({"op": "decode", "text": text, "strict": strict, "ignore": ignore})
        for text in ("", " ", "\t\r\n", "  edges  ", "中文😀é", "é", "e\u0301", "\ud800", "\udc00", "x\ud800y"):
            cases.append({"op": "encode", "text": text})
        for strict in (True, False):
            for ignore in (False, True):
                for char, count in ((" ", 2097152), ("A", 2097153), ("A", 1398103), ("\u00a0", 1048577)):
                    cases.append({"op": "decode", "repeat": char, "count": count, "strict": strict, "ignore": ignore})
        cases.append({"op": "encode", "repeat": "a", "count": 1048576})
        # Fresh real desktop AGV1 output, including empty/whitespace data/passwords.
        for text, password in (("", " "), (" \t\r\n", " \t "), ("  中文🔐  ", " edge password "),
                               ("é e\u0301", "päss-密钥"), ("", ""), ("x", ""),
                               ("\ud800", "p"), ("x", "\udc00")):
            cases.append({"op": "encrypt", "text": text, "password": password})
        for i, case in enumerate(cases):
            case["id"] = i
            case["desktop"] = execute(case)
        spec = importlib.util.spec_from_file_location("migration", ROOT / "scripts/migrate-legacy.py")
        migration = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(migration)
        old = json.loads((ROOT / "app/src/test/resources/android-historical.json").read_text(encoding="utf-8"))["modern"]
        migrated = migration.migrate(old["token"], old["password"], " \t ", "android-json")
        assert service.decrypt_text(migrated, " \t ").plaintext == old["plaintext"]
        fixture.write_text(json.dumps({"desktop_sha": SHA, "oracle_python": sys.version, "notice": "Artificial public inputs only", "cases": cases,
                                      "migration_whitespace": {"token": migrated, "password": " \t ", "plaintext": old["plaintext"]}}, ensure_ascii=True, indent=2) + "\n", encoding="utf-8")
        receipt["cases"] = len(cases)
        receipt["decode_cases"] = sum(c["op"] == "decode" for c in cases)
        receipt["four_mode_combinations"] = 4
    else:
        data = json.loads(fixture.read_text(encoding="utf-8"))
        actual = json.loads((args.exchange_dir / "android-semantics.json").read_text(encoding="utf-8"))
        assert len(actual) == len(data["cases"])
        for case, android in zip(data["cases"], actual, strict=True):
            assert case["id"] == android["id"]
            current = execute(case)
            if case["op"] == "encrypt" and current["kind"] == "ok":
                assert service.decrypt_text(android["token"], case["password"]).plaintext == input_text(case)
            else:
                expected = {k: v for k, v in current.items() if k != "desktop_code"}
                assert {k: v for k, v in android.items() if k != "id"} == expected, f"Cross-platform mismatch case {case['id']}"
        receipt["actual_android_vs_actual_desktop"] = len(actual)
        receipt["android_agv1_read_by_actual_desktop"] = 4
        migration = data["migration_whitespace"]
        assert service.decrypt_text(migration["token"], migration["password"]).plaintext == migration["plaintext"]
        receipt["migration_with_whitespace_password"] = "PASS"
    (args.exchange_dir / f"desktop-{args.phase}.json").write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
    print(f"PASS real desktop CryptoService {args.phase}; {receipt.get('cases', receipt.get('actual_android_vs_actual_desktop'))} cases; source {SHA}")


if __name__ == "__main__":
    main()
