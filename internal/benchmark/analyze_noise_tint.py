#!/usr/bin/env python3
"""Verify this experiment's construction contract and retain per-iteration trace evidence."""

import argparse
import csv
import hashlib
import io
import json
import subprocess
from pathlib import Path
from xml.etree import ElementTree

APP = "dev.chrisbanes.haze.sample.android"
CLASS = "dev.chrisbanes.haze.BlurNoiseTintBenchmark"
METHODS = ("stable1", "stable3", "tint1", "tint3", "radius1", "radius3", "noise1", "noise3", "cold1", "cold3")
PASSES = ("baseline-forward", "candidate-forward", "candidate-reverse", "baseline-reverse")
# Same-thread temporal containment is equivalent to ancestry for these nested synchronous sections.
# Preserve both process and source attribution; a similarly named marker in another process is not ours.
SQL = f"""
WITH window AS (SELECT ts, dur FROM slice WHERE name = 'measureBlock' AND dur > 0)
SELECT 'window' kind, ts, dur, '' process, 0 attributed, -1 cpu, '' thread FROM window
UNION ALL
SELECT CASE s.name WHEN 'HazeBlur.combinedNoiseTint' THEN 'effect' ELSE 'constructor' END,
 s.ts, s.dur, p.name,
 EXISTS(SELECT 1 FROM slice parent WHERE parent.name = 'HazeBlur.combinedNoiseTint'
   AND parent.track_id = s.track_id AND parent.ts <= s.ts
   AND parent.ts + parent.dur >= s.ts + s.dur AND parent.dur > 0),
 -1, t.name
FROM slice s JOIN thread_track tt ON tt.id = s.track_id
JOIN thread t ON t.utid = tt.utid JOIN process p ON p.upid = t.upid
WHERE s.name IN ('HazeBlur.combinedNoiseTint', 'HazeRuntimeShader.construct')
UNION ALL
SELECT 'sched', sc.ts, sc.dur, p.name, 0, sc.cpu,
 CASE WHEN t.tid = p.pid THEN 'main' ELSE 'RenderThread' END
FROM sched sc JOIN thread t ON t.utid = sc.utid JOIN process p ON p.upid = t.upid
JOIN window w ON sc.ts < w.ts + w.dur AND sc.ts + sc.dur > w.ts
WHERE p.name = '{APP}' AND (t.tid = p.pid OR t.name = 'RenderThread') AND sc.dur > 0
"""


def analyze_rows(rows, method, shared):
    windows = [r for r in rows if r["kind"] == "window"]
    if len(windows) != 1:
        raise ValueError("Expected exactly one complete measureBlock")
    start = int(windows[0]["ts"])
    end = start + int(windows[0]["dur"])
    if end <= start:
        raise ValueError("Expected a positive complete measureBlock")
    selected = [r for r in rows if r["process"] == APP and start <= int(r["ts"]) < end]
    effects = [r for r in selected if r["kind"] == "effect"]
    constructors = [r for r in selected if r["kind"] == "constructor" and int(r["attributed"]) == 1]
    if any(int(r["dur"]) <= 0 or int(r["ts"]) + int(r["dur"]) > end for r in effects + constructors):
        raise ValueError("Incomplete combined-path section in measured window")
    cold = method.startswith("cold")
    stable = method.startswith("stable")
    if cold or not stable:
        if not effects:
            raise ValueError("Missing positive combined-path evidence")
    elif constructors or effects:
        raise ValueError("Stable warm control unexpectedly recreated effects")
    if shared:
        expected = 1 if cold else 0
        if len(constructors) != expected:
            raise ValueError(f"Shared construction contract: expected {expected}, found {len(constructors)}")
    elif not stable:
        minimum = int(method[-1]) if cold else 2
        if len(constructors) < minimum or len(constructors) != len(effects):
            raise ValueError("Baseline must construct source for each combined-effect miss")
    placement = {}
    for row in rows:
        if row["kind"] == "sched" and row["process"] == APP:
            running = max(0, min(end, int(row["ts"]) + int(row["dur"])) - max(start, int(row["ts"])))
            if not running:
                continue
            key = f"{row['thread']}:cpu{row['cpu']}"
            placement[key] = placement.get(key, 0) + running
    if not placement or not any(key.startswith("main:") for key in placement) or not any(key.startswith("RenderThread:") for key in placement):
        raise ValueError("Missing measured-window main/RenderThread scheduler evidence")
    return {
        "window_ms": (end - start) / 1e6,
        "combined_effect_count": len(effects),
        "constructor_count": len(constructors),
        "constructor_elapsed_ms": sum(int(r["dur"]) for r in constructors) / 1e6,
        "running_cpu_ms": {key: value / 1e6 for key, value in sorted(placement.items())},
    }


def trace_rows(trace, processor):
    digest = hashlib.sha256(trace.read_bytes()).hexdigest()
    sidecar = trace.with_suffix(trace.suffix + ".noise-tint.json")
    if sidecar.exists():
        cached = json.loads(sidecar.read_text())
        if cached.get("sha256") == digest and cached.get("sql_sha256") == hashlib.sha256(SQL.encode()).hexdigest():
            return cached["rows"]
    result = subprocess.run([processor, "query", str(trace), SQL], check=True, text=True, capture_output=True)
    rows = list(csv.DictReader(io.StringIO(result.stdout)))
    if not rows or "kind" not in rows[0]:
        raise ValueError(f"Trace query returned no evidence: {trace}")
    sidecar.write_text(json.dumps({"sha256": digest, "sql_sha256": hashlib.sha256(SQL.encode()).hexdigest(), "rows": rows}, indent=2) + "\n")
    return rows


