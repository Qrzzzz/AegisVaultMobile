"""Historical corpus and CLI failure atomicity; all values are public fixtures."""
import base64
import importlib.util
import json
import hashlib
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parent.parent
spec = importlib.util.spec_from_file_location("migration", ROOT / "scripts/migrate-legacy.py")
m = importlib.util.module_from_spec(spec)
spec.loader.exec_module(m)
SAMPLES = json.loads((ROOT / "app/src/test/resources/android-historical.json").read_text(encoding="utf-8"))


class MigrationTests(unittest.TestCase):
    def test_unicode_embedded_ak_password_is_recovered_without_logging(self):
        password, plaintext = "公开测试密钥-é", "公开 Unicode 迁移样本 🔐"
        nonce = bytes(range(12))
        cipher = m.AESGCM(hashlib.sha256(password.encode("utf-8")).digest()).encrypt(nonce, plaintext.encode("utf-8"), None)
        token = "AK#" + password + "#" + base64.b64encode(nonce + cipher).decode("ascii")
        self.assertEqual(plaintext, m.recover(token, "", "ak"))
        invalid = m.AESGCM(hashlib.sha256(password.encode("utf-8")).digest()).encrypt(nonce, b"\x80", None)
        with self.assertRaisesRegex(m.MigrationError, "UTF8"):
            m.recover(base64.b64encode(nonce + invalid).decode("ascii"), password, "aes")
    def test_frozen_corpus_and_explicit_formats(self):
        for name, fmt in (("modern", "android-json"), ("legacy", "aes"), ("ak", "ak")):
            f = SAMPLES[name]
            self.assertEqual(f["plaintext"], m.recover(f["token"], f["password"], fmt))
            self.assertTrue(m.migrate(f["token"], f["password"], "new-public-test-password", fmt).startswith("AGV1.QUdWVEVYVA"))
        with self.assertRaises(m.MigrationError):
            m.recover(SAMPLES["modern"]["token"], SAMPLES["modern"]["password"], "aes")

    def test_wrong_password_and_damage_never_succeed(self):
        for name, fmt in (("modern", "android-json"), ("legacy", "aes")):
            with self.assertRaisesRegex(m.MigrationError, "AUTH"):
                m.migrate(SAMPLES[name]["token"], "wrong", "new", fmt)
        for name, fmt in (("modern", "android-json"), ("legacy", "aes"), ("ak", "ak")):
            with self.assertRaises(m.MigrationError):
                m.migrate(SAMPLES[name]["token"][:-8], SAMPLES[name]["password"], "new", fmt)
        with self.assertRaisesRegex(m.MigrationError, "AUTH"):
            m.migrate(SAMPLES["ak"]["token"].replace("legacy-pass", "wrong"), "", "new", "ak")

    def test_cost_and_input_preflight(self):
        with self.assertRaisesRegex(m.MigrationError, "RESOURCE"):
            m.scrypt("p", bytes(16), 131072, 8, 1, 32)
        with self.assertRaisesRegex(m.MigrationError, "PROTOCOL"):
            m.scrypt("p", bytes(16), 16384.0, 8, 1, 32)
        with self.assertRaisesRegex(m.MigrationError, "RESOURCE"):
            m.recover("A" * (m.ENCODED + 1), "p", "aes")
        for raw in (b'{"v":1,"v":1}', b'{"x":NaN}', b'{"x":1e999}', b'\x80', b'[' * 33 + b']' * 33):
            with self.assertRaises(m.MigrationError):
                m.parse_old(raw)

    def test_cli_no_plaintext_logs_no_output_on_failure_and_no_overwrite(self):
        from contextlib import redirect_stdout, redirect_stderr
        from io import StringIO
        with tempfile.TemporaryDirectory() as td:
            src, out = Path(td) / "old.txt", Path(td) / "new.txt"
            src.write_text(SAMPLES["modern"]["token"], encoding="ascii")
            argv = ["migration", "--format", "android-json", "--input", str(src), "--output", str(out)]
            logs = StringIO()
            with patch("sys.argv", argv), patch("getpass.getpass", side_effect=["wrong", "new", "new"]), redirect_stdout(logs), redirect_stderr(logs):
                self.assertEqual(1, m.main())
            self.assertFalse(out.exists())
            self.assertEqual("Migration failed: AUTH\n", logs.getvalue())
            out.write_text("keep-original", encoding="ascii")
            with patch("sys.argv", argv), patch("getpass.getpass") as prompt, redirect_stderr(StringIO()):
                self.assertEqual(1, m.main())
                prompt.assert_not_called()
            self.assertEqual("keep-original", out.read_text())


if __name__ == "__main__":
    unittest.main()
