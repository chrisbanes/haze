// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.blurEffect
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun LayerTransformations(
  blurEnabled: Boolean = true,
  topOffset: DpOffset = DpOffset.Zero,
) {
  val hazeState = rememberHazeState(blurEnabled)

  Box(
    modifier = Modifier
      .fillMaxSize(),
  ) {
    Spacer(
      modifier = Modifier
        .hazeSource(hazeState)
        .fillMaxSize()
        .background(
          brush = Brush.verticalGradient(
            colors = listOf(Color.Red, Color.Cyan, Color.Blue, Color.Magenta, Color.Red),
          ),
        ),
    )

    AsyncImage(
      model = rememberRandomSampleImageUrl(),
      contentScale = ContentScale.Crop,
      contentDescription = null,
      modifier = Modifier
        .hazeSource(hazeState)
        .graphicsLayer {
          scaleX = 2f
          scaleY = 2f
          rotationZ = 45f
        }
        .align(Alignment.Center)
        .size(100.dp),
    )

    val density = LocalDensity.current

    val infinite = rememberInfiniteTransition(label = "orbit")
    val angle by infinite.animateFloat(
      initialValue = 0f,
      targetValue = 360f,
      animationSpec = infiniteRepeatable(
        animation = tween(6_000, easing = LinearEasing),
        repeatMode = RepeatMode.Restart,
      ),
      label = "angle",
    )

    val radiusPx = with(density) { 64.dp.toPx() }

    Text(
      text = "Hi",
      color = Color.White,
      style = MaterialTheme.typography.headlineSmall,
      modifier = Modifier
        .graphicsLayer {
          val theta = toRadians(angle.toDouble())
          translationX = (radiusPx * cos(theta)).toFloat()
          translationY = (radiusPx * sin(theta)).toFloat()
        }
        .align(Alignment.Center)
        .hazeEffect(state = hazeState) {
          blurEffect {
            blurRadius = 20.dp
          }
        }
        .padding(16.dp),
    )
  }
}

fun toRadians(deg: Double): Double = deg / 180.0 * PI
