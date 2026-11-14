import importlib.util
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "tools" / "patch_files_settings_smali.py"
spec = importlib.util.spec_from_file_location("patch_files_settings_smali", SCRIPT)
patcher = importlib.util.module_from_spec(spec)
spec.loader.exec_module(patcher)


class PatchFilesSettingsSmaliTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)
        (self.root / "smali/o").mkdir(parents=True)
        (self.root / "smali/org/skvalex/cr").mkdir(parents=True)

    def tearDown(self):
        self.tmp.cleanup()

    def write(self, relative, text):
        path = self.root / relative
        path.write_text(text, encoding="utf-8")
        return path

    def test_replaces_two_folder_picker_strings_and_sim_tag(self):
        pg = self.write(
            "smali/o/pg2.smali",
            """.class Lo/pg2;
.method public a(Landroidx/preference/Preference;)Z
    const-wide v0, -0x7b2c01d2b835L
    invoke-static {v0, v1}, Lo/w80;->c(J)Ljava/lang/String;
    move-result-object v0
    const-wide v0, -0x7b4201d2b835L
    invoke-static {v0, v1}, Lo/w80;->c(J)Ljava/lang/String;
    move-result-object v0
    return v0
.end method
""",
        )
        rg = self.write(
            "smali/o/rg2.smali",
            """.class Lo/rg2;
.method public a(Landroidx/preference/Preference;)Z
    const-wide v0, -0x7b0e01d2b835L
    invoke-static {v0, v1}, Lo/w80;->c(J)Ljava/lang/String;
    move-result-object v0
    return v0
.end method
""",
        )
        for case in patcher.CASES[1:]:
            patcher.patch_case(self.root, case)
        pg_text = pg.read_text()
        rg_text = rg.read_text()
        self.assertIn('const-string v0, "skvalex.files.folder-picker"', pg_text)
        self.assertIn('const-string v0, "skvalex.files.folder-picker.dialog"', pg_text)
        self.assertIn('const-string v0, "skvalex.files.sim-selection.dialog"', rg_text)
        self.assertNotIn("Lo/w80;->c(J)", pg_text + rg_text)

    def test_removes_only_discarded_clinit_call(self):
        path = self.write(
            "smali/org/skvalex/cr/Settings$FilesSettingsFragment.smali",
            """.class Lorg/skvalex/cr/Settings$FilesSettingsFragment;
.method static constructor <clinit>()V
    const-wide v0, -0x7b5801d2b835L
    invoke-static {v0, v1}, Lo/w80;->c(J)Ljava/lang/String;
    return-void
.end method
""",
        )
        patcher.patch_case(self.root, patcher.CASES[0])
        text = path.read_text()
        self.assertNotIn("Lo/w80;->c(J)", text)
        self.assertIn("return-void", text)

    def test_fails_closed_when_expected_sequence_is_missing(self):
        path = self.write(
            "smali/o/rg2.smali",
            """.class Lo/rg2;
.method public a(Landroidx/preference/Preference;)Z
    return v0
.end method
""",
        )
        with self.assertRaises(RuntimeError):
            patcher.patch_case(self.root, patcher.CASES[3])
        self.assertNotIn("skvalex.files.sim-selection.dialog", path.read_text())


if __name__ == "__main__":
    unittest.main()
