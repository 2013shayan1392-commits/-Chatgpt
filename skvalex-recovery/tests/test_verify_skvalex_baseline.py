import importlib.util
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).parents[1] / "tools" / "verify_skvalex_baseline.py"
SPEC = importlib.util.spec_from_file_location("verify_skvalex_baseline", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class BaselineVerifierTests(unittest.TestCase):
    def test_rejects_missing_file(self):
        with self.assertRaises(MODULE.VerificationError):
            MODULE.verify(Path("/definitely/not/a/real/skvalex.apk"))

    def test_rejects_nonmatching_hash_before_zip_parsing(self):
        with tempfile.TemporaryDirectory() as temp:
            path = Path(temp) / "wrong.apk"
            path.write_bytes(b"not an apk")
            with self.assertRaisesRegex(MODULE.VerificationError, "SHA-256"):
                MODULE.verify(path)


if __name__ == "__main__":
    unittest.main()
