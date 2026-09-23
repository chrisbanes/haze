#!/usr/bin/env python3
"""Preserve and verify one physical-device Macrobenchmark invocation."""

import argparse
import json
import shutil
from pathlib import Path
from xml.etree import ElementTree


def archive_result(outputs: Path, destination: Path, method: str, iterations: int) -> None:
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
            if count < 2:
                raise ValueError(f"Glass diagnostic requires repeated {name}: {count}")

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


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--method", required=True, help="Fully qualified Class#method")
    parser.add_argument("--destination", required=True, type=Path)
    parser.add_argument("--iterations", type=int, default=8)
    parser.add_argument("--outputs", type=Path, default=Path(__file__).parent / "build/outputs")
    args = parser.parse_args()
    try:
        archive_result(args.outputs, args.destination, args.method, args.iterations)
    except (OSError, ValueError, KeyError, ElementTree.ParseError) as error:
        parser.exit(1, f"Archive verification failed: {error}\nEvidence retained at {args.destination}\n")
    print(f"Verified {args.method}: {args.iterations} iterations and traces at {args.destination}")


if __name__ == "__main__":
    main()
