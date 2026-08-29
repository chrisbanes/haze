# Migrating to Haze 2.0

Haze 2.0 separates structural input policy, shareable Style, and node-owned rendering resources.
The preferred Blur API is now the typed `Modifier.hazeBlur`.

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

An explicit `colorEffects(emptyList())` clears inherited effects. Caller-owned lists are
snapshotted before replay.

### Finalized Blur API

The temporary prerelease adapters are removed in beta01. Replace both former
`HazeBlurStyle(...)` construction forms, `HazeBlurStyle.Unspecified`, and the
`HazeBlurDefaults.style(...)` builder with a replayable `HazeBlurStyle { ... }` program. Start
from `HazeBlurDefaults.style.then { ... }` when extending the defaults.

`HazeColorEffect` is now opaque. Create an effect only with `HazeColorEffect.tint(...)` or
`HazeColorEffect.colorFilter(...)`; its concrete implementations, sentinel, `isSpecified`,
`DefaultBlendMode`, `copy`, and destructuring APIs are not public. `Color.Unspecified` is rejected
by the color tint factory. Use `fallbackColorEffect(null)` to explicitly clear an inherited
fallback effect; `null` is the only fallback-absence value.

`HazeBlurDefaults.tintAlpha`, `HazeBlurDefaults.tint(...)`, and the deprecated
`HazeBlurDefaults.style(...)` builder are removed. The supported default-enable query is
`HazeBlurDefaults.isBlurEnabledByDefault()`.

