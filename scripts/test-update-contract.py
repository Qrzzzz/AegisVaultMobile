"""Contract and actual release verifier regression; no GitHub credentials/network."""
import copy
import hashlib
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parent.parent


def load(name):
    spec = importlib.util.spec_from_file_location(name, ROOT / f"scripts/{name}.py")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


contract = load("update-contract")
verifier = load("verify-release")


class ContractTests(unittest.TestCase):
    def sample(self, folder):
        apk = folder / "AegisVaultMobile-v1.5-debug.apk"
        apk.write_bytes(b"artificial APK bytes, not installable")
        return contract.manifest("1.5", 7, apk, contract.SIGNER)

    def test_canonical_cross_language_fixture(self):
        with tempfile.TemporaryDirectory() as temp:
            data = self.sample(Path(temp))
            fixture = json.loads((ROOT / "app/src/test/resources/update-manifest-v1.json").read_text())
            self.assertEqual(fixture, data)

    def test_bad_types_bounds_or_identity_fail(self):
        with tempfile.TemporaryDirectory() as temp:
            original = self.sample(Path(temp))
            for field, bad in (("schemaVersion", True), ("schemaVersion", 2), ("versionCode", "7"),
                               ("versionCode", 0), ("versionCode", 2147483648), ("minSdk", 25),
                               ("minSdk", True), ("applicationId", "another.app"), ("releaseTag", "v1.6")):
                data = copy.deepcopy(original); data[field] = bad
                with self.assertRaises(ValueError):
                    contract.validate_manifest(data, "v1.5", original["apk"]["name"], original["apk"]["sha256"])
            for field, bad in (("size", True), ("size", 0), ("size", 134217729),
                               ("signingCertificateSha256", "0" * 64), ("name", "../../evil.apk")):
                data = copy.deepcopy(original); data["apk"][field] = bad
                with self.assertRaises(ValueError):
                    contract.validate_manifest(data, "v1.5", original["apk"]["name"], original["apk"]["sha256"])

    def test_duplicate_json_is_not_coerced(self):
        with self.assertRaises(ValueError):
            json.loads('{"schemaVersion":1,"schemaVersion":2}', object_pairs_hook=contract.strict_object)

    def verify(self, folder, tag, entries, data=None, corrupt=None):
        lines = []
        assets = []
        for name, raw in entries.items():
            (folder / name).write_bytes(raw)
            digest = hashlib.sha256(raw).hexdigest()
            lines.append(f"{digest}  {name}\n")
            assets.append({"name": name, "digest": "sha256:" + digest, "state": "uploaded", "size": len(raw)})
        if data is not None:
            raw = (json.dumps(data, sort_keys=True, indent=2) + "\n").encode()
            (folder / "update-manifest.json").write_bytes(raw)
            digest = hashlib.sha256(raw).hexdigest(); lines.append(f"{digest}  update-manifest.json\n")
            assets.append({"name": "update-manifest.json", "digest": "sha256:" + digest, "state": "uploaded", "size": len(raw)})
        raw = ''.join(lines).encode(); (folder / "SHA256SUMS.txt").write_bytes(raw)
        assets.append({"name": "SHA256SUMS.txt", "digest": "sha256:" + hashlib.sha256(raw).hexdigest(), "state": "uploaded", "size": len(raw)})
        if corrupt: corrupt(assets)
        release = {"id": 1, "tag_name": tag, "draft": False, "prerelease": False, "assets": assets, "html_url": "artificial"}
        with patch.object(sys, "argv", ["verify", "--tag", tag, "--assets-dir", str(folder)]), \
                patch.object(verifier.subprocess, "check_output", return_value=json.dumps(release)):
            verifier.main()

    def test_actual_verifier_accepts_old_two_asset_release(self):
        with tempfile.TemporaryDirectory() as temp:
            self.verify(Path(temp), "v1.4", {"AegisVaultMobile-v1.4-debug.apk": b"old artifact"})

    def test_actual_verifier_accepts_standard_three_assets(self):
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp); data = self.sample(folder)
            self.verify(folder, "v1.5", {data["apk"]["name"]: (folder / data["apk"]["name"]).read_bytes()}, data)

    def test_actual_verifier_rejects_missing_manifest_for_new_release(self):
        with tempfile.TemporaryDirectory() as temp, self.assertRaises(SystemExit):
            self.verify(Path(temp), "v1.5", {"AegisVaultMobile-v1.5-debug.apk": b"missing contract"})

    def test_actual_verifier_rejects_api_digest_and_size_mismatch(self):
        with tempfile.TemporaryDirectory() as temp:
            folder = Path(temp); data = self.sample(folder); apk = {data["apk"]["name"]: (folder / data["apk"]["name"]).read_bytes()}
            with self.assertRaises(SystemExit):
                self.verify(folder, "v1.5", apk, data, lambda assets: assets[0].update(digest="sha256:" + "0" * 64))
            with self.assertRaises(SystemExit):
                self.verify(folder, "v1.5", apk, data, lambda assets: assets[0].update(size=123))


if __name__ == "__main__":
    unittest.main()
