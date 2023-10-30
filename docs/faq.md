
## What's the difference between this and Modifier.blur?

On Android, [Modifier.blur](https://developer.android.com/reference/kotlin/androidx/compose/ui/Modifier#(androidx.compose.ui.Modifier).blur(androidx.compose.ui.unit.Dp,androidx.compose.ui.unit.Dp,androidx.compose.ui.draw.BlurredEdgeTreatment)) and Haze both use the same concepts underneath, and enable blurring when running on Android 12 devices (or newer). There are some key differences though. The obvious one is multiplatform support. 

Ignoring that though...

- Haze allows selective blurring based on the rectangles provided to it. This is super useful for blurring selective pieces of content (such as any content behind top app bars).
- Haze will automatically apply a tint color to the blurred content, in keeping with the 'glassmorpism' style. This primarily enables content to be displayed over the blurred content, with enough contrast to be visible.
- On older devices (API 30 and below), Haze uses a fallback translucent scrim, unlike `blur` which is a no-op.

## Are the blur implementations the same across different platforms?

Broadly speaking, we try to keep the platforms as consistent as possible, but the platforms each have their own API surfaces so what we can do on each is different (easily).

#### Skia backed platforms (iOS and Desktop)

The iOS and Desktop implementations are enabled by using Skia APIs directly, giving us a broad API surface to use. The `Modifier.haze` on these platforms largely mirrors what is documented in this [blog post](https://www.pushing-pixels.org/2022/04/09/shader-based-render-effects-in-compose-desktop-with-skia.html), using the Skia-provided [guassian blur](https://api.skia.org/classSkImageFilters.html#a9cbc8ef4bef80adda33622b229136f90) image filter, and [perlin noise](https://api.skia.org/classSkPerlinNoiseShader.html) shader, brought together in a custom runtime shader (which also applies the tint).

#### Android (Jetpack Compose)

!!! warning "Jetpack Compose"
    Please note, this section refers to the Jetpack Compose implementation. Please read the [Android guide](android.md) if you haven't already.

On Android, we don't have direct access to the Skia APIs, therefore we need to use the APIs which are provided by the Android framework. This means that we have access to [RenderEffect.createBlurEffect](https://developer.android.com/reference/android/graphics/RenderEffect#createBlurEffect(float,%20float,%20android.graphics.RenderEffect,%20android.graphics.Shader.TileMode)) for the blurring, and [createColorFilterEffect](https://developer.android.com/reference/android/graphics/RenderEffect#createColorFilterEffect(android.graphics.ColorFilter,%20android.graphics.RenderEffect)) for the tinting, both of which were added in API 31.

The Android implementation is missing the noise effect, since there is no built-in way to achieve that. We may look to add this later, but it will likely require [runtime shader](https://developer.android.com/reference/android/graphics/RenderEffect#createRuntimeShaderEffect(android.graphics.RuntimeShader,%20java.lang.String)) support, added in API 33.