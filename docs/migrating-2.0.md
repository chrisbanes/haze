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

`HazeSampling.Default` preserves Blur's adaptive default. Use `FullResolution`, `Adaptive`, or
`Fixed(scale)` when the policy must be explicit.

## Lifecycle and sharing

A Style can be shared by any number of modifiers. Each modifier creates its own Blur runtime,
delegate, cache, retained layers, platform resources, and adaptive-sampling history. Recomposition
replaces the complete Style on the existing runtime; detachment releases only that node's
resources.

## Other Haze 2 migrations

The typed Blur API does not change the other Haze 2 migrations.

### Glass Style

`GlassStyle` is also a replayable Style. Write each property through its Style scope:

```kotlin
GlassStyle {
  tint(Color.White.copy(alpha = 0.12f))
  optics(
    GlassOptics.Absolute(
      refractionStrength = 0.7f,
      depth = 0.4f,
    ),
  )
  specularIntensity(0.4f)
  edgeSoftness(12.dp)
}
```

### Position and geometry

`HazeArea.positionOnScreen` is replaced by `HazeArea.coordinates`. Read
`coordinates.localPosition` or `coordinates.screenPosition` for the coordinate space you need.
Custom `VisualEffect` implementations use `VisualEffectContext.position`, `rootBounds`,
`positionOf(area)`, and `boundsOf(area)`.

`HazeState.positionStrategy` defaults to `HazePositionStrategy.Auto`. Override it with `Local` or
`Screen` only when the host window arrangement requires a fixed coordinate space.

The `VisualEffect.detach` lifecycle method now receives its attached `VisualEffectContext`.

## Temporary compatibility boundary

`BlurVisualEffect`, `HazeEffectScope.blurEffect`, and legacy lambda-based `hazeEffect` overloads
remain temporarily available so migration can be staged. They are compatibility APIs, not the
recommended 2.0 Blur interface, and are scheduled for contraction separately. The sentinel-based
`HazeBlurStyle(...)` constructors and `HazeBlurDefaults.style(...)` builder are deprecated.
`HazeBlurStyle.copy` and readable patch properties are removed; use replayable Style blocks and
`then`.

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
| `HazeEffectScope.inputScale` | `Modifier.hazeBlur(sampling = ...)` | Map `Default`, `None`, `Auto`, and `Fixed` to `Default`, `FullResolution`, `Adaptive`, and `Fixed`. |
| `HazeEffectScope.drawContentBehind` | `HazeEffectScope.drawContentBehind` | Unchanged on the temporary lambda-based compatibility API. |
| `HazeEffectScope.clipToAreasBounds` | `HazeEffectScope.clipToAreasBounds` | Unchanged on the temporary lambda-based compatibility API. |
| `HazeEffectScope.expandLayerBounds` | `Modifier.hazeBlur(expandLayerBounds = ...)` | Non-null and `true` by default. |
| `HazeEffectScope.forceInvalidateOnPreDraw` | `HazeEffectScope.forceInvalidateOnPreDraw` | Unchanged on the temporary lambda-based compatibility API. |
| `HazeEffectScope.canDrawArea` | `HazeSourceSelection.where { ... }` | Typed selection exposes immutable `key` and `zIndex` metadata. |
| `HazeEffectScope.retainOutputWhenSourceUnavailable` | `HazeSourceRetention` | Choose `KeepLastFrame` or `ClearWhenUnavailable`. |
| `rememberHazeState(blurEnabled)` | `rememberHazeState()` | Put `blurEnabled(...)` in the Style. |
| `HazeState.blurEnabled` | `HazeBlurStyle { blurEnabled(...) }` | Configure each effect explicitly. |
| `HazeState.contentLayer` | Removed | This was an internal detail of the old single-source model. |
| `HazeState.positionOnScreen` | Removed | Use the position-strategy and coordinate APIs instead. |
| `HazeArea.positionOnScreen` | `HazeArea.coordinates.localPosition` or `.screenPosition` | Choose the coordinate space required by the caller. |
| `VisualEffectContext.positionOnScreen` | `VisualEffectContext.position` | Renamed for the selected position strategy. |
| `VisualEffectContext.rootBoundsOnScreen` | `VisualEffectContext.rootBounds` | Renamed. |
| `VisualEffectContext.visualEffect` | Removed | Custom effects read their own properties directly. |
| `VisualEffect.calculateInputScaleFactor()` | Removed | Use sampling and effect-specific policy. |
| `VisualEffect.requireInvalidation()` | Removed | Call `VisualEffectContext.invalidateDraw()`. |
| `VisualEffect.detach()` | `VisualEffect.detach(context)` | Detach receives the attached context. |
| N/A | `HazeState.positionStrategy` | New; defaults to `HazePositionStrategy.Auto`. |
| N/A | `rememberHazeState(positionStrategy)` | New optional state-construction parameter. |
| N/A | `HazeCoordinates` | Stores local and screen positions for each source area. |
| N/A | `VisualEffectContext.positionOf(area)` / `boundsOf(area)` | Preferred geometry helpers for custom effects. |
| `HazeDefaults` Blur values | `HazeBlurDefaults` | Blur defaults moved to `haze-blur`. |
| `HazeDefaults.drawContentBehind` | `HazeEffectScope.drawContentBehind` | Set directly in the temporary `hazeEffect` compatibility block when needed. |
| `HazeStyle` | `HazeBlurStyle` | Renamed, moved, and changed to a replayable Style. |
| `HazeTint` | `HazeColorEffect` | Renamed and moved to `dev.chrisbanes.haze.blur`. |
| `dev.chrisbanes.haze.blur.HazeProgressive` | `dev.chrisbanes.haze.HazeProgressive` | The old Blur-package name remains as a deprecated typealias during the v2 alpha cycle. |
| `LocalHazeStyle` | `LocalHazeBlurStyle` | The local contains replayable Style writes. |
| `dev.chrisbanes.haze.materials.*` | `dev.chrisbanes.haze.blur.materials.*` | The artifact is now `haze-blur-materials`. |
| `ExperimentalHazeMaterialsApi` | Removed | Materials APIs no longer require this opt-in. |
| `HazeDialog` | Regular Compose dialogs | Share one `HazeState` between the source and dialog effect. |

Legacy `HazeEffectScope` properties that are not represented above remain available only through
the temporary lambda-based compatibility API.

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
8. Update custom-effect geometry and `detach` overrides using the mappings above.

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
- `VisualEffect` remains temporarily available for compatibility and built-in adapter plumbing.

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
