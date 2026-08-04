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
import dev.chrisbanes.haze.HazeSampling
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

### Temporary Blur construction shims

During the remaining Haze 2 prereleases, the former `HazeBlurStyle(...)` construction forms,
`HazeBlurStyle.Unspecified`, and `HazeBlurDefaults.style(...)` builder remain available as
warning-level deprecations. They are source-migration aids only: code already compiled against the
former `HazeBlurStyle` class is not binary compatible with the replayable Style interface. Every
shim will be removed before Haze 2.0 stable.

The plural construction form translates to guarded Style writes:

```kotlin
val migrated = HazeBlurStyle {
  if (backgroundColor.isSpecified) backgroundColor(backgroundColor)
  if (colorEffects != null) colorEffects(colorEffects)
  if (blurRadius.isSpecified) blurRadius(blurRadius)
  if (!(noiseFactor < 0f)) noiseFactor(noiseFactor)
  if (fallbackColorEffect.isSpecified) fallbackColorEffect(fallbackColorEffect)
}
```

For the singular form, replace the effects line with:

```kotlin
if (colorEffect != null) colorEffects(listOf(colorEffect))
```

`null` omits the color-effects write and reveals a lower-precedence value. A non-null empty plural
list explicitly clears inherited effects, and non-empty caller-owned lists are snapshotted. An
unspecified color, dimension, or fallback effect also omits its write. Negative numeric noise is
the legacy omission sentinel; other values use normal Style resolution, so values above `1f`
clamp and `NaN` fails validation.

Replace `HazeBlurStyle.Unspecified` with `HazeBlurStyle`. Migrate the defaults builder by replaying
the canonical defaults first and guarding its overrides in the same way:

