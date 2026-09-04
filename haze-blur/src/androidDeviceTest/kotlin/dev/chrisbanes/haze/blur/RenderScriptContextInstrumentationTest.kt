// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("DEPRECATION")

package dev.chrisbanes.haze.blur

import android.graphics.Color
import android.renderscript.RenderScript
import androidx.compose.ui.unit.IntSize
import androidx.test.platform.app.InstrumentationRegistry
import assertk.assertFailure
import assertk.assertions.isInstanceOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test

class RenderScriptContextInstrumentationTest {

  @Test
  fun applyBlur_acceptsFinitePositiveRadiusThroughRenderScriptMaximum() {
    withContext { context ->
      writeInput(context)
      listOf(0.25f, 1f, 25f).forEach(context::applyBlur)
    }
  }

  @Test
  fun applyBlur_rejectsInvalidRadiusValues() {
    withContext { context ->
      listOf(
        0f,
        -1f,
        Float.NaN,
        Float.NEGATIVE_INFINITY,
        Float.POSITIVE_INFINITY,
        25.1f,
      ).forEach { radius ->
        assertFailure { context.applyBlur(radius) }
          .isInstanceOf<IllegalArgumentException>()
      }
    }
  }

  private fun withContext(block: (RenderScriptContext) -> Unit) {
    val renderScript = RenderScript.create(
      InstrumentationRegistry.getInstrumentation().targetContext,
    )
    val context = RenderScriptContext(renderScript, IntSize(4, 4))
    try {
      block(context)
    } finally {
      context.release()
      renderScript.destroy()
    }
  }

  private fun writeInput(context: RenderScriptContext) {
    val canvas = context.inputSurface.lockCanvas(null)
    try {
      canvas.drawColor(Color.BLUE)
    } finally {
      context.inputSurface.unlockCanvasAndPost(canvas)
    }
    runBlocking { withTimeout(3_000L) { context.awaitSurfaceWritten() } }
  }
}
