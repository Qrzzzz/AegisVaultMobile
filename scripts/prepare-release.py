"""Validate the tag, built APK version and persistent test signer before packaging."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess

ROOT = Path(__file__).resolve().parent.parent
EXPECTED_SIGNER = "b2b0f013dcfa428bb6f4c3563faa204c7890f72abd6062d1cbbb155222d493c9"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--tag", required=True)
    parser.add_argument("--check-only", action="store_true")
    args = parser.parse_args()
    config = (ROOT / "app/build.gradle.kts").read_text(encoding="utf-8")
    version = re.search(r'versionName\s*=\s*"(\d+\.\d+)"', config)
    code = re.search(r'versionCode\s*=\s*(\d+)', config)
    if not version or not code or args.tag != f"v{version[1]}":
        raise SystemExit("Product version must be major.minor and match the tag")
    if not (ROOT / f"docs/releases/{args.tag}.md").is_file():
        raise SystemExit("Missing version-specific release notes")
    if args.check_only:
        print(f"Validated {args.tag}, versionCode {code[1]}")
        return
    output = ROOT / "app/build/outputs/apk/debug"
    metadata = json.loads((output / "output-metadata.json").read_text())
    elements = metadata["elements"]
    if len(elements) != 1:
        raise SystemExit("Expected one universal APK")
    element = elements[0]
    if element["versionName"] != version[1] or element["versionCode"] != int(code[1]):
        raise SystemExit("Built APK metadata does not match the source version")
    apk = output / "app-debug.apk"
    sdk = Path(os.environ.get("ANDROID_HOME") or os.environ["ANDROID_SDK_ROOT"])
    tools = sdk / "build-tools/34.0.0"
    suffix = ".bat" if os.name == "nt" else ""
    verified = subprocess.check_output([str(tools / f"apksigner{suffix}"), "verify", "--print-certs", str(apk)], text=True)
    if f"certificate SHA-256 digest: {EXPECTED_SIGNER}" not in verified:
        raise SystemExit("APK signer differs from the existing 1.0.1 test identity")
    badging = subprocess.check_output([str(tools / ("aapt.exe" if os.name == "nt" else "aapt")), "dump", "badging", str(apk)], text=True)
    if f"versionCode='{code[1]}' versionName='{version[1]}'" not in badging.splitlines()[0]:
        raise SystemExit("Actual APK version does not match metadata")
    dist = ROOT / "dist"
    dist.mkdir(exist_ok=True)
    target = dist / f"AegisVaultMobile-{args.tag}-debug.apk"
    shutil.copyfile(apk, target)
    digest = hashlib.sha256(target.read_bytes()).hexdigest()
    (dist / "SHA256SUMS.txt").write_text(f"{digest}  {target.name}\n", encoding="ascii")
    print(f"Verified and packaged {target.name}: {digest}")


if __name__ == "__main__":
    main()
