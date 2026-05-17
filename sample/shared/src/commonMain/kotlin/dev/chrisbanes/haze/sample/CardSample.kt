// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import dev.chrisbanes.haze.blur.HazeBlurDefaults
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

@Composable
fun CreditCardSample(
  navController: NavHostController,
  blurEnabled: Boolean = HazeBlurDefaults.blurEnabled(),
) {
  val cardStyle = HazeBlurStyle(
    backgroundColor = Color.Black,
    colorEffects = listOf(HazeColorEffect.tint(Color.Yellow.copy(alpha = 0.4f))),
    blurRadius = 8.dp,
    noiseFactor = HazeBlurDefaults.noiseFactor,
  )

  CreditCardScene(onNavigateUp = navController::navigateUp) { hazeState, modifier, shape, zIndex ->
    Box(
      modifier = modifier
        .hazeSource(hazeState, zIndex = zIndex)
        .clip(shape)
        .hazeEffect(state = hazeState) {
          blurEffect {
            this.blurEnabled = blurEnabled
            style = cardStyle
          }
        },
    ) {
      DefaultCreditCardContents()
    }
  }
}
