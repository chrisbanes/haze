# Android baseline profiles

The library baseline profile generator exercises the current core, Blur, and Glass paths through
the sample's Images List, Scaffold, Credit Card, Glass Product, and Glass Playground journeys.
Additional collections run changing-source Blur and Glass workloads in Quality, Balanced, and
Performance modes, add and remove a Glass container member, and exercise native Blur and Glass
backdrop rendering. Glass mode and container collections require API 33 or newer; the native
backdrop collection requires a supported Android 37.2 build.
Blur launches force blur on, including the legacy renderer on API 30.

Unsupported collection methods return without collecting. Test counts therefore include methods
that produced no profile on that SDK; check the per-method profile outputs when verifying coverage.

Generation uses both AOSP managed devices: `pixel5Api30` (API 30) and `pixel5Api34` (API 34).
Connected-device collection is opt-in. The profile filter includes Haze-owned packages under
`dev.chrisbanes.haze.**` and excludes `dev.chrisbanes.haze.sample.**`.

Generation requires JDK 21 and an Android SDK selected by `ANDROID_HOME` or `ANDROID_SDK_ROOT`.
Verify the environment with `java -version`, `echo "$ANDROID_HOME"` (or
`echo "$ANDROID_SDK_ROOT"`), and `command -v sdkmanager`; the latter checks that SDK package
management tooling is available, while Gradle uses the configured SDK environment. The managed-
device definitions and AOSP images are declared in `internal/benchmark/build.gradle.kts`.

`RenderScriptScrollRegressionTest` exercises allocation teardown while scrolling Images on API 30.
It runs five launches with twelve alternating scrolls each, without profile compilation or timing
metrics. Run it independently from profile collection:

```shell
./gradlew --no-scan :internal:benchmark:pixel5Api30NonMinifiedReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.chrisbanes.haze.RenderScriptScrollRegressionTest
```

Run generation from the repository root:

```shell
./gradlew --no-scan --no-configuration-cache --rerun-tasks :haze:generateBaselineProfile \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.chrisbanes.haze.BaselineProfileGenerator
```

To include native backdrop rules, connect an unlocked Android 37.2 device and opt into connected
devices in the same generation run. This merges its collection with both managed-device outputs:

```shell
./gradlew --no-scan --no-configuration-cache --rerun-tasks :haze:generateBaselineProfile \
  -Phaze.baselineProfile.connectedDevices=true \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.chrisbanes.haze.BaselineProfileGenerator
```

This installs and runs the sample and instrumentation on connected devices. Disconnect unrelated
devices before running. Leaving out the opt-in property restores managed-device-only collection,
which cannot collect native backdrop rules on API 30 or API 34.

These commands force fresh device collection. Check the per-method profile artifacts as well as
the Gradle result; a successful aggregate task can otherwise reuse earlier test outputs. When
including native backdrop, the merged profile must contain `AndroidHazeBackdropRenderer.draw`.
Before a targeted rerun, preserve the passing per-method text profiles: test reports and exported
artifact directories can be replaced by the next invocation.

If a shared managed-device reservation blocks collection after an interrupted run, create an empty
directory for this task and add `-DANDROID_USER_HOME=/absolute/path/to/directory` to the generation
command. This gives the run its own managed-device state. Keep that directory until collection
finishes.

The checked-in output is
`haze/src/androidMain/generated/baselineProfiles/baseline-prof.txt`. Generation and packaging
coverage verify which classes and methods are exercised and shipped; they do not measure startup,
frame time, or any other performance improvement.

After generating, package the six existing Haze Kotlin Multiplatform `androidMain` AARs and the
non-minified sample consumer. The package task is `bundleAndroidMainAar`; skip profile generation
when validating packaging because the checked-in profile is the input to this check:

```shell
./gradlew --no-scan \
  :haze-utils:bundleAndroidMainAar \
  :haze:bundleAndroidMainAar \
  :haze-blur:bundleAndroidMainAar \
  :haze-glass:bundleAndroidMainAar \
  :haze-materials:bundleAndroidMainAar \
  :haze-glass-material3:bundleAndroidMainAar \
  :sample:android:assembleNonMinifiedRelease \
  -Pandroidx.baselineprofile.skipgeneration
```

The six AARs are written to `build/outputs/aar/<module>.aar` in their respective module
directories. The consumer APK is
`sample/android/build/outputs/apk/nonMinifiedRelease/android-nonMinifiedRelease.apk`. Verify the
profile against exact defined class and method members in every AAR and every consumer
`classes*.dex` file:

```shell
python3 internal/benchmark/verify_baseline_profile.py \
  --profile haze/src/androidMain/generated/baselineProfiles/baseline-prof.txt \
  --aar haze/build/outputs/aar/haze.aar \
  --aar haze-blur/build/outputs/aar/haze-blur.aar \
  --aar haze-glass/build/outputs/aar/haze-glass.aar \
  --aar haze-utils/build/outputs/aar/haze-utils.aar \
  --aar haze-materials/build/outputs/aar/haze-materials.aar \
  --aar haze-glass-material3/build/outputs/aar/haze-glass-material3.aar \
  --apk sample/android/build/outputs/apk/nonMinifiedRelease/android-nonMinifiedRelease.apk
```

The retained profile combines two API 30 collections, four API 34 collections, and five collections
from a physical Pixel 8a running Android 37.2. The verifier reports 3,190 rules
(518 class and 2,672 method), zero missing ordinary AAR members, and zero missing
consumer-Dex members. Its ordinary counts are `724` (`haze`), `373` (`haze-blur`),
`1,703` (`haze-glass`), `69` (`haze-utils`), `7` (`haze-materials`), and `2`
(`haze-glass-material3`). The generated counts are 293 external-synthetic entries,
18 lambda bridges, zero `$-CC` interface companions, and one namespaced `R$drawable`
class. Generated entries still require exact consumer-Dex definitions;
`$-CC` entries also require an interface owner with the interface access flag in an AAR, and
`R$drawable` requires the matching AAR manifest namespace.

The Pixel 8a's combined sample journey reached the 15-iteration collection limit
with only one stable iteration at the end. It produced a profile and passed the
test, but did not reach two consecutive stable iterations.

Only the root `haze` AAR may contain `baseline-prof.txt`, and its bytes must match the checked-in
file exactly. The other five AARs must contain no profile asset. The verifier preserves HSP flags,
nested `$` names, synthetic names, and complete method descriptors. It rejects malformed,
foreign/sample, missing, or unexplained rules instead of deleting or renaming them. Keep the
generated profile as collection output; do not hand-author rules or retain obsolete Haze 1 rules.
Device collections need no rerun for verifier or README changes while the
collection-relevant generator, sample/runtime, and toolchain inputs remain unchanged. Generation,
packaging, and descriptor verification establish artifact coverage only; they do not measure
startup, frame time, or any other performance improvement.


Return to the [Android benchmark runbook](README.md).
