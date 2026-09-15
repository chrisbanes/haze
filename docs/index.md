Haze is a library providing visual effects (such as blur) for Compose Multiplatform.

![type:video](./media/desktop-small.mp4)

Haze is built with [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/), meaning that it supports different platforms:

| Platform      | Supported        |
|---------------|------------------|
| Android       | ✅               |
| Desktop (JVM) | ✅               |
| iOS           | ✅               |
| macOS         | ✅               |
| Wasm          | ✅               |
| JS/Canvas     | ✅               |

You can also see it in action in the [Tivi app](https://github.com/chrisbanes/tivi):

![type:video](./media/tivi.mp4)

## Download

[![Maven Central](https://img.shields.io/maven-central/v/dev.chrisbanes.haze/haze)](https://search.maven.org/search?q=g:dev.chrisbanes.haze)

``` kotlin
repositories {
    mavenCentral()
}

dependencies {
    // Core library. Usually don't need this...
    implementation("dev.chrisbanes.haze:haze:<version>")
    
    // For blur effects:
    implementation("dev.chrisbanes.haze:haze-blur:<version>")

}
```

!!! info "Using Effects"

    Haze v2 uses a modular architecture where effects are provided as separate modules. The core `haze` module provides the infrastructure, while effects like blur are in dedicated modules (e.g., `haze-blur`). If you want to use blur effects, you need the `haze-blur` dependency. The core `haze` module alone provides just the base infrastructure for visual effects.

    See [Architecture](architecture.md) for more details about the effect system.

## Why Haze

Build Blur and Glass surfaces around the content that belongs behind them. Haze lets a screen keep
that relationship as its layout changes, and make an intentional choice when the content is no
longer there.

### Choose the content behind each surface

For example, a translucent card can use its artwork while excluding a nearby toolbar or overlay,
rather than depending on draw order. The [source selection and retention sample](https://github.com/chrisbanes/haze/blob/main/sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/SourceInputContractsSample.kt)
lets you select the green source while excluding the purple one. The [explicit input contract](core-concepts.md#explicit-inputs)
documents how source selection is expressed.

### Keep surfaces aligned as a layout changes

A moving, scaled, or rotated image should still look like it sits behind its Blur or Glass surface.
The [Layer Transformations sample](https://github.com/chrisbanes/haze/blob/main/sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/LayerTransformations.kt)
demonstrates that relationship with a scaled and rotated source and an animated effect surface.

### Choose continuity or clearing when content disappears

When a source is replaced during navigation or a state change, a surface can keep its last output
for a continuous transition or clear until new content is available. The [source selection and
retention sample](https://github.com/chrisbanes/haze/blob/main/sample/shared/src/commonMain/kotlin/dev/chrisbanes/haze/sample/SourceInputContractsSample.kt)
shows both behaviors; the [input contract](core-concepts.md#explicit-inputs) describes the options.

### Avoid capture work when no effect uses it

Source layers record only while an attached effect needs source input. This demand-driven capture
avoids recording unused sources; see the [explicit input contract](core-concepts.md#explicit-inputs).

## What the evidence covers

The [sample catalogue](https://github.com/chrisbanes/haze/tree/main/sample) contains runnable
examples for these screen-building choices. The [screenshot-test guide](screenshot-tests.md)
documents the checked Blur and Glass `creditCard` matrix: two scenes across Sources and forced
Backdrop fallback, four modes on Desktop and Android host SDKs 28, 32, and 35, plus native
Backdrop cases on SDK 37. It is host coverage, not physical-device coverage.

The [physical-device benchmark reports](benchmark-results.md) let you inspect qualified workload
summaries: CPU frame duration and frame-overrun metrics, devices, builds, conditions, aggregation,
and single-pass limits. The reports include reproduction runbooks, but raw results and traces are
absent, so readers can reproduce the workloads without independently verifying every published
table.

## Acknowledgements

In previous versions, the Skia-backed implementation (used on iOS and Desktop) was heavily influenced by [Kirill Grouchnikov](https://www.pushing-pixels.org)'s explorations on Compose Desktop. He wrote about it in his [Shader based render effects in Compose Desktop with Skia](https://www.pushing-pixels.org/2022/04/09/shader-based-render-effects-in-compose-desktop-with-skia.html) blog post.

The Android implementation was inspired by the techniques documented by [Chet Haase](https://twitter.com/chethaase) and [Nader Jawad](https://twitter.com/nadewad) in the [RenderNode for Bigger, Better Blurs](https://medium.com/androiddevelopers/rendernode-for-bigger-better-blurs-ced9f108c7e2) blog post.

Thank you all.

## License

```
Copyright 2024 Chris Banes

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    https://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

[compose]: https://developer.android.com/jetpack/compose
