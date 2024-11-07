Haze is a library providing a 'glassmorphism' style blur for Compose.

![type:video](./media/desktop-small.mp4)

Haze is built with [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/), meaning that it supports different platforms:

| Platform      | Supported        |
|---------------|------------------|
| Android       | ✅               |
| Desktop (JVM) | ✅               |
| iOS           | ✅               |
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
    implementation("dev.chrisbanes.haze:haze:<version>")
}
```

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
[snap]: https://oss.sonatype.org/content/repositories/snapshots/dev/chrisbanes/haze/
