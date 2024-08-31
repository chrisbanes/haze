// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.materials

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint

/**
 * A class which contains functions to build [HazeStyle]s which implement 'material' styles similar
 * to those available on Windows platforms. The values used are taken from the
 * [WinUI 3 Figma](https://www.figma.com/community/file/1159947337437047524) file published by
 * Microsoft.
 *
 * The primary use case for using these is for when aiming for consistency with native UI
 * (i.e. for when mixing Compose Multiplatform content alongside WinUI content).
 */
@ExperimentalHazeMaterialsApi
object FluentMaterials {

  /**
   * A [HazeStyle] which implements a mostly translucent material.
   */
  @ExperimentalHazeMaterialsApi
  @Composable
  @ReadOnlyComposable
  fun thinAcrylic(
    isDark: Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f,
  ): HazeStyle = hazeAcrylicMaterial(
    containerColor = if (isDark) {
      Color(0x545454)
    } else {
      Color(0xD3D3D3)
    },
    fallbackColor = if (isDark) {
      Color(0x545454, 0.64f)
    } else {
      Color(0xD3D3D3, 0.6f)
    },
    isDark = isDark,
    lightTintOpacity = 0f,
    lightLuminosityOpacity = 0.44f,
    darkTintOpacity = 0f,
    darkLuminosityOpacity = 0.64f,
  )

  /**
   * A [HazeStyle] which implements a translucent material used for the most translucent layer with accent color.
   */
  @ExperimentalHazeMaterialsApi
  @Composable
  @ReadOnlyComposable
  fun accentAcrylicBase(
    accentColor: Color = MaterialTheme.colorScheme.primaryContainer,
    isDark: Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f,
  ): HazeStyle = hazeAcrylicMaterial(
    containerColor = accentColor,
    isDark = isDark,
    lightTintOpacity = 0.8f,
    lightLuminosityOpacity = 0.8f,
    darkTintOpacity = 0.8f,
    darkLuminosityOpacity = 0.8f,
  )

  /**
   * A [HazeStyle] which implements a translucent material used for the popup container background with accent color.
   */
  @ExperimentalHazeMaterialsApi
  @Composable
  @ReadOnlyComposable
  fun accentAcrylicDefault(
    accentColor: Color = MaterialTheme.colorScheme.primaryContainer,
    isDark: Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f,
  ): HazeStyle = hazeAcrylicMaterial(
    containerColor = accentColor,
    isDark = isDark,
    lightTintOpacity = 0.8f,
    lightLuminosityOpacity = 0.9f,
    darkTintOpacity = 0.8f,
    darkLuminosityOpacity = 0.8f,
  )

  /**
   * A [HazeStyle] which implements a translucent material used for the most translucent layer.
   */
  @ExperimentalHazeMaterialsApi
  @Composable
  @ReadOnlyComposable
  fun acrylicBase(
    isDark: Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f,
  ): HazeStyle = hazeAcrylicMaterial(
    containerColor = if (isDark) {
      Color(0x202020)
    } else {
      Color(0xF3F3F3)
    },
    fallbackColor = if (isDark) {
      Color(0x1C1C1C)
    } else {
      Color(0xEEEEEE)
    },
    isDark = isDark,
    lightTintOpacity = 0f,
    lightLuminosityOpacity = 0.9f,
    darkTintOpacity = 0.5f,
    darkLuminosityOpacity = 0.96f,
  )

  /**
   * A [HazeStyle] which implements a translucent material used for the popup container background.
   */
  @ExperimentalHazeMaterialsApi
  @Composable
  @ReadOnlyComposable
  fun acrylicDefault(
    isDark: Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f,
  ): HazeStyle = hazeAcrylicMaterial(
    containerColor = if (isDark) {
      Color(0x2C2C2C)
    } else {
      Color(0xFCFCFC)
    },
    fallbackColor = if (isDark) {
      Color(0x2C2C2C)
    } else {
      Color(0xF9F9F9)
    },
    isDark = isDark,
    lightTintOpacity = 0f,
    lightLuminosityOpacity = 0.85f,
    darkTintOpacity = 0.15f,
    darkLuminosityOpacity = 0.96f,
  )

