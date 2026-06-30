# Migrating to Haze 2.0

Haze 2.0 introduces a major architectural refactor that improves modularity and extensibility by introducing a pluggable visual effects system. While this is a breaking change, the migration path is straightforward and the core usage patterns remain familiar.

## Overview of Changes

The primary change in v2 is the extraction of blur functionality from the core `haze` module into a separate `haze-blur` module. This is part of a new architecture that introduces the `VisualEffect` interface, allowing Haze to support different types of visual effects beyond just blurring.

**Key Changes:**

- **Hard source break:** v1 blur convenience names and root-package aliases are removed in v2.
- **New module dependency:** Blur functionality now requires the `haze-blur` module
- **Materials artifact rename:** Blur material presets are now published as `haze-blur-materials`.
- **API nesting:** All blur properties now require a `blurEffect {}` wrapper
- **Package changes:** Blur APIs moved to `dev.chrisbanes.haze.blur`; blur materials moved to `dev.chrisbanes.haze.blur.materials`.
- **Removed v1 aliases:** `haze`, `hazeChild`, `HazeDefaults`, `HazeStyle`, `HazeTint`, `LocalHazeStyle`, and `HazeDialog` are removed.
- **Liquid glass style grouping:** `LiquidGlassStyle` parameters are grouped into `optics`, `lighting`, `color`, and `rendering`.
- **Removed APIs:** `HazeState.blurEnabled` and the `rememberHazeState(blurEnabled)` parameter removed.
- **Position strategy:** New `HazePositionStrategy` configuration on `HazeState`
- **Geometry changes:** `HazeArea.positionOnScreen` is replaced by `HazeArea.coordinates`; `VisualEffectContext` now exposes `position`, `rootBounds`, `positionOf(area)`, and `boundsOf(area)`.

**What Hasn't Changed:**

- `hazeSource` remains in the core module
- `hazeEffect` remains in the core module, but blur-specific style parameters moved into `blurEffect {}`.
- Platform support unchanged
- Performance characteristics unchanged
- `HazeEffectScope` properties like `inputScale`, `drawContentBehind`, `canDrawArea` unchanged

## Dependency Changes

### Add the haze-blur Module

In addition to the core `haze` module, you now need to explicitly add the `haze-blur` module to use blur effects:

```kotlin
dependencies {
  implementation("dev.chrisbanes.haze:haze:2.0.0-alpha03")
  implementation("dev.chrisbanes.haze:haze-blur:2.0.0-alpha03") // NEW in v2
}
```

### Rename the Materials Artifact

If you use the pre-built Material, Cupertino, or Fluent blur styles, replace the v1 materials artifact with the renamed v2 artifact:

```kotlin
dependencies {
  // v1
  implementation("dev.chrisbanes.haze:haze-materials:1.7.2")

  // v2
  implementation("dev.chrisbanes.haze:haze-blur-materials:2.0.0-alpha03")
}
```

`haze-materials` is the old 1.x artifact. It does not publish 2.0 versions.

### Update Imports

Several blur-related classes have moved to the `dev.chrisbanes.haze.blur` package:

**V1 imports:**
```kotlin
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.LocalHazeStyle
import dev.chrisbanes.haze.materials.HazeMaterials
```

**V2 imports:**
```kotlin
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.blur.LocalHazeBlurStyle
import dev.chrisbanes.haze.blur.blurEffect // NEW: extension function
import dev.chrisbanes.haze.blur.materials.HazeMaterials
```

Blur-specific defaults from `HazeDefaults` are now in `HazeBlurDefaults`. `HazeDefaults.drawContentBehind` has no direct replacement; leave `drawContentBehind` unset or set it directly on `HazeEffectScope`.

### HazeProgressive moved to core

`HazeProgressive` now lives in `dev.chrisbanes.haze.HazeProgressive` because progressive masks are shared by blur and liquid glass. The old `dev.chrisbanes.haze.blur.HazeProgressive` name remains as a deprecated typealias for source compatibility during the v2 alpha cycle.

## API Migration

### Basic Blur Configuration

All blur-related properties that were previously set directly on `HazeEffectScope` now need to be wrapped in a `blurEffect {}` block.

=== "v1"

    ```kotlin
    Modifier.hazeEffect(state = hazeState) {
      blurRadius = 20.dp
      tints = listOf(HazeTint(Color.Black.copy(alpha = 0.7f)))
      noiseFactor = 0.15f
    }
    ```

=== "v2"

    ```kotlin
    Modifier.hazeEffect(state = hazeState) {
      blurEffect {  // NEW: wrap blur properties
        blurRadius = 20.dp
        colorEffects = listOf(HazeColorEffect.tint(Color.Black.copy(alpha = 0.7f)))
        noiseFactor = 0.15f
      }
    }
    ```

