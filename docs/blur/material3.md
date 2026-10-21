# Material 3 integration

The `haze-blur-material3` artifact creates a Blur Style from your current Material 3 theme. For
ready-made presets, see [Blur material presets](materials.md).

```kotlin
dependencies {
  implementation("dev.chrisbanes.haze:haze-blur-material3:<version>")
}
```

`HazeBlurStyle.Material3()` uses `MaterialTheme.colorScheme.surface` as the background color. Pass
`containerColor` to use a different color. An optional block runs afterwards, so it can override
any property, including the background:

```kotlin
Modifier.hazeBlur(
  input = HazeInput.Backdrop(hazeState),
  style = HazeBlurStyle.Material3 {
    blurRadius(12.dp)
  },
)
```

To apply it to every Blur in a subtree, provide it through [`LocalHazeBlurStyle`][LocalHazeBlurStyle]:

```kotlin
CompositionLocalProvider(LocalHazeBlurStyle provides HazeBlurStyle.Material3()) {
  // Blur modifiers in this subtree inherit the Material 3 surface background.
}
```

[LocalHazeBlurStyle]: ../api/haze-blur/dev.chrisbanes.haze.blur/-local-haze-blur-style.html
