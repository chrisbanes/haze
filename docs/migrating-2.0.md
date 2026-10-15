# Migrating to Haze 2.0

Haze 2.0 separates what an effect reads (its input), how it looks (an immutable, shareable Style),
and per-modifier settings such as performance mode. The preferred Blur API is now the typed
`Modifier.hazeBlur`.

## Dependencies and imports

Blur lives in `haze-blur`, with optional presets in `haze-blur-materials`:

```kotlin
dependencies {
  implementation("dev.chrisbanes.haze:haze:<version>")
  implementation("dev.chrisbanes.haze:haze-blur:<version>")
  implementation("dev.chrisbanes.haze:haze-blur-materials:<version>")
}
```

```kotlin
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
```

`HazeProgressive` remains in the core `dev.chrisbanes.haze` package.

## Migrate source-backed Blur

Before:

```kotlin
Modifier.hazeEffect(hazeState) {
  blurEffect {
    blurRadius = 20.dp
    colorEffects = listOf(HazeColorEffect.tint(Color.Black.copy(alpha = 0.7f)))
  }
}
```

After:

```kotlin
Modifier.hazeBlur(
  input = HazeInput.Sources(hazeState),
  style = HazeBlurStyle {
    blurRadius(20.dp)
    colorEffects(
      listOf(HazeColorEffect.tint(Color.Black.copy(alpha = 0.7f))),
    )
  },
)
```

Use `HazeInput.Content` for foreground or own-content Blur.

## Migrate Styles

`HazeBlurStyle` is no longer a readable value patch with `copy`. It records Blur-specific writes:

```kotlin
val base = HazeBlurStyle {
  blurRadius(20.dp)
  noiseFactor(0.15f)
}

val compact = base.then {
  blurRadius(12.dp)
}
```

Resolution is defaults, then `LocalHazeBlurStyle`, then the explicit modifier Style. The last write
wins. Replacing `compact` with `base` removes the 12 dp write immediately; no previous resolved
value is retained.

An explicit `colorEffects(emptyList())` clears inherited effects. Haze copies lists you pass, so
changing a list afterwards does not affect the Style.

### Other Blur API changes

Build every Blur Style with `HazeBlurStyle { ... }`, and extend the defaults with
`HazeBlurDefaults.style.then { ... }`. The `HazeStyle(...)` constructor, `HazeStyle.Unspecified`,
and the `HazeDefaults.style(...)` builder have no direct replacement.

Create color effects with `HazeColorEffect.tint(...)` or `HazeColorEffect.colorFilter(...)`. The
former `HazeTint` constructors and `HazeTint.Unspecified` are gone, and the tint factory rejects
`Color.Unspecified`. Write `fallbackColorEffect(null)` to clear an inherited fallback effect.

`HazeDefaults.tintAlpha` and `HazeDefaults.tint(...)` are removed; write the tint you want into the
Style. To check whether Blur is enabled by default on the current platform, call
`HazeBlurDefaults.isBlurEnabledByDefault()`.

## Migrate material presets

Before:

```kotlin
Modifier.hazeEffect(hazeState) {
  blurEffect {
    style = HazeMaterials.thin()
  }
}
```

After:

```kotlin
Modifier.hazeBlur(
  input = HazeInput.Backdrop(hazeState),
  style = HazeMaterials.thin(),
)
```

Customize a preset with `then`, not `copy`.

## Migrate input and rendering policy

For ordinary built-in Blur and Glass, use the current-window Backdrop input. Keep
`HazeInput.Sources` when the effect needs exact source selection, cross-window alignment, or a
specific retained-output policy that governs the actual input:

| Requirement | Input |
| --- | --- |
| Normal built-in surface behind content | `HazeInput.Backdrop(hazeState)` |
| Exact captured-source semantics | `HazeInput.Sources(hazeState)` |
| Modifier's own content | `HazeInput.Content` |

Use `HazeInput.Backdrop(state)` for the default source fallback. If you need to customize the
portable fallback, pass a `HazeInput.Sources` value to `Backdrop`:

```kotlin
HazeInput.Backdrop(
  HazeInput.Sources(
    state = state,
    selection = HazeSourceSelection.All,
    retention = HazeSourceRetention.ClearWhenUnavailable,
  ),
)
```

The fallback source input does not filter native window pixels.

These values are modifier structure, not Style:

```kotlin
Modifier.hazeBlur(
  input = HazeInput.Sources(
    state = hazeState,
    retention = HazeSourceRetention.ClearWhenUnavailable,
  ),
  style = style,
  performanceMode = HazePerformanceMode.Quality,
  expandLayerBounds = false,
)
```

