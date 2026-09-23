#!/usr/bin/env python3
"""Preserve and verify one physical-device Macrobenchmark invocation."""

import argparse
import hashlib
import json
import shutil
from pathlib import Path
from typing import Optional
from xml.etree import ElementTree


def archive_result(
    outputs: Path,
    destination: Path,
    method: str,
    iterations: int,
    diagnostic_evidence: Optional[Path] = None,
) -> None:
    class_name, method_name = method.split("#")
    if iterations < 1:
        raise ValueError("Expected iterations must be positive")
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
    for group in ("metrics", "sampledMetrics"):
        for name, metric in benchmark.get(group, {}).items():
            actual = len(metric["runs"])
            if actual != iterations:
                raise ValueError(
                    f"Expected {iterations} runs for {group}.{name}, found {actual}"
                )
    if method_name == "sourceUpdateAdaptiveDiagnostic":
        if iterations != 1:
            raise ValueError("Glass diagnostic requires one measured iteration")
        for name in (
            "hazeSourceRecordCount",
            "hazeGlassRuntimeDrawCount",
            "hazeGlassPrepareCount",
        ):
            count = benchmark["metrics"][name]["runs"][0]
            if count < 120:
                raise ValueError(f"Glass diagnostic requires an active {name} window: {count}")
        if benchmark["metrics"]["frameCount"]["runs"][0] < 120:
            raise ValueError("Glass diagnostic requires at least 120 frame samples")
        if len(benchmark["sampledMetrics"]["frameDurationCpuMs"]["runs"][0]) < 120:
            raise ValueError("Glass diagnostic requires at least 120 sampled frame durations")
        active_duration_ms = benchmark["metrics"]["cb10DiagnosticActiveSumMs"]["runs"][0]
        if active_duration_ms < 4_000:
            raise ValueError("Glass diagnostic trace must show four seconds of active source")

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
    if method_name == "sourceUpdateAdaptiveDiagnostic":
        if diagnostic_evidence is None:
            raise ValueError("Glass diagnostic requires trace-bound tier evidence")
        evidence = json.loads(diagnostic_evidence.read_text())
        trace = (report.parent / traces[0]).resolve()
        digest = hashlib.sha256(trace.read_bytes()).hexdigest()
        if evidence["traceSha256"] != digest:
            raise ValueError("Glass diagnostic tier evidence does not match its trace")
        start = evidence["activeStartNanos"]
        end = evidence["activeEndNanos"]
        if not isinstance(start, int) or not isinstance(end, int) or end - start < 4_000_000_000:
            raise ValueError("Glass diagnostic active window must span at least four seconds")
        if abs((end - start) / 1_000_000 - active_duration_ms) > 500:
            raise ValueError("Glass diagnostic active window disagrees with the trace metric")
        events = evidence["tierEvents"]
        if not any(
            event["tier"] == "BALANCED" and start <= event["atNanos"] <= end
            for event in events
        ) or not any(
            event["tier"] == "FULL_RESOLUTION"
            and start + 3_500_000_000 <= event["atNanos"] <= end
            and event["sampleCount"] >= 30
            for event in events
        ):
            raise ValueError("Glass diagnostic requires post-warm-up promotion tier evidence")
        shutil.copyfile(diagnostic_evidence, destination / "diagnostic-tier-evidence.json")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--method", required=True, help="Fully qualified Class#method")
    parser.add_argument("--destination", required=True, type=Path)
    parser.add_argument("--iterations", type=int, default=8)
    parser.add_argument("--outputs", type=Path, default=Path(__file__).parent / "build/outputs")
    parser.add_argument("--diagnostic-evidence", type=Path)
    args = parser.parse_args()
    try:
        archive_result(
            args.outputs, args.destination, args.method, args.iterations, args.diagnostic_evidence
        )
    except (OSError, ValueError, KeyError, TypeError, ElementTree.ParseError) as error:
        parser.exit(1, f"Archive verification failed: {error}\nEvidence retained at {args.destination}\n")
    print(f"Verified {args.method}: {args.iterations} iterations and traces at {args.destination}")


if __name__ == "__main__":
    main()
