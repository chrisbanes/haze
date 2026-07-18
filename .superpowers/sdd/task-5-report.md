# Task 5 report: transforms and fallback interaction lighting

## TDD evidence

- RED (transforms): `:haze-glass:jvmTest --tests 'dev.chrisbanes.haze.glass.GlassInteractionControllerTest'` failed at compilation because `currentMaterialTransform` did not exist. After importing the test-only `center` extension, the missing material-transform API remained the feature failure.
- GREEN (transforms): the same focused controller suite passed after resolving transforms by target, pivot, drawable geometry, and retained state.
- RED (fallback): `:haze-glass:jvmTest --tests 'dev.chrisbanes.haze.glass.FallbackGlassInteractionTest'` failed with the pointer luminance lower than the configured opposite-corner base highlight.
- GREEN (fallback): the fallback test passed after adding the localized, clipped radial white interaction highlight inside its existing alpha block.

## Fixture adaptation

The transform tests use `GlassReducedMotionPolicy.Full` with declarations outside `animate`, so target values snap immediately while preserving the approved `Reduced` identity-transform behavior. The fallback fixture uses a black content background to avoid white capture saturation and points the pre-existing static highlight at the comparison corner; it also assigns `FallbackGlassDelegate` through the internal delegate seam.

## Verification

- Focused controller and fallback suites: passed.
- Full `./gradlew :haze-glass:jvmTest`: passed (26 actionable tasks).
- `./gradlew spotlessApply`: passed.
- `git diff --check`: passed.

## Rendering-boundary self-review

`prepareDraw`, source/content capture, geometry, cache keys, and pointer hit testing are untransformed. `MaterialOnly` wraps only delegate draw and foreground; `MaterialAndContent` exposes only the core `VisualEffectTransform`. The fallback pass reads only position, lighting state, and radius; it retains all base tint/highlight/edge passes and does not read interaction refraction or white-point values. Runtime-shader optics remain untouched.

## Concerns

None found. `haze/src/commonMain/kotlin/dev/chrisbanes/haze/VisualEffect.kt` is concurrently modified by another task and was intentionally excluded from this task's commit.
