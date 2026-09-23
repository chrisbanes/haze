"""Run with: python3 -m unittest discover -s internal/benchmark -p 'test_archive_result.py'."""

import json
import tempfile
import unittest
from pathlib import Path

from archive_result import archive_result


class ArchiveResultTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.outputs = self.root / "outputs"
        self.destination = self.root / "archive"
        self.additional = self.outputs / "connected_android_test_additional_output/benchmarkRelease/device"
        self.additional.mkdir(parents=True)
        self.xml = self.outputs / "androidTest-results/connected/benchmarkRelease/TEST-device.xml"
        self.xml.parent.mkdir(parents=True)
        self.xml.write_text('<testsuite><testcase classname="Example" name="sample" /></testsuite>')
        self.benchmark = {
            "className": "Example",
            "name": "sample",
            "repeatIterations": 8,
            "metrics": {"frameCount": {"runs": [180] * 8}},
            "sampledMetrics": {"frameDurationCpuMs": {"runs": [[2.0]] * 8}},
            "profilerOutputs": [
                {"type": "PerfettoTrace", "filename": f"iter{i}.perfetto-trace"}
                for i in range(8)
            ],
        }
        for item in self.benchmark["profilerOutputs"]:
            (self.additional / item["filename"]).write_bytes(b"trace fixture")

    def archive(self):
        (self.additional / "sample-benchmarkData.json").write_text(
            json.dumps({"benchmarks": [self.benchmark]})
        )
        archive_result(self.outputs, self.destination, "Example#sample", 8)

    def test_preserves_complete_result(self):
        self.archive()
        self.assertEqual(len(list(self.destination.rglob("*.perfetto-trace"))), 8)
        self.assertEqual(len(list(self.destination.rglob("TEST-*.xml"))), 1)

    def test_never_overwrites_existing_archive(self):
        self.archive()
        with self.assertRaises(FileExistsError):
            self.archive()

    def test_rejects_wrong_method_and_retains_evidence(self):
        self.benchmark["name"] = "wrong"
        with self.assertRaisesRegex(ValueError, "does not match"):
            self.archive()
        self.assertTrue(list(self.destination.rglob("*-benchmarkData.json")))

    def test_rejects_wrong_iteration_count(self):
        self.benchmark["repeatIterations"] = 3
        with self.assertRaisesRegex(ValueError, "measured iterations"):
            self.archive()

    def test_rejects_missing_scalar_run(self):
        self.benchmark["metrics"]["frameCount"]["runs"].pop()
        with self.assertRaisesRegex(ValueError, "8 runs for metrics.frameCount, found 7"):
            self.archive()

    def test_rejects_missing_sampled_run(self):
        self.benchmark["sampledMetrics"]["frameDurationCpuMs"]["runs"].pop()
        with self.assertRaisesRegex(ValueError, "8 runs for sampledMetrics.frameDurationCpuMs, found 7"):
            self.archive()

    def test_diagnostic_requires_repeated_markers(self):
        self.benchmark["name"] = "sourceUpdateAdaptiveDiagnostic"
        self.benchmark["repeatIterations"] = 1
        self.benchmark["metrics"] = {
            "hazeSourceRecordCount": {"runs": [180]},
            "hazeGlassRuntimeDrawCount": {"runs": [1]},
            "hazeGlassPrepareCount": {"runs": [181]},
        }
        self.benchmark["sampledMetrics"]["frameDurationCpuMs"]["runs"] = [[2.0]]
        self.benchmark["profilerOutputs"] = self.benchmark["profilerOutputs"][:1]
        self.xml.write_text(
            '<testsuite><testcase classname="Example" name="sourceUpdateAdaptiveDiagnostic" /></testsuite>'
        )
        (self.additional / "sample-benchmarkData.json").write_text(
            json.dumps({"benchmarks": [self.benchmark]})
        )
        with self.assertRaisesRegex(ValueError, "repeated hazeGlassRuntimeDrawCount"):
            archive_result(self.outputs, self.destination, "Example#sourceUpdateAdaptiveDiagnostic", 1)

    def test_rejects_missing_trace(self):
        (self.additional / "iter0.perfetto-trace").unlink()
        with self.assertRaisesRegex(ValueError, "Missing or invalid trace"):
            self.archive()

    def test_rejects_empty_trace(self):
        (self.additional / "iter0.perfetto-trace").write_bytes(b"")
        with self.assertRaisesRegex(ValueError, "Empty trace"):
            self.archive()

    def test_rejects_duplicate_trace(self):
        self.benchmark["profilerOutputs"][1] = self.benchmark["profilerOutputs"][0]
        with self.assertRaisesRegex(ValueError, "distinct Perfetto"):
            self.archive()

    def test_rejects_failed_xml(self):
        self.xml.write_text(
            '<testsuite><testcase classname="Example" name="sample"><failure /></testcase></testsuite>'
        )
        with self.assertRaisesRegex(ValueError, "failed or was skipped"):
            self.archive()

    def test_rejects_wrong_xml_method(self):
        self.xml.write_text('<testsuite><testcase classname="Example" name="wrong" /></testsuite>')
        with self.assertRaisesRegex(ValueError, "XML testcase"):
            self.archive()

    def test_rejects_missing_outputs(self):
        with self.assertRaises(FileNotFoundError):
            archive_result(self.root / "missing", self.destination, "Example#sample", 8)


if __name__ == "__main__":
    unittest.main()
