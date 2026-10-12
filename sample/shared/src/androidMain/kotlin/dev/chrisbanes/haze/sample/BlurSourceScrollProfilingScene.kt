// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.ceil

/**
 * A locally complete workload in which only the source moves. The offset is read in the placement
 * lambda, so the source is re-placed without recomposition and every effect keeps a constant size,
 * position, and style.
 */
@Composable
internal fun BlurSourceScrollProfilingScene(state: BlurProfilingState, scenario: BlurProfilingScenario) {
  val source = rememberHazeState()
  val style = remember { HazeBlurStyle { blurRadius(18.dp) } }
  val columns = if (scenario.sourceScrollEffects > 1) 3 else 1
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Canvas(
      Modifier
        .fillMaxSize()
        .offset { IntOffset(0, profilingSourceScrollOffset(state.progress).roundToPx()) }
        .hazeSource(source),
    ) {
      val tile = 24.dp.toPx()
      for (x in 0 until ceil(size.width / tile).toInt()) {
        for (y in 0 until ceil(size.height / tile).toInt()) {
          drawRect(
            color = if ((x + y) % 2 == 0) Color(0xFFEEEEEE) else Color(0xFF222222),
            topLeft = Offset(x * tile, y * tile),
            size = Size(tile, tile),
          )
        }
      }
    }
    Column {
      repeat(columns) { row ->
        Row {
          repeat(columns) { column ->
            Box(
              Modifier
                .size(width = (240f / columns).dp, height = (180f / columns).dp)
                .testTag("source_scroll_node_${row * columns + column}")
                .hazeBlur(
                  input = HazeInput.Sources(source),
                  performanceMode = HazePerformanceMode.Balanced,
                  style = style,
                ),
            )
          }
        }
      }
    }
  }
}
