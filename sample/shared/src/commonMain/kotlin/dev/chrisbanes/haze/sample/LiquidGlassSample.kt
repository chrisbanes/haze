// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.liquidglass.liquidGlassEffect
import dev.chrisbanes.haze.rememberHazeState

@Composable
fun LiquidGlassCreditCardSample(navController: NavHostController) {
  val hazeState = rememberHazeState()

  Box {
    // Background content
    Box(
      modifier = Modifier
        .fillMaxSize()
        .hazeSource(state = hazeState),
    ) {
      Spacer(
        Modifier
          .fillMaxSize()
          .background(brush = Brush.linearGradient(colors = listOf(Color.Black, Color.DarkGray))),
      )

      Text(
        text = LorumIspum,
        color = Color.White.copy(alpha = 0.2f),
        modifier = Modifier.padding(24.dp),
      )
    }

    // Card 1

    repeat(3) { index ->
      // Our card
      val reverseIndex = (2 - index)
      val cardOffset = remember { mutableFloatStateOf(0f) }
      val draggableState = rememberDraggableState { cardOffset.value += it }

      Box(
        modifier = Modifier
          .testTag("credit_card_$index")
          .fillMaxWidth(0.7f - (reverseIndex * 0.05f))
          .aspectRatio(16 / 9f)
          .align(Alignment.Center)
          .offset { IntOffset(x = 0, y = reverseIndex * -100) }
          .offset { IntOffset(x = 0, y = cardOffset.value.toInt()) }
          .draggable(
            state = draggableState,
            orientation = Orientation.Vertical,
            onDragStopped = { velocity ->
              animate(
                initialValue = cardOffset.value,
                targetValue = 0f,
                initialVelocity = velocity,
                animationSpec = spring(Spring.DampingRatioLowBouncy, Spring.StiffnessLow),
              ) { value, _ ->
                cardOffset.value = value
              }
            },
          )
          // We add 1 to the zIndex as the background content is zIndex 0f
          .hazeSource(hazeState, zIndex = 1f + index)
          .clip(RoundedCornerShape(16.dp))
          .hazeEffect(state = hazeState) {
            liquidGlassEffect {
              this.refractionStrength = 0.9f
              blurRadius = 0.dp
            }
          },
      ) {
        Column(Modifier.padding(32.dp)) {
          Text("Bank of Haze")
        }
      }
    }

    FloatingActionButton(
      onClick = navController::navigateUp,
      modifier = Modifier
        .windowInsetsPadding(WindowInsets.statusBars)
        .padding(24.dp),
    ) {
      Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = null,
      )
    }
  }
}
