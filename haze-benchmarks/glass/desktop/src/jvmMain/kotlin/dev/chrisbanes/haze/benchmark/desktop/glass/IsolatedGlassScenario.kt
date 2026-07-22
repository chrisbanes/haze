// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.benchmark.desktop.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.benchmark.desktop.DesktopBenchmarkScenario
import dev.chrisbanes.haze.benchmark.desktop.DesktopInputEvent
import dev.chrisbanes.haze.benchmark.desktop.DesktopInputEventType
import dev.chrisbanes.haze.benchmark.desktop.NormalizedPoint
import dev.chrisbanes.haze.glass.GlassReducedMotionPolicy
import dev.chrisbanes.haze.glass.GlassVisualEffect
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import kotlin.math.PI
import kotlin.math.sin

internal val isolatedGlassSurfaceWidthFraction = 0.5625f
internal val isolatedGlassSurfaceHeightFraction = 5f / 9f

internal fun isolatedPointerEvents(): List<DesktopInputEvent> = buildList {
  repeat(480) { index ->
    val progress = index / 479f
    add(
      DesktopInputEvent(
        offsetNanos = index * 1_000_000_000L / 120L,
        type = DesktopInputEventType.Move,
        position = NormalizedPoint(
          x = 0.25f + progress * 0.5f,
          y = 0.5f + sin(progress * 4f * PI.toFloat()) * 0.18f,
        ),
      ),
    )
  }
  add(DesktopInputEvent(4_000_000_000L, DesktopInputEventType.Exit, null))
}

internal class IsolatedGlassScenario : DesktopBenchmarkScenario {
  override val id: String = "pointer_sweep"
  override val protocolVersion: Int = 1
  override val events: List<DesktopInputEvent> = isolatedPointerEvents()

  @Composable
  override fun Content() {
    val hazeState = remember { HazeState() }
    val effect = remember {
      GlassVisualEffect().apply {
        hovered()
        interactionReducedMotionPolicy = GlassReducedMotionPolicy.Full
        shape = RoundedCornerShape(20.dp)
      }
    }
    IsolatedGlassBenchmarkScene(hazeState, effect)
  }

  override suspend fun reset() = Unit
}

@Composable
private fun IsolatedGlassBenchmarkScene(
  hazeState: HazeState,
  effect: GlassVisualEffect,
) {
  Box(Modifier.fillMaxSize()) {
    Canvas(Modifier.fillMaxSize().hazeSource(hazeState)) {
      drawRect(Color(0xFF10233E))
      rotate(-25f) {
        repeat(18) { index ->
          drawRect(
            color = if (index % 2 == 0) Color(0xFF2CE1C2) else Color(0xFFF15B8A),
            topLeft = Offset(index * 56f - 300f, -200f),
            size = size.copy(width = 22f),
            alpha = 0.72f,
          )
        }
      }
      drawCircle(Color(0xFFFFD166), radius = 96f, center = center * 1.35f)
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      Box(
        Modifier
          .fillMaxWidth(isolatedGlassSurfaceWidthFraction)
          .fillMaxHeight(isolatedGlassSurfaceHeightFraction)
          .hazeEffect(hazeState) { visualEffect = effect },
        contentAlignment = Alignment.Center,
      ) {
        Text("POINTER SWEEP", color = Color.White)
      }
    }
  }
}
