# Screenshot tests

The host matrix replaces the Blur and Glass `creditCard` tests with independent configurations.
Other library regression and sample presentation suites remain in place. Current coverage is
16 Desktop cases and 48 Robolectric cases: two scenes × Sources/forced Backdrop fallback ×
Quality/Balanced/Performance/Adaptive, on Desktop and Android SDKs 28, 32, and 35.

Baselines live at `screenshots/matrix/<profile>/<scene>/<input>/<mode>.webp`. The original
Sources/Adaptive references were moved without changing their image contents. Other configurations
have independent goldens. Forced-fallback cases also compare live pixels against a fresh Sources
attachment using the same scene and mode. This checks parity separately from the goldens.

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
  -PscreenshotMatrixCase=glass-credit-card-backdrop-fallback-adaptive \
  -PscreenshotMatrixSdk=35 --no-scan
```

Selectors are scene/input/mode IDs; the task and optional SDK select the platform. Android's
JUnit report adds SDK suffixes for 28 and 32; Robolectric omits the suffix for the last configured
SDK (35). Baseline paths always include the SDK explicitly.

## PR coverage

```sh
./gradlew :haze-screenshot-tests:verifyScreenshotMatrixPr --no-scan
```

This runs the legacy library suite and verifies the exact enrolled matrix case set in JUnit XML,
including failure and skip checks. CI uses `verifyScreenshotMatrixJvm` and
`verifyScreenshotMatrixAndroid` in its existing separate host jobs. These gates reject matrix
selectors, recording, and disabled verification before running tests. A failed or filtered test
run cannot satisfy the report check. Sample screenshots remain under `:sample:screenshot-tests:test`.

## Scoped recording

Diagnose a failure before recording; preserve unrelated references and the global comparator.
For an intentional change, record only its case, inspect the image, then run the focused verification
command above without record mode:

```sh
./gradlew :haze-screenshot-tests:recordRoborazziJvm \
  --tests '*ScreenshotMatrixDesktopTest*' \
  -PscreenshotMatrixCase=blur-credit-card-sources-quality --no-scan
```

When enrolling a scene or changing the SDK matrix, update `ScreenshotMatrix`, its configuration
tests, and the independent acceptance set in `scripts/verify_screenshot_matrix.py`. Rename/move
existing baselines explicitly; do not regenerate unchanged default references.

## Deferred device coverage

Native Backdrop capture, qualified-device artifacts, native parity/probes and the full release
gate are deferred. No current host command proves native rendering or constitutes full release
acceptance. The release script is unchanged. Do not access a busy device just to run the host gate.

Future native acceptance requires a supported device, fresh window captures, exact environment
identity, a genuine empty-fallback probe, and same-device parity. An enabled flag or a host fallback
image is insufficient. Until that work is implemented and verified, report native coverage as pending.
