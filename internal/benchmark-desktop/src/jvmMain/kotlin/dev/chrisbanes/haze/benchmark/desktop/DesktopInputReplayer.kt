// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.benchmark.desktop

import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.nanoseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import org.jetbrains.skiko.SkiaLayer

internal class DesktopInputReplayer(
  private val layer: SkiaLayer,
  private val nanoTime: () -> Long = System::nanoTime,
) {
  suspend fun replay(events: List<DesktopInputEvent>) {
    val startedAt = nanoTime()
    events.forEach { event ->
      val target = startedAt + event.offsetNanos
      val remaining = target - nanoTime()
      if (remaining > 0) delay(remaining.nanoseconds)
      withContext(Dispatchers.Swing) {
        layer.dispatchEvent(event.toAwtEvent(layer))
      }
    }
  }
}

private fun DesktopInputEvent.toAwtEvent(layer: SkiaLayer): MouseEvent {
  val x = position?.let { (it.x * layer.width).roundToInt() } ?: 0
  val y = position?.let { (it.y * layer.height).roundToInt() } ?: 0
  val (id, button, modifiers) = when (type) {
    DesktopInputEventType.Move -> Triple(MouseEvent.MOUSE_MOVED, MouseEvent.NOBUTTON, 0)
    DesktopInputEventType.Press -> Triple(MouseEvent.MOUSE_PRESSED, MouseEvent.BUTTON1, 0)
    DesktopInputEventType.Drag -> Triple(
      MouseEvent.MOUSE_DRAGGED,
      MouseEvent.NOBUTTON,
      InputEvent.BUTTON1_DOWN_MASK,
    )
    DesktopInputEventType.Release -> Triple(MouseEvent.MOUSE_RELEASED, MouseEvent.BUTTON1, 0)
    DesktopInputEventType.Exit -> Triple(MouseEvent.MOUSE_EXITED, MouseEvent.NOBUTTON, 0)
  }
  return MouseEvent(
    layer,
    id,
    System.currentTimeMillis(),
    modifiers,
    x,
    y,
    1,
    false,
    button,
  )
}
