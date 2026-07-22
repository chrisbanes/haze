// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.benchmark.desktop.glass

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import dev.chrisbanes.haze.benchmark.desktop.DesktopBenchmarkScenario
import dev.chrisbanes.haze.benchmark.desktop.DesktopInputEvent
import dev.chrisbanes.haze.benchmark.desktop.DesktopInputEventType
import dev.chrisbanes.haze.benchmark.desktop.NormalizedPoint
import dev.chrisbanes.haze.glass.GlassReducedMotionPolicy
import dev.chrisbanes.haze.sample.GlassPlaygroundSampleContent
import dev.chrisbanes.haze.sample.GlassPlaygroundSurfaceId
import dev.chrisbanes.haze.sample.SamplesTheme
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext

internal fun playgroundEvents(): List<DesktopInputEvent> = buildList {
  val start = NormalizedPoint(0.5f, 0.52f)
  add(DesktopInputEvent(0, DesktopInputEventType.Move, start))
  add(DesktopInputEvent(500_000_000L, DesktopInputEventType.Press, start))
  repeat(300) { index ->
    val progress = index / 299f
    add(
      DesktopInputEvent(
        offsetNanos = 500_000_000L + index * 5_000_000_000L / 299L,
        type = DesktopInputEventType.Drag,
        position = NormalizedPoint(
          x = 0.5f + 0.20f * progress,
          y = 0.52f + 0.10f * sin((progress * PI.toFloat()).toDouble()).toFloat(),
        ),
      ),
    )
  }
  add(DesktopInputEvent(5_500_000_000L, DesktopInputEventType.Release, NormalizedPoint(0.7f, 0.52f)))
  add(DesktopInputEvent(6_000_000_000L, DesktopInputEventType.Exit, null))
}

internal class PlaygroundGlassScenario : DesktopBenchmarkScenario {
  override val id: String = "playground_drag"
  override val protocolVersion: Int = 1
  override val events: List<DesktopInputEvent> = playgroundEvents()

  private var dragOffset by mutableStateOf(Offset.Zero)

  @Composable
  override fun Content() {
    SamplesTheme(useDarkColors = true) {
      GlassPlaygroundSampleContent(
        progressProvider = { 0.5f },
        dragOffsetProvider = { id ->
          if (id == GlassPlaygroundSurfaceId.Prism) dragOffset else Offset.Zero
        },
        isPlaying = false,
        recordingMode = true,
        onPlayPause = {},
        onReset = {},
        onRecordingModeChanged = {},
        onBack = {},
        onDragStart = {},
        onDrag = { id, delta ->
          if (id == GlassPlaygroundSurfaceId.Prism) dragOffset += delta
        },
        onDragEnd = {},
        interactionReducedMotionPolicy = GlassReducedMotionPolicy.Full,
      )
    }
  }

  override suspend fun reset() = withContext(Dispatchers.Swing) {
    dragOffset = Offset.Zero
  }

  internal fun applyDragForTest(delta: Offset) {
    dragOffset += delta
  }

  internal fun dragOffsetForTest(): Offset = dragOffset
}
