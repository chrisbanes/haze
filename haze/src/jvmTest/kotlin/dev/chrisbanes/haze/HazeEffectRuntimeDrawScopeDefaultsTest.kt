// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.CompositionLocal
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasMessage
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope

@OptIn(InternalHazeApi::class)
class HazeEffectRuntimeDrawScopeDefaultsTest {
  @Test fun unknownScope_captureFailsWithoutAccessingLiveResources() {
    val scope = UnsupportedCaptureScope()
    assertFailure { scope.captureInput() }
      .isInstanceOf<UnsupportedOperationException>()
      .hasMessage("This scope does not support managed input capture")
  }

  @Test fun unknownScope_doesNotOptIntoSourceSnapshotsOrSchedulePresentation() {
    val scope = UnsupportedCaptureScope()
    val renderer = object : HazeEffectRendererDrawHooks<Unit> {}
    assertThat(scope.isSourceBackedInput).isFalse()
    assertThat(renderer.observesSourceSnapshotChanges).isFalse()
    scope.inputPresentationInvalidation().invoke()
    assertThat(scope.invalidations).isEqualTo(0)
  }

  private class UnsupportedCaptureScope : HazeEffectRuntimeDrawScope, DrawScope by CanvasDrawScope() {
    override val modifierSize = Size.Zero
    override val modifierBounds = Rect.Zero
    override val layerSize = Size.Zero
    override val layerOffset = Offset.Zero
    override val sampling = HazeSampling.FullResolution
    override val hasDrawableInput = false
    override val inputSnapshot: HazeEffectInputSnapshot? = null
    override val coroutineScope: CoroutineScope get() = error("Unexpected coroutine scope access")
    var invalidations = 0

    override fun requirePlatformContext(): PlatformContext = error("Unexpected platform access")
    override fun requireGraphicsContext(): GraphicsContext = error("Unexpected graphics access")
    override fun invalidateDraw() {
      invalidations++
    }
    override fun drawInput(): Unit = error("Unexpected input drawing")
    override fun DrawScope.drawInput(): Unit = error("Unexpected input drawing")
    override fun <T> currentValueOf(local: CompositionLocal<T>): T = error("Unexpected composition read")
  }
}
