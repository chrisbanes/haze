import csv
from pathlib import Path
import tempfile
import unittest

from check_windows_blur_probe import check_probe


class WindowsBlurProbeCheckTest(unittest.TestCase):
    def test_accepts_fast_complete_draws_and_rejects_stall_and_missing_evidence(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary)
            (path / "renderer.txt").write_text("actual=DIRECT3D\n" * 2)
            rows = []
            for window in range(2):
                for phase in [*(f"baseline-{i}" for i in range(3)), "construct-progressive", *(f"progressive-{i}" for i in range(4))]:
                    rows.append([window, phase, 100])
                for image in ("baseline", "progressive"):
                    (path / f"window-{window}-{image}.png").touch()

            def write_rows():
                with (path / "timings.csv").open("w", newline="") as stream:
                    writer = csv.writer(stream)
                    writer.writerow(["window", "phase", "milliseconds"])
                    writer.writerows(rows)

            write_rows()
            check_probe(path)
            rows[4][2] = 26818
            write_rows()
            with self.assertRaisesRegex(AssertionError, "stall ceiling"):
                check_probe(path)
            rows[4][2] = 100
            rows.pop()
            write_rows()
            with self.assertRaisesRegex(AssertionError, "Incomplete"):
                check_probe(path)


if __name__ == "__main__":
    unittest.main()
