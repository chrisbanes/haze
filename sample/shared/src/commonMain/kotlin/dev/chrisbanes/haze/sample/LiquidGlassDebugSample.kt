// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.liquidglass.liquidGlassEffect

@Composable
fun LiquidGlassDebugSample(navController: NavHostController) {
  val hazeState = remember { HazeState() }

  Box(modifier = Modifier.fillMaxSize()) {
    // Background
    Box(
      modifier = Modifier
        .fillMaxSize()
        .hazeSource(state = hazeState)
        .background(
          brush = Brush.verticalGradient(
            colors = listOf(
              Color(0xFF1E88E5), // Bright blue
              Color(0xFF00ACC1), // Cyan
            ),
          ),
        ),
    ) {
      // High contrast text pattern to make refraction visible
      Column(
        modifier = Modifier
          .fillMaxSize()
          .padding(24.dp)
          .align(Alignment.BottomCenter),
        verticalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        repeat(20) { i ->
          Text(
            text = "█████ Line ${i + 1} █████████████████",
            color = Color.White.copy(alpha = 0.3f),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
          )
        }
      }
    }

    // Test cards - each with different settings
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      Text(
        text = "Check console for delegate info. If shader works, you should see OBVIOUS warping on Card 2.",
        color = Color.White,
        fontSize = 14.sp,
      )

      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        // Card 1: Just tint (baseline)
        TestCard(
          hazeState = hazeState,
          title = "1. Tint Only",
          modifier = Modifier.weight(1f),
        ) {
          tint = Color.White.copy(alpha = 0.2f)
          refractionStrength = 0f
          specularIntensity = 0f
          ambientResponse = 0f
          depth = 0f
        }

        // Card 2: Tint + Refraction
        TestCard(
          hazeState = hazeState,
          title = "2. + Refraction",
          modifier = Modifier.weight(1f),
        ) {
          tint = Color.White.copy(alpha = 0.2f)
          refractionStrength = 0.8f // Strong to make it obvious
          specularIntensity = 0f
          ambientResponse = 0f
          depth = 0f
        }

        // Card 3: + Depth/Blur
        TestCard(
          hazeState = hazeState,
          title = "3. + Blur",
          modifier = Modifier.weight(1f),
        ) {
          tint = Color.White.copy(alpha = 0.2f)
          refractionStrength = 0.8f
          specularIntensity = 0f
          ambientResponse = 0f
          depth = 0.6f // Enable depth mixing
        }

        // Card 4: Full effect
        TestCard(
          hazeState = hazeState,
          title = "4. Full",
          modifier = Modifier.weight(1f),
        ) {
          tint = Color.White.copy(alpha = 0.15f)
          refractionStrength = 0.8f
          specularIntensity = 0.8f
          ambientResponse = 0.8f
          depth = 0.6f
          edgeSoftness = 16.dp
        }
      }
    }
  }
}

@Composable
private fun TestCard(
  hazeState: HazeState,
  title: String,
  modifier: Modifier = Modifier,
  config: dev.chrisbanes.haze.liquidglass.LiquidGlassVisualEffect.() -> Unit,
) {
  Card(
    modifier = modifier
      .size(width = 120.dp, height = 160.dp)
      .clip(RoundedCornerShape(12.dp))
      .hazeEffect(state = hazeState) {
        liquidGlassEffect(config)
      },
    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
  ) {
    Box(
      modifier = Modifier.fillMaxSize(),
      contentAlignment = Alignment.Center,
    ) {
      Text(
        text = title,
        color = Color.Black,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
      )
    }
  }
}
