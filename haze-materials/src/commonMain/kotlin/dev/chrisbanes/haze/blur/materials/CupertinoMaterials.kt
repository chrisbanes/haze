// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze.blur.materials

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.valueBackedHazeBlurStyle

/**
 * A class which contains functions to build [dev.chrisbanes.haze.blur.HazeBlurStyle]s which implement 'material' styles similar
 * to those available on Apple platforms. The values used are taken from the
 * [iOS 18 Figma](https://www.figma.com/community/file/1385659531316001292) file published by
 * Apple.
 *
 * The primary use case for using these is for when aiming for consistency with native UI
 * (i.e. for when mixing Compose Multiplatform content alongside SwiftUI content).
 *
 * When Blur is disabled, each style falls back to a flat frost colour (its foreground tint over the
 * container colour) rather than the blur tint.
 */
public object CupertinoMaterials {

  /**
   * A [dev.chrisbanes.haze.blur.HazeBlurStyle] which implements a mostly translucent material.
   */
  @Composable
  @ReadOnlyComposable
  public fun ultraThin(
    containerColor: Color = MaterialTheme.colorScheme.surface,
  ): HazeBlurStyle = cupertinoMaterialStyle(containerColor, CupertinoPresets.ultraThin)

  /**
   * A [dev.chrisbanes.haze.blur.HazeBlurStyle] which implements a translucent material. More opaque than [ultraThin],
   * more translucent than [regular].
   */
  @Composable
  @ReadOnlyComposable
  public fun thin(
    containerColor: Color = MaterialTheme.colorScheme.surface,
  ): HazeBlurStyle = cupertinoMaterialStyle(containerColor, CupertinoPresets.thin)

  /**
   * A [dev.chrisbanes.haze.blur.HazeBlurStyle] which implements a somewhat opaque material. More opaque than [thin],
   * more translucent than [thick].
   */
  @Composable
  @ReadOnlyComposable
  public fun regular(
    containerColor: Color = MaterialTheme.colorScheme.surface,
  ): HazeBlurStyle = cupertinoMaterialStyle(containerColor, CupertinoPresets.regular)

  /**
   * A [dev.chrisbanes.haze.blur.HazeBlurStyle] which implements a mostly opaque material. More opaque than [regular].
   */
  @Composable
  @ReadOnlyComposable
  public fun thick(
    containerColor: Color = MaterialTheme.colorScheme.surface,
  ): HazeBlurStyle = cupertinoMaterialStyle(containerColor, CupertinoPresets.thick)
}

internal class CupertinoPreset(
  val name: String,
  val lightBackgroundColor: Color,
  val lightForegroundColor: Color,
  val darkBackgroundColor: Color,
  val darkForegroundColor: Color,
)

internal object CupertinoPresets {
  val ultraThin = CupertinoPreset(
    name = "ultraThin",
    lightBackgroundColor = Color(0xFF0D0D0D),
    lightForegroundColor = Color(color = 0xBFBFBF, alpha = 0.44f),
    darkBackgroundColor = Color(0xFF9C9C9C),
    darkForegroundColor = Color(color = 0x252525, alpha = 0.55f),
  )

  val thin = CupertinoPreset(
    name = "thin",
    lightBackgroundColor = Color(0xFF333333),
    lightForegroundColor = Color(color = 0xA6A6A6, alpha = 0.7f),
    darkBackgroundColor = Color(0xFF9C9C9C),
    darkForegroundColor = Color(color = 0x252525, alpha = 0.7f),
  )

  val regular = CupertinoPreset(
    name = "regular",
    lightBackgroundColor = Color(0xFF383838),
    lightForegroundColor = Color(color = 0xB3B3B3, alpha = 0.82f),
    darkBackgroundColor = Color(0xFF8C8C8C),
    darkForegroundColor = Color(color = 0x252525, alpha = 0.82f),
  )

  val thick = CupertinoPreset(
    name = "thick",
    lightBackgroundColor = Color(0xFF5C5C5C),
    lightForegroundColor = Color(color = 0x999999, alpha = 0.97f),
    darkBackgroundColor = Color(0xFF7C7C7C),
    darkForegroundColor = Color(color = 0x252525, alpha = 0.9f),
  )

  val all: List<CupertinoPreset> = listOf(ultraThin, thin, regular, thick)
}

internal fun cupertinoMaterialStyle(
  containerColor: Color,
  preset: CupertinoPreset,
  isDark: Boolean = containerColor.luminance() < 0.5f,
): HazeBlurStyle {
  val lightBackgroundColor = preset.lightBackgroundColor
  val lightForegroundColor = preset.lightForegroundColor
  val darkBackgroundColor = preset.darkBackgroundColor
  val darkForegroundColor = preset.darkForegroundColor
  val (backgroundColor, colorEffects) = cupertinoMaterialValues(
    containerColor = containerColor,
    isDark = isDark,
    lightBackgroundColor = lightBackgroundColor,
    lightForegroundColor = lightForegroundColor,
    darkBackgroundColor = darkBackgroundColor,
    darkForegroundColor = darkForegroundColor,
  )
  val fallbackColorEffect = cupertinoFallbackColorEffect(
    containerColor = containerColor,
    isDark = isDark,
    lightForegroundColor = lightForegroundColor,
    darkForegroundColor = darkForegroundColor,
  )
  return valueBackedHazeBlurStyle(
    "CupertinoMaterials",
    containerColor,
    isDark,
    lightBackgroundColor,
    lightForegroundColor,
    darkBackgroundColor,
    darkForegroundColor,
  ) {
    blurRadius(24.dp)
    backgroundColor(backgroundColor)
    colorEffects(colorEffects)
    fallbackColorEffect(fallbackColorEffect)
  }
}

internal fun cupertinoMaterialValues(
  containerColor: Color,
  isDark: Boolean,
  lightBackgroundColor: Color,
  lightForegroundColor: Color,
  darkBackgroundColor: Color,
  darkForegroundColor: Color,
): Pair<Color, List<HazeColorEffect>> = containerColor to listOf(
  HazeColorEffect.tint(
    color = if (isDark) darkBackgroundColor else lightBackgroundColor,
    blendMode = if (isDark) BlendMode.Overlay else BlendMode.ColorDodge,
  ),
  HazeColorEffect.tint(
    color = if (isDark) darkForegroundColor else lightForegroundColor,
  ),
)

/**
 * The flat scrim drawn when Blur is off: the foreground tint composited over [containerColor].
 * The first entry of the style's colour effects is a blend-mode tint meant for blurred pixels
 * and would not cover the content if used as a scrim.
 */
internal fun cupertinoFallbackColorEffect(
  containerColor: Color,
  isDark: Boolean,
  lightForegroundColor: Color,
  darkForegroundColor: Color,
): HazeColorEffect = HazeColorEffect.tint(
  (if (isDark) darkForegroundColor else lightForegroundColor).compositeOver(containerColor),
)

internal fun Color(color: Int, alpha: Float): Color {
  return Color(color).copy(alpha = alpha)
}
