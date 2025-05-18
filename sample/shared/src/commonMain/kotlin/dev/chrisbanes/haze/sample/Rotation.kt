// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.roundToInt

@Composable
fun RotationSample(
  navController: NavHostController,
  blurEnabled: Boolean = HazeDefaults.blurEnabled()
) {
  val hazeState = rememberHazeState(blurEnabled)

  Box(modifier = Modifier.fillMaxSize()) {
    Box(
      modifier = Modifier
        .fillMaxSize()
        .hazeSource(hazeState)
        .background(
          brush = Brush.verticalGradient(
            colors = listOf(
              Color.Red,
              Color.Yellow,
              Color.Green,
              Color.Cyan,
              Color.Blue,
              Color.Magenta
            )
          )
        )
    )
    Text(
      text = "Hello, Compose!", style = MaterialTheme.typography.headlineMedium,
      color = MaterialTheme.colorScheme.onPrimaryContainer,
      modifier = Modifier
        .hazeSource(hazeState)
        .padding(16.dp)
    )
    DraggableRotatedScaledImage(hazeState)
    DraggableText(hazeState)
  }
}

@Composable
fun DraggableRotatedScaledImage(hazeState: HazeState) {
  var imageOffset by remember { mutableStateOf(Offset.Zero) }

  AsyncImage(
    model = rememberRandomSampleImageUrl(10),
    contentDescription = "Draggable Rotated and Scaled Image",
    contentScale = ContentScale.Crop,
    modifier = Modifier
      .pointerInput(Unit) {
        detectDragGestures { change, dragAmount ->
          change.consume()
          imageOffset += dragAmount
        }
      }
      .offset {
        IntOffset(
          imageOffset.x.roundToInt(),
          imageOffset.y.roundToInt(),
        )
      }
      .hazeSource(hazeState, zIndex = 1f)
      .graphicsLayer {
        rotationZ = 45f
      }
      .size(200.dp)
      .padding(16.dp),
  )
}

@Composable
fun DraggableText(hazeState: HazeState) {
  var offset by remember { mutableStateOf(Offset.Zero) }

  Text(
    text = "Drag Here",
    color = Color(0xFFFFFFFF),
    fontSize = 20.sp,
    style = MaterialTheme.typography.bodyLarge,
    modifier = Modifier
      .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
      .pointerInput(Unit) {
        detectDragGestures { change, dragAmount ->
          change.consume()
          offset = offset + dragAmount
        }
      }
      .background(Color(0x1A000000))
      .hazeEffect(state = hazeState) {
        blurRadius = 20.dp
      }
      .padding(16.dp),
  )
}
