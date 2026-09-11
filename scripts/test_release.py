import subprocess
import sys
import unittest
from pathlib import Path
from unittest import mock

import release


class ReleasePreflightTest(unittest.TestCase):
    def test_failed_screenshot_preflight_prevents_release_mutations(self):
        with (
            mock.patch.object(sys, "argv", ["release.py", "1.2.3"]),
            mock.patch.object(release, "get_property", return_value="1.2.2-SNAPSHOT"),
            mock.patch.object(
                release,
                "run",
                side_effect=subprocess.CalledProcessError(
                    1,
                    ["./gradlew", ":haze-screenshot-tests:verifyScreenshotMatrixFull", "--no-scan"],
                ),
            ) as run,
            mock.patch.object(release, "set_property") as set_property,
        ):
            with self.assertRaises(subprocess.CalledProcessError):
                release.main()

        run.assert_called_once_with(
            ["./gradlew", ":haze-screenshot-tests:verifyScreenshotMatrixFull", "--no-scan"],
        )
        set_property.assert_not_called()


if __name__ == "__main__":
    unittest.main()
