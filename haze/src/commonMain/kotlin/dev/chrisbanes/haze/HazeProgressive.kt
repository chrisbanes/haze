// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.Easing
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shader
import androidx.compose.ui.graphics.ShaderBrush
import kotlin.jvm.JvmInline

/**
 * Parameters for applying a progressive blur effect.
 */
@Immutable
public sealed interface HazeProgressive {

  /**
   * A linear gradient effect.
   *
   * You may wish to use the convenience builder functions provided in [horizontalGradient] and
   * [verticalGradient] for more common use cases.
   *
   * The [preferPerformance] flag below can be set to tell Haze how to handle the progressive effect
   * in certain situations:
   *
   * * On certain platforms (Android SDK 32), drawing the progressive effect is inefficient.
   *   When [preferPerformance] is set to true, Haze will use a mask when running on those
   *   platforms, which is far more performant.
   *
   * @param easing - The easing function to use when applying the effect. Defaults to a
   * linear easing effect.
   * @param start - Starting position of the gradient. Defaults to [androidx.compose.ui.geometry.Offset.Companion.Zero] which
   * represents the top-left of the drawing area.
   * @param startIntensity - The intensity of the haze effect at the start, in the range `0f`..`1f`.
   * @param end - Ending position of the gradient. Defaults to
   * [androidx.compose.ui.geometry.Offset.Companion.Infinite] which represents the bottom-right of the drawing area.
   * @param endIntensity - The intensity of the haze effect at the end, in the range `0f`..`1f`
   * @param preferPerformance - Whether Haze should prefer performance (when true), or
   * quality (when false). See above for more information.
   */
  public data class LinearGradient(
    public val easing: Easing = EaseIn,
    public val start: Offset = Offset.Companion.Zero,
    public val startIntensity: Float = 0f,
    public val end: Offset = Offset.Companion.Infinite,
    public val endIntensity: Float = 1f,
    public val preferPerformance: Boolean = false,
  ) : HazeProgressive

  /**
   * A radial gradient effect.
   *
   * Platform support:
   * - Skia backed platforms (iOS, Desktop, etc): ✅
   * - Android SDK Level 33+: ✅
   * - Android SDK Level 31-32: Falls back to a mask
   * - Android SDK Level < 31: Falls back to a scrim
   *
   * @param easing - The easing function to use when applying the effect. Defaults to a
   * linear easing effect.
   * @param center Center position of the radial gradient circle. If this is set to
   * [Offset.Companion.Unspecified] then the center of the drawing area is used as the center for
   * the radial gradient. [Float.POSITIVE_INFINITY] can be used for either [Offset.x] or
   * [Offset.y] to indicate the far right or far bottom of the drawing area respectively.
   * @param centerIntensity - The intensity of the haze effect at the [center], in the range `0f`..`1f`.
   * @param radius Radius for the radial gradient. Defaults to positive infinity to indicate
   * the largest radius that can fit within the bounds of the drawing area.
   * @param radiusIntensity - The intensity of the haze effect at the [radius], in the range `0f`..`1f`
   */
  @Poko
  public class RadialGradient(
    public val easing: Easing = EaseIn,
    public val center: Offset = Offset.Companion.Unspecified,
    public val centerIntensity: Float = 1f,
    public val radius: Float = Float.POSITIVE_INFINITY,
    public val radiusIntensity: Float = 0f,
  ) : HazeProgressive

  /**
   * A progressive effect which is derived by using the provided [Brush] as an alpha mask.
   *
   * This allows custom effects driven from a brush. It could be using a bitmap shader, via
   * a [androidx.compose.ui.graphics.ShaderBrush] or something more complex. The RGB values from the brush's pixels will
   * be ignored, only the alpha values are used.
   */
  @JvmInline
  public value class Brush(public val brush: androidx.compose.ui.graphics.Brush) : HazeProgressive

