# Blur material presets

The `haze-blur-materials` artifact provides ready-made Blur Styles in Haze, Cupertino, and Fluent
designs:

```kotlin
dependencies {
  implementation("dev.chrisbanes.haze:haze-blur-materials:<version>")
}
```

For a Style that follows your Material 3 theme, see the [Material 3 integration](material3.md).

## Material

`HazeMaterials` provides `ultraThin()`, `thin()`, `regular()`, `thick()`, and `ultraThick()`:

```kotlin
Modifier.hazeBlur(
  input = HazeInput.Backdrop(hazeState),
  style = HazeMaterials.thin(),
)
```

## Cupertino

`CupertinoMaterials` follows Apple platform materials:

```kotlin
Modifier.hazeBlur(
  input = HazeInput.Backdrop(hazeState),
  style = CupertinoMaterials.regular(),
)
```

## Fluent

`FluentMaterials` provides acrylic and Mica presets:

```kotlin
Modifier.hazeBlur(
  input = HazeInput.Backdrop(hazeState),
  style = FluentMaterials.acrylicDefault(),
)
```

## Customizing a preset

Presets are ordinary Styles. Use `then` to override individual properties:

```kotlin
val compact = HazeMaterials.thin().then {
  blurRadius(12.dp)
  noiseFactor(0f)
}
```

The result can be shared between modifiers.
