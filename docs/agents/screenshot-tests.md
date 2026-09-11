# Screenshot-test guidance

Read [the canonical screenshot-test commands and profiles](../screenshot-tests.md) before changing
library screenshots. The matrix currently covers only the Blur and Glass credit-card scenes. The
remaining library regression tests and sample presentation screenshots are still separate suites.

Reuse one of those shared scenes and add applicable catalog cases. Do not copy a test class or put
multiple configurations inside one test loop: each matrix case needs its own JUnit result and
baseline. Keep mode-transition and pixel-invariant tests separate because they check lifecycle
behavior rather than fresh configuration setup.

Use an exact selected case for diagnosis and the PR gate for normal coverage. The Android host gate
also runs the narrow SDK 37 native-Backdrop runner and the native pixel proof. It is not release
acceptance: physical-device correctness and performance remain deferred. Always include `--no-scan`;
use the repository's Gradle workflow guidance when running Gradle.

For Backdrop cases, set the platform flag before the effect attaches and restore it even when
capture fails. `sources`, `backdrop-fallback`, and `backdrop-native` are named input requests. The
SDK 37 host-native cases must use a distinct empty fallback state and invalidate the decor view
before capture. Their acceptance also depends on `BackdropAndroidRegressionTest` positive native
pixel checks and unsupported/disabled negative controls. This establishes host-native coverage;
only physical hardware can establish physical correctness or performance.

Diagnose a failure before recording. Record only intentional case changes, inspect image diffs,
keep unrelated baselines and global tolerances intact, then run verification again. Preserve the
stable profile/scene/input/mode path, and make baseline moves explicit when case IDs change.

When native-device work resumes, before treating full coverage as complete, verify the device profile and capability gate, run a
fresh capture, and check positive native evidence. Missing hardware, stale artifacts, unsupported
SDKs, filtered runs, and record mode are incomplete full validation, not skips. Report the baseline
revision, selected coverage, results, and missing device evidence accurately. When adding a case,
update the independent acceptance set in `scripts/verify_screenshot_matrix.py` alongside the catalog
and tests so discovery cannot silently omit it.
