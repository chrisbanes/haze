# Screenshot tests

The host matrix replaces the Blur and Glass `creditCard` tests with independent configurations.
Other library regression and sample presentation suites remain in place. Current coverage is
12 Desktop cases, 36 Sources/forced-fallback Android host cases, and 6 SDK 37 native-Backdrop
Robolectric cases: two scenes × Sources/forced Backdrop fallback × Quality/Balanced/Performance
on Desktop and Android SDKs 28, 32, and 35, plus two scenes × native Backdrop × those three
modes on SDK 37.

Baselines live at `screenshots/matrix/<profile>/<scene>/<input>/<mode>.webp`. Each configuration
has an independent golden. Forced-fallback cases also compare live pixels against a fresh Sources
attachment using the same scene and mode. This checks parity separately from the goldens. Native
SDK 37 cases use a separate, empty fallback state, so their golden cannot silently use the scene's
registered Sources.

## Focused runs

Run one Desktop case:

```sh
./gradlew :haze-screenshot-tests:jvmTest \
  --tests '*ScreenshotMatrixDesktopTest*' \
  -PscreenshotMatrixCase=blur-credit-card-sources-quality --no-scan
```

Run one Android case on a specific SDK:

```sh
./gradlew :haze-screenshot-tests:testAndroidHostTest \
  --tests '*ScreenshotMatrixAndroidTest*' \
  -PscreenshotMatrixCase=glass-credit-card-backdrop-fallback-balanced \
  -PscreenshotMatrixSdk=35 --no-scan
```

Run one native Backdrop SDK 37 case:

```sh
./gradlew :haze-screenshot-tests:testAndroidHostTest \
  --tests '*ScreenshotMatrixNativeBackdropAndroidTest*' \
  -PscreenshotMatrixCase=glass-credit-card-backdrop-native-balanced --no-scan
```

Selectors are scene/input/mode IDs; the task and optional SDK select the platform. Android's
JUnit report adds SDK suffixes for 28 and 32; Robolectric omits the suffix for the last configured
SDK (35). Baseline paths always include the SDK explicitly.

## PR coverage

```sh
./gradlew :haze-screenshot-tests:verifyScreenshotMatrixPr --no-scan
```

This runs the legacy library suite and verifies the exact enrolled matrix case set in JUnit XML,
including failure and skip checks. The Android host gate also requires all six SDK 37 native
Backdrop cases and the exact four `BackdropAndroidRegressionTest` proof methods to pass. CI uses
`verifyScreenshotMatrixJvm` and `verifyScreenshotMatrixAndroid` in its existing separate host jobs.
These gates reject matrix selectors, recording, and disabled verification before running tests. A
failed or filtered test run cannot satisfy the report check. Sample screenshots remain under
`:sample:screenshot-tests:test`.

## Scoped recording

Diagnose a failure before recording; preserve unrelated references and the global comparator.
For an intentional change, record only its case, inspect the image, then run the focused verification
command above without record mode:

```sh
./gradlew :haze-screenshot-tests:recordRoborazziJvm \
  --tests '*ScreenshotMatrixDesktopTest*' \
  -PscreenshotMatrixCase=blur-credit-card-sources-quality --no-scan
```

To record one native Backdrop SDK 37 case, keep the runner and selector equally narrow:

```sh
./gradlew :haze-screenshot-tests:recordRoborazziAndroidHostTest \
  --tests '*ScreenshotMatrixNativeBackdropAndroidTest*' \
  -PscreenshotMatrixCase=glass-credit-card-backdrop-native-balanced --no-scan
```

When enrolling a scene or changing the SDK matrix, update `ScreenshotMatrix`, its configuration
tests, and the independent acceptance set in `scripts/verify_screenshot_matrix.py`. Rename/move
existing baselines explicitly; do not regenerate unchanged default references.

## Deferred device coverage

Robolectric SDK 37 now exercises native Backdrop visual references with an empty fallback state,
and the host gate also requires the native pixel proof and its unsupported/disabled negative controls.
This is host coverage only. Physical-device correctness, performance, qualified-device artifacts,
native parity, and the full release gate remain deferred. Do not
access a busy device just to run the host gate.
