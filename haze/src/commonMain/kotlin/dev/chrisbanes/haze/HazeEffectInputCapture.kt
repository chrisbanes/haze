// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(InternalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer

/** Core-owned input and geometry retained independently of live selection and capture demand. */
@InternalHazeApi
public interface HazeEffectInputCapture {
  /** Draws the acquired input using the geometry copied at acquisition. */
  public fun DrawScope.drawInput()

  /** Releases retained input ownership. Repeated calls have no effect. */
  public fun release()
}

internal class HazeContentCapture(
  private val context: GraphicsContext,
  val layer: GraphicsLayer,
) {
  private var owner = true
  private var leases = 0
  val isLeased: Boolean get() = leases > 0

  fun acquire() {
    check(owner || leases > 0)
    leases++
  }

  fun dropOwner() {
    if (!owner) return
    owner = false
    releaseIfUnused()
  }

  fun release() {
    check(leases > 0)
    leases--
    releaseIfUnused()
  }

  private fun releaseIfUnused() {
    if (!owner && leases == 0) context.releaseGraphicsLayer(layer)
  }
}

internal class CapturedHazeInput(val capture: HazeContentCapture, val transform: Matrix)

internal class HazeEffectInputCaptureImpl(
  private var inputs: List<CapturedHazeInput>,
  private val layerOffset: Offset,
) : HazeEffectInputCapture {
  override fun DrawScope.drawInput() {
    translate(layerOffset.x, layerOffset.y) {
      for (input in inputs) {
        withTransform({ transform(input.transform) }) { drawLayer(input.capture.layer) }
      }
    }
  }

  override fun release() {
    val released = inputs
    inputs = emptyList()
    released.forEach { it.capture.release() }
  }
}