The native Backdrop path is experimental and disabled by default. Set
`HazeFeatureFlags.isPlatformBackdropEnabled = true` before the effects are first composed. The flag
makes native rendering eligible rather than guaranteed, and changing it affects only effects
composed afterwards. Unsupported platforms and effects, software rendering, and native failures
use the source fallback for as long as the effect stays composed. Set `HazeLogger.enabled = true`
to log which path was chosen. Native Backdrop has not shown a CPU benefit for Glass in Haze's
benchmarks; see the [performance guide](performance.md#backdrop-input) before enabling it.

`HazePerformanceMode.Default` selects the fixed `Balanced` profile for built-in Blur and Glass.
`Adaptive` remains deprecated for binary compatibility and behaves the same as `Default`; replace
it with `Default`. Use `Quality` or `Performance` for their named profiles, or
`Fixed(qualityFraction)` for an explicit quality level from `0f` (lowest) to `1f` (highest).
`Quality` replaces the previous built-in full-resolution choice. Previous fixed input-pixel
fractions are not equivalent to the same `qualityFraction` value; compare appearance and
performance before choosing a replacement.
`HazeSampling` remains the generic policy used by custom effects.

## Lifecycle and sharing

Styles are immutable and safe to share. Supply a replacement Style through recomposition when the
appearance changes.

## Other Haze 2 migrations

The typed Blur API does not change the other Haze 2 migrations.

### Glass migration

Glass also has a typed modifier and an immutable `GlassStyle`. Replace the Style through
recomposition instead of mutating an effect or using sentinel patches, `copy`, or `clear*` calls.
Use `then` to build variations from a shared base Style.

Keep one final `GlassStyle` for all platforms. Remove renderer-capability checks, platform-specific
Style variants, and secondary fallback Styles. Haze selects the available implementation and
gracefully simplifies unsupported optics.

Invalid Glass values now fail instead of being corrected later: a `GlassOptics` value when it is
created, and a `GlassStyle` write when the Style is applied to an effect. Only animatable
overshoot is coerced: alpha and tint alpha are clamped into `0f..1f`, and negative corner radii are
treated as zero. Validate or clamp other external values before writing them in the Style. See the
generated API reference for property-specific ranges.

| Legacy | Typed replacement |
| --- | --- |
| `hazeEffect { glassEffect { … } }` | `hazeGlass(input, style, performanceMode, expandLayerBounds, …)` |
| `GlassVisualEffect` | `GlassStyle` plus `Modifier.hazeGlass` |
| `GlassLighting`, `GlassColor`, `GlassRendering` | property writes inside `GlassStyle { … }` |
| `GlassStyle.Unspecified` | `GlassStyle`, the empty replayable Style |
| mutable effect properties | replace `GlassStyle` through recomposition |
| sentinel patches and `copy` | `GlassStyle { … }`, `then`, and `LocalGlassStyle` |
| interaction mutation and `clear*` | declarative `hovered`, `focused`, and `pressed` blocks; omit blocks in a replacement Style |
| `Modifier.hazeGlass(interactionLightRadiusFraction = value)` | `GlassStyle { interactionLightRadiusFraction(value) }` |
| `Modifier.hazeGlass(interactionPositionAnimationSpec = spec)` | `GlassStyle { interactionPositionAnimationSpec(spec) }` |
| `GlassDefaults.hoverAnimationSpec`, `pressAnimationSpec`, `releaseAnimationSpec` | explicit `animate(toSpec, fromSpec) { … }` declarations |
| consumer implementation of `GlassInteractionScope` | removed; it is now a sealed declaration DSL implemented by Haze |
| `GlassStyleConfiguration`, `GlassRenderer`, `GlassRendererCache`, and lifecycle or retention hooks | removed with no public replacement |
| effect-owned hover, focus, press, light-radius, and light-position animation presentation | property writes inside `GlassStyle { … }` |
| effect-owned interaction source, transform target/pivot, and reduced-motion policy | explicit `Modifier.hazeGlass` arguments set on each modifier |
| implicit source/content | explicit `HazeInput.Backdrop`, `HazeInput.Sources`, or `HazeInput.Content` |
| `GlassOptics.Absolute` and `GlassOptics.Fixed` | `GlassOptics` with fixed `OpticalSizeValue` values; this is an intentional source break with no alias or compatibility bridge |
| `GlassOptics.Adaptive` used as a complete Style | `GlassStyle.regular` |
| `GlassOptics.Adaptive` supplied through `optics(...)` | `optics(GlassOptics.regular)` |
| `lightPosition(Offset)` and `Offset.Unspecified` | `lightPosition(Alignment)`; omit the write or use `Alignment.Center` for the former automatic center |

Use `optics(...)` for inline fixed values. Keep a `GlassOptics` value when the configuration needs
to be reused or selected programmatically. Use `OpticalSizeValue.Responsive` for
shortest-dimension-dependent blur or depth.

`GlassOptics()` creates fixed optics and is not a replacement for `GlassOptics.Adaptive`. Use
`GlassStyle.regular` when selecting the complete built-in Style, or `GlassOptics.regular` when only
the optics value needs to be supplied or copied.

Glass light position is now an intentional source break from pixel `Offset` to semantic
`Alignment`, with no compatibility overload. Use `Alignment.Center` (or omit the write) for the
former `Offset.Unspecified` behavior, logical start/end alignments for directional intent, and
`BiasAlignment` for continuous proportional positions.

Write each property through the Style scope, then pass the Style and structural policies to
`hazeGlass`:

```kotlin
val glassStyle = GlassStyle {
  tint(Color.White.copy(alpha = 0.12f))
  optics(
    GlassOptics(
      refractionStrength = 0.7f,
      depth = OpticalSizeValue.Fixed(0.4f),
    ),
  )
  specularIntensity(0.4f)
  edgeSoftness(12.dp)
  pressed { scale(0.98f) }
  interactionLightRadiusFraction(0.7f)
  interactionPositionAnimationSpec(spring())
}

Modifier.hazeGlass(
  input = HazeInput.Backdrop(hazeState),
  style = glassStyle,
  performanceMode = HazePerformanceMode.Default,
  expandLayerBounds = true,
  interactionSource = interactionSource,
  interactionTransformTarget = GlassTransformTarget.MaterialOnly,
  interactionTransformPivot = GlassTransformPivot.Pointer,
  interactionReducedMotionPolicy = GlassReducedMotionPolicy.System,
)
```

Interaction appearance belongs in the Style and can be shared. Each element still supplies its own
interaction source and behavior options to the modifier.

### Position and geometry

Position handling is now automatic. Source-selection predicates receive library-owned
`HazeSourceMetadata` with only immutable `key` and `zIndex` values. Refine a selection with its
member `HazeSourceSelection.where { ... }`; metadata construction, source geometry, and captured
content remain library-owned. Custom renderers work with modifier-relative bounds and draw the
selected input with `drawInput()`.

## Removed compatibility APIs

`VisualEffect`, `VisualEffectContext`, `HazeEffectScope`, `BlurVisualEffect`,
`HazeEffectScope.blurEffect`, and the lambda-based `hazeEffect` overloads are removed. Readable
`HazeBlurStyle` properties, `copy`, destructuring, and mutable runtime interfaces are not
available; use Style blocks and `then`.

## Complete API mapping

| Haze 1 API | Haze 2 API | Notes |
| --- | --- | --- |
| `Modifier.haze(state)` | `Modifier.hazeSource(state)` | The deprecated `haze` alias is removed. |
| `Modifier.hazeChild(...)` | `Modifier.hazeBlur(input = HazeInput.Sources(state), ...)` | Use `HazeInput.Content` for own-content Blur. |
| `Modifier.hazeEffect(state, style = style)` | `Modifier.hazeBlur(input = HazeInput.Sources(state), style = style)` | Blur now has a typed modifier. |
| `HazeEffectScope.blurRadius` | `HazeBlurStyle { blurRadius(...) }` | Style properties are write functions. |
| `HazeEffectScope.tints` | `HazeBlurStyle { colorEffects(...) }` | `HazeTint` is renamed to `HazeColorEffect`. |
| `HazeEffectScope.noiseFactor` | `HazeBlurStyle { noiseFactor(...) }` | Values are canonicalized during resolution. |
| `HazeEffectScope.progressive` | `HazeBlurStyle { progressive(...) }` | `HazeProgressive` remains in the core package. |
| `HazeEffectScope.mask` | `HazeBlurStyle { mask(...) }` | Write `null` to clear an inherited mask. |
| `HazeEffectScope.backgroundColor` | `HazeBlurStyle { backgroundColor(...) }` | `Color.Unspecified` is not an inheritance sentinel in the new Style API. |
| `HazeEffectScope.blurredEdgeTreatment` | `HazeBlurStyle { blurredEdgeTreatment(...) }` | Layer expansion remains structural. |
| `HazeEffectScope.fallbackTint` | `HazeBlurStyle { fallbackColorEffect(...) }` | Pass `null` to explicitly clear an inherited fallback. |
| `HazeEffectScope.alpha` | `HazeBlurStyle { alpha(...) }` | Values are clamped to `0f..1f`. |
| `HazeEffectScope.blurEnabled` | `HazeBlurStyle { blurEnabled(...) }` | State-level Blur enablement is removed. |
| `HazeEffectScope.inputScale` | `Modifier.hazeBlur(performanceMode = ...)` | Map `Default` and `Auto` to `Default`, `None` to `Quality`, or choose `Balanced`, `Performance`, or `Fixed(qualityFraction)` for an explicit Blur profile. |
| `HazeEffectScope.drawContentBehind` | Removed | Custom renderers control their own draw order inside `HazeEffectRenderer.draw`. |
| `HazeEffectScope.clipToAreasBounds` | Removed | Source geometry is internal. Return required modifier-relative bounds from `calculateLayerBounds`. |
| `HazeEffectScope.expandLayerBounds` | `Modifier.hazeBlur(expandLayerBounds = ...)` | Non-null and `true` by default. |
| `HazeEffectScope.forceInvalidateOnPreDraw` | Removed | Haze owns source invalidation. |
| `HazeEffectScope.canDrawArea` | `HazeSourceSelection.where { ... }` | `where` is a selection member; its library-owned `HazeSourceMetadata` exposes immutable `key` and `zIndex`. |
| `HazeEffectScope.retainOutputWhenSourceUnavailable` | `HazeSourceRetention` | Choose `KeepLastFrame` or `ClearWhenUnavailable`. |
| `rememberHazeState(blurEnabled)` | `rememberHazeState()` | Put `blurEnabled(...)` in the Style. |
| `HazeState.blurEnabled` | `HazeBlurStyle { blurEnabled(...) }` | Configure each effect explicitly. |
| `HazeState.contentLayer` | Removed | This was an internal detail of the old single-source model. |
| `HazeState.positionOnScreen` | Removed | Source geometry is internal. |
| `HazeEffectFactoryVisualEffect`, `HazeEffectVisualEffectFactory` | Removed | There is no public replacement. Use `HazeEffectFactory` for custom effects. |
| `HazeArea`, `HazeCoordinates`, `HazeSourceNode`, `HazeEffectNode` | Removed from the public API | Haze owns source capture and modifier nodes. |
| `VisualEffectContext.positionOnScreen` | `HazeEffectDrawScope.modifierBounds` | Custom renderers receive only modifier-relative semantic bounds. |
| `VisualEffectContext.rootBoundsOnScreen` | Removed | Root and window geometry are internal. |
| `VisualEffectContext.visualEffect` | Removed | Custom effects read their own properties directly. |
| `VisualEffect.calculateInputScaleFactor()` | `HazePerformanceMode` for built-in Blur and Glass, `HazeSampling` for custom effects | Built-in effects choose default, named, or fixed performance profiles; generic effects retain sampling. |
| `VisualEffect.requireInvalidation()` | Snapshot state read by `draw` or `calculateLayerBounds` | Haze observes reads in their rendering phase. |
| `VisualEffect`, `VisualEffectContext`, `InteractiveVisualEffect`, `RetainedOutputVisualEffect`, `VisualEffectRendererFactory`, `VisualEffectTransform` | `HazeEffectFactory` and `HazeEffectRenderer` | Haze manages the renderer lifecycle and input. |
| `HazeState.positionStrategy`, `rememberHazeState(positionStrategy)` | Removed | Cross-window position strategy is internal. |
| `HazeDefaults` Blur values | `HazeBlurDefaults` | Blur defaults moved to `haze-blur`. |
| `HazeDefaults.drawContentBehind` | Removed | Custom renderers own draw order; built-ins choose their internal composition. |
| `HazeStyle` | `HazeBlurStyle` | Renamed, moved, and changed to a replayable Style. |
| `HazeTint` | `HazeColorEffect` | Renamed and moved to `dev.chrisbanes.haze.blur`. |
| `dev.chrisbanes.haze.blur.HazeProgressive` | `dev.chrisbanes.haze.HazeProgressive` | The old Blur-package typealias is removed. |
| `HazeProgressive.LinearGradient(..., preferPerformance = ...)` | `Modifier.hazeBlur(performanceMode = ...)` | Choose `Quality` for full resolution or a downsampled `Balanced`/`Performance` profile. |
| `HazeTint.Unspecified` or a `HazeTint` constructor | `HazeColorEffect.tint(...)` or `HazeColorEffect.colorFilter(...)` | Effects are factory-only; use `fallbackColorEffect(null)` to clear a fallback. |
| `HazeDefaults.blurEnabled()` | `HazeBlurDefaults.isBlurEnabledByDefault()` | The platform-aware default-enable query. |
| `Poko` | Removed | This implementation annotation is not part of Haze's supported API. |
| `HazeLogger.d(...)` | Removed | `HazeLogger.enabled` remains the supported logging control. |
| `LocalHazeStyle` | `LocalHazeBlurStyle` | The local contains replayable Style writes. |
| `dev.chrisbanes.haze.materials.*` | `dev.chrisbanes.haze.blur.materials.*` | The artifact is now `haze-blur-materials`. |
| `ExperimentalHazeMaterialsApi` | Removed | Materials APIs no longer require this opt-in. |
| `HazeDialog` | Regular Compose dialogs | Share one `HazeState` between the source and dialog effect. |

## Step-by-step migration

1. Add `haze-blur`, and replace `haze-materials` with `haze-blur-materials` if presets are used.
2. Update imports from `HazeStyle`, `HazeTint`, `LocalHazeStyle`, and the old materials package to
   `HazeBlurStyle`, `HazeColorEffect`, `LocalHazeBlurStyle`, and
   `dev.chrisbanes.haze.blur.materials`.
3. Replace `Modifier.haze(...)` with `Modifier.hazeSource(...)`.
4. Replace Blur-configuring `hazeEffect` blocks with `hazeBlur`, choosing
   `HazeInput.Backdrop(state)` for ordinary built-in surfaces, `HazeInput.Sources(state)` for
   exact captured-source semantics, or `HazeInput.Content` for own-content Blur.
5. Move Blur properties into `HazeBlurStyle { ... }`, changing property assignments into Style
   functions. Use `then` instead of `copy` when customizing a preset.
6. Move built-in Blur input-scale choices to `HazePerformanceMode`: map the previous default and
   automatic selection to `Default`, and full resolution to `Quality`. Remeasure old
   fixed input-pixel fractions before choosing `Fixed(qualityFraction)`. Move retention, source
   selection, and layer expansion to `HazeSourceRetention`, `HazeSourceSelection`, and
   `expandLayerBounds`.
7. Remove `blurEnabled` from `rememberHazeState`; write it in the Style for each effect instead.
8. Replace custom `VisualEffect` implementations with a stateless `HazeEffectFactory` that
   creates one `HazeEffectRenderer` per modifier.

For example:

```kotlin
val style = HazeMaterials.thin().then {
  blurRadius(24.dp)
}

Modifier.hazeBlur(
  input = HazeInput.Sources(
    state = hazeState,
    selection = HazeSourceSelection.Behind
      .where { source -> source.key != "sensitive" },
    retention = HazeSourceRetention.ClearWhenUnavailable,
  ),
  style = style,
  performanceMode = HazePerformanceMode.Quality,
  expandLayerBounds = false,
)
```

## Custom effects and architecture

Custom effects now pair a shareable `HazeEffectFactory<Style>` with a renderer created for each
modifier. Keep configuration in the Style and mutable resources in the renderer.

Custom typed effects use the generic modifier rather than `hazeBlur`:

```kotlin
Modifier.hazeEffect(
  factory = myFactory,
  input = HazeInput.Content,
  style = myStyle,
)
```

See [Custom effects](custom-effects.md) for lifecycle and ownership guidance.

## Performance compared with Haze 1

In a historical comparison of two shared sample interactions, Haze 2 had lower P90 CPU frame
duration than Haze 1:

| Workload | Haze 1 | Haze 2 | Reduction in P90 CPU frame duration |
| --- | ---: | ---: | ---: |
| Images List scrolling | 14.9 ms | 9.3 ms | 37% lower |
| Credit Card dragging | 11.5 ms | 9.8 ms | 15% lower |

These results describe the compared builds, not a guaranteed improvement for every app.
Use the [performance guide](performance.md) to measure and tune your migrated screen.

??? info "Measurement details"

    Android Macrobenchmarks ran on a Pixel 6 with Android 17 at 60 Hz, with 32 iterations
    per workload. The comparison used Haze 1 at `7a2557f1` and Haze 2 at `fc46813e`.
    P90 highlights the slower frames during each interaction; CPU frame duration does not
    directly measure GPU shader time. The original summary did not record the run date,
    build variant, or run order.

## Getting help

- Read the [Blur usage guide](blur/usage.md).
- See the [sample applications](https://github.com/chrisbanes/haze/tree/main/sample).
- Ask in [GitHub Discussions](https://github.com/chrisbanes/haze/discussions).
- Report reproducible problems in [GitHub Issues](https://github.com/chrisbanes/haze/issues).