### Using Material Styles

=== "v1"

    ```kotlin
    Modifier.hazeEffect(state = hazeState, style = HazeMaterials.ultraThin())
    ```

=== "v2"

    ```kotlin
    val style = HazeMaterials.ultraThin()

    Modifier.hazeEffect(state = hazeState) {
      blurEffect {
        this.style = style
      }
    }
    ```

### Liquid Glass Style Grouping

Flat `LiquidGlassStyle` construction has been grouped by concept.

```kotlin
// Before
LiquidGlassStyle(
  tint = Color.White.copy(alpha = 0.12f),
  refractionStrength = 0.7f,
  specularIntensity = 0.4f,
  depth = 0.4f,
  edgeSoftness = 12.dp,
)

// After
LiquidGlassStyle(
  tint = Color.White.copy(alpha = 0.12f),
  optics = LiquidGlassOptics(
    refractionStrength = 0.7f,
    depth = 0.4f,
  ),
  lighting = LiquidGlassLighting(
    specularIntensity = 0.4f,
  ),
  rendering = LiquidGlassRendering(
    edgeSoftness = 12.dp,
  ),
)
```

### Progressive Blurs

=== "v1"

    ```kotlin
    Modifier.hazeEffect(state = hazeState) {
      progressive = HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f)
    }
    ```

=== "v2"

    ```kotlin
    Modifier.hazeEffect(state = hazeState) {
      blurEffect {
        progressive = HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f)
      }
    }
    ```

### Masking

=== "v1"

    ```kotlin
    Modifier.hazeEffect(state = hazeState) {
      mask = Brush.verticalGradient(...)
    }
    ```

=== "v2"

    ```kotlin
    Modifier.hazeEffect(state = hazeState) {
      blurEffect {
        mask = Brush.verticalGradient(...)
      }
    }
    ```

### Enabling/Disabling Blur

=== "v1"

    ```kotlin
    // At the state level (REMOVED in v2)
    val hazeState = rememberHazeState(blurEnabled = true)

    // At the effect level
    Modifier.hazeEffect(state = hazeState) {
      blurEnabled = true
    }
    ```

=== "v2"

    ```kotlin
    // At the state level - parameter removed
    val hazeState = rememberHazeState()

    // At the effect level - now inside blurEffect {}
    Modifier.hazeEffect(state = hazeState) {
      blurEffect {
        blurEnabled = true
      }
    }
    ```

### Foreground Blurring

=== "v1"

    ``` kotlin
    Modifier.hazeEffect {
      tints = listOf(HazeTint(Color.Black.copy(alpha = 0.5f)))
      progressive = HazeProgressive.verticalGradient(...)
    }
    ```

=== "v2"

    ``` kotlin
    Modifier.hazeEffect {
      blurEffect {
        colorEffects = listOf(HazeColorEffect.tint(Color.Black.copy(alpha = 0.5f)))
        progressive = HazeProgressive.verticalGradient(...)
      }
    }
    ```

## Complete API Mapping