```kotlin
val migrated = HazeBlurDefaults.style.then {
  if (backgroundColor.isSpecified) backgroundColor(backgroundColor)
  if (tint.isSpecified) colorEffects(listOf(tint))
  if (blurRadius.isSpecified) blurRadius(blurRadius)
  if (!(noiseFactor < 0f)) noiseFactor(noiseFactor)
}
```

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
  sampling = HazeSampling.FullResolution,
  expandLayerBounds = false,
)
```

`HazeSampling.Default` points to `Adaptive` for both Blur and Glass. Use `Adaptive` to pin that
policy explicitly, or use `FullResolution` or `Fixed(pixelFraction)` to override it. A fixed value
is a fraction of total input pixels rather than a per-dimension scale, so `Fixed(0.5f)` scales each
dimension by approximately `0.707`.

## Lifecycle and sharing

A Style can be shared by any number of modifiers. Each modifier creates its own Blur runtime,
delegate, cache, retained layers, platform resources, and adaptive-sampling history. Recomposition
replaces the complete Style on the existing runtime; detachment releases only that node's
resources.

## Other Haze 2 migrations

The typed Blur API does not change the other Haze 2 migrations.

### Glass migration

Glass also has a typed modifier and an opaque, replayable `GlassStyle`. Replace the complete Style
through recomposition; do not mutate an effect, use sentinel patches, `copy`, or `clear*` calls.
The `GlassStyle` builder executes once during construction and records immutable, canonicalized
writes. Resolution never reruns the builder, so mutating state captured by an unchanged Style is
inert; construct and supply a replacement Style to reflect new inputs.

Keep one final `GlassStyle` for all platforms. Remove renderer-capability checks, platform-specific
Style variants, and secondary fallback Styles: `hazeGlass` selects its private renderer
automatically, replaying the same Style while limited renderers approximate supported appearance
and omit unsupported optics.

| Legacy | Typed replacement |
| --- | --- |
| `hazeEffect { glassEffect { … } }` | `hazeGlass(input, style, sampling, expandLayerBounds, …)` |
| `GlassVisualEffect` | `GlassStyle` plus `Modifier.hazeGlass` |
| `GlassLighting`, `GlassColor`, `GlassRendering` | property writes inside `GlassStyle { … }` |
| `GlassStyle.Unspecified` | `GlassStyle`, the empty replayable Style |
| mutable effect properties | replace `GlassStyle` through recomposition |
| sentinel patches and `copy` | `GlassStyle { … }`, `then`, and `LocalGlassStyle` |
| interaction mutation and `clear*` | declarative `hovered`, `focused`, and `pressed` blocks; omit blocks in a replacement Style |
| `Modifier.hazeGlass(interactionLightRadiusFraction = value)` | `GlassStyle { interactionLightRadiusFraction(value) }` |
| `Modifier.hazeGlass(interactionPositionAnimationSpec = spec)` | `GlassStyle { interactionPositionAnimationSpec(spec) }` |
| `GlassDefaults.hoverAnimationSpec`, `pressAnimationSpec`, `releaseAnimationSpec` | explicit `animate(toSpec, fromSpec) { … }` declarations |
| consumer implementation of `GlassInteractionScope` | removed; the sealed receiver is implemented by Haze and used only as the interaction-response DSL |
| `GlassStyleConfiguration`, `GlassRenderer`, `GlassRendererCache`, retained-output methods, delegate and lifecycle hooks | no public replacement; each `hazeGlass` node owns and disposes these internal resources |
| effect-owned hover, focus, press, light-radius, and light-position animation presentation | property writes inside `GlassStyle { … }` |
| effect-owned interaction source, transform target/pivot, and reduced-motion policy | explicit `Modifier.hazeGlass` arguments owned by each node |
| implicit source/content | explicit `HazeInput.Sources` or `HazeInput.Content` |
| raw optical displacement/caps | semantic `GlassOptics` and `Dp` controls |
| `GlassOptics.Absolute` | `GlassOptics.Fixed`; this is a hard rename with no alias or compatibility bridge |
| `lightPosition(Offset)` and `Offset.Unspecified` | `lightPosition(Alignment)`; omit the write or use `Alignment.Center` for the former automatic center |

For ordinary inline fixed Style authoring, pass the complete fixed parameter set directly to
`optics(...)`. The complete-value overload remains available for `GlassOptics.Adaptive`, reusable
fixed values, copies, storage, and programmatic selection.

Glass light position is now an intentional source break from pixel `Offset` to semantic
`Alignment`, with no compatibility overload. The Alignment is resolved inside each node's current
measured bounds and layout direction. Use `Alignment.Center` (or omit the write) for the former
`Offset.Unspecified` behavior, logical start/end alignments for directional intent, and
`BiasAlignment` for continuous proportional positions:

```kotlin
val normalizedX = 0.7f
val normalizedY = 0.2f
val style = GlassStyle {
  lightPosition(
    BiasAlignment(
      horizontalBias = normalizedX * 2f - 1f,
      verticalBias = normalizedY * 2f - 1f,
    ),
  )
}
```

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
  sampling = HazeSampling.Adaptive,
  expandLayerBounds = true,
  interactionSource = interactionSource,
  interactionTransformTarget = GlassTransformTarget.MaterialOnly,
  interactionTransformPivot = GlassTransformPivot.Pointer,
  interactionReducedMotionPolicy = GlassReducedMotionPolicy.System,
)
```

Interaction presentation composes with the rest of `GlassStyle` and can be shared. The modifier
arguments above remain per-node mechanics: sharing a Style never shares signals, geometry,
animation state, controllers, renderers, retained layers, or platform resources. Replacing a Style
updates presentation on the existing renderer; replacing mechanics updates only that modifier
node.

### Position and geometry

Source areas, coordinates, captured layers, windows, and position strategy are no longer public.
Custom renderers receive semantic modifier bounds through `HazeEffectDrawScope` and
`HazeEffectLayoutScope`, and draw the selected input with `drawInput()`.

Source-selection predicates receive only `HazeSourceInfo.key` and `zIndex`. Cross-window coordinate
selection is handled internally by Haze.

## Removed compatibility APIs

