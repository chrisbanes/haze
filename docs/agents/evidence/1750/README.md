# Adaptive Glass edge research

[Issue #1750](https://github.com/chrisbanes/haze/issues/1750) asks whether local
backdrop luminance improves the visibility of Glass rim lighting or authored outlines. This is
research evidence, not an adaptive feature or a change to the library's defaults. The restored
production sources are byte-identical to base `8742b2688999a83c7d681d285b1465729e5b24ac`.
The [accepted plan](https://github.com/chrisbanes/haze/issues/1750#issuecomment-6698563843)
and [T1 gate](https://github.com/chrisbanes/haze/issues/1750#issuecomment-6735721674)
record the experiment's authority and scope.

## Recommendation

**Recommendation: neither adaptive rim nor adaptive outline for now; keep fixed lighting.** The
experiment demonstrates controllable local
opacity changes, but does not establish that either change improves visibility across content.
The rim changes were subtle in the inspected static comparisons, particularly on light content.
The outline changes were more visible, but the frozen multiplier weakens the red outline over the
dark source, potentially reducing its visibility there. These are observations and risks, not a
measured contrast-benefit score. The maintainer's final visual-benefit and delivery review remains
open; no rim, outline, or combined shipping choice is approved by this report.

The candidate sampled one local pre-optical source value per edge fragment and multiplied only the
edge alpha by `mix(1, 0.5 + Y, clamp(gain, 0, 1))`, clamped to the valid final alpha. Tagged sRGB
SDR input was unpremultiplied when alpha exceeded `1e-4`, decoded once to linear light, and
`Y = 0.2126R + 0.7152G + 0.0722B`; transparent input used fixed strength. Authored straight RGB
was retained. Both effective gains zero selected the original fixed path; the separate numeric
adaptive-zero shader was a control, not that selection.

## Evidence and limits

The [T1 manifest](manifest.json) and [T2 manifest](t2-manifest.json) record the frozen scenes,
coordinates, controls and tolerances; the [T3 summary](t3-summary.json) records backend
qualification limits. T1 calibrated actual Skiko and API 33 Android emulator
colour/sampling paths, including local versus material-average and frozen-source faults. Its final
JVM calibration comprised 12 direct shader tests and two source-backed configurations; the API 33
instrumentation comprised three methods, only one of which was source-backed Glass. Faults and
restored runs apply to their recorded source snapshots, not automatically to later versions.

T2 used a 128 × 96 source, a single 96 × 64 surface and the actual regular built-in material. The
20 still comparisons are five backdrops (light, dark, split, checker and repository photo) times
four modes (fixed, rim-only, outline-only and combined), with five material-only controls. Across
the 15 adaptive stills, interior change was 0/255. In the predeclared 3,648-pixel edge mask,
rim-only mean maximum-channel changes ranged 0.57–2.94/255 (maximum 8–29/255); outline-only
means ranged 3.34–7.38/255 (maximum 46–49/255). These numbers measure change from fixed, not
improvement. Red/cyan hue, transparent outline, gain, identity and stationary/ramp controls were
checked separately.

The private motion evidence contains 12 mode/backdrop sequences of 120 lossless PNG frames each
(1,440 frames) and 12 H.264 clips, with three additional half-speed encodings. Frames advance at
60 **simulated** fps: a one-pixel source shift per frame through frame 89, followed by 30 stationary
frames. Repeated tail frames matched, while loop endpoints differ. Static source and encoded sample
frames were inspected. Chris explicitly said “Let's skip the playback review”; the
[recorded exception](https://github.com/chrisbanes/haze/issues/1750#issuecomment-6739064366)
removes playback as a progression gate. Full-speed and slowed playback were not performed or
passed, and shimmer, popping and perceived motion quality were not assessed through playback.
Nothing here measures physical frame rate or GPU cost.

T3's final Android host selector passed 31 methods with no failures or skips (19 prototype and 12
existing). SDK 35/37 Robolectric NATIVE cases asserted a fused runtime delegate, hardware canvas,
positive adaptive pixels and actual Quality/Balanced/Performance input scales of 1, 0.7905694 and
0.5. Android fused and Skiko multipass tests exercised merged bridge/separation, outside outline,
and a same-instance transition and restoration. A Skiko test additionally used an actual
container-owned shared-settings cluster; Android's adaptive merged fields were supplied by the
test apparatus, not independently qualified through an actual container-owned runtime. Separate
host tests cover independent programs, source changes, trim/recreation, detach/reattach, material
and geometry changes, a layer-only move, and foreground over coloured content with pressed
lighting. SDK 31 selected fixed fallback; separate existing over-budget controls checked authored
fallback colour. These host results do not establish physical Android 15 or GPU performance.

Reduced transparency is an important qualification: an unguarded temporary collaborator remained
active and differed from fixed output by 18/255. A **test-owned** selector observing the actual
setting cleared it and restored fixed output exactly. That qualifies the apparatus selection only;
it does not prove automatic production accessibility precedence, authored-control retention or
reactivation. Native Backdrop did not provide the collaborator with a qualified recorded pre-optical
input, so the experiment uses fixed behavior there and makes no native parity claim.

Protocol history matters. D102's early coarse observation preceded a complete freeze and is
quarantined, not qualification. D103's prospective base freeze was completed by separately timed
prospective supplements; it was not one freeze before every observation. The D113 absent-outline
positive oracle was invalid because regular Glass has no default outline; D114 corrected it with a
separately frozen authored outline and retained the failure. The first separated-merge reference
was copied from a red-source fixture and failed before candidate installation; D114 replaced it
with a separately frozen achromatic source reference and green member probes. A deliberate wrong-gap
fixture failed that pixel oracle, and the original source was restored and rerun. Later bridge-band
and transition supplements were frozen after earlier candidate observations and before their own
measurements. Exact failure, fault, normal and restored source/XML snapshots remain in the private
evidence archive; a fixture fault is not a production-shader fault.

## Reproduction

The exact full prototype patch, including the temporary production seam and copied photo resource,
is retained privately with SHA256
`3cf212b37a5b518baad9d1f5e0d87d3f594b51b3f09c38a55c234db8d9b53b1d`. The separate
[source-only patch](prototype-source.patch) has SHA256
`6652111e25a3b5861e50726c3d7783a37b3cbf647e728f637eb86598f0fd3d63`; it excludes the duplicate
photo and is **not complete without the copy below**. Both patches passed forward checks against
the base; the complete patch also passed reverse checking against the prototype tree. Apply the
source-only patch to a clean checkout at that exact base, not over this restored research tree.
Keep a copy of the linked patch outside that checkout and substitute its path below:

```sh
shasum -a 256 haze-screenshot-tests/src/commonMain/composeResources/drawable/photo.jpg
# Require 1170e80909419d8c7181c4db2d7a251ec457433541d6a59a9612d3b22925137e
mkdir -p haze-glass/src/jvmTest/resources
cp haze-screenshot-tests/src/commonMain/composeResources/drawable/photo.jpg haze-glass/src/jvmTest/resources/adaptive-edge-photo.jpg
git apply --check /path/to/prototype-source.patch
git apply /path/to/prototype-source.patch
```

The repository photo was added in commit `d7c51af836477340f6e30a1911d80e9c142b28a9`;
individual photographer, source and licence were not found in the bounded local check. No licence
conclusion or external download is assumed. This handback adds no photo file; photo-derived
artifacts remain in the local evidence archive.

Use Java 21 and the repository's `gradle-run` skill, with each lifecycle command standalone and
outside the sandbox. Set up Java and any artifact-output environment in the shell beforehand; do
not prefix managed commands with assignments. Create a fresh workflow, replace `<workflow>` with
its returned ID, and preserve XML and private output before finishing. For evidence recording,
set `HAZE_T2_ARTIFACT_DIR` to a new private output directory before the first invocation. Run the
fixed-baseline and Skiko colour probes first, retaining their capture, repeat and shader digests
before observing candidates. Ordinary verification may leave the output variable unset.

The actual Android colour/sampling claim additionally requires an authorized API 33 target, not
Robolectric or an API 34 substitute. Select that target and set `ANDROID_SERIAL` in the execution
environment before the device command; verify its SDK is 33. The instrumentation asserts its SDK,
hardware canvas and sRGB inputs. A skipped or unavailable device run is incomplete qualification.

```sh
python3 <gradle-run-skill>/scripts/gradle_run.py create
python3 <gradle-run-skill>/scripts/gradle_run.py run --workflow <workflow> --scope targeted --question 'Do fixed-baseline and Skiko colour probes pass before candidate observations?' -- ./gradlew :haze-glass:jvmTest --tests '*AdaptiveEdgeFixedBaselineTest*' --tests '*AdaptiveEdgeColourCalibrationTest*' --no-scan
python3 <gradle-run-skill>/scripts/gradle_run.py run --workflow <workflow> --scope targeted --question 'Do prototype JVM controls pass?' -- ./gradlew :haze-glass:jvmTest --tests '*AdaptiveEdgePrototype*' --no-scan
python3 <gradle-run-skill>/scripts/gradle_run.py run --workflow <workflow> --scope targeted --question 'Do Android adaptive and fixed controls pass?' -- ./gradlew :haze-glass:testAndroidHostTest --tests '*AdaptiveEdgePrototypeAndroidHostTest*' --tests '*ReducedTransparencyFallbackAndroidHostTest*' --tests '*GlassSharedRimShaderAndroidHostTest*' --tests '*MergedGlassFusedAndroidHostTest*' --no-scan
python3 <gradle-run-skill>/scripts/gradle_run.py run --workflow <workflow> --scope targeted --question 'Does actual API 33 hardware rendering reproduce the colour and source-backed calibration?' -- ./gradlew :haze-glass:connectedAndroidDeviceTest -Pandroid.testInstrumentationRunnerArguments.class=dev.chrisbanes.haze.glass.AdaptiveEdgePrototypeInstrumentationTest --no-scan
python3 <gradle-run-skill>/scripts/gradle_run.py status --workflow <workflow>
python3 <gradle-run-skill>/scripts/gradle_run.py finish --workflow <workflow>
```

Artifact emission in the prototype depends on `HAZE_T2_ARTIFACT_DIR`; unset it for ordinary tests.
The rendering and assertions remain active without artifact output. The private receipt and
inventories contain the original commands, hashes, settings, renderer metadata and 1,557 checked
T2 artifacts. Canonical screenshot references were not regenerated.

## Conditional future work — unshipped

If a future design establishes visual benefit, consider independent opt-in gains attached to the
existing rim and outline controls. Preserve exact fixed, source-independent behavior when both
gains are zero, authored hue and transparent-alpha behavior, and the once-only local coordinate and
colour transforms. A shipping design would need source/settings/coordinate invalidation, keys and
cluster compatibility, per-instance shader-uniform isolation, lifecycle/resource and cost evaluation,
and explicit fixed behavior where native Backdrop input or the renderer is unavailable. Automatic
accessibility precedence, retention of authored controls, reactivation after the setting changes,
and fault-sensitive proof of those transitions are required; the temporary selector does not supply
that policy. Physical backend qualification, normal CI, independent review and maintainer visual
assessment remain required. No proposed signature or default has landed.
