Haze is a library providing a 'glassmorpism' style blur for Compose. 

It is built with Compose Multiplatform, meaning that we support multiple platforms:

| Platform      | Supported        |
|---------------|------------------|
| Android       | ✅ (read below)   |
| iOS           | ✅                |
| Desktop (JVM) | ✅                |

### Android

The situation on Android is slighty tricky right now. The main Haze library is built with Compose Multiplatform, which currently uses Compose UI 1.5.x. 

The Android implementation of `Modifier.haze` we have requires some drawing APIs which were recently added in the Compose UI 1.6.0 (alphas). Since we do not have access to those APIs when building with Compose Multiplatform, the Android target in the main library currently displays a translucent scrim, without a blur.

#### Jetpack Compose

If you are **not** building with Compose Multiplatform (i.e. Android only), and can use the latest Jetpack Compose 1.6.0 alphas, we've also published a version of the library specifically targetting Jetpack Compose. This version contains the 'real' blur implementation:

```kotlin
dependencies {
    implementation("dev.chrisbanes.haze:haze-jetpack-compose:<version>")
}
```

The API is exactly the same as it's basically a copy. Once Compose Multiplatform is updated to use Jetpack Compose UI 1.6.0 in the future, this extension library will no longer be required, and eventually removed.

## Download

[![Maven Central](https://img.shields.io/maven-central/v/dev.chrisbanes.haze/haze)](https://search.maven.org/search?q=g:dev.chrisbanes.haze)

```groovy
repositories {
    mavenCentral()
}

dependencies {
    // For Compose Multiplatform
    implementation("dev.chrisbanes.haze:haze:<version>")

    // Or if you're Android only
    implementation("dev.chrisbanes.haze:haze-jetpack-compose:<version>")
}
```

Snapshots of the development version are available in Sonatype's [snapshots repository][snap]. These are updated on every commit.

## License

```
Copyright 2023 Chris Banes

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
