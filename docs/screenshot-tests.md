# Screenshot tests

The library screenshot matrix currently enrolls two shared scenes: Blur credit card and Glass
credit card. It adds configuration coverage around the legacy library suites; it does not mean
that every legacy or sample screenshot has moved into the matrix.

Each matrix image has a stable path:

```text
screenshots/matrix/<profile>/<scene>/<input>/<mode>.webp
```

Host profiles are `desktop` and `android-sdk-28`, `android-sdk-32`, and `android-sdk-35`. The
device profile is reserved for verified Pixel 6 Android 37.2 window captures. Do not replace one
profile's image with output from another profile.

## Running coverage

Run one host case when diagnosing a focused change:

```sh
./gradlew :haze-screenshot-tests:jvmTest \
  --tests '*ScreenshotMatrixDesktopTest*' \
  -PscreenshotMatrixCase=blur-credit-card-sources-quality --no-scan
```

Run the normal pull-request gate for legacy coverage plus every enrolled host matrix case:

```sh
./gradlew :haze-screenshot-tests:verifyScreenshotMatrixPr --no-scan
```

Run the full release gate only after a fresh qualified-device capture is available:

```sh
./gradlew :haze-screenshot-tests:verifyScreenshotMatrixFull --no-scan
```

The full gate fails when required device/native evidence is unavailable, incompatible, stale, or
filtered. This is intentional: host output and an enabled feature flag do not prove native
backdrop rendering.

## Recording

Record only after diagnosing an intentional visual change. Scope recording to the selected test,
inspect the resulting image diffs, then rerun verification. Preserve existing unrelated baselines
and the repository's Roborazzi tolerances.

```sh
./gradlew :haze-screenshot-tests:recordRoborazziJvm \
  --tests '*ScreenshotMatrixDesktopTest*' \
  -PscreenshotMatrixCase=blur-credit-card-sources-quality --no-scan
```

Matrix case selectors are profile-local; their profile is part of both the report identity and
baseline path. When a case is renamed, make an explicit baseline move and report it with the
change.

## Release evidence

Full validation records the baseline revision, selected coverage, result, device profile, full SDK,
preview SDK, density, viewport, and build fingerprint. Native cases need positive behavior evidence
from an empty-fallback probe and same-device parity evidence; similarity to a source or fallback
image is insufficient.
