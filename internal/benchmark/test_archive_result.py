"""Run with: python3 -m unittest discover -s internal/benchmark -p 'test_archive_result.py'."""

import json
import hashlib
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
        self.prepare_diagnostic()
        self.benchmark["metrics"]["hazeGlassRuntimeDrawCount"]["runs"] = [2]
        self.write_report()
        with self.assertRaisesRegex(ValueError, "active hazeGlassRuntimeDrawCount window"):
            archive_result(self.outputs, self.destination, "Example#sourceUpdateAdaptiveDiagnostic", 1)

    def prepare_diagnostic(self):
        self.benchmark["name"] = "sourceUpdateAdaptiveDiagnostic"
        self.benchmark["repeatIterations"] = 1
        self.benchmark["metrics"] = {
            "hazeSourceRecordCount": {"runs": [240]},
            "hazeGlassRuntimeDrawCount": {"runs": [240]},
            "hazeGlassPrepareCount": {"runs": [240]},
            "frameCount": {"runs": [240]},
            "cb10DiagnosticActiveSumMs": {"runs": [5_000]},
        }
        self.benchmark["sampledMetrics"]["frameDurationCpuMs"]["runs"] = [[2.0] * 240]
        self.benchmark["profilerOutputs"] = self.benchmark["profilerOutputs"][:1]
        self.xml.write_text(
            '<testsuite><testcase classname="Example" name="sourceUpdateAdaptiveDiagnostic" /></testsuite>'
        )
        self.write_report()

    def write_report(self):
        (self.additional / "sample-benchmarkData.json").write_text(json.dumps({"benchmarks": [self.benchmark]}))

    def write_tier_evidence(self, active_end=5_000_000_000, promoted=True):
        trace = self.additional / "iter0.perfetto-trace"
        evidence = {
            "traceSha256": hashlib.sha256(trace.read_bytes()).hexdigest(),
            "activeStartNanos": 0,
            "activeEndNanos": active_end,
            "tierEvents": [
                {"tier": "BALANCED", "atNanos": 500_000_000, "sampleCount": 30},
                {
                    "tier": "FULL_RESOLUTION" if promoted else "BALANCED",
                    "atNanos": 4_000_000_000,
                    "sampleCount": 240,
                },
            ],
        }
        path = self.root / "tier-evidence.json"
        path.write_text(json.dumps(evidence))
        return path

    def test_diagnostic_accepts_active_window_and_tier_evidence(self):
        self.prepare_diagnostic()
        evidence = self.write_tier_evidence()
        archive_result(
            self.outputs, self.destination, "Example#sourceUpdateAdaptiveDiagnostic", 1, evidence
        )
        self.assertTrue((self.destination / "diagnostic-tier-evidence.json").is_file())

    def test_diagnostic_rejects_sparse_frame_run(self):
        self.prepare_diagnostic()
        self.benchmark["metrics"]["frameCount"]["runs"] = [2]
        self.write_report()
        with self.assertRaisesRegex(ValueError, "120 frame samples"):
            archive_result(self.outputs, self.destination, "Example#sourceUpdateAdaptiveDiagnostic", 1)

    def test_diagnostic_rejects_sparse_sampled_frame_run(self):
        self.prepare_diagnostic()
        self.benchmark["sampledMetrics"]["frameDurationCpuMs"]["runs"] = [[2.0] * 2]
        self.write_report()
        with self.assertRaisesRegex(ValueError, "120 sampled frame durations"):
            archive_result(self.outputs, self.destination, "Example#sourceUpdateAdaptiveDiagnostic", 1)

    def test_diagnostic_rejects_short_active_window(self):
        self.prepare_diagnostic()
        evidence = self.write_tier_evidence(active_end=3_000_000_000)
        with self.assertRaisesRegex(ValueError, "at least four seconds"):
            archive_result(
                self.outputs, self.destination, "Example#sourceUpdateAdaptiveDiagnostic", 1, evidence
            )

    def test_diagnostic_rejects_short_trace_activity(self):
        self.prepare_diagnostic()
        self.benchmark["metrics"]["cb10DiagnosticActiveSumMs"]["runs"] = [3_000]
        self.write_report()
        with self.assertRaisesRegex(ValueError, "trace must show four seconds"):
            archive_result(self.outputs, self.destination, "Example#sourceUpdateAdaptiveDiagnostic", 1)

    def test_diagnostic_rejects_window_disagreeing_with_trace(self):
        self.prepare_diagnostic()
        evidence = self.write_tier_evidence(active_end=6_000_000_000)
        with self.assertRaisesRegex(ValueError, "disagrees with the trace metric"):
            archive_result(
                self.outputs, self.destination, "Example#sourceUpdateAdaptiveDiagnostic", 1, evidence
            )

    def test_diagnostic_rejects_missing_tier_evidence(self):
        self.prepare_diagnostic()
        with self.assertRaisesRegex(ValueError, "trace-bound tier evidence"):
            archive_result(self.outputs, self.destination, "Example#sourceUpdateAdaptiveDiagnostic", 1)

    def test_diagnostic_rejects_missing_promotion(self):
        self.prepare_diagnostic()
        evidence = self.write_tier_evidence(promoted=False)
        with self.assertRaisesRegex(ValueError, "promotion tier evidence"):
            archive_result(
                self.outputs, self.destination, "Example#sourceUpdateAdaptiveDiagnostic", 1, evidence
            )

    def test_diagnostic_rejects_balanced_after_full_resolution(self):
        self.prepare_diagnostic()
        evidence = self.write_tier_evidence()
        data = json.loads(evidence.read_text())
        data["tierEvents"][0]["atNanos"] = 4_500_000_000
        data["tierEvents"][0]["sampleCount"] = 250
        evidence.write_text(json.dumps(data))
        with self.assertRaisesRegex(ValueError, "promotion tier evidence"):
            archive_result(
                self.outputs, self.destination, "Example#sourceUpdateAdaptiveDiagnostic", 1, evidence
            )

    def test_diagnostic_rejects_decreasing_report_sequence(self):
        self.prepare_diagnostic()
        evidence = self.write_tier_evidence()
        data = json.loads(evidence.read_text())
        data["tierEvents"][0]["sampleCount"] = 250
        evidence.write_text(json.dumps(data))
        with self.assertRaisesRegex(ValueError, "promotion tier evidence"):
            archive_result(
                self.outputs, self.destination, "Example#sourceUpdateAdaptiveDiagnostic", 1, evidence
            )

    def test_diagnostic_rejects_tier_evidence_for_another_trace(self):
        self.prepare_diagnostic()
        evidence = self.write_tier_evidence()
        data = json.loads(evidence.read_text())
        data["traceSha256"] = "0" * 64
        evidence.write_text(json.dumps(data))
        with self.assertRaisesRegex(ValueError, "does not match its trace"):
            archive_result(
                self.outputs, self.destination, "Example#sourceUpdateAdaptiveDiagnostic", 1, evidence
            )

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