| V1 Location | V2 Location | Notes |
|-------------|-------------|-------|
| `Modifier.haze(state)` | `Modifier.hazeSource(state)` | `haze` was a deprecated alias in v1 and is removed in v2 |
| `Modifier.hazeChild(...)` | `Modifier.hazeEffect(...)` | `hazeChild` was a deprecated alias in v1 and is removed in v2 |
| `Modifier.hazeEffect(state, style = style)` | `Modifier.hazeEffect(state) { blurEffect { this.style = style } }` | Style parameter removed from the core effect modifier |
| `HazeEffectScope.blurRadius` | `BlurVisualEffect.blurRadius` | Inside `blurEffect {}` |
| `HazeEffectScope.tints` | `BlurVisualEffect.colorEffects` | Inside `blurEffect {}` |
| `HazeEffectScope.noiseFactor` | `BlurVisualEffect.noiseFactor` | Inside `blurEffect {}` |
| `HazeEffectScope.progressive` | `BlurVisualEffect.progressive` | Inside `blurEffect {}` |
| `HazeEffectScope.mask` | `BlurVisualEffect.mask` | Inside `blurEffect {}` |
| `HazeEffectScope.style` | `BlurVisualEffect.style` | Inside `blurEffect {}` |
| `HazeEffectScope.backgroundColor` | `BlurVisualEffect.backgroundColor` | Inside `blurEffect {}` |
| `HazeEffectScope.blurredEdgeTreatment` | `BlurVisualEffect.blurredEdgeTreatment` | Inside `blurEffect {}` |
| `HazeEffectScope.fallbackTint` | `BlurVisualEffect.fallbackTint` | Inside `blurEffect {}` |
| `HazeEffectScope.alpha` | `BlurVisualEffect.alpha` | Inside `blurEffect {}` |
| `HazeEffectScope.blurEnabled` | `BlurVisualEffect.blurEnabled` | Inside `blurEffect {}` |
| `HazeEffectScope.inputScale` | `HazeEffectScope.inputScale` | **Unchanged** - still on scope |
| `HazeEffectScope.drawContentBehind` | `HazeEffectScope.drawContentBehind` | **Unchanged** - still on scope |
| `HazeEffectScope.clipToAreasBounds` | `HazeEffectScope.clipToAreasBounds` | **Unchanged** - still on scope |
| `HazeEffectScope.expandLayerBounds` | `HazeEffectScope.expandLayerBounds` | **Unchanged** - still on scope |
| `HazeEffectScope.forceInvalidateOnPreDraw` | `HazeEffectScope.forceInvalidateOnPreDraw` | **Unchanged** - still on scope |
| `HazeEffectScope.canDrawArea` | `HazeEffectScope.canDrawArea` | **Unchanged** - still on scope |
| `rememberHazeState(blurEnabled)` | *Removed* | Use `blurEffect { blurEnabled = ... }` |
| `HazeState.blurEnabled` | *Removed* | Use `blurEffect { blurEnabled = ... }` on each effect |
| `HazeState.contentLayer` | *Removed* | Internal implementation detail from old single-source model |
| `HazeState.positionOnScreen` | *Removed* | Internal/deprecated geometry property from old model |
| `HazeArea.positionOnScreen` | `HazeArea.coordinates.localPosition` or `HazeArea.coordinates.screenPosition` | Coordinate model now stores both spaces |
| `VisualEffectContext.positionOnScreen` | `VisualEffectContext.position` | Renamed |
| `VisualEffectContext.rootBoundsOnScreen` | `VisualEffectContext.rootBounds` | Renamed |
| `VisualEffectContext.visualEffect` | *Removed* | Custom effects should read their own properties directly |
| `VisualEffect.calculateInputScaleFactor()` | *Removed* | Use `VisualEffect.shouldDrawContentBehind(context)`, `HazeEffectScope.inputScale`, or effect-specific logic as needed |
| `VisualEffect.requireInvalidation()` | *Removed* | Call `VisualEffectContext.invalidateDraw()` from `update` or other lifecycle paths |
| `VisualEffect.detach()` | `VisualEffect.detach(context)` | Detach now receives the attached context |
| *N/A* | `HazeState.positionStrategy` | New — configurable position calculation |
| *N/A* | `rememberHazeState(positionStrategy)` | New parameter, defaults to `Auto` |
| *N/A* | `HazeCoordinates` | New value storing local and screen positions for each area |
| *N/A* | `VisualEffectContext.positionOf(area)` / `boundsOf(area)` | Preferred helpers for custom effects |
| `dev.chrisbanes.haze.HazeDefaults` blur defaults | `dev.chrisbanes.haze.blur.HazeBlurDefaults` | Blur-specific defaults moved to the blur module |
| `dev.chrisbanes.haze.HazeDefaults.drawContentBehind` | `HazeEffectScope.drawContentBehind` | Set directly in the `hazeEffect` block if needed |
| `dev.chrisbanes.haze.HazeStyle` | `dev.chrisbanes.haze.blur.HazeBlurStyle` | Renamed + package change |
| `dev.chrisbanes.haze.HazeTint` | `dev.chrisbanes.haze.blur.HazeColorEffect` | Renamed + package change |
| `dev.chrisbanes.haze.HazeProgressive` | `dev.chrisbanes.haze.HazeProgressive` | Unchanged core location; `dev.chrisbanes.haze.blur.HazeProgressive` remains as a deprecated typealias during the v2 alpha cycle |
| `dev.chrisbanes.haze.LocalHazeStyle` | `dev.chrisbanes.haze.blur.LocalHazeBlurStyle` | Renamed + package change |
| `dev.chrisbanes.haze.materials.*` | `dev.chrisbanes.haze.blur.materials.*` | Package moved; artifact also renamed to `haze-blur-materials` |
| `dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi` | *Removed* | Materials APIs are no longer annotated with this opt-in |
| `HazeDialog` | *Removed* | Use regular Compose dialogs with `hazeSource` / `hazeEffect` where needed |

## Step-by-Step Migration

