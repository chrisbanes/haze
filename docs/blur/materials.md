# Blur materials

The `haze-blur-materials` artifact provides replayable Blur Styles for common design systems:

```kotlin
dependencies {
  implementation("dev.chrisbanes.haze:haze-blur-materials:<version>")
}
```

## Material

`HazeMaterials` provides `ultraThin()`, `thin()`, `regular()`, `thick()`, and `ultraThick()`:

```kotlin
Modifier.hazeBlur(
  input = HazeInput.Sources(hazeState),
  style = HazeMaterials.thin(),
)
```

## Cupertino

`CupertinoMaterials` follows Apple platform materials:

```kotlin
Modifier.hazeBlur(
  input = HazeInput.Sources(hazeState),
  style = CupertinoMaterials.regular(),
)
```

## Fluent

`FluentMaterials` provides acrylic and Mica presets:

```kotlin
Modifier.hazeBlur(
  input = HazeInput.Sources(hazeState),
  style = FluentMaterials.acrylicDefault(),
)
```

## Customizing a preset

Material presets are ordinary replayable Styles. Chain additional writes without copying readable
fields:

```kotlin
val compact = HazeMaterials.thin().then {
  blurRadius(12.dp)
  noiseFactor(0f)
}
```

The chained Style remains stateless and can be reused by concurrent modifiers.