  public companion object {
    /**
     * A vertical gradient effect.
     *
     * @param easing - The easing function to use when applying the effect. Defaults to a
     * linear easing effect.
     * @param startY - Starting x position of the horizontal gradient. Defaults to 0 which
     * represents the top of the drawing area.
     * @param startIntensity - The intensity of the haze effect at the start, in the range `0f`..`1f`.
     * @param endY - Ending x position of the horizontal gradient. Defaults to
     * [Float.POSITIVE_INFINITY] which represents the bottom of the drawing area.
     * @param endIntensity - The intensity of the haze effect at the end, in the range `0f`..`1f`.
     * @param preferPerformance - Whether Haze should prefer performance (when true), or
     * quality (when false). See [HazeProgressive.LinearGradient]'s documentation for more
     * information.
     */
    public fun verticalGradient(
      easing: Easing = EaseIn,
      startY: Float = 0f,
      startIntensity: Float = 0f,
      endY: Float = Float.POSITIVE_INFINITY,
      endIntensity: Float = 1f,
      preferPerformance: Boolean = false,
    ): LinearGradient = LinearGradient(
      easing = easing,
      start = Offset(0f, startY),
      startIntensity = startIntensity,
      end = Offset(0f, endY),
      endIntensity = endIntensity,
      preferPerformance = preferPerformance,
    )

    /**
     * A horizontal gradient effect.
     *
     * @param easing - The easing function to use when applying the effect. Defaults to a
     * linear easing effect.
     * @param startX - Starting x position of the horizontal gradient. Defaults to 0 which
     * represents the left of the drawing area
     * @param startIntensity - The intensity of the haze effect at the start, in the range `0f`..`1f`
     * @param endX - Ending x position of the horizontal gradient. Defaults to
     * [Float.POSITIVE_INFINITY] which represents the right of the drawing area.
     * @param endIntensity - The intensity of the haze effect at the end, in the range `0f`..`1f`.
     * @param preferPerformance - Whether Haze should prefer performance (when true), or
     * quality (when false). See [HazeProgressive.LinearGradient]'s documentation for more
     * information.
     */
    public fun horizontalGradient(
      easing: Easing = EaseIn,
      startX: Float = 0f,
      startIntensity: Float = 0f,
      endX: Float = Float.POSITIVE_INFINITY,
      endIntensity: Float = 1f,
      preferPerformance: Boolean = false,
    ): LinearGradient = LinearGradient(
      easing = easing,
      start = Offset(startX, 0f),
      startIntensity = startIntensity,
      end = Offset(endX, 0f),
      endIntensity = endIntensity,
      preferPerformance = preferPerformance,
    )

    /**
     * Helper function for building a [HazeProgressive.Brush] with a [androidx.compose.ui.graphics.Shader]. The block is
     * provided with the size of the content, allowing you to setup the shader as required.
     */
    public inline fun forShader(
      crossinline block: (Size) -> Shader,
    ): Brush = Brush(
      object : ShaderBrush() {
        override fun createShader(size: Size): Shader = block(size)
      },
    )
  }
}

internal fun HazeProgressive.asBrush(numStops: Int = 20): Brush = when (this) {
  is HazeProgressive.LinearGradient -> asBrush(numStops)
  is HazeProgressive.RadialGradient -> asBrush(numStops)
  is HazeProgressive.Brush -> brush
}

private fun HazeProgressive.LinearGradient.asBrush(numStops: Int = 20): Brush =
  Brush.linearGradient(
    colors = List(numStops) { i ->
      val x = i * 1f / (numStops - 1)
      Color.Magenta.copy(alpha = lerp(startIntensity, endIntensity, easing.transform(x)))
    },
    start = start,
    end = end,
  )

private fun HazeProgressive.RadialGradient.asBrush(numStops: Int = 20): Brush =
  Brush.radialGradient(
    colors = List(numStops) { i ->
      val x = i * 1f / (numStops - 1)
      Color.Magenta.copy(alpha = lerp(centerIntensity, radiusIntensity, easing.transform(x)))
    },
    center = center,
    radius = radius,
  )
