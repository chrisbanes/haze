import os
import shutil
import subprocess
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("delete_old_version_docs.sh")


# shortcut: skipped where zsh is absent (ubuntu-latest CI), the docs job runs on macOS; install zsh in build.yml to run it in CI.
@unittest.skipUnless(shutil.which("zsh"), "zsh is required")
class DeleteOldVersionDocsTest(unittest.TestCase):
    def deleted(self, listing):
        """Runs the script against a mock `mike` and returns the versions it deleted."""
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        root = Path(directory.name)
        (root / "list.txt").write_text(listing)
        mike = root / "mike"
        mike.write_text(
            '#!/bin/sh\n'
            f'if [ "$1" = list ]; then cat "{root}/list.txt"; '
            f'else echo "$2" >> "{root}/deleted.txt"; fi\n'
        )
        mike.chmod(0o755)
        env = {**os.environ, "PATH": f"{root}:{os.environ['PATH']}"}
        subprocess.run(["zsh", str(SCRIPT)], env=env, check=True, capture_output=True, text=True)
        deleted = root / "deleted.txt"
        return sorted(deleted.read_text().split()) if deleted.exists() else []

    def test_stableOutranksPrerelease_keepsStable(self):
        self.assertEqual(["2.1.0-alpha01"], self.deleted("2.1.0-alpha01\n2.1.0\n"))
        self.assertEqual(["2.1.0-alpha01"], self.deleted("2.1.0\n2.1.0-alpha01\n"))

    def test_numericPrereleaseSuffix_comparesNumerically(self):
        self.assertEqual(["2.1.0-alpha2"], self.deleted("2.1.0-alpha2\n2.1.0-alpha10\n"))
        self.assertEqual(["2.1.0-alpha10"], self.deleted("2.1.0-alpha10\n2.1.0-beta01\n"))

    def test_dottedPrerelease_groupsWithItsMinorLine(self):
        self.assertEqual(["2.1.0-alpha.2"], self.deleted("2.1.0-alpha.2\n2.1.0-alpha.10\n"))
        self.assertEqual(["2.1.0-alpha.1"], self.deleted("2.1.0-alpha.1\n2.1.0\n"))

    def test_newerPatch_winsOverOlderStableAndPrerelease(self):
        self.assertEqual(["2.1.0", "2.1.1-rc01"], self.deleted("2.1.0\n2.1.1-rc01\n2.1.1\n"))
        self.assertEqual(["2.1.0"], self.deleted("2.1.0\n2.1.1-rc01\n"))

    def test_separateMinorLines_keepOneEach(self):
        self.assertEqual(["2.0.0"], self.deleted("2.0.0\n2.0.1\n2.1.0-alpha01\n"))

    def test_aliasedVersions_areNeverDeleted(self):
        listing = "2.1.0-alpha01 [dev]  2.1.0-alpha01\n2.1.0 [latest]  2.1.0\n2.0.0\n2.0.1\n"
        self.assertEqual(["2.0.0"], self.deleted(listing))
        self.assertEqual([], self.deleted("2.0.0 [latest]\n2.0.1\n"))


if __name__ == "__main__":
    unittest.main()
