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

## Android SDK 37.2

Haze is built with Android SDK 37.2. Its Android artifacts declare `minCompileSdk=37` and
`minCompileMinorSdk=2`, so projects that consume Haze must compile with SDK 37.2. They declare
`minCompileSdkExtension=0`: SDK 37.2 is a minor SDK version, not an SDK Extension.

To consume Haze, install the
[Android 17 SDK](https://developer.android.com/about/versions/17/setup-sdk) from Android Studio's
**Tools > SDK Manager**, then configure an Android Gradle Plugin (AGP) 9.4.0 project. For a
Kotlin Multiplatform Android library using `com.android.kotlin.multiplatform.library`, configure
its Android target as follows:

```kotlin
kotlin {
    android {
        compileSdk {
            version = release(37) {
                minorApiLevel = 2
            }
        }
    }
}
```

For an Android application or library using `com.android.application` or
`com.android.library`, configure the same SDK version in its `android` block:

```kotlin
android {
    compileSdk {
        version = release(37) {
            minorApiLevel = 2
        }
    }
}
```

AGP 9.4.0 requires Gradle 9.6 or later and JDK 17 or later; see the
[AGP 9.4.0 release notes](https://developer.android.com/build/releases/agp-9-4-0-release-notes).
That is the minimum for a consuming project. Building Haze from source requires JDK 21 because
its build configuration requires a local Java 21 toolchain.

This configuration uses an Android SDK *minor* version, not an
[SDK Extension](https://developer.android.com/guide/sdk-extensions). Do not set `compileSdkExtension`
for SDK 37.2. `minCompileSdk`, `minCompileMinorSdk`, and `minCompileSdkExtension` are library AAR
metadata; configure your app with `compileSdk` instead.

`compileSdk` only controls the APIs available while building. It does not change your app's
`minSdk` or `targetSdk`, and it does not enable Haze's experimental native Backdrop path. Native
Backdrop still requires its [runtime opt-in and supported window](performance.md#backdrop-input).

!!! info "Using Effects"

    Haze v2 uses a modular architecture where effects are provided as separate modules. The core `haze` module provides the infrastructure, while effects like blur are in dedicated modules (e.g., `haze-blur`). If you want to use blur effects, you need the `haze-blur` dependency. The core `haze` module alone provides just the base infrastructure for visual effects.

    See [Architecture](architecture.md) for more details about the effect system.

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
