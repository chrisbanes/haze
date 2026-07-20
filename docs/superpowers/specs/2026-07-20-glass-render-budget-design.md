# Glass render budget and iOS memory-pressure design

## Context

GitHub issue [#1046](https://github.com/chrisbanes/haze/issues/1046) reports two related
memory-safety gaps in Glass rendering:

1. Supported optical values can expand the sampled layer to tens of thousands of pixels on each
   axis. The runtime renderer validates only that dimensions are finite and positive before it
   creates retained `GraphicsLayer` instances.
2. The iOS trim-memory registration is a no-op, so UIKit memory warnings never reach the existing
   retained-output release path.

The retained runtime pipeline can contain the source, up to three blur layers, depth mixing,
optical output, refraction detail, rim lighting, and up to three interaction layers. Retaining these
stages is important for frame-to-frame performance, but it multiplies the cost of an oversized
sample.

## Goals

- Prevent runtime Glass from creating or recording a retained layer whose width or height exceeds
  the library limit.
- Bound the combined backing-pixel estimate for every active retained stage in one Glass effect.
- Preserve the requested rendering scale when it already fits.
- Degrade an unsafe request predictably by reducing its effective input scale, then use fallback
  rendering if no useful safe scale exists.
- Canonicalize invalid resolved values before they can affect bounds or allocation decisions.
- Release retained Glass resources when iOS posts a memory warning and recreate them safely on the
  next required frame.
- Keep the policy internal and avoid new public API.

## Non-goals

- Enforcing an aggregate process-wide budget across multiple Glass effects.
- Querying vendor-specific GPU capabilities at runtime.
- Changing the documented range of `GlassOptics.Absolute.refractionScale`.
- Changing visual output for requests that already fit the budget.
- Addressing unrelated shader, fallback-parity, or retained-invalidation findings.

## Fixed library policy

Runtime Glass uses these internal limits:

| Limit | Value |
|---|---:|
| Maximum width or height of one retained layer | `4096 px` |
| Maximum combined retained backing area per Glass effect | `16,777,216 px` |
| Minimum scale reached by automatic degradation | `0.25f` |

The combined pixel limit is equivalent to 64 MiB of raw four-byte RGBA pixels before driver,
alignment, mip, and render-target overhead. It is deliberately expressed in pixels because the
Compose graphics API does not expose the backing format or exact allocation size.

The `0.25f` floor applies only to automatic degradation. If a caller explicitly supplies
`HazeInputScale.Fixed` below `0.25f`, that lower requested scale is evaluated and preserved when it
fits; the library never increases a requested scale.

## Budget model

Add an internal, pure common-code budget model in `haze-glass`. It accepts the requested scale,
unscaled sample geometry, resolved optics, blur plan, depth and detail requirements, rim presence,
and the current interaction state. It returns one of:

- `Runtime(scaleFactor)`: the largest evaluated scale at or below the requested scale that satisfies
  both limits.
- `Fallback(reason)`: the graph does not fit at the applicable scale floor, or its inputs are
  invalid.

The estimator sums the actual planned dimensions for each active retained surface:

- source at the rounded sample size;
- blur prefilter at sample size when required;
- horizontal and vertical blur at the blur plan's working size;
- depth mix at sample size only for a partial non-zero depth;
- optical output at sample size;
- refraction detail at sample size only when active;
- rim at sample size only when active;
- interaction optical, interaction detail, and interaction lighting at sample size only when their
  corresponding channels are active.

This avoids both undercounting optional interaction layers and overcounting inactive or downscaled
blur stages.

All multiplication uses `Long` after dimensions have been checked for finite, positive, and
bounded conversion. Addition is overflow-safe. Invalid or overflowing calculations return
`Fallback` rather than saturating into an apparently valid estimate.

## Scale selection

Evaluate the caller's requested scale first. If it fits, return it unchanged. Otherwise:

1. Evaluate the applicable floor: `min(requestedScale, 0.25f)`.
2. If the floor does not fit, return `Fallback`.
3. If the floor fits, run 16 binary-search iterations between the floor and requested scale to
   select the largest known fitting scale. This bounds scale uncertainty below `1 / 65,536` for
   the public `0f..1f` scale domain.
4. Rebuild coordinates, resolved render parameters, and the blur plan for every candidate. Rounded
   layer dimensions and blur-plan thresholds therefore participate in the decision rather than
   being approximated from the original graph.
5. Return the last known fitting candidate. A final exact evaluation must pass before runtime
   preparation continues.

The search is deterministic and bounded; it must not allocate graphics layers or mutate retained
state.

## Renderer integration

`GlassVisualEffect.prepareDraw` resolves the budget before selecting or preparing a delegate. The
budget decision is recalculated when geometry, input scale, optics, active stages, or interaction
state changes. The effect retains the selected runtime scale for the current prepared draw.

Platform delegate selection remains responsible for runtime-shader support, but also respects the
common budget decision:

- supported and budget-safe: select `RuntimeShaderGlassDelegate`;
- unsupported or `Fallback`: select `FallbackGlassDelegate`.

The runtime delegate uses the prepared scale instead of resolving `HazeInputScale` again. Its
budget check completes before `requireGraphicsContext`, `ensureLayer`, or any other retained-layer
creation. A scale change releases the old graph through the existing `scaledSize` path and records a
new graph on demand.

Switching to fallback releases the previous runtime delegate through the existing delegate
lifecycle. When a later request fits, normal delegate selection recreates runtime output. Log a
budget-driven scale reduction or fallback only when the decision changes, avoiding per-frame log
spam.

## Invalid values and bounds

Before calculating Glass padding, canonicalize resolved scalar inputs to finite supported values.
Invalid material, sample, or size-affecting values yield a fallback decision. For non-sizing shader
values, use the existing documented default when a resolved value is non-finite, then clamp it to
its documented range. An invalid light or interaction position resolves to the material center.
Non-finite style-supplied values therefore cannot reach shader uniforms or size arithmetic.

The bounds calculation may still represent the full requested optical reach; it does not allocate
a texture. The selected runtime scale is what constrains backing dimensions. No runtime
`GraphicsLayer` is created unless the scaled, rounded graph passes both limits.

## iOS memory warnings

Move the no-op trim-memory actual from `appleMain` to `iosMain`, keeping the common semantic
`registerTrimMemoryCallback` boundary unchanged.

The iOS implementation registers an observer with the default `NSNotificationCenter` for
`UIApplicationDidReceiveMemoryWarningNotification`. Delivery occurs on the main operation queue and
maps to `TrimMemoryLevel.COMPLETE`. The returned `DisposableHandle` removes the opaque observer token
from the notification center.

The existing path then performs recovery:

1. `HazeEffectNode` forwards the level to the active visual effect.
2. `RuntimeShaderGlassDelegate` releases every retained layer and cached render effect, clears
   successful-output metadata, and invalidates drawing.
3. The next required frame recalculates the budget and rebuilds only a safe graph.

Detaching the node disposes the notification registration, so the notification center does not
retain dead effect nodes or callbacks.

## Testing

### Common budget tests

- requested scale is unchanged when the graph fits;
- exact dimension and combined-pixel limits fit;
- one-pixel dimension and combined-pixel overages are reduced or rejected;
- the chosen result is the largest fitting scale within the search precision;
- automatic degradation stops at `0.25f`;
- an explicitly smaller `Fixed` request is honored and never increased;
- an impossible graph returns fallback;
- non-finite, non-positive, overflowing, and invalid rounded dimensions return fallback;
- every baseline, blur, detail, rim, and interaction stage contributes its actual dimensions;
- inactive stages contribute zero pixels;
- maximum supported refraction values cannot produce an over-budget runtime decision.

### Delegate and lifecycle tests

- fallback is selected before a runtime `GraphicsLayer` is created for an impossible graph;
- scale changes release the previous retained graph and rebuild at the selected safe size;
- `COMPLETE` releases all baseline and interaction layers, clears cached output, and invalidates
  drawing;
- the next required draw after release recreates valid retained output.

### iOS tests

An iOS simulator test registers a callback, posts
`UIApplicationDidReceiveMemoryWarningNotification`, and verifies one `COMPLETE` delivery. After
disposing the handle, posting again must not invoke the callback.

### Verification commands

Run focused formatting and tests, followed by affected platform and screenshot verification:

```text
./gradlew spotlessCheck
./gradlew :haze-glass:jvmTest :haze-glass:testAndroidHostTest
./gradlew :haze:compileKotlinIosSimulatorArm64
./gradlew :haze:iosSimulatorArm64Test -Phaze.enableAppleTests
./gradlew :haze-screenshot-tests:jvmTest :haze-screenshot-tests:testAndroidHostTest
```

Normal screenshot cases are expected to remain pixel-identical because fitting requests keep their
existing scale. Any changed baseline requires investigation rather than automatic recording.

## Alternatives considered

### Clamp refraction reach

Reducing resolved `refractionScale` can control the original source of oversized padding while
keeping full material resolution. It changes requested optics and does not independently constrain
the multiplied cost of the retained stage graph, so it is not sufficient as the primary policy.

### Immediate fallback

Selecting fallback as soon as either limit is exceeded is simple and safe, but produces an abrupt
visual downgrade for graphs that fit with a modest resolution reduction.

### Platform capability probing

Querying the GPU's maximum texture size could permit larger surfaces on some devices, but it would
not provide a reliable total memory budget and would make behavior platform- and driver-dependent.
The fixed conservative library policy is predictable and testable.

## Compatibility and residual risk

The design adds no public API and preserves fitting output. Over-budget requests change from likely
allocation failure to lower-resolution runtime Glass or fallback rendering. This is an intentional
safety improvement.

The budget is per effect, not process-wide. Several individually safe full-screen effects can still
consume substantial memory. iOS warnings provide recovery after pressure, while the fixed per-effect
limit prevents one configuration from requesting catastrophic individual surfaces.
