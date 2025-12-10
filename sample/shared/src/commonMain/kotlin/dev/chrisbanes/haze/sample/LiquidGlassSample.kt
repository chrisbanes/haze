// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.liquidglass.liquidGlassEffect

@Composable
fun LiquidGlassSample(
  navController: NavHostController,
) {
  val hazeState = remember { HazeState() }

  Box {
    // Background content
    Box(
      modifier = Modifier
        .fillMaxSize()
        .hazeSource(state = hazeState, zIndex = 0f),
    ) {
      Spacer(
        Modifier
          .fillMaxSize()
          .background(brush = Brush.linearGradient(colors = listOf(Color.Blue, Color.Cyan))),
      )

      Text(
        text = LorumIspum,
        color = Color.White.copy(alpha = 0.2f),
        modifier = Modifier.padding(24.dp),
      )
    }

    // Glass card with liquid glass effect
    Box(
      modifier = Modifier
        .fillMaxWidth(0.7f)
        .aspectRatio(16 / 9f)
        .align(Alignment.Center)
        // We add 1 to the zIndex as the background content is zIndex 0f
        .hazeSource(hazeState, zIndex = 1f)
        .clip(RoundedCornerShape(16.dp))
        .hazeEffect(state = hazeState) {
          liquidGlassEffect {
            // Using more aggressive settings to make the effect very obvious
            tint = Color.White.copy(alpha = 0.15f)
            refractionStrength = 0.8f // Much higher - should create obvious distortion
            specularIntensity = 0.8f // Brighter highlights
            depth = 0.6f // More blur mixing
            ambientResponse = 0.8f // Stronger fresnel
            edgeSoftness = 16.dp
          }
        },
    ) {
      Column(Modifier.padding(32.dp)) {
        Text("Bank of Haze", color = Color.Black)
        Spacer(Modifier.weight(1f))
        Text("•••• •••• •••• 1234", color = Color.Black)
      }
    }
  }
}