  /**
   * A [HazeStyle] which implements a translucent application background material.
   */
  @ExperimentalHazeMaterialsApi
  @Composable
  @ReadOnlyComposable
  fun mica(
    isDark: Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f,
  ): HazeStyle = hazeMicaMaterial(
    containerColor = if (isDark) {
      Color(0x202020)
    } else {
      Color(0xF3F3F3)
    },
    isDark = isDark,
    lightTintOpacity = 0.5f,
    lightLuminosityOpacity = 1f,
    darkTintOpacity = 0.8f,
    darkLuminosityOpacity = 1f,
  )

  /**
   * A [HazeStyle] which implements a translucent application background material used for the tab experience.
   */
  @ExperimentalHazeMaterialsApi
  @Composable
  @ReadOnlyComposable
  fun micaAlt(
    isDark: Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f,
  ): HazeStyle = hazeMicaMaterial(
    containerColor = if (isDark) {
      Color(0x0A0A0A)
    } else {
      Color(0xDADADA)
    },
    isDark = isDark,
    lightTintOpacity = 0.5f,
    lightLuminosityOpacity = 1f,
    darkTintOpacity = 0.0f,
    darkLuminosityOpacity = 1f,
  )

  @ReadOnlyComposable
  @Composable
  private fun hazeMaterial(
    containerColor: Color = MaterialTheme.colorScheme.surface,
    isDark: Boolean = containerColor.luminance() < 0.5f,
    fallbackColor: Color = containerColor,
    blurRadius: Dp,
    noiseFactor: Float,
    lightTintOpacity: Float,
    lightLuminosityOpacity: Float,
    darkTintOpacity: Float,
    darkLuminosityOpacity: Float,
  ): HazeStyle = HazeStyle(
    blurRadius = blurRadius,
    noiseFactor = noiseFactor,
    backgroundColor = containerColor,
    tints = listOf(
      HazeTint.Color(
        color = containerColor.copy(
          alpha = if (isDark) darkTintOpacity else lightTintOpacity
        ),
        blendMode = BlendMode.Color
      ),
      HazeTint.Color(
        color = containerColor.copy(
          alpha = if (isDark) darkLuminosityOpacity else lightLuminosityOpacity
        ),
        blendMode = BlendMode.Luminosity,
      ),
    ),
    fallbackTint = HazeTint.Color(fallbackColor),
  )

  @ReadOnlyComposable
  @Composable
  private fun hazeAcrylicMaterial(
    containerColor: Color = MaterialTheme.colorScheme.surface,
    fallbackColor: Color = containerColor,
    isDark: Boolean = containerColor.luminance() < 0.5f,
    lightTintOpacity: Float,
    lightLuminosityOpacity: Float,
    darkTintOpacity: Float,
    darkLuminosityOpacity: Float,
  ): HazeStyle = hazeMaterial(
    containerColor = containerColor,
    fallbackColor = fallbackColor,
    isDark = isDark,
    lightTintOpacity = lightTintOpacity,
    lightLuminosityOpacity = lightLuminosityOpacity,
    darkTintOpacity = darkTintOpacity,
    darkLuminosityOpacity = darkLuminosityOpacity,
    blurRadius = 60.dp,
    noiseFactor = 0.02f,
  )

  @ReadOnlyComposable
  @Composable
  private fun hazeMicaMaterial(
    containerColor: Color = MaterialTheme.colorScheme.surface,
    fallbackColor: Color = containerColor,
    isDark: Boolean = containerColor.luminance() < 0.5f,
    lightTintOpacity: Float,
    lightLuminosityOpacity: Float,
    darkTintOpacity: Float,
    darkLuminosityOpacity: Float,
  ): HazeStyle = hazeMaterial(
    containerColor = containerColor,
    fallbackColor = fallbackColor,
    isDark = isDark,
    lightTintOpacity = lightTintOpacity,
    lightLuminosityOpacity = lightLuminosityOpacity,
    darkTintOpacity = darkTintOpacity,
    darkLuminosityOpacity = darkLuminosityOpacity,
    blurRadius = 240.dp,
    noiseFactor = 0f,
  )

}

private fun Color(color: Int, alpha: Float): Color {
  return Color(color).copy(alpha = alpha)
}
