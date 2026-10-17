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
            "metrics": {"frameCount": {"runs": [180.0] * 8}},
            "profilerOutputs": [
                {"type": "PerfettoTrace", "filename": f"iter{i}.perfetto-trace"}
                for i in range(8)
            ],
        }
        for item in self.benchmark["profilerOutputs"]:
            (self.additional / item["filename"]).write_bytes(b"trace fixture")

    def archive(self, **gates):
        (self.additional / "sample-benchmarkData.json").write_text(
            json.dumps({"benchmarks": [self.benchmark]})
        )
        archive_result(self.outputs, self.destination, "Example#sample", 8, **gates)

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

    def test_rejects_single_value_metric_missing_iterations(self):
        self.benchmark["metrics"] = {"frameCount": {"runs": [180.0] * 5}}
        with self.assertRaisesRegex(ValueError, "frameCount"):
            self.archive()
        self.assertTrue(list(self.destination.rglob("*-benchmarkData.json")))

    def test_accepts_per_frame_ratio_within_range(self):
        self.benchmark["metrics"] = {
            "frameCount": {"runs": [180.0] * 8},
            "graphicsLayerDrawCount": {"runs": [180.0] * 8},
        }
        self.archive(per_frame={"graphicsLayerDrawCount": (0.9, 1.1)})

    def test_rejects_per_frame_ratio_outside_range(self):
        self.benchmark["metrics"] = {
            "frameCount": {"runs": [180.0] * 8},
            "graphicsLayerDrawCount": {"runs": [360.0] * 8},
        }
        with self.assertRaisesRegex(ValueError, "graphicsLayerDrawCount iteration 0"):
            self.archive(per_frame={"graphicsLayerDrawCount": (0.9, 1.1)})

    def test_rejects_missing_gate_metric(self):
        self.benchmark["metrics"] = {"frameCount": {"runs": [180.0] * 8}}
        with self.assertRaisesRegex(ValueError, "graphicsLayerDrawCount is missing"):
            self.archive(per_frame={"graphicsLayerDrawCount": (0.9, 1.1)})

    def test_rejects_missing_positive_metric(self):
        with self.assertRaisesRegex(ValueError, "hazeGlassRuntimeDrawCount is missing"):
            self.archive(positive=["hazeGlassRuntimeDrawCount"])

    def test_rejects_non_positive_metric(self):
        self.benchmark["metrics"]["hazeGlassRuntimeDrawCount"] = {"runs": [2.0] * 7 + [0.0]}
        with self.assertRaisesRegex(ValueError, "not positive"):
            self.archive(positive=["hazeGlassRuntimeDrawCount"])

    def test_rejects_zero_frame_iteration(self):
        self.benchmark["metrics"] = {"frameCount": {"runs": [180.0, 180.0, 0.0] + [180.0] * 5}}
        with self.assertRaisesRegex(ValueError, "frameCount iteration 2: 0.0 frames"):
            self.archive()
        self.assertTrue(list(self.destination.rglob("*-benchmarkData.json")))

    def test_rejects_iteration_below_frame_floor(self):
        self.benchmark["metrics"] = {"frameCount": {"runs": [5.0] + [180.0] * 7}}
        with self.assertRaisesRegex(ValueError, "below the minimum of 10"):
            self.archive(min_frames=10)

    def test_allows_low_frame_iteration_when_floor_disabled(self):
        self.benchmark["metrics"] = {"frameCount": {"runs": [180.0, 180.0, 0.0] + [180.0] * 5}}
        self.archive(min_frames=0)
        self.assertEqual(len(list(self.destination.rglob("*.perfetto-trace"))), 8)

    def test_accepts_all_valid_frame_counts(self):
        self.benchmark["metrics"] = {"frameCount": {"runs": [180.0] * 8}}
        self.archive()

    def test_rejects_missing_frame_count(self):
        del self.benchmark["metrics"]["frameCount"]
        with self.assertRaisesRegex(ValueError, "frameCount is missing"):
            self.archive()

    def test_allows_missing_frame_count_when_floor_disabled(self):
        del self.benchmark["metrics"]["frameCount"]
        self.archive(min_frames=0)

    def test_rejects_negative_frame_floor(self):
        with self.assertRaisesRegex(ValueError, "Minimum frames"):
            self.archive(min_frames=-1)

    def test_rejects_missing_outputs(self):
        with self.assertRaises(FileNotFoundError):
            archive_result(self.root / "missing", self.destination, "Example#sample", 8)


if __name__ == "__main__":
    unittest.main()
