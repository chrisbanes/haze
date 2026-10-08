// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.Poko
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlin.math.ceil

internal enum class BlurNoiseTintProperty { Stable, Tint, Radius, Noise }

@OptIn(InternalHazeApi::class)
@Poko
internal class BlurNoiseTintParameters(val radius: Float, val noise: Float, val tint: Color)

internal fun blurNoiseTintParameters(
  scenario: BlurProfilingScenario,
  progress: Float,
  node: Int,
): BlurNoiseTintParameters {
  require(progress.isFinite() && progress in 0f..1f)
  require(node in 0 until scenario.noiseTintNodes)
  val property = checkNotNull(scenario.noiseTintProperty)
  val radius = if (property == BlurNoiseTintProperty.Radius) lerp(12f, 28f, progress) else 18f
  val noise = if (property == BlurNoiseTintProperty.Noise) lerp(0.1f, 0.6f, progress) else 0.3f
  val alpha = if (property == BlurNoiseTintProperty.Tint) lerp(0.1f, 0.6f, progress) else 0.3f
  val color = when (node) {
    0 -> Color.Red
    1 -> Color.Green
    else -> Color.Blue
  }
  return BlurNoiseTintParameters(radius + node, noise + node * 0.02f, color.copy(alpha = alpha + node * 0.02f))
}

/** A locally complete, source-backed workload with constant total visible effect area. */
@Composable
internal fun BlurNoiseTintProfilingScene(state: BlurProfilingState, scenario: BlurProfilingScenario) {
  val source = rememberHazeState()
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    Canvas(Modifier.fillMaxSize().hazeSource(source)) {
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
    Row {
      repeat(scenario.noiseTintNodes) { node ->
        // Styles record immutable values. Changing styles are constructed equally in both APKs;
        // stable styles avoid a composition-time frame read and invalidate drawing only.
        val progress = if (scenario.noiseTintProperty == BlurNoiseTintProperty.Stable) 0f else state.progress
        val params = blurNoiseTintParameters(scenario, progress, node)
        Box(
          Modifier.width((240f / scenario.noiseTintNodes).dp).height(180.dp)
            .testTag("noise_tint_node_$node")
            .drawWithContent {
              state.progress
              drawContent()
            }
            .hazeBlur(
              input = HazeInput.Sources(source),
              performanceMode = HazePerformanceMode.Quality,
              style = HazeBlurStyle {
                blurRadius(params.radius.dp)
                noiseFactor(params.noise)
                colorEffects(listOf(HazeColorEffect.tint(params.tint)))
              },
            ),
        )
      }
    }
  }
}
