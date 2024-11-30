
## What's the difference between this and Modifier.blur?

On Android, [Modifier.blur](https://developer.android.com/reference/kotlin/androidx/compose/ui/Modifier#(androidx.compose.ui.Modifier).blur(androidx.compose.ui.unit.Dp,androidx.compose.ui.unit.Dp,androidx.compose.ui.draw.BlurredEdgeTreatment)) and Haze both use the same concepts underneath, and enable blurring when running on Android 12 devices (or newer). There are some key differences though. The obvious one is multiplatform support.

Ignoring that though...

- Haze allows selective blurring based on the rectangles provided to it. This is super useful for blurring selective pieces of content (such as any content behind top app bars).
- Haze will automatically apply a tint color to the blurred content, in keeping with the 'glassmorpism' style. This primarily enables content to be displayed over the blurred content, with enough contrast to be visible.
- On older devices (API 30 and below), Haze uses a fallback translucent scrim, unlike `blur` which is a no-op.

## Are the blur implementations the same across different platforms?

In v1.0 onwards, all platforms use the same implementation (mostly).

In older versions of Haze (older than v1.0), the Android implementation was always built upon [RenderNode][rendernode] and [RenderEffect][rendereffect]. In Compose 1.7.0, we now have access to new GraphicsLayer APIs, which are similar to [RenderNode][rendernode], but available in `commonMain` in Compose Multiplatform.

The migration to [GraphicsLayer][graphicslayer] has resulted in Haze now having a single implementation across all platforms, based on the previous Android implementation. This will help minimize platform differences, and bugs.

It goes further though. In v0.7.x and older, Haze is all 'smoke and mirrors'. It draws all of the blurred areas in the `hazeBackground` layout node. The `hazeContent` nodes just updates the size, shape, etc, which the `hazeBackground` modifier reads, to know where to draw blurred.

With the adoption of [GraphicsLayer][graphicslayer]s, we now have a way to pass 'drawn' content around, meaning that we are no longer bound by the previous restrictions. v1.0 contains a re-written drawing pipeline, where the blurred content is drawn by the `hazeContent`, not the parent. The parent `hazeBackground` is now only responsible for drawing the background content into a graphics layer, and putting it somewhere for the children to access.

This fixes a number of long-known issues on Haze, where all were caused by the fact that the blurred area wasn't drawn by the child.

There are differences in the platform [RenderEffect][rendereffect]s which we use for actual effect though. These are platform specific, and need to use platform APIs, but the way they are written is very similar.

## What versions of Android does Haze work on?

Haze works on all versions of Android, but the effect it uses differs based on the version of Android that it is running on.

Haze utilizes RenderEffects for blurring, which technically only 'work' when running on Android 12 (SDK Level 31) or above. However, Haze by default does not enable blurring on SDK Level 31 due to [some issues](https://github.com/chrisbanes/haze/issues/77) found in testing, therefore only enables blurring on SDK Level 32 and above.

You can override this by setting the `blurEnabled` property in the `hazeContent` block, like so:

```kotlin
Modifier.hazeContent(...) {
    // Enable blur everywhere where it is technically possible...
    blurEnabled = true
}
```

This will enable blurring everywhere where it is technically possible, including Android SDK Level 31, but please testing on devices before releasing with this enabled. The issues that we've seen might be limited to just the emulator, but we're not sure.

 [rendernode]: https://developer.android.com/reference/android/graphics/RenderNode
 [rendereffect]: https://developer.android.com/reference/android/graphics/RenderEffect
 [graphicslayer]: https://duckduckgo.com/?q=graphicslayer+compose&t=osx
