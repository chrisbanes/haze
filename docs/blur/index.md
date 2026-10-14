# Blur Effect

Blur creates a frosted-glass effect over content in your UI. It can blur the content behind a
surface, or a composable's own content.

## Download

[![Maven Central](https://img.shields.io/maven-central/v/dev.chrisbanes.haze/haze-blur)](https://search.maven.org/search?q=g:dev.chrisbanes.haze)

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    // Core library infrastructure
    implementation("dev.chrisbanes.haze:haze:<version>")

    // Blur effect
    implementation("dev.chrisbanes.haze:haze-blur:<version>")

    // Optional: Pre-built blur styles
    implementation("dev.chrisbanes.haze:haze-blur-materials:<version>")
}
```

## Platform Support

Blur is available on every platform Haze supports:

| Platform      | Notes                                                                      |
|---------------|----------------------------------------------------------------------------|
| Android       | Android 6.0 (API 23)+. Blurs by default on Android 12+; a translucent scrim is drawn on Android 11 and below unless you opt in. |
| Desktop (JVM) | Full support.                                                              |
| iOS & macOS   | Full support.                                                              |
| Web (Wasm, JS)| Full support.                                                              |

See [Platforms](platforms.md) for details.

## Basic Usage

Mark the content to blur with `hazeSource`, then apply `Modifier.hazeBlur` to the surface drawn
over it:

```kotlin
val hazeState = rememberHazeState()
val style = HazeMaterials.thin()

Box {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .hazeSource(state = hazeState)
    ) {
        // scrollable content
    }

    TopAppBar(
        modifier = Modifier.hazeBlur(
            input = HazeInput.Backdrop(hazeState),
            style = style,
        ),
    )
}
```

See the [Blur usage](usage.md) guide for more.

## Styling

[HazeBlurStyle](../api/haze-blur/dev.chrisbanes.haze.blur/-haze-blur-style/index.html) controls
how Blur looks:

- **Blur radius**: the strength of the blur.
- **Tint**: a color overlay that improves contrast.
- **Noise**: visual texture.

For ready-made styles, see the [material presets](materials.md). To follow the current Material 3
theme, see the [Material 3 integration](material3.md).

## Performance

Blur can be expensive. See [Performance](../performance.md) for how to measure and tune it.

## Next Steps

- [Blur usage](usage.md): inputs, styles, progressive Blur, and masks
- [Material presets](materials.md): ready-made Blur Styles
- [Material 3 integration](material3.md): a Blur Style from your Material 3 theme
- [Performance](../performance.md): measuring and tuning
