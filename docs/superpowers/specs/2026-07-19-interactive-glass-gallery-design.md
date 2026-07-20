# Interactive Glass Gallery Design

## Goal

Showcase opt-in Glass interaction through the existing Product, Playground, and Lab Gallery
samples. Each sample should demonstrate a distinct use case while preserving the Gallery's polished,
recordable presentation.

## Scope

- Integrate interaction into the existing three Glass Gallery destinations.
- Remove the separate `InteractiveGlassSample` and its sample registration.
- Keep interaction disabled unless each sample explicitly opts in.
- Add deterministic sample and screenshot coverage for the new wiring.

This work does not change the public Glass interaction API or its defaults.

## Sample Roles

### Product

Product demonstrates the default `interactable()` behavior in realistic controls.

- Enable interaction on the top bar and action dock.
- Leave the metadata card inert because it is informational rather than actionable.
- Add no explanatory labels or interaction-specific chrome.

### Playground

Playground demonstrates custom interaction on every draggable Glass surface.

- Use the default pointer-following hover light.
- On press, retain the hover light, scale material and content to `0.98`, and increase refraction to
  `1.08`.
- Use the pointer as the transform pivot.
- Use the standard Glass press and release animation specifications.
- Keep drag handling separate. Glass observes the pointer stream without consuming it, while the
  existing drag gesture remains responsible for movement.
- Honor the system reduced-motion policy.

### Lab

Lab demonstrates that interaction is optional and configurable.

- Add an `Interaction` segmented selector with `Off`, `Pressed`, and `All` modes.
- Open the sample with `All` selected so the feature is immediately discoverable.
- Map `Off` to no interaction declarations, `Pressed` to `pressed()`, and `All` to
  `interactable()`.
- Apply mode changes immediately without an Apply action.
- Share a `MutableInteractionSource` between the effect and the specimen's focusable modifier so
  the Lab demonstrates keyboard focus as well as pointer and touch input.

The API itself remains disabled by default; the Lab explicitly opts into `All` as showcase state.

## Shared Sample Plumbing

Extend the internal `GlassSurface` helper with an optional `GlassVisualEffect.() -> Unit`
configuration block. Invoke it inside the existing `glassEffect` DSL after style and shape are
applied.

An absent block must do nothing. The helper must not enable interaction globally or introduce a
second sample-specific interaction model. Product and Lab use this block; Playground continues to
configure its remembered `GlassVisualEffect` instances directly.

Add a small `GlassLabInteractionMode` value to `GlassLabState`. Resetting the Lab restores `All`,
along with the rest of the initial showcase state.

## Standalone Sample Removal

Remove:

- `InteractiveGlassSample.kt`.
- `Sample.InteractiveGlass` and its `CommonSamples` entry.
- The standalone sample rendering test.

The Gallery samples replace its responsibilities: Product covers defaults, Playground covers custom
responses, and Lab covers opt-in configuration and focus.

## Testing and Screenshots

Sample tests should verify wiring rather than duplicate library interaction-controller tests.

- Add a unit test for Lab mode mapping: `Off` declares no slots, `Pressed` declares only press, and
  `All` declares hover, focus, and press.
- Update sample registration and rendering coverage after removing the standalone sample.
- Keep existing resting Gallery screenshots visually unchanged.
- Add one deterministic Playground scenario showing a surface pressed while dragged. Capture the
  same scenario on Android and Desktop through the existing shared Gallery screenshot structure.

The pressed screenshot should use an injected `MutableInteractionSource`, a fixed scene frame and
drag offset, and paused autoplay. Emit `PressInteraction.Press`, settle the animation, then capture;
do not synthesize platform mouse or touch input.

## Verification

Run:

```shell
./gradlew \
  :sample:shared:jvmTest \
  :sample:shared:testAndroidHostTest \
  :sample:shared:spotlessCheck \
  :sample:screenshot-tests:test \
  :sample:screenshot-tests:spotlessCheck
git diff --check
```

Record the intentional new Android and Desktop baselines with:

```shell
./gradlew :sample:screenshot-tests:recordRoborazzi
```
