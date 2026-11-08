#!/usr/bin/env python3
"""Reject issue #1260's multi-second first draw on the real Direct3D path."""

import csv
import math
from pathlib import Path
import sys


def check_probe(directory):
    directory = Path(directory)
    info = (directory / "renderer.txt").read_text()
    assert info.count("actual=DIRECT3D") == 2, "Both windows must use Direct3D"
    with (directory / "timings.csv").open(newline="") as stream:
        rows = list(csv.DictReader(stream))
    phases = [*(f"baseline-{i}" for i in range(3)), "construct-progressive", *(f"progressive-{i}" for i in range(4))]
    expected = {(str(w), phase) for w in range(2) for phase in phases}
    values = {(row["window"], row["phase"]): float(row["milliseconds"]) for row in rows}
    assert len(rows) == len(expected) and values.keys() == expected, "Incomplete or duplicate timing records"
    assert all(math.isfinite(v) and v >= 0 for v in values.values()), "Invalid timing"
    for window in range(2):
        for image in ("baseline", "progressive"):
            assert (directory / f"window-{window}-{image}.png").is_file(), "Missing rendered evidence"
    first = [values[str(w), "progressive-0"] for w in range(2)]
    print("First progressive draws (ms): " + ", ".join(f"{v:.1f}" for v in first))
    # A generous stall ceiling, not a frame-rate target for the shared CI machine.
    assert max(first) < 5000, "First progressive draw exceeded the 5000 ms stall ceiling"


if __name__ == "__main__":
    check_probe(sys.argv[1])
