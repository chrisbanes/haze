"""Run with: python3 -m unittest discover -s internal/benchmark -p 'test_verify_baseline_profile.py'."""

import argparse
import unittest
from pathlib import Path

from verify_baseline_profile import (
    EXPECTED_AARS,
    AarInfo,
    ClassInfo,
    ProfileRule,
    VerificationError,
    classify,
    verify,
)

BLUR_M3_OWNER = "Ldev/chrisbanes/haze/blur/material3/BlurMaterial3Kt;"
GLASS_M3_OWNER = "Ldev/chrisbanes/haze/glass/material3/GlassMaterial3Kt;"
MEMBER = "Material3(JLandroidx/compose/runtime/Composer;II)Ldev/chrisbanes/haze/blur/HazeBlurStyle;"


def rule(owner, member=MEMBER):
    line = "HSP" + owner + (f"->{member}" if member else "")
    return ProfileRule(line, "HSP", owner, member)


class VerifyBaselineProfileTest(unittest.TestCase):
    def setUp(self):
        self.aars = {name: AarInfo(name, Path(f"{name}.aar"), {}, set(), []) for name in EXPECTED_AARS}

    def test_expectedAars_excludeBlurMaterial3(self):
        # Changing the inventory must update this test and BASELINE_PROFILES.md together.
        self.assertEqual(len(EXPECTED_AARS), 6)
        self.assertNotIn("haze-blur-material3", EXPECTED_AARS)

    def test_classify_blurMaterial3OwnerMissingFromConsumer_raises(self):
        with self.assertRaises(VerificationError) as error:
            classify(rule(BLUR_M3_OWNER), self.aars, {})
        self.assertIn("missing consumer definition", str(error.exception))

    def test_classify_blurMaterial3OwnerMissingFromAars_raises(self):
        with self.assertRaises(VerificationError) as error:
            classify(rule(BLUR_M3_OWNER), self.aars, {BLUR_M3_OWNER: {MEMBER}})
        self.assertIn("ordinary owner or method missing in AARs", str(error.exception))

    def test_classify_ownerInExpectedAar_isOrdinary(self):
        # Sensitivity control: the same shape of rule passes when an expected AAR owns it.
        self.aars["haze-glass-material3"].classes[GLASS_M3_OWNER] = ClassInfo({MEMBER}, 0x11)
        result = classify(rule(GLASS_M3_OWNER), self.aars, {GLASS_M3_OWNER: {MEMBER}})
        self.assertEqual(result, ("ordinary", "haze-glass-material3"))

    def test_classify_blurMaterial3ResourceClass_raises(self):
        owner = "Ldev/chrisbanes/haze/blur/material3/R$drawable;"
        with self.assertRaises(VerificationError) as error:
            classify(rule(owner, None), self.aars, {owner: set()})
        self.assertIn("resource owner has no AAR namespace", str(error.exception))

    def test_verify_blurMaterial3AarArgument_rejectsInventory(self):
        names = sorted(EXPECTED_AARS | {"haze-blur-material3"})
        args = argparse.Namespace(
            profile=Path("p"), aar=[Path(f"{name}.aar") for name in names], apk=Path("a"),
        )
        with self.assertRaises(VerificationError) as error:
            verify(args)
        self.assertIn("exactly the six expected Haze AARs", str(error.exception))


if __name__ == "__main__":
    unittest.main()