The former Blur-package `HazeProgressive` typealias is also removed. Import
`dev.chrisbanes.haze.HazeProgressive` directly.

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
  input = HazeInput.Sources(hazeState),
  style = HazeMaterials.thin(),
)
```

Customize a preset with `then`, not `copy`.

## Migrate input and rendering policy

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

`HazePerformanceMode.Default` points to `Adaptive` for built-in Blur and Glass. Use `Adaptive` to pin that policy,
`Quality`, `Balanced`, or `Performance` for its named profiles, or `Fixed(qualityFraction)` for an
explicit normalized trade-off. `Quality` replaces the previous built-in full-resolution choice;
remeasure a previous built-in fixed input-pixel fraction before choosing `Fixed`. `HazeSampling`
remains the generic policy used by custom effects.

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

Invalid Glass values now fail when the Style or `GlassOptics` value is created instead of
being corrected later. Validate or clamp external values before building the Style. See the
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
| effect-owned interaction source, transform target/pivot, and reduced-motion policy | explicit `Modifier.hazeGlass` arguments owned by each node |
| implicit source/content | explicit `HazeInput.Sources` or `HazeInput.Content` |
| `GlassOptics.Absolute` and `GlassOptics.Fixed` | `GlassOptics`; this is an intentional source break with no alias or compatibility bridge |
| `lightPosition(Offset)` and `Offset.Unspecified` | `lightPosition(Alignment)`; omit the write or use `Alignment.Center` for the former automatic center |

Use `optics(...)` for inline fixed values. Keep a `GlassOptics` value when the configuration needs
to be reused or selected programmatically. Use `SizeValue.Responsive` for
shortest-dimension-dependent blur or depth.

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
    refractionStrength = 0.7f,
    depth = 0.4f,
  )
  specularIntensity(0.4f)
  edgeSoftness(12.dp)
  pressed { scale(0.98f) }
  interactionLightRadiusFraction(0.7f)
  interactionPositionAnimationSpec(spring())
}

Modifier.hazeGlass(
  input = HazeInput.Sources(hazeState),
  style = glassStyle,
  performanceMode = HazePerformanceMode.Adaptive,
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
`HazeBlurStyle` properties, `copy`, destructuring, and mutable runtime interfaces remain removed.
The prerelease Blur construction shims and aliases are now removed; use replayable Style blocks
and `then`.

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
| `HazeEffectScope.inputScale` | `Modifier.hazeBlur(performanceMode = ...)` | Map `Default` to `Default`, `Auto` to `Adaptive`, `None` to `Quality`, and choose `Balanced`, `Performance`, or `Fixed(qualityFraction)` for an explicit Blur profile. |
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
| `HazeEffectFactoryVisualEffect`, `HazeEffectVisualEffectFactory` | Removed | Built-ins use capability-specific `@InternalHazeApi` renderer hooks; there is no third-party full-runtime replacement. |
| `HazeArea`, `HazeCoordinates`, `HazeSourceNode`, `HazeEffectNode` | Removed from the public API | Haze owns source capture and modifier nodes. |
| `VisualEffectContext.positionOnScreen` | `HazeEffectDrawScope.modifierBounds` | Custom renderers receive only modifier-relative semantic bounds. |
| `VisualEffectContext.rootBoundsOnScreen` | Removed | Root and window geometry are internal. |
| `VisualEffectContext.visualEffect` | Removed | Custom effects read their own properties directly. |
| `VisualEffect.calculateInputScaleFactor()` | `HazePerformanceMode` for built-in Blur and Glass, `HazeSampling` for custom effects | Built-in effects choose default, adaptive, named, or fixed performance profiles; generic effects retain sampling. |
| `VisualEffect.requireInvalidation()` | Snapshot state read by `draw` or `calculateLayerBounds` | Haze observes reads in their rendering phase. |
| `VisualEffect`, `VisualEffectContext`, `InteractiveVisualEffect`, `RetainedOutputVisualEffect`, `VisualEffectRendererFactory`, `VisualEffectTransform` | `HazeEffectFactory` and `HazeEffectRenderer` | Renderer lifecycle and input are opaque and node-owned. |
| `HazeState.positionStrategy`, `rememberHazeState(positionStrategy)` | Removed | Cross-window position strategy is internal. |
| `HazeDefaults` Blur values | `HazeBlurDefaults` | Blur defaults moved to `haze-blur`. |
| `HazeDefaults.drawContentBehind` | Removed | Custom renderers own draw order; built-ins choose their internal composition. |
| `HazeStyle` | `HazeBlurStyle` | Renamed, moved, and changed to a replayable Style. |
| `HazeTint` | `HazeColorEffect` | Renamed and moved to `dev.chrisbanes.haze.blur`. |
| `dev.chrisbanes.haze.blur.HazeProgressive` | `dev.chrisbanes.haze.HazeProgressive` | The old Blur-package typealias is removed. |
| `HazeProgressive.LinearGradient(..., preferPerformance = ...)` | `Modifier.hazeBlur(performanceMode = ...)` | Choose `Quality` for full resolution or a downsampled `Balanced`/`Performance` tier; `Adaptive` follows Haze's workload policy. |
| `HazeColorEffect.Unspecified` or a concrete `HazeColorEffect` constructor | `HazeColorEffect.tint(...)` or `HazeColorEffect.colorFilter(...)` | Effects are factory-only; use `fallbackColorEffect(null)` to clear a fallback. |
| `HazeBlurDefaults.blurEnabled()` | `HazeBlurDefaults.isBlurEnabledByDefault()` | This remains the platform-aware default-enable query. |
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
   `HazeInput.Sources(state)` or `HazeInput.Content`.
5. Move Blur properties into `HazeBlurStyle { ... }`, changing property assignments into Style
   functions. Use `then` instead of `copy` when customizing a preset.
6. Move built-in Blur input-scale choices to `HazePerformanceMode`: map the previous default to
   `Default`, automatic selection to `Adaptive`, and full resolution to `Quality`. Remeasure old
   fixed input-pixel fractions before choosing `Fixed(qualityFraction)`. Move retention, source
   selection, and layer expansion to `HazeSourceRetention`, `HazeSourceSelection`, and
   `expandLayerBounds`.
7. Remove `blurEnabled` from `rememberHazeState`; write it in the Style for each effect instead.
8. Replace custom `VisualEffect` implementations with a stateless `HazeEffectFactory` and one
   node-owned `HazeEffectRenderer` per modifier.

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

## Getting help

- Read the [Blur usage guide](blur/usage.md).
- See the [sample applications](https://github.com/chrisbanes/haze/tree/main/sample).
- Ask in [GitHub Discussions](https://github.com/chrisbanes/haze/discussions).
- Report reproducible problems in [GitHub Issues](https://github.com/chrisbanes/haze/issues).
