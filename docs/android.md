The situation on Android is slighty tricky right now. The main Haze library is built with Compose Multiplatform, which currently uses Compose UI 1.5.x. 

The Android implementation of `Modifier.haze` requires some drawing APIs which were recently added in the Compose UI 1.6.0 (alphas). Since we do not have access to those APIs when building with Compose Multiplatform, the Android target in the main library currently displays a translucent scrim, **without a blur**.

## Jetpack Compose

If you are building your app with Jetpack Compose (thus Android only), and can use the latest Jetpack Compose 1.6.0 alphas, we've also published a version of the library specifically targetting Jetpack Compose. This version contains the 'real' blur implementation:

```kotlin
dependencies {
    implementation("dev.chrisbanes.haze:haze-jetpack-compose:<version>")
}
```

The API is exactly the same as it's basically a copy. Once Compose Multiplatform is updated to use Jetpack Compose UI 1.6.0 in the future, this extension library will no longer be required, and eventually removed.