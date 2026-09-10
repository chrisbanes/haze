# Android baseline profiles

The library baseline profile generator exercises the current core, Blur, and Glass paths through
the sample's Images List, Scaffold, Credit Card, Glass Product, and Glass Playground journeys. It
runs on both AOSP managed devices: `pixel5Api30` (API 30) and `pixel5Api34` (API 34). The profile
filter is limited to Haze-owned packages under `dev.chrisbanes.haze.**`.

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
./gradlew --no-scan :haze:generateBaselineProfile \
  -Pandroid.testInstrumentationRunnerArguments.class=dev.chrisbanes.haze.BaselineProfileGenerator
```

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

For the retained final profile, the verifier reports 2,285 rules (215 class and 2,070 method), zero
missing ordinary AAR members, and zero missing consumer-Dex members. Its ordinary counts are `469`
(`haze`), `341` (`haze-blur`), `1,101` (`haze-glass`), `36` (`haze-utils`), `7`
(`haze-materials`), and `3` (`haze-glass-material3`). The expected generated counts are 295
external-synthetic entries, 16 lambda bridges, 16 `$-CC` interface companions, and one
namespaced `R$drawable` class. Generated entries still require exact consumer-Dex definitions;
`$-CC` entries also require an interface owner with the interface access flag in an AAR, and
`R$drawable` requires the matching AAR manifest namespace.

Only the root `haze` AAR may contain `baseline-prof.txt`, and its bytes must match the checked-in
file exactly. The other five AARs must contain no profile asset. The verifier preserves HSP flags,
nested `$` names, synthetic names, and complete method descriptors. It rejects malformed,
foreign/sample, missing, or unexplained rules instead of deleting or renaming them. Keep the
generated profile as collection output; do not hand-author rules or retain obsolete Haze 1 rules.
The API 30 and API 34 device collections need no rerun for verifier or README changes while the
collection-relevant generator, sample/runtime, and toolchain inputs remain unchanged. Generation,
packaging, and descriptor verification establish artifact coverage only; they do not measure
startup, frame time, or any other performance improvement.


Return to the [Android benchmark runbook](README.md).
