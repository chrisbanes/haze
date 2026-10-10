import hashlib
import tempfile
import unittest
from pathlib import Path
from analyze_noise_tint import APP, analyze_rows, validate_environment, validate_manifest


def row(kind, ts, dur=10, process=APP, attributed=0, cpu=-1, thread="main"):
    return dict(kind=kind, ts=str(ts), dur=str(dur), process=process,
                attributed=str(attributed), cpu=str(cpu), thread=thread)


class ConstructionEvidenceTest(unittest.TestCase):
    def rows(self, constructors=2, effects=2):
        result = [row("window", 100, 1000, process=""), row("sched", 90, 40, cpu=2), row("sched", 110, 20, cpu=4, thread="RenderThread")]
        result += [row("effect", 110 + i * 30, 20) for i in range(effects)]
        result += [row("constructor", 111 + i * 30, attributed=1) for i in range(constructors)]
        return result

    def test_baseline_requires_repeated_attributable_construction(self):
        result = analyze_rows(self.rows(), "tint1", False)
        self.assertEqual(result["constructor_count"], 2)
        self.assertEqual(result["running_cpu_ms"]["main:cpu2"], 30 / 1e6)

    def test_foreign_process_or_unattributed_constructor_is_not_counted(self):
        for kwargs in (dict(process="com.other"), dict(attributed=0)):
            rows = self.rows(constructors=0)
            rows.append(row("constructor", 111, **kwargs))
            with self.assertRaisesRegex(ValueError, "Baseline"):
                analyze_rows(rows, "tint1", False)

    def test_setup_construction_is_excluded_from_warm_window(self):
        rows = self.rows(constructors=0)
        rows.append(row("constructor", 50, attributed=1))
        self.assertEqual(analyze_rows(rows, "tint1", True)["constructor_count"], 0)

    def test_shared_cold_requires_one_source_construction_for_three_nodes(self):
        analyze_rows(self.rows(constructors=1, effects=3), "cold3", True)
        with self.assertRaisesRegex(ValueError, "Shared"):
            analyze_rows(self.rows(constructors=3, effects=3), "cold3", True)

    def test_shared_animation_rejects_reconstruction(self):
        with self.assertRaisesRegex(ValueError, "Shared"):
            analyze_rows(self.rows(), "noise3", True)

    def test_missing_effect_marker_does_not_prove_shared_reuse(self):
        with self.assertRaisesRegex(ValueError, "positive"):
            analyze_rows(self.rows(constructors=0, effects=0), "radius1", True)

    def test_stable_warm_requires_no_new_effects(self):
        analyze_rows(self.rows(constructors=0, effects=0), "stable1", True)
        with self.assertRaisesRegex(ValueError, "Stable"):
            analyze_rows(self.rows(), "stable1", False)

    def test_missing_or_duplicate_window_is_rejected(self):
        for rows in (self.rows()[1:], self.rows() + [row("window", 100, 1000)]):
            with self.assertRaisesRegex(ValueError, "exactly one"):
                analyze_rows(rows, "tint1", False)

    def test_nonpositive_window_or_scheduler_outside_it_is_rejected(self):
        rows = self.rows(constructors=0, effects=0)
        rows[0]["dur"] = "0"
        with self.assertRaisesRegex(ValueError, "positive complete"):
            analyze_rows(rows, "stable1", False)
        rows = self.rows(constructors=0, effects=0)
        for row in rows:
            if row["kind"] == "sched":
                row["ts"] = "0"
        with self.assertRaisesRegex(ValueError, "scheduler"):
            analyze_rows(rows, "stable1", False)

    def test_incomplete_marker_or_missing_scheduler_is_rejected(self):
        rows = self.rows()
        rows[-1]["dur"] = "-1"
        with self.assertRaisesRegex(ValueError, "Incomplete"):
            analyze_rows(rows, "tint1", False)
        with self.assertRaisesRegex(ValueError, "scheduler"):
            analyze_rows([r for r in self.rows() if r["kind"] != "sched"], "tint1", False)


class FrozenIdentityTest(unittest.TestCase):
    def test_frozen_apk_or_harness_mismatch_is_rejected(self):
        with tempfile.TemporaryDirectory() as directory:
            session = Path(directory)
            apk = session / "target.apk"
            apk.write_bytes(b"frozen baseline")
            digest = hashlib.sha256(apk.read_bytes()).hexdigest()
            build = dict(commit="a" * 40, target_apk="target.apk", benchmark_apk="target.apk",
                         target_sha256=digest, benchmark_sha256=digest)
            manifest = dict(harness_commit="a" * 40, device=dict(sdk_int=37, serial="physical", fingerprint="build"),
                            variants=dict(baseline=build, candidate=dict(build)))
            validate_manifest(session, manifest, ("baseline-forward", "candidate-forward"))
            apk.write_bytes(b"replaced APK")
            with self.assertRaisesRegex(ValueError, "APK identity"):
                validate_manifest(session, manifest, ("baseline-forward",))

    def test_installed_variant_must_match_and_wall_time_must_be_recorded(self):
        build = dict(target_sha256="a" * 64, benchmark_sha256="b" * 64)
        environment = dict(build, variant="baseline", gradle_elapsed_seconds=30)
        validate_environment(environment, build, "baseline")
        for change in (dict(variant="candidate"), dict(target_sha256="c" * 64), dict(gradle_elapsed_seconds=0)):
            with self.assertRaises(ValueError):
                validate_environment(dict(environment, **change), build, "baseline")


if __name__ == "__main__":
    unittest.main()
