
## What's the difference between this and Modifier.blur?

The [Modifier.blur](https://developer.android.com/reference/kotlin/androidx/compose/ui/Modifier#(androidx.compose.ui.Modifier).blur(androidx.compose.ui.unit.Dp,androidx.compose.ui.unit.Dp,androidx.compose.ui.draw.BlurredEdgeTreatment)) modifier and Haze may sound similar, but what they provide is different.

Haze provides background blurring, meaning that it will blur content behind, not the content itself. This is what you need to implement glass-like effects. Other than that, Haze provides a whole bunch of other features which you can read about on the [Usage](usage.md) page.

## Are the blur implementations the same across different platforms?

In v1.0 onwards, all platforms use the same implementation (mostly).

In older versions of Haze (older than v1.0), the Android implementation was always built upon [RenderNode][rendernode] and [RenderEffect][rendereffect]. In Compose 1.7.0, we now have access to new GraphicsLayer APIs, which are similar to [RenderNode][rendernode], but available in `commonMain` in Compose Multiplatform.

The migration to [GraphicsLayer][graphicslayer] has resulted in Haze now having a single implementation across all platforms. This helps minimize platform differences and bugs. There are differences in the platform [RenderEffect][rendereffect]s which we use for actual effect though. These are platform specific, and need to use platform APIs, but the way they are written is very similar.

## What versions of Android does Haze work on?

Haze works on all versions of Android, but the effect it uses differs based on the version of Android that it is running on.

- When running on Android 12 and above (API Level 31): Haze utilizes [RenderEffects][rendereffect] for blurring, and is enabled by default.
- When running on older versions of Android: Haze v1.6.0 and newer can now use RenderScript to achieve similar blurring as newer platforms. This is not enabled by default. See the [docs here](usage.md#enabling-blur) on how to enable it.
- When blurring is disabled, Haze will use a scrim (translucent overlay).

### RenderScript

The RenderScript implementation, used on devices running Android 11 or older, is new in Haze v1.6.0 and considered experimental. ⚠️

It attempts to bring a consistent blurring experience as provided by newer platforms, but prioritizes performance over staying in sync with content behind. Using RenderScript means that processing a draw frame can take longer than our allocated frame time. To combat this, Haze uses a background thread to process frames. This means that content updates will nearly always be a frame (or more) behind. 

In addition to this, Haze will skip any new content if it is still processing a previous frame. This is to ensure that Haze does not unintentionally create a queue of frames, and potentially overwhelm the device.

To the user the blur effect may look 'laggy' or slow to update. This is the compromise Haze makes.

 [rendernode]: https://developer.android.com/reference/android/graphics/RenderNode
 [rendereffect]: https://developer.android.com/reference/android/graphics/RenderEffect
 [graphicslayer]: https://duckduckgo.com/?q=graphicslayer+compose&t=osx
