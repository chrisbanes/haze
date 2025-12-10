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
    implementation("dev.chrisbanes.haze:haze-materials:<version>")
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

The blur effect is applied using the `blurEffect {}` builder within `Modifier.hazeEffect`:

```kotlin
val hazeState = rememberHazeState()

Box {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .hazeSource(state = hazeState)
    ) {
        // scrollable content
    }

    TopAppBar(
        modifier = Modifier
            .hazeEffect(state = hazeState) {
                blurEffect {
                    style = HazeMaterials.thin()
                }
            }
    )
}
```

For more detailed usage patterns, see the [Blur Usage](usage.md) guide.

## Styling

The appearance of the blur effect is controlled through the [HazeStyle](../api/haze-blur/dev.chrisbanes.haze.blur/-haze-style/index.html) class:

- **Blur Radius**: Controls the strength of the blur
- **Tint**: Applies a color overlay for contrast
- **Noise**: Adds visual texture

Pre-built styles are available in the [materials](materials.md) guide.

## Performance

Blur can be a resource-intensive effect. See the [Performance](../performance.md) page for detailed benchmarks and optimization techniques on each platform.

## Next Steps

- [Blur Usage Guide](usage.md) - Comprehensive usage patterns and features
- [Materials](materials.md) - Pre-built blur styles
- [Performance Tips](../performance.md) - Optimization techniques