`VisualEffect`, `VisualEffectContext`, `HazeEffectScope`, `BlurVisualEffect`,
`HazeEffectScope.blurEffect`, and the lambda-based `hazeEffect` overloads are removed. Readable
`HazeBlurStyle` properties, `copy`, destructuring, and mutable runtime interfaces remain removed.
Only the temporary source construction shims described above survive during the prerelease cycle;
use replayable Style blocks and `then` before Haze 2.0 stable.

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
| `HazeEffectScope.fallbackTint` | `HazeBlurStyle { fallbackColorEffect(...) }` | The effect type is unchanged. |
| `HazeEffectScope.alpha` | `HazeBlurStyle { alpha(...) }` | Values are clamped to `0f..1f`. |
| `HazeEffectScope.blurEnabled` | `HazeBlurStyle { blurEnabled(...) }` | State-level Blur enablement is removed. |
| `HazeEffectScope.inputScale` | `Modifier.hazeBlur(sampling = ...)` | Map `Default` to `Default`, `Auto` to `Adaptive`, `None` to `FullResolution`, and `Fixed` to `Fixed`; fixed values now express total pixel fraction. |
| `HazeEffectScope.drawContentBehind` | Removed | Custom renderers control their own draw order inside `HazeEffectRenderer.draw`. |
| `HazeEffectScope.clipToAreasBounds` | Removed | Source geometry is internal. Return required modifier-relative bounds from `calculateLayerBounds`. |
| `HazeEffectScope.expandLayerBounds` | `Modifier.hazeBlur(expandLayerBounds = ...)` | Non-null and `true` by default. |
| `HazeEffectScope.forceInvalidateOnPreDraw` | Removed | Haze owns source invalidation. |
| `HazeEffectScope.canDrawArea` | `HazeSourceSelection.where { ... }` | Typed selection exposes immutable `key` and `zIndex` metadata. |
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
| `VisualEffect.calculateInputScaleFactor()` | `HazeSampling` | Choose default, adaptive, full-resolution, or fixed sampling. |
| `VisualEffect.requireInvalidation()` | Snapshot state read by `draw` or `calculateLayerBounds` | Haze observes reads in their rendering phase. |
| `VisualEffect`, `VisualEffectContext`, `InteractiveVisualEffect`, `RetainedOutputVisualEffect`, `VisualEffectRendererFactory`, `VisualEffectTransform` | `HazeEffectFactory` and `HazeEffectRenderer` | Renderer lifecycle and input are opaque and node-owned. |
| `HazeState.positionStrategy`, `rememberHazeState(positionStrategy)` | Removed | Cross-window position strategy is internal. |
| `HazeDefaults` Blur values | `HazeBlurDefaults` | Blur defaults moved to `haze-blur`. |
| `HazeDefaults.drawContentBehind` | Removed | Custom renderers own draw order; built-ins choose their internal composition. |
| `HazeStyle` | `HazeBlurStyle` | Renamed, moved, and changed to a replayable Style. |
| `HazeTint` | `HazeColorEffect` | Renamed and moved to `dev.chrisbanes.haze.blur`. |
| `dev.chrisbanes.haze.blur.HazeProgressive` | `dev.chrisbanes.haze.HazeProgressive` | The old Blur-package name remains as a deprecated typealias during the v2 alpha cycle. |
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
6. Move input scale, retention, source selection, and layer expansion to `HazeSampling`,
   `HazeSourceRetention`, `HazeSourceSelection`, and `expandLayerBounds`.
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
  sampling = HazeSampling.FullResolution,
  expandLayerBounds = false,
)
```

## Custom effects and architecture

Haze 2 separates typed, shareable configuration from node-owned rendering resources:

- `HazeEffectFactory<Style>` is stateless and may be shared.
- Every modifier node creates its own `HazeEffectRenderer<Style>`.
- Replacing a Style reuses that renderer.
- Replacing the factory or detaching the node disposes its renderer exactly once.
- Source capture, node lifecycle, and built-in capabilities remain internal.

Custom typed effects use the generic modifier rather than `hazeBlur`:

```kotlin
Modifier.hazeEffect(
  factory = myFactory,
  input = HazeInput.Content,
  style = myStyle,
)
```

## Getting help

- Read the [Blur usage guide](blur/usage.md).
- See the [sample applications](https://github.com/chrisbanes/haze/tree/main/sample).
- Ask in [GitHub Discussions](https://github.com/chrisbanes/haze/discussions).
- Report reproducible problems in [GitHub Issues](https://github.com/chrisbanes/haze/issues).
