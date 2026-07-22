// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.benchmark.desktop

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import assertk.assertions.isTrue
import java.awt.event.InputEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import kotlin.test.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import org.jetbrains.skiko.SkiaLayer

class DesktopInputReplayerTest {
  @Test
  fun replayTargetsDelegatedCanvasInOrder() = runBlocking {
    val received = mutableListOf<MouseEvent>()
    val listener = object : MouseAdapter() {
      override fun mouseMoved(event: MouseEvent) {
        received += event
      }

      override fun mousePressed(event: MouseEvent) {
        received += event
      }

      override fun mouseDragged(event: MouseEvent) {
        received += event
      }

      override fun mouseReleased(event: MouseEvent) {
        received += event
      }

      override fun mouseExited(event: MouseEvent) {
        received += event
      }
    }
    val layer = withContext(Dispatchers.Swing) {
      SkiaLayer().apply {
        canvas.setSize(200, 100)
        addMouseListener(listener)
        addMouseMotionListener(listener)
      }
    }

    try {
      DesktopInputReplayer(layer).replay(inputEvents())

      assertThat(received.map { it.id }).containsExactly(
        MouseEvent.MOUSE_MOVED,
        MouseEvent.MOUSE_PRESSED,
        MouseEvent.MOUSE_DRAGGED,
        MouseEvent.MOUSE_RELEASED,
        MouseEvent.MOUSE_EXITED,
      )
      assertThat(received.map { it.x to it.y }).containsExactly(
        20 to 20,
        50 to 40,
        100 to 50,
        150 to 60,
        0 to 0,
      )
      assertThat(received.all { it.source === layer.canvas }).isTrue()
      assertThat(received[1].modifiersEx).isEqualTo(InputEvent.BUTTON1_DOWN_MASK)
    } finally {
      withContext(Dispatchers.Swing) { layer.dispose() }
    }
  }
}

private fun inputEvents(): List<DesktopInputEvent> = listOf(
  DesktopInputEvent(0, DesktopInputEventType.Move, NormalizedPoint(0.1f, 0.2f)),
  DesktopInputEvent(0, DesktopInputEventType.Press, NormalizedPoint(0.25f, 0.4f)),
  DesktopInputEvent(0, DesktopInputEventType.Drag, NormalizedPoint(0.5f, 0.5f)),
  DesktopInputEvent(0, DesktopInputEventType.Release, NormalizedPoint(0.75f, 0.6f)),
  DesktopInputEvent(0, DesktopInputEventType.Exit, null),
)
