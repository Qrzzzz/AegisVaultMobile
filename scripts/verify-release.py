"""Verify exact public/draft release assets using GitHub's server-side digests."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import re
import importlib.util


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--tag", required=True)
    parser.add_argument("--repo", default="Qrzzzz/AegisVaultMobile")
    parser.add_argument("--assets-dir", type=Path, default=Path("dist"))
    parser.add_argument("--draft", action="store_true")
    parser.add_argument("--release-id", type=int)
    args = parser.parse_args()
    endpoint = f"repos/{args.repo}/releases/{args.release_id}" if args.release_id else f"repos/{args.repo}/releases/tags/{args.tag}"
    release = json.loads(subprocess.check_output(["gh", "api", endpoint], encoding="utf-8"))
    if release["tag_name"] != args.tag or release["draft"] != args.draft or release["prerelease"]:
        raise SystemExit("Unexpected release tag or publication state")
    apk = f"AegisVaultMobile-{args.tag}-debug.apk"
    manifest = args.assets_dir / "SHA256SUMS.txt"
    entries = {}
    for line in manifest.read_text(encoding="ascii").splitlines():
        fields = line.split()
        if len(fields) != 2 or not re.fullmatch(r"[0-9a-f]{64}", fields[0]) or fields[1] in entries:
            raise SystemExit("Unexpected checksum manifest")
        entries[fields[1]] = fields[0]
    digest = entries.get(apk)
    if digest is None:
        raise SystemExit("Missing APK checksum")
    expected = {apk: digest, "SHA256SUMS.txt": hashlib.sha256(manifest.read_bytes()).hexdigest()}
    update = args.assets_dir / "update-manifest.json"
    version = re.fullmatch(r"v(\d+)\.(\d+)", args.tag)
    if version and tuple(map(int, version.groups())) >= (1, 5) and not update.exists():
        raise SystemExit("Missing standardized update manifest")
    if update.exists():
        spec = importlib.util.spec_from_file_location("update_contract", Path(__file__).with_name("update-contract.py"))
        contract = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(contract)
        data = json.loads(update.read_text(encoding="utf-8"), object_pairs_hook=contract.strict_object)
        contract.validate_manifest(data, args.tag, apk, digest)
        expected[update.name] = hashlib.sha256(update.read_bytes()).hexdigest()
        if entries.get(update.name) != expected[update.name]:
            raise SystemExit("Update manifest checksum mismatch")
    if set(entries) != (set(expected) - {"SHA256SUMS.txt"}):
        raise SystemExit("Unexpected checksum asset set")
    assets = {item["name"]: item for item in release["assets"]}
    if len(release["assets"]) != len(expected) or set(assets) != set(expected):
        raise SystemExit("Unexpected release asset set")
    for name, digest in expected.items():
        asset = assets[name]
        if asset["state"] != "uploaded" or asset["size"] <= 0 or asset.get("digest") != f"sha256:{digest}":
            raise SystemExit(f"Server digest/state mismatch: {name}")
    if update.exists() and data["apk"]["size"] != assets[apk]["size"]:
        raise SystemExit("APK size does not match update contract")
    print(f"Verified exact release {release['id']}: {release['html_url']}")


if __name__ == "__main__":
    main()