**Update dependencies** in your `build.gradle.kts`:

   ```kotlin
    implementation("dev.chrisbanes.haze:haze:2.0.0-alpha03")
    implementation("dev.chrisbanes.haze:haze-blur:2.0.0-alpha03") // Add this for blur effects
    implementation("dev.chrisbanes.haze:haze-blur-materials:2.0.0-alpha03") // Add this for material presets
   ```

**Update imports** for blur-related classes:

  - Change blur-specific `dev.chrisbanes.haze.HazeDefaults` usage → `dev.chrisbanes.haze.blur.HazeBlurDefaults`
  - Change `HazeDefaults.drawContentBehind` usage → direct `drawContentBehind` assignment in the `hazeEffect` block
  - Change `dev.chrisbanes.haze.HazeStyle` → `dev.chrisbanes.haze.blur.HazeBlurStyle`
  - Change `dev.chrisbanes.haze.HazeTint` → `dev.chrisbanes.haze.blur.HazeColorEffect`
  - Keep `dev.chrisbanes.haze.HazeProgressive`; `dev.chrisbanes.haze.blur.HazeProgressive` remains as a deprecated typealias during the v2 alpha cycle
  - Change `dev.chrisbanes.haze.LocalHazeStyle` → `dev.chrisbanes.haze.blur.LocalHazeBlurStyle`
  - Change `dev.chrisbanes.haze.materials.HazeMaterials` → `dev.chrisbanes.haze.blur.materials.HazeMaterials`
  - Add `import dev.chrisbanes.haze.blur.blurEffect`

**Wrap blur properties** in `blurEffect {}`:

   - Find all `Modifier.hazeEffect { ... }` blocks
   - Wrap blur-related properties in `blurEffect { ... }`
   - Leave `inputScale`, `drawContentBehind`, `clipToAreasBounds`, `canDrawArea` outside the `blurEffect {}` block

**Update `rememberHazeState()` calls**:

   - Remove `blurEnabled` parameter if present
   - Move blur enabling logic to `blurEffect { blurEnabled = ... }` if needed

**Update Material style usage**:

   - Change `hazeEffect(state, style = style)` to `val style = ...` followed by `hazeEffect(state) { blurEffect { this.style = style } }`
   - Change the dependency from `haze-materials` to `haze-blur-materials`

**Replace removed aliases**:

   - Change `Modifier.haze(state)` to `Modifier.hazeSource(state)`
   - Change `Modifier.hazeChild(...)` to `Modifier.hazeEffect(...)`
   - Remove `HazeDialog` usage and compose dialogs directly

## Position Strategy

V2 introduces a configurable position calculation strategy that fixes blur misalignment in split-window modes (e.g. Huawei Parallel Space). In most cases, no action is needed — the default `Auto` strategy handles everything.

**If you use `HazeArea.positionOnScreen`** in custom effects, read the coordinate space you need:

```kotlin
// v1
val pos = area.positionOnScreen

// v2
val localPos = area.coordinates.localPosition
val screenPos = area.coordinates.screenPosition
```

**If you implement custom `VisualEffect`s**, update `VisualEffectContext` references:

```kotlin
// v1
context.positionOnScreen
context.rootBoundsOnScreen

// v2
context.position
context.rootBounds
```

Prefer the context helpers when working with source areas, because they respect `HazePositionStrategy`:

```kotlin
val areaPosition = context.positionOf(area)
val areaBounds = context.boundsOf(area)
```

Also update lifecycle overrides:

```kotlin
// v1
override fun detach() = Unit

// v2
override fun detach(context: VisualEffectContext) = Unit
```

**If you need to force screen coordinates** (e.g. for a custom cross-window setup):

```kotlin
val state = rememberHazeState(positionStrategy = HazePositionStrategy.Screen)
```

## Understanding the New Architecture

The v2 architecture introduces the `VisualEffect` interface, which allows Haze to support different types of visual effects:

- **`VisualEffect`** - Core interface for all visual effects (in `haze` module)
- **`BlurVisualEffect`** - Implementation for blur effects (in `haze-blur` module)
- **`HazeEffectScope.visualEffect`** - Property that holds the current effect

The `blurEffect {}` extension function is a convenience API that creates and configures a `BlurVisualEffect` instance. Under the hood, it sets the `visualEffect` property on `HazeEffectScope`.

This architecture enables:

- Better separation of concerns
- Potential for custom visual effects in the future
- Smaller core module for users who don't need blur
- More maintainable and testable code

## Getting Help

If you encounter issues during migration:

- Check the [GitHub Discussions](https://github.com/chrisbanes/haze/discussions)
- Review the [updated usage documentation](blur/usage.md)
- See working examples in the [sample app](https://github.com/chrisbanes/haze/tree/main/sample)
- File an issue on [GitHub](https://github.com/chrisbanes/haze/issues)
