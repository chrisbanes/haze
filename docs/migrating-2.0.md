# Migrating to Haze 2.0

Haze 2.0 introduces a major architectural refactor that improves modularity and extensibility by introducing a pluggable visual effects system. While this is a breaking change, the migration path is straightforward and the core usage patterns remain familiar.

## Overview of Changes

The primary change in v2 is the extraction of blur functionality from the core `haze` module into a separate `haze-blur` module. This is part of a new architecture that introduces the `VisualEffect` interface, allowing Haze to support different types of visual effects beyond just blurring.

**Key Changes:**

- **New module dependency:** Blur functionality now requires the `haze-blur` module
- **API nesting:** All blur properties now require a `blurEffect {}` wrapper
- **Package changes:** Blur-related classes moved to `dev.chrisbanes.haze.blur` package
- **Removed APIs:** `rememberHazeState(blurEnabled)` parameter removed

**What Hasn't Changed:**

- Core modifiers (`hazeSource`, `hazeEffect`) signatures remain the same
- `HazeState` core API unchanged
- Platform support unchanged
- Performance characteristics unchanged
- `HazeEffectScope` properties like `inputScale`, `drawContentBehind`, `canDrawArea` unchanged

## Dependency Changes

### Add the haze-blur Module

In addition to the core `haze` module, you now need to explicitly add the `haze-blur` module to use blur effects:

```kotlin
dependencies {
  implementation("dev.chrisbanes.haze:haze:2.0.0")
  implementation("dev.chrisbanes.haze:haze-blur:2.0.0") // NEW in v2
}
```

### Update Imports

Several blur-related classes have moved to the `dev.chrisbanes.haze.blur` package:

**V1 imports:**
```kotlin
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.LocalHazeStyle
```

**V2 imports:**
```kotlin
import dev.chrisbanes.haze.blur.HazeStyle
import dev.chrisbanes.haze.blur.HazeTint
import dev.chrisbanes.haze.blur.HazeProgressive
import dev.chrisbanes.haze.blur.LocalHazeStyle
import dev.chrisbanes.haze.blur.blurEffect // NEW: extension function
```

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
        tints = listOf(HazeTint(Color.Black.copy(alpha = 0.7f)))
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
    val ultraThin = HazeMaterials.ultraThin()
    Modifier.hazeEffect(state = hazeState) {
      blurEffect {
        style = ultraThin
      }
    }
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
        tints = listOf(HazeTint(Color.Black.copy(alpha = 0.5f)))
        progressive = HazeProgressive.verticalGradient(...)
      }
    }
    ```

## Complete API Mapping

| V1 Location | V2 Location | Notes |
|-------------|-------------|-------|
| `HazeEffectScope.blurRadius` | `BlurVisualEffect.blurRadius` | Inside `blurEffect {}` |
| `HazeEffectScope.tints` | `BlurVisualEffect.tints` | Inside `blurEffect {}` |
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
| `HazeEffectScope.canDrawArea` | `HazeEffectScope.canDrawArea` | **Unchanged** - still on scope |
| `rememberHazeState(blurEnabled)` | *Removed* | Use `blurEffect { blurEnabled = ... }` |
| `dev.chrisbanes.haze.HazeStyle` | `dev.chrisbanes.haze.blur.HazeStyle` | Package change |
| `dev.chrisbanes.haze.HazeTint` | `dev.chrisbanes.haze.blur.HazeTint` | Package change |
| `dev.chrisbanes.haze.HazeProgressive` | `dev.chrisbanes.haze.blur.HazeProgressive` | Package change |
| `dev.chrisbanes.haze.LocalHazeStyle` | `dev.chrisbanes.haze.blur.LocalHazeStyle` | Package change |

## Step-by-Step Migration

1. **Update dependencies** in your `build.gradle.kts`:
   ```kotlin
   implementation("dev.chrisbanes.haze:haze:2.0.0")
   implementation("dev.chrisbanes.haze:haze-blur:2.0.0") // Add this
   ```

2. **Update imports** for blur-related classes:
   - Change `dev.chrisbanes.haze.HazeStyle` → `dev.chrisbanes.haze.blur.HazeStyle`
   - Change `dev.chrisbanes.haze.HazeTint` → `dev.chrisbanes.haze.blur.HazeTint`
   - Change `dev.chrisbanes.haze.HazeProgressive` → `dev.chrisbanes.haze.blur.HazeProgressive`
   - Change `dev.chrisbanes.haze.LocalHazeStyle` → `dev.chrisbanes.haze.blur.LocalHazeStyle`
   - Add `import dev.chrisbanes.haze.blur.blurEffect`

3. **Wrap blur properties** in `blurEffect {}`:
   - Find all `Modifier.hazeEffect { ... }` blocks
   - Wrap blur-related properties in `blurEffect { ... }`
   - Leave `inputScale`, `drawContentBehind`, `clipToAreasBounds`, `canDrawArea` outside the `blurEffect {}` block

4. **Update `rememberHazeState()` calls**:
   - Remove `blurEnabled` parameter if present
   - Move blur enabling logic to `blurEffect { blurEnabled = ... }` if needed

5. **Update Material style usage**:
   - Change `hazeEffect(state, style = ...)` to `hazeEffect(state) { blurEffect { style = ... } }`

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
- Review the [updated usage documentation](usage.md)
- See working examples in the [sample app](https://github.com/chrisbanes/haze/tree/main/sample)
- File an issue on [GitHub](https://github.com/chrisbanes/haze/issues)