def analyze_archive(directory, method, shared, processor):
    cases = [c for p in (directory / "xml").rglob("TEST-*.xml") for c in ElementTree.parse(p).iter("testcase")]
    if len(cases) != 1 or cases[0].get("classname") != CLASS or cases[0].get("name") != method:
        raise ValueError(f"Wrong/missing XML method: {directory}")
    if any(cases[0].find(tag) is not None for tag in ("failure", "error", "skipped")):
        raise ValueError(f"Failed or skipped method: {directory}")
    reports = list((directory / "additional").rglob("*-benchmarkData.json"))
    if len(reports) != 1:
        raise ValueError(f"Expected one benchmark report: {directory}")
    benchmarks = json.loads(reports[0].read_text())["benchmarks"]
    if len(benchmarks) != 1:
        raise ValueError("Unexpected method set")
    benchmark = benchmarks[0]
    if benchmark["className"] != CLASS or benchmark["name"] != method or benchmark["repeatIterations"] != 8:
        raise ValueError("Wrong method or iteration count")
    filenames = [p["filename"] for p in benchmark.get("profilerOutputs", []) if p["type"] == "PerfettoTrace"]
    if len(filenames) != 8 or len(set(filenames)) != 8:
        raise ValueError("Expected eight distinct traces")
    environment = json.loads((directory / "environment.json").read_text())
    if any(environment[key] != value for key, value in (("thermal_status", 0), ("thermal_status_after", 0), ("refresh_hz", 60), ("refresh_hz_after", 60), ("fixed_performance_mode", True))):
        raise ValueError("Thermal/refresh conditions do not match the frozen workload")
    results = []
    for filename in filenames:
        trace = (reports[0].parent / filename).resolve()
        if not trace.is_relative_to(reports[0].parent.resolve()) or not trace.is_file() or not trace.stat().st_size:
            raise ValueError("Missing/invalid trace")
        result = analyze_rows(trace_rows(trace, processor), method, shared)
        result["trace"] = str(trace.relative_to(directory.resolve()))
        results.append(result)
    return {"iterations": results, "metrics": benchmark["metrics"], "instrumentation_seconds": float(cases[0].get("time", "0")), "environment": environment}


def validate_manifest(session, manifest, passes):
    if len(manifest["harness_commit"]) != 40 or manifest["device"]["sdk_int"] < 33:
        raise ValueError("Missing harness or supported physical-device identity")
    if not manifest["device"]["serial"] or not manifest["device"]["fingerprint"]:
        raise ValueError("Missing physical-device identity")
    hashes = []
    for variant in dict.fromkeys(name.split("-")[0] for name in passes):
        build = manifest["variants"][variant]
        if len(build["commit"]) != 40:
            raise ValueError("Missing full source identity")
        for role in ("target", "benchmark"):
            apk = (session / build[f"{role}_apk"]).resolve()
            if not apk.is_relative_to(session.resolve()) or hashlib.sha256(apk.read_bytes()).hexdigest() != build[f"{role}_sha256"]:
                raise ValueError("Frozen APK identity mismatch")
        hashes.append(build["benchmark_sha256"])
    if len(set(hashes)) != 1:
        raise ValueError("Baseline/candidate benchmark harness APK differs")


def validate_environment(environment, build, variant):
    if environment["variant"] != variant or any(environment[f"{role}_sha256"] != build[f"{role}_sha256"] for role in ("target", "benchmark")):
        raise ValueError("Installed APK identity does not match frozen variant")
    if environment["gradle_elapsed_seconds"] <= 0:
        raise ValueError("Missing Gradle elapsed time")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--session", required=True, type=Path)
    parser.add_argument("--pass", dest="selected_pass", choices=PASSES)
    parser.add_argument("--expect-shared", action="store_true")
    parser.add_argument("--trace-processor", default="trace_processor")
    args = parser.parse_args()
    try:
        manifest = json.loads((args.session / "manifest.json").read_text())
        passes = (args.selected_pass,) if args.selected_pass else PASSES
        if args.expect_shared and args.selected_pass:
            raise ValueError("Final shared acceptance requires all four passes")
        validate_manifest(args.session, manifest, passes)
        report = {"manifest": manifest, "passes": {}}
        for pass_name in passes:
            shared = pass_name.startswith("candidate")
            variant = pass_name.split("-")[0]
            report["passes"][pass_name] = {
                method: analyze_archive(args.session / pass_name / method, method, shared, args.trace_processor)
                for method in METHODS
            }
            for result in report["passes"][pass_name].values():
                validate_environment(result["environment"], manifest["variants"][variant], variant)
        output = args.session / (f"analysis-{args.selected_pass}.json" if args.selected_pass else "analysis.json")
        output.write_text(json.dumps(report, indent=2) + "\n")
        print(f"Verified {len(passes) * len(METHODS)} methods / {len(passes) * len(METHODS) * 8} iterations: {output}")
    except (OSError, ValueError, KeyError, ElementTree.ParseError, subprocess.CalledProcessError) as error:
        parser.exit(1, f"Noise/tint evidence incomplete: {error}\n")


if __name__ == "__main__":
    main()
