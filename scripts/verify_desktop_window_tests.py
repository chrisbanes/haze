"""Verify that every required real-window rendering test passed in its JUnit report."""

import argparse
import sys
import xml.etree.ElementTree as ET
from pathlib import Path


EXPECTED_CLASS = "dev.chrisbanes.haze.HazeDesktopWindowRenderingTest"
EXPECTED_METHODS = {
    "crossWindowEffect_samplesExpectedScreenRegion",
    "effectWindowMove_updatesSampledRegion",
    "sourceWindowMove_updatesSampledRegion",
    "sourceDrawChange_refreshesEffectWindow",
    "windowsReturnToOriginalPosition_restoresSampledRegion",
}


def normalized_name(name: str) -> str:
    return name.removesuffix("[jvm]")


def verify_report(path: Path) -> list[str]:
    if not path.is_file():
        raise ValueError(f"JUnit report does not exist: {path}")

    try:
        root = ET.parse(path).getroot()
    except ET.ParseError as error:
        raise ValueError(f"Malformed JUnit report {path}: {error}") from error

    if root.tag not in {"testsuite", "testsuites"}:
        raise ValueError(f"Unexpected JUnit root element in {path}: {root.tag}")

    # Gradle may emit totals on either a testsuite or its enclosing testsuites element.
    for suite in root.iter():
        if suite.tag not in {"testsuite", "testsuites"}:
            continue
        for attribute in ("failures", "errors", "skipped"):
            value = suite.get(attribute, "0")
            try:
                count = int(value)
            except ValueError as error:
                raise ValueError(f"Invalid {attribute} total in {path}: {value!r}") from error
            if count != 0:
                raise ValueError(f"JUnit suite reports {count} {attribute} in {path}")

    cases = root.findall(".//testcase")
    if not cases:
        raise ValueError(f"JUnit report has no test cases: {path}")

    names = []
    for case in cases:
        name = case.get("name")
        if not name:
            raise ValueError(f"JUnit test case has no name in {path}")
        if case.get("classname") != EXPECTED_CLASS:
            raise ValueError(f"Unexpected test class for {name!r}: {case.get('classname')!r}")
        names.append(normalized_name(name))
        for tag in ("failure", "error", "skipped"):
            if case.find(tag) is not None:
                raise ValueError(f"JUnit case {name!r} contains <{tag}> in {path}")

    if len(names) != len(set(names)):
        raise ValueError(f"Duplicate test cases after [jvm] normalization in {path}: {names}")

    actual = set(names)
    missing = EXPECTED_METHODS - actual
    unexpected = actual - EXPECTED_METHODS
    if missing or unexpected:
        raise ValueError(f"Wrong test set in {path}: missing={sorted(missing)}, unexpected={sorted(unexpected)}")

    return sorted(names)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("report", type=Path, help="Gradle JUnit XML report for the Desktop window test task")
    args = parser.parse_args()

    try:
        names = verify_report(args.report)
    except (OSError, ValueError, ET.ParseError) as error:
        print(f"Desktop window test verification failed: {error}", file=sys.stderr)
        return 1

    print("Verified Desktop window test methods:")
    for name in names:
        print(f"  {name}")
    print(f"{len(names)} passed, 0 failed, 0 errors, 0 skipped; report: {args.report}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
