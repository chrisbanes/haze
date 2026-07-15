// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze.sample

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.clip
import androidx.navigation.NavHostController
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.glass.glassEffect
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource

@Composable
fun GlassCreditCardSample(navController: NavHostController) {
  CreditCardScene(onNavigateUp = navController::navigateUp) { hazeState, modifier, shape, zIndex ->
    Box(
      modifier = modifier
        .hazeSource(hazeState, zIndex = zIndex)
        .clip(shape)
        .hazeEffect(state = hazeState) {
          glassEffect {
            this.shape = shape as RoundedCornerShape
          }
        },
    ) {
      DefaultCreditCardContents()
    }
  }
}
