// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.unit.Dp

/**
 * A [ProvidableCompositionLocal] which provides the default [HazeStyle] for all [hazeChild]
 * layout nodes placed within this composition local's content.
 *
 * There are precedence rules to how each styling property is applied. The order of precedence
 * for each property are as follows:
 *
 *  - Value set in [HazeChildScope], if specified.
 *  - Value set in style provided to [hazeChild] (or [HazeChildScope.style]), if specified.
 *  - Value set in this composition local.
 */
val LocalHazeStyle: ProvidableCompositionLocal<HazeStyle> = compositionLocalOf { HazeStyle.Unspecified }

/**
 * A holder for the style properties used by Haze.
 *
 * Can be set via [haze] and [hazeChild].
 *
 * @property backgroundColor Color to draw behind the blurred content. Ideally should be opaque
 * so that the original content is not visible behind. Typically this would be
 * `MaterialTheme.colorScheme.surface` or similar.
 * @property tints The [HazeTint]s to apply to the blurred content.
 * @property blurRadius Radius of the blur.
 * @property noiseFactor Amount of noise applied to the content, in the range `0f` to `1f`.
 * Anything outside of that range will be clamped.
 * @property fallbackTint The [HazeTint] to use when Haze uses the fallback scrim functionality.
 * In this scenario, the tints provided in [tints] are ignored.
 */
@Immutable
data class HazeStyle(
  val backgroundColor: Color = Color.Unspecified,
  val tints: List<HazeTint> = emptyList(),
  val blurRadius: Dp = Dp.Unspecified,
  val noiseFactor: Float = -1f,
  val fallbackTint: HazeTint = HazeTint.Unspecified,
) {
  constructor(
    backgroundColor: Color = Color.Unspecified,
    tint: HazeTint? = null,
    blurRadius: Dp = Dp.Unspecified,
    noiseFactor: Float = -1f,
    fallbackTint: HazeTint = HazeTint.Unspecified,
  ) : this(
    backgroundColor = backgroundColor,
    tints = listOfNotNull(tint),
    blurRadius = blurRadius,
    noiseFactor = noiseFactor,
    fallbackTint = fallbackTint,
  )

  companion object {
    val Unspecified: HazeStyle = HazeStyle(tints = emptyList())
  }
}

@Stable
data class HazeTint(
  val color: Color,
  val blendMode: BlendMode = BlendMode.SrcOver,
) {
  companion object {
    val Unspecified: HazeTint = HazeTint(Color.Unspecified)
  }

  val isSpecified: Boolean get() = color.isSpecified
}

internal inline fun Float.takeOrElse(block: () -> Float): Float =
  if (this in 0f..1f) this else block()
