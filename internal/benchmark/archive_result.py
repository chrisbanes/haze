#!/usr/bin/env python3
"""Preserve and verify one physical-device Macrobenchmark invocation."""

import argparse
import json
import shutil
from collections.abc import Iterable
from pathlib import Path
from xml.etree import ElementTree


def archive_result(
    outputs: Path,
    destination: Path,
    method: str,
    iterations: int,
    per_frame: dict[str, tuple[float, float]] | None = None,
    positive: Iterable[str] = (),
    min_frames: int = 1,
) -> None:
    class_name, method_name = method.split("#")
    if iterations < 1:
        raise ValueError("Expected iterations must be positive")
    if min_frames < 0:
        raise ValueError("Minimum frames must not be negative")
    # Never replace an earlier result, including evidence from a failed verification.
    destination.mkdir(parents=True, exist_ok=False)
    for source, target in (
        (outputs / "connected_android_test_additional_output/benchmarkRelease", "additional"),
        (outputs / "androidTest-results/connected/benchmarkRelease", "xml"),
    ):
        shutil.copytree(source, destination / target)

    reports = list((destination / "additional").rglob("*-benchmarkData.json"))
    if len(reports) != 1:
        raise ValueError(f"Expected one device JSON report, found {len(reports)}")
    report = reports[0]
    benchmarks = json.loads(report.read_text())["benchmarks"]
    if len(benchmarks) != 1:
        raise ValueError("Expected exactly one benchmark in the JSON report")
    benchmark = benchmarks[0]
    if (benchmark["className"], benchmark["name"]) != (class_name, method_name):
        raise ValueError(f"JSON result does not match {method}")
    if benchmark["repeatIterations"] != iterations:
        raise ValueError(f"Expected {iterations} measured iterations")
    # AndroidX drops a single-value metric for any iteration that lacks one of them, so a short
    # list means a measurement defect rather than fewer samples.
    metrics = benchmark.get("metrics", {})
    for name, metric in metrics.items():
        if len(metric["runs"]) != iterations:
            raise ValueError(f"Metric {name} has {len(metric['runs'])} runs, expected {iterations}")
    # An iteration that drew nothing still counts as a run, so the run-count check cannot see it.
    for i, frames in enumerate(metrics.get("frameCount", {}).get("runs", [])):
        if frames < min_frames:
            raise ValueError(
                f"frameCount iteration {i}: {frames} frames is below the minimum of {min_frames}"
                " (pass --min-frames 0 to allow)"
            )
    check_gates(metrics, per_frame or {}, positive)

    cases = [
        case
        for xml in (destination / "xml").rglob("TEST-*.xml")
        for case in ElementTree.parse(xml).iter("testcase")
    ]
    if len(cases) != 1 or (
        cases[0].get("classname"), cases[0].get("name")
    ) != (class_name, method_name):
        raise ValueError(f"Expected exactly one XML testcase for {method}")
    if any(cases[0].find(tag) is not None for tag in ("failure", "error", "skipped")):
        raise ValueError("XML testcase failed or was skipped")

    traces = [
        item["filename"]
        for item in benchmark.get("profilerOutputs", [])
        if item["type"] == "PerfettoTrace"
    ]
    if len(traces) != iterations or len(set(traces)) != iterations:
        raise ValueError(f"Expected {iterations} distinct Perfetto trace references")
    for filename in traces:
        trace = (report.parent / filename).resolve()
        if not trace.is_relative_to(report.parent.resolve()) or not trace.is_file():
            raise ValueError(f"Missing or invalid trace: {filename}")
        if trace.stat().st_size == 0:
            raise ValueError(f"Empty trace: {filename}")


def check_gates(
    metrics: dict,
    per_frame: dict[str, tuple[float, float]],
    positive: Iterable[str],
) -> None:
    def runs(name: str) -> list[float]:
        if name not in metrics:
            raise ValueError(f"Gate metric {name} is missing from the report")
        return metrics[name]["runs"]

    for name, (low, high) in per_frame.items():
        for i, (value, frames) in enumerate(zip(runs(name), runs("frameCount"))):
            if not frames or not low <= value / frames <= high:
                raise ValueError(
                    f"{name} iteration {i}: {value} over {frames} frames is outside {low}:{high} per frame"
                )
    for name in positive:
        if min(runs(name)) <= 0:
            raise ValueError(f"{name} is not positive in every iteration")


def parse_per_frame(value: str) -> tuple[str, tuple[float, float]]:
    name, _, band = value.partition("=")
    low, _, high = band.partition(":")
    try:
        return name, (float(low), float(high))
    except ValueError:
        raise argparse.ArgumentTypeError(f"expected NAME=LOW:HIGH, got {value!r}") from None


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--method", required=True, help="Fully qualified Class#method")
    parser.add_argument("--destination", required=True, type=Path)
    parser.add_argument("--iterations", type=int, default=8)
    parser.add_argument(
        "--per-frame",
        action="append",
        default=[],
        type=parse_per_frame,
        metavar="NAME=LOW:HIGH",
        help="Require NAME / frameCount to stay within LOW:HIGH in every iteration (repeatable)",
    )
    parser.add_argument(
        "--positive",
        action="append",
        default=[],
        metavar="NAME",
        help="Require NAME to be above zero in every iteration (repeatable)",
    )
    parser.add_argument(
        "--min-frames",
        type=int,
        default=1,
        metavar="N",
        help="Require frameCount to be at least N in every iteration; 0 disables the check (default: 1)",
    )
    parser.add_argument("--outputs", type=Path, default=Path(__file__).parent / "build/outputs")
    args = parser.parse_args()
    try:
        archive_result(
            args.outputs,
            args.destination,
            args.method,
            args.iterations,
            per_frame=dict(args.per_frame),
            positive=args.positive,
            min_frames=args.min_frames,
        )
    except (OSError, ValueError, KeyError, ElementTree.ParseError) as error:
        parser.exit(1, f"Archive verification failed: {error}\nEvidence retained at {args.destination}\n")
    gated = [name for name, _ in args.per_frame] + args.positive
    suffix = f", gates passed for {', '.join(gated)}" if gated else ""
    print(f"Verified {args.method}: {args.iterations} iterations and traces{suffix} at {args.destination}")


if __name__ == "__main__":
    main()
