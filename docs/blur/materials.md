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

[`HazeMaterials`][HazeMaterials] provides `ultraThin()`, `thin()`, `regular()`, `thick()`, and `ultraThick()`:

```kotlin
Modifier.hazeBlur(
  input = HazeInput.Backdrop(hazeState),
  style = HazeMaterials.thin(),
)
```

## Cupertino

[`CupertinoMaterials`][CupertinoMaterials] follows Apple platform materials:

```kotlin
Modifier.hazeBlur(
  input = HazeInput.Backdrop(hazeState),
  style = CupertinoMaterials.regular(),
)
```

## Fluent

[`FluentMaterials`][FluentMaterials] provides acrylic and Mica presets:

```kotlin
Modifier.hazeBlur(
  input = HazeInput.Backdrop(hazeState),
  style = FluentMaterials.acrylicDefault(),
)
```

## Customizing a preset

Presets are ordinary Styles. Use [`then`][HazeBlurStyle.then] to override individual properties:

```kotlin
val compact = HazeMaterials.thin().then {
  blurRadius(12.dp)
  noiseFactor(0f)
}
```

The result can be shared between modifiers.

[CupertinoMaterials]: ../api/haze-materials/dev.chrisbanes.haze.blur.materials/-cupertino-materials/index.html
[FluentMaterials]: ../api/haze-materials/dev.chrisbanes.haze.blur.materials/-fluent-materials/index.html
[HazeMaterials]: ../api/haze-materials/dev.chrisbanes.haze.blur.materials/-haze-materials/index.html
[HazeBlurStyle.then]: ../api/haze-blur/dev.chrisbanes.haze.blur/-haze-blur-style/then.html
