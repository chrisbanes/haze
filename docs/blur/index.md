# Blur Effect

The blur effect creates a glassmorphism-style blur of content in your UI. It's the primary visual effect in Haze and can be applied to both background and foreground content.

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

The blur effect is supported on all platforms with platform-optimized implementations:

| Platform      | Supported | Notes                               |
|---------------|-----------|-------------------------------------|
| Android       | ✅        | API 11+, optimized for API 13+      |
| Desktop (JVM) | ✅        | Skia-based shader implementation    |
| iOS           | ✅        | Skia-based shader implementation    |
| Wasm          | ✅        | Custom shader implementation        |
| JS/Canvas     | ✅        | Canvas filter-based                 |

## Basic Usage

Apply Blur with the typed `Modifier.hazeBlur` API:

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
            input = HazeInput.Sources(hazeState),
            style = style,
        ),
    )
}
```

For more detailed usage patterns, see the [Blur Usage](usage.md) guide.

## Styling

The appearance of the blur effect is controlled through the [HazeBlurStyle](../api/haze-blur/dev.chrisbanes.haze.blur/-haze-blur-style/index.html) class:

- **Blur Radius**: Controls the strength of the blur
- **Tint**: Applies a color overlay for contrast
- **Noise**: Adds visual texture

Opinionated pre-built styles are available in the [material presets](materials.md) guide. To adapt
the current Compose Material 3 theme into a Blur Style, see the [Material 3 integration](material3.md).

## Performance

Blur can be a resource-intensive effect. See the [Performance](../performance.md) page for detailed benchmarks and optimization techniques on each platform.

## Next Steps

- [Blur Usage Guide](usage.md) - Comprehensive usage patterns and features
- [Material presets](materials.md) - Opinionated pre-built Blur Styles
- [Material 3 integration](material3.md) - Adapt a Compose Material 3 theme into a Blur Style
- [Performance Tips](../performance.md) - Optimization techniques
