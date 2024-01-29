// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.materials

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeStyle

@RequiresOptIn(
  message = "Experimental Haze Materials API",
  level = RequiresOptIn.Level.WARNING,
)
annotation class ExperimentalHazeMaterialsApi

/**
 * A class which contains functions to build [HazeStyle]s which implement 'material-like' styles.
 * It is inspired by the material APIs available in SwiftUI, but it makes no attempt to provide
 * the exact effects provided in iOS.
 *
 * The functions are marked as experimental, as the effects provided are still being tweaked.
 */
object HazeMaterials {

  /**
   * A [HazeStyle] which implements a mostly translucent material.
   */
  @ExperimentalHazeMaterialsApi
  @Composable
  @ReadOnlyComposable
  fun ultraThin(
    containerColor: Color = MaterialTheme.colorScheme.surface,
  ): HazeStyle = hazeMaterial(
    containerColor = containerColor,
    lightAlpha = 0.35f,
    darkAlpha = 0.55f,
  )

  /**
   * A [HazeStyle] which implements a translucent material. More opaque than [ultraThin],
   * more translucent than [regular].
   */
  @ExperimentalHazeMaterialsApi
  @Composable
  @ReadOnlyComposable
  fun thin(
    containerColor: Color = MaterialTheme.colorScheme.surface,
  ): HazeStyle = hazeMaterial(
    containerColor = containerColor,
    lightAlpha = 0.6f,
    darkAlpha = 0.65f,
  )

  /**
   * A [HazeStyle] which implements a somewhat opaque material. More opaque than [thin],
   * more translucent than [thick].
   */
  @ExperimentalHazeMaterialsApi
  @Composable
  @ReadOnlyComposable
  fun regular(
    containerColor: Color = MaterialTheme.colorScheme.surface,
  ): HazeStyle = hazeMaterial(
    containerColor = containerColor,
    lightAlpha = 0.73f,
    darkAlpha = 0.8f,
  )

  /**
   * A [HazeStyle] which implements a mostly opaque material. More opaque than [regular],
   * more translucent than [ultraThick].
   */
  @ExperimentalHazeMaterialsApi
  @Composable
  @ReadOnlyComposable
  fun thick(
    containerColor: Color = MaterialTheme.colorScheme.surface,
  ): HazeStyle = hazeMaterial(
    containerColor = containerColor,
    lightAlpha = 0.83f,
    darkAlpha = 0.9f,
  )

  /**
   * A [HazeStyle] which implements a nearly opaque material.
   */
  @ExperimentalHazeMaterialsApi
  @Composable
  @ReadOnlyComposable
  fun ultraThick(
    containerColor: Color = MaterialTheme.colorScheme.surface,
  ): HazeStyle = hazeMaterial(
    containerColor = containerColor,
    lightAlpha = 0.92f,
    darkAlpha = 0.97f,
  )

  private fun hazeMaterial(
    containerColor: Color,
    lightAlpha: Float,
    darkAlpha: Float,
  ): HazeStyle = HazeStyle(
    blurRadius = 24.dp,
    tint = containerColor.copy(
      alpha = if (containerColor.luminance() >= 0.5) lightAlpha else darkAlpha,
    ),
  )
}
