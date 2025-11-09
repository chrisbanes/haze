// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur.materials

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.blur.HazeStyle
import dev.chrisbanes.haze.blur.HazeTint

/**
 * A class which contains functions to build [dev.chrisbanes.haze.blur.HazeStyle]s which implement 'material' styles similar
 * to those available on Apple platforms. The values used are taken from the
 * [iOS 18 Figma](https://www.figma.com/community/file/1385659531316001292) file published by
 * Apple.
 *
 * The primary use case for using these is for when aiming for consistency with native UI
 * (i.e. for when mixing Compose Multiplatform content alongside SwiftUI content).
 */
public object CupertinoMaterials {

  /**
   * A [dev.chrisbanes.haze.blur.HazeStyle] which implements a mostly translucent material.
   */
  @ExperimentalHazeMaterialsApi
  @Composable
  @ReadOnlyComposable
  public fun ultraThin(
    containerColor: Color = MaterialTheme.colorScheme.surface,
  ): HazeStyle = hazeMaterial(
    containerColor = containerColor,
    lightBackgroundColor = Color(0xFF0D0D0D),
    lightForegroundColor = Color(color = 0xBFBFBF, alpha = 0.44f),
    darkBackgroundColor = Color(0xFF9C9C9C),
    darkForegroundColor = Color(color = 0x252525, alpha = 0.55f),
  )

  /**
   * A [dev.chrisbanes.haze.blur.HazeStyle] which implements a translucent material. More opaque than [ultraThin],
   * more translucent than [regular].
   */
  @ExperimentalHazeMaterialsApi
  @Composable
  @ReadOnlyComposable
  public fun thin(
    containerColor: Color = MaterialTheme.colorScheme.surface,
  ): HazeStyle = hazeMaterial(
    containerColor = containerColor,
    lightBackgroundColor = Color(0xFF333333),
    lightForegroundColor = Color(color = 0xA6A6A6, alpha = 0.7f),
    darkBackgroundColor = Color(0xFF9C9C9C),
    darkForegroundColor = Color(color = 0x252525, alpha = 0.7f),
  )

  /**
   * A [dev.chrisbanes.haze.blur.HazeStyle] which implements a somewhat opaque material. More opaque than [thin],
   * more translucent than [thick].
   */
  @ExperimentalHazeMaterialsApi
  @Composable
  @ReadOnlyComposable
  public fun regular(
    containerColor: Color = MaterialTheme.colorScheme.surface,
  ): HazeStyle = hazeMaterial(
    containerColor = containerColor,
    lightBackgroundColor = Color(0xFF383838),
    lightForegroundColor = Color(color = 0xB3B3B3, alpha = 0.82f),
    darkBackgroundColor = Color(0xFF8C8C8C),
    darkForegroundColor = Color(color = 0x252525, alpha = 0.82f),
  )

  /**
   * A [dev.chrisbanes.haze.blur.HazeStyle] which implements a mostly opaque material. More opaque than [regular].
   */
  @ExperimentalHazeMaterialsApi
  @Composable
  @ReadOnlyComposable
  public fun thick(
    containerColor: Color = MaterialTheme.colorScheme.surface,
  ): HazeStyle = hazeMaterial(
    containerColor = containerColor,
    lightBackgroundColor = Color(0xFF5C5C5C),
    lightForegroundColor = Color(color = 0x999999, alpha = 0.97f),
    darkBackgroundColor = Color(0xFF7C7C7C),
    darkForegroundColor = Color(color = 0x252525, alpha = 0.9f),
  )

  @ReadOnlyComposable
  @Composable
  private fun hazeMaterial(
    containerColor: Color = MaterialTheme.colorScheme.surface,
    isDark: Boolean = containerColor.luminance() < 0.5f,
    lightBackgroundColor: Color,
    lightForegroundColor: Color,
    darkBackgroundColor: Color,
    darkForegroundColor: Color,
  ): HazeStyle = HazeStyle(
    blurRadius = 24.dp,
    backgroundColor = MaterialTheme.colorScheme.surface,
    tints = listOf(
      HazeTint(
        color = if (isDark) darkBackgroundColor else lightBackgroundColor,
        blendMode = if (isDark) BlendMode.Overlay else BlendMode.ColorDodge,
      ),
      HazeTint(color = if (isDark) darkForegroundColor else lightForegroundColor),
    ),
  )
}

private fun Color(color: Int, alpha: Float): Color {
  return Color(color).copy(alpha = alpha)
}
