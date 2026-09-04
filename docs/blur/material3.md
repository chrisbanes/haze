# Material 3 integration

The `haze-blur-material3` artifact adapts the current Compose Material 3 theme into a Blur Style.
It is a theme integration rather than a catalog of opinionated material presets; for those, see
[Blur material presets](materials.md).

```kotlin
dependencies {
  implementation("dev.chrisbanes.haze:haze-blur-material3:<version>")
}
```

`HazeBlurStyle.Material3()` uses `MaterialTheme.colorScheme.surface` as its default container
color. Pass `containerColor` to use a different color. When supplied, its block runs afterward, so
a one-off Style can override that background without adding an implicit color effect:

```kotlin
Modifier.hazeBlur(
  input = HazeInput.Backdrop(hazeState),
  style = HazeBlurStyle.Material3 {
    blurRadius(12.dp)
  },
)
```

Use the returned Style with `LocalHazeBlurStyle` to provide a Material 3 background to a subtree:

```kotlin
CompositionLocalProvider(LocalHazeBlurStyle provides HazeBlurStyle.Material3()) {
  // Blur modifiers in this subtree inherit the Material 3 surface background.
}
```
