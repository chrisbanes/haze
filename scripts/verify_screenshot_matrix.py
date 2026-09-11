"""Verify the declared host matrix was actually executed, using Gradle's JUnit reports."""

import argparse
import itertools
import xml.etree.ElementTree as ET
from pathlib import Path


SCENES = ["blur-credit-card", "glass-credit-card"]
MODES = ["quality", "balanced", "performance", "adaptive"]
NATIVE_BACKDROP_PROOF = {
    "blurBackdrop_blursEarlierPixelsWithoutFallbackSources",
    "blurBackdrop_disabledFlag_leavesEarlierPixelsSharpWithoutFallbackSources",
    "blurBackdrop_unsupportedSdk_leavesEarlierPixelsSharpWithoutFallbackSources",
    "glassBackdrop_blursEarlierPixelsWithoutFallbackSources",
}


def verify_report(path: Path, expected: set[str]) -> None:
    cases = ET.parse(path).getroot().findall("testcase")
    names = [case.attrib["name"] for case in cases]
    if len(names) != len(set(names)):
        raise ValueError(f"Duplicate matrix cases in {path}")
    if set(names) != expected:
        raise ValueError(f"Incomplete matrix in {path}: missing {expected - set(names)}, unexpected {set(names) - expected}")
    if any(case.find(tag) is not None for case in cases for tag in ("failure", "error", "skipped")):
        raise ValueError(f"Failed or skipped matrix cases in {path}")


def expected_cases(platform: str) -> set[str]:
    if platform == "native-backdrop-proof":
        return NATIVE_BACKDROP_PROOF

    inputs = ["backdrop-native"] if platform == "android-native" else ["sources", "backdrop-fallback"]
    selectors = ["-".join(parts) for parts in itertools.product(SCENES, inputs, MODES)]
    if platform == "android-native":
        return {f"capture[{selector}]" for selector in selectors}

    # Robolectric omits the suffix for the last SDK in @Config (currently 35).
    sdk_suffixes = ["[28]", "[32]", ""] if platform == "android" else [""]
    target_suffix = "[jvm]" if platform == "desktop" else ""
    return {f"capture{sdk}[{selector}]{target_suffix}" for sdk in sdk_suffixes for selector in selectors}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("platform", choices=["desktop", "android", "android-native", "native-backdrop-proof"])
    parser.add_argument("report", type=Path)
    args = parser.parse_args()
    # This is the acceptance set, deliberately independent of the runner's enumeration.
    # Extend it alongside ScreenshotMatrix when enrolling more scenes or axes.
    expected = expected_cases(args.platform)
    verify_report(args.report, expected)
    print(f"Verified complete {args.platform} matrix: {len(expected)} passed cases")


if __name__ == "__main__":
    main()
