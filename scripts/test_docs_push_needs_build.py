import subprocess
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("docs_push_needs_build.sh")


class DocsPushNeedsBuildTest(unittest.TestCase):
    def setUp(self):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        self.repo = Path(directory.name) / "repo"
        self.repo.mkdir()
        self.git("init", "-q")
        self.git("config", "user.email", "test@example.com")
        self.git("config", "user.name", "Test")
        self.git("remote", "add", "origin", str(self.repo))
        self.commit({"README.md": "initial", "src/Main.kt": "initial"})
        self.before = self.git("rev-parse", "HEAD").stdout.strip()

    def git(self, *args, cwd=None):
        return subprocess.run(
            ["git", *args], cwd=cwd or self.repo, check=True, capture_output=True, text=True
        )

    def commit(self, files):
        for name, content in files.items():
            path = self.repo / name
            path.parent.mkdir(parents=True, exist_ok=True)
            if content is None:
                path.unlink()
            else:
                path.write_text(content)
        self.git("add", "--all")
        self.git("commit", "-qm", "Fixture")

    def decision(self, before=None, after="HEAD", cwd=None, check=True):
        return subprocess.run(
            ["sh", str(SCRIPT), before or self.before, after],
            cwd=cwd or self.repo, check=check, capture_output=True, text=True,
        )

    def test_builds_docs_only_changes_and_deletions(self):
        self.commit({"README.md": None, "nested/guide.md": "new", "docs/image.png": "new"})
        self.assertEqual(self.decision().stdout.strip(), "true")

    def test_defers_mixed_push_until_build_completes(self):
        self.commit({"README.md": "updated", "src/Main.kt": "updated"})
        self.assertEqual(self.decision().stdout.strip(), "false")

    def test_large_mixed_push_builds_docs_when_path_filters_may_skip_build(self):
        files = {f"docs/page-{index:03}.md": "new" for index in range(300)}
        files["src/Main.kt"] = "updated"
        self.commit(files)
        self.assertEqual(self.decision().stdout.strip(), "true")

    def test_mixed_push_at_the_300_file_limit_still_defers(self):
        files = {f"docs/page-{index:03}.md": "new" for index in range(299)}
        files["src/Main.kt"] = "updated"
        self.commit(files)
        self.assertEqual(self.decision().stdout.strip(), "false")

    def test_large_docs_only_push_still_builds_docs(self):
        self.commit({f"docs/page-{index:03}.md": "new" for index in range(301)})
        self.assertEqual(self.decision().stdout.strip(), "true")

    def test_checks_the_whole_push_not_only_the_last_commit(self):
        self.commit({"src/Main.kt": "updated"})
        self.commit({"README.md": "updated"})
        self.assertEqual(self.decision().stdout.strip(), "false")

    def test_documentation_prefix_does_not_match_other_directories(self):
        self.commit({"docs-extra/config.yml": "new", "README.md": "updated"})
        self.assertEqual(self.decision().stdout.strip(), "false")

    def test_renaming_code_into_docs_still_requires_build(self):
        self.git("mv", "src/Main.kt", "docs.md")
        self.git("commit", "-qm", "Rename")
        self.assertEqual(self.decision().stdout.strip(), "false")

    def test_fetches_before_commit_in_a_shallow_checkout(self):
        self.commit({"README.md": "updated"})
        clone = self.repo.parent / "shallow"
        self.git("clone", "-q", "--depth=1", self.repo.as_uri(), str(clone))
        self.assertEqual(self.decision(cwd=clone).stdout.strip(), "true")

    def test_initial_push_uses_the_entire_tree(self):
        self.assertEqual(self.decision(before="0" * 40).stdout.strip(), "false")

    def test_fetch_failure_fails_instead_of_skipping_docs(self):
        result = self.decision(before="f" * 40, check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(result.stdout, "")

    def test_diff_failure_fails_instead_of_skipping_docs(self):
        result = self.decision(after="missing", check=False)
        self.assertNotEqual(result.returncode, 0)
        self.assertEqual(result.stdout, "")


if __name__ == "__main__":
    unittest.main()
