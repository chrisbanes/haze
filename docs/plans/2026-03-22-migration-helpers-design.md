# Migration Helpers Design for Haze v2.0

**Date:** 2026-03-22  
**Status:** Approved for implementation  
**Goal:** Reduce friction when migrating from Haze v1.x to v2.0

## Background

Haze v2.0 introduces a major architectural refactor with breaking changes:
- Classes moved from `dev.chrisbanes.haze` to `dev.chrisbanes.haze.blur` package
- Blur properties now require `blurEffect {}` wrapper
- New required `haze-blur` module dependency

This design provides deprecation helpers to ease the migration path.

## Design

### 1. Deprecation Typealiases (Package Renames)

**File:** `haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/MigrationAliases.kt`

Provides typealiases in the old package location that point to new classes:

```kotlin
@Deprecated(
    "Moved to dev.chrisbanes.haze.blur package. Update your imports.",
    ReplaceWith("HazeBlurStyle", "dev.chrisbanes.haze.blur.HazeBlurStyle")
)
typealias HazeStyle = HazeBlurStyle
```

**Typealiases to create:**
- `HazeStyle` → `HazeBlurStyle`
- `HazeTint` → `HazeTint` (same name, different package)
- `HazeProgressive` → `HazeProgressive` (same name, different package)
- `LocalHazeStyle` → `LocalHazeBlurStyle`

**Benefits:**
- IDEs offer automatic import fixes via ReplaceWith
- Code compiles during transition period
- Clear deprecation messages guide users to new locations

### 2. Style Parameter Overload

**File:** `haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/blur/HazeEffectScope.kt`

Provides an overload accepting the `style` parameter directly:

```kotlin
@Deprecated(
    "Style parameter moved to blurEffect {} block. See migration guide.",
    ReplaceWith("Modifier.hazeEffect(state) { blurEffect { this.style = style } }")
)
inline fun Modifier.hazeEffect(
    state: HazeState,
    style: HazeBlurStyle,
    crossinline block: HazeEffectScope.() -> Unit = {}
): Modifier
```

**Benefits:**
- Supports common v1 pattern: `hazeEffect(state, style = HazeMaterials.thin())`
- IDE can auto-convert to new pattern
- Minimal API surface added

### 3. Legacy Property Wrapper (Optional)

**File:** `haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/blur/LegacyBlurScope.kt`

Provides a scope interface for direct property access without `blurEffect {}`:

```kotlin
@Deprecated("Use blurEffect {} block for blur properties")
interface LegacyBlurScope : HazeEffectScope {
    var blurRadius: Dp
    var tints: List<HazeColorEffect>
    var noiseFactor: Float
    var progressive: HazeProgressive?
    var mask: Brush?
    var backgroundColor: Color
}
```

With helper function:

```kotlin
@Deprecated("Migrate to using blurEffect {} block")
inline fun Modifier.hazeEffectLegacy(...)
```

**Benefits:**
- Allows gradual migration of complex configurations
- Power users can defer full migration
- Marked as WARNING level to encourage migration

## Implementation Notes

- All helpers live in `haze-blur` module only
- Core `haze` module remains unchanged
- Deprecation level: WARNING (not ERROR) to allow gradual migration
- ReplaceWith annotations enable IDE auto-fixes
- Documentation references migration guide

## Success Criteria

1. v1 code with typealiases compiles with deprecation warnings
2. IDE can auto-fix import statements via ReplaceWith
3. IDE can auto-convert style parameter usage
4. Migration guide is referenced in all deprecation messages
5. No changes required to core `haze` module

## Files to Create/Modify

1. **New:** `haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/MigrationAliases.kt`
2. **Modify:** `haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/blur/HazeEffectScope.kt` (add overload)
3. **New:** `haze-blur/src/commonMain/kotlin/dev/chrisbanes/haze/blur/LegacyBlurScope.kt` (optional)

## API Compatibility

These helpers are purely additive and deprecated. They:
- Do not break existing v2 APIs
- Do not change existing behavior
- Can be removed in a future version (2.1.0+)
- Provide clear migration paths
