import tempfile
import unittest
from pathlib import Path

from verify_screenshot_matrix import verify_report


class ScreenshotMatrixReportTest(unittest.TestCase):
    def report(self, cases):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        path = Path(directory.name) / "report.xml"
        path.write_text("<testsuite>" + cases + "</testsuite>")
        return path

    def test_accepts_exact_successful_cases(self):
        verify_report(self.report('<testcase name="capture[a]"/>'), {"capture[a]"})

    def test_rejects_missing_cases(self):
        with self.assertRaises(ValueError):
            verify_report(self.report('<testcase name="capture[a]"/>'), {"capture[a]", "capture[b]"})

    def test_rejects_skipped_failed_and_duplicate_cases(self):
        for child in ["<skipped/>", "<failure/>", "<error/>"]:
            with self.subTest(child=child), self.assertRaises(ValueError):
                verify_report(self.report(f'<testcase name="capture[a]">{child}</testcase>'), {"capture[a]"})
        with self.assertRaises(ValueError):
            verify_report(self.report('<testcase name="capture[a]"/><testcase name="capture[a]"/>'), {"capture[a]"})

    def test_rejects_unexpected_cases(self):
        with self.assertRaises(ValueError):
            verify_report(self.report('<testcase name="capture[b]"/>'), {"capture[a]"})
