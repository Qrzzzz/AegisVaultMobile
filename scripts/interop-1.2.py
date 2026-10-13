"""Real frozen desktop CryptoService exchange. Artificial public test data only."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path
import platform
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent
SHA = "c6664e2b7e6a11b7dfb55f0881136131e1d2bc74"


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--desktop-root", type=Path, required=True, help="Exact exported desktop source at the frozen SHA")
    p.add_argument("--exchange-dir", type=Path, required=True)
    p.add_argument("--phase", choices=("prepare", "verify"), required=True)
    args = p.parse_args()
    # Compare each imported desktop source to the original Git blob, not just a HEAD label.
    repo = ROOT.parent / "桌面版"
    source_hashes = {}
    for source in sorted((args.desktop_root / "src").rglob("*.py")):
        rel = source.relative_to(args.desktop_root).as_posix()
        original = subprocess.check_output(["git", "-C", str(repo), "show", f"{SHA}:{rel}"])
        if source.read_bytes() != original:
            raise SystemExit("Desktop source differs from frozen commit")
        source_hashes[rel] = hashlib.sha256(original).hexdigest()
    for name, digest in json.loads((ROOT / "docs/contracts/desktop-c6664e2/manifest.json").read_text())["sha256"].items():
        if hashlib.sha256((ROOT / "docs/contracts/desktop-c6664e2" / name).read_bytes()).hexdigest() != digest:
            raise SystemExit("Frozen contract hash mismatch")
    sys.path.insert(0, str(args.desktop_root / "src"))
    from aegisvault.services.crypto_service import CryptoService
    from aegisvault.settings.models import AppSettings
    import cryptography
    service = CryptoService(AppSettings())
    args.exchange_dir.mkdir(parents=True, exist_ok=True)
    receipt = {"desktop_sha": SHA, "python": sys.version, "platform": platform.platform(),
               "cryptography": cryptography.__version__, "source_sha256": source_hashes}
    resource = ROOT / "app/src/test/resources/interop-1.2.json"
    if args.phase == "prepare":
        spec = importlib.util.spec_from_file_location("migration", ROOT / "scripts/migrate-legacy.py")
        migration = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(migration)
        samples = json.loads((ROOT / "app/src/test/resources/android-historical.json").read_text(encoding="utf-8"))
        cases = []
        for text in ("Real desktop → Android 中文 🔐", "line one\nline two é😀"):
            token = service.encrypt_text(text, "public-interop-password").ciphertext
            cases.append({"origin": "desktop", "plaintext": text, "password": "public-interop-password", "token": token})
        for name, fmt in (("modern", "android-json"), ("legacy", "aes"), ("ak", "ak")):
            sample = samples[name]
            token = migration.migrate(sample["token"], sample["password"], "public-new-password", fmt)
            assert service.decrypt_text(token, "public-new-password").plaintext == sample["plaintext"]
            cases.append({"origin": f"migration-{name}", "plaintext": sample["plaintext"], "password": "public-new-password", "token": token})
        resource.write_text(json.dumps({"notice": "Artificial public data only", "desktop_sha": SHA, "cases": cases}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        receipt["prepared_cases"] = len(cases)
    else:
        cases = json.loads((args.exchange_dir / "android-tokens.json").read_text(encoding="utf-8"))
        for case in cases:
            assert service.decrypt_text(case["token"], case["password"]).plaintext == case["plaintext"]
        receipt["android_to_actual_desktop"] = {"passed": len(cases)}
        for case in json.loads(resource.read_text(encoding="utf-8"))["cases"]:
            assert service.decrypt_text(case["token"], case["password"]).plaintext == case["plaintext"]
        receipt["migration_and_desktop_cases_rechecked"] = 5
    (args.exchange_dir / f"desktop-{args.phase}.json").write_text(json.dumps(receipt, indent=2) + "\n", encoding="utf-8")
    print(f"PASS actual desktop CryptoService {args.phase}; source {SHA}; secrets are not logged")


if __name__ == "__main__":
    main()
