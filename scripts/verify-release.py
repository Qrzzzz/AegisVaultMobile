"""Verify exact public/draft release assets using GitHub's server-side digests."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--tag", required=True)
    parser.add_argument("--repo", default="Qrzzzz/AegisVaultMobile")
    parser.add_argument("--assets-dir", type=Path, default=Path("dist"))
    parser.add_argument("--draft", action="store_true")
    args = parser.parse_args()
    release = json.loads(subprocess.check_output(["gh", "api", f"repos/{args.repo}/releases/tags/{args.tag}"], text=True))
    if release["tag_name"] != args.tag or release["draft"] != args.draft or release["prerelease"]:
        raise SystemExit("Unexpected release tag or publication state")
    apk = f"AegisVaultMobile-{args.tag}-debug.apk"
    manifest = args.assets_dir / "SHA256SUMS.txt"
    digest, name = manifest.read_text().strip().split()
    if name != apk or len(digest) != 64:
        raise SystemExit("Unexpected checksum manifest")
    expected = {apk: digest, "SHA256SUMS.txt": hashlib.sha256(manifest.read_bytes()).hexdigest()}
    assets = {item["name"]: item for item in release["assets"]}
    if len(release["assets"]) != 2 or set(assets) != set(expected):
        raise SystemExit("Unexpected release asset set")
    for name, digest in expected.items():
        asset = assets[name]
        if asset["state"] != "uploaded" or asset["size"] <= 0 or asset.get("digest") != f"sha256:{digest}":
            raise SystemExit(f"Server digest/state mismatch: {name}")
    print(f"Verified exact release {release['id']}: {release['html_url']}")


if __name__ == "__main__":
    main()
