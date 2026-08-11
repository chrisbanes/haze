// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource

@Composable
@OptIn(ExperimentalHazeApi::class)
fun CreditCardSample(
  navController: NavHostController,
  effect: SampleEffect = SampleEffect.Blur,
) {
  val glassBackgroundColor = MaterialTheme.colorScheme.surface
  CreditCardScene(onNavigateUp = navController::navigateUp) { hazeState, modifier, shape, zIndex ->
    Box(
      modifier = modifier
        .hazeSource(hazeState, zIndex = zIndex)
        .then(
          when (effect) {
            SampleEffect.Blur ->
              Modifier
                .clip(shape)
                .hazeBlur(
                  input = HazeInput.Sources(hazeState),
                  style = HazeBlurStyle {
                    backgroundColor(Color.Black)
                    colorEffects(listOf(HazeColorEffect.tint(Color.Yellow.copy(alpha = 0.4f))))
                    blurRadius(8.dp)
                  },
                )

            SampleEffect.Glass ->
              Modifier
                .hazeGlass(
                  input = HazeInput.Sources(hazeState),
                  style = GlassStyle {
                    backgroundColor(glassBackgroundColor)
                    tint(Color.Yellow.copy(alpha = 0.18f))
                    shape(shape)
                    optics(GlassOptics.Adaptive)
                  },
                )
                .clip(shape)
          },
        ),
    ) {
      DefaultCreditCardContents()
    }
  }
}
