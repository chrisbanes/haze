// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("DEPRECATION")
@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.blur

import android.content.Context
import android.renderscript.Allocation
import android.renderscript.BaseObj
import android.renderscript.RSRuntimeException
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.each
import assertk.assertions.hasSize
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import dev.chrisbanes.haze.HazeEffectDrawScope
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeEffectRenderer
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeSampling
import dev.chrisbanes.haze.TrimMemoryLevel
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotUiTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test
import org.junit.After
import org.robolectric.annotation.Config

/**
 * Full delegate draws with injected native failures and zero-radius updates. The host has no input
 * Surface or blur kernel, so the write simulates the buffer callback and the blur only counts.
 */
@Config(sdk = [35])
class RenderScriptUpdateLifecycleAndroidHostTest : ScreenshotTest() {
  private val context get() = ApplicationProvider.getApplicationContext<Context>()

  @After
  fun resetSharedRenderScript() {
    SharedRenderScript.create = SharedRenderScript.defaultCreate
    SharedRenderScript.destroy = SharedRenderScript.defaultDestroy
  }

  @Test
  fun updateSurface_contextCreationFails_releasesPartialObjectsAndPublishesNothing() = runScreenshotTest {
    val h = Harness()
    val created = mutableListOf<BaseObj>()
    h.onDelegate = { delegate ->
      delegate.createRenderScriptContext = { rs, size ->
        RenderScriptContext(rs, size) {
          created += it
          // Fails at the output allocation, after the input allocation's buffer listener is set.
          if (created.size == 3) throw RSRuntimeException("injected")
        }
      }
    }
    setContent { h.Content() }
    h.settle(this)

    assertThat(created).hasSize(3)
    assertThat(created.map { it.isDestroyed() }).each { it.isTrue() }
    assertThat((created[1] as Allocation).bufferNotifier()).isNull()
    assertThat(h.delegate.renderScriptContext).isNull()
    assertThat(h.invalidations).isEqualTo(0)
    assertThat(h.delegate.canDrawRetainedOutput()).isFalse()

    h.delegate.createRenderScriptContext = ::RenderScriptContext
    h.redraw(this)

    assertThat(h.delegate.canDrawRetainedOutput()).isTrue()
    assertThat(h.invalidations).isEqualTo(1)
  }

  @Test
  fun updateSurface_resize_releasesTheReplacedContextOnceTheNewWriteCompletes() = runScreenshotTest {
    val h = Harness()
    setContent { h.Content() }
    h.settle(this)
    val old = checkNotNull(h.delegate.renderScriptContext)
    var oldDestroyedDuringWrite: Boolean? = null
    h.delegate.writeToSurface = { rs, layer, density ->
      oldDestroyedDuringWrite = old.isDestroyed()
      h.defaultWrite(rs, layer, density)
    }

    h.layerSize = Size(108f, 100f)
    h.redraw(this)

    val replacement = checkNotNull(h.delegate.renderScriptContext)
    assertThat(replacement).isNotSameInstanceAs(old)
    assertThat(replacement.size).isNotEqualTo(old.size)
    assertThat(oldDestroyedDuringWrite).isNotNull().isFalse()
    assertThat(old.isDestroyed()).isTrue()
    assertThat(h.delegate.canDrawRetainedOutput()).isTrue()
  }

  @Test
  fun updateSurface_resizeWithHeldWrite_keepsTheReplacedContextUntilTheBufferArrives() = runScreenshotTest {
    val h = Harness()
    setContent { h.Content() }
    h.settle(this)
    val old = checkNotNull(h.delegate.renderScriptContext)
    var heldWrite: (() -> Unit)? = null
    // A write that returns before drawing, as a slow Surface write would.
    h.delegate.writeToSurface = { rs, layer, density -> heldWrite = { h.defaultWrite(rs, layer, density) } }

    h.layerSize = Size(108f, 100f)
    frame()
    waitForIdle()

    assertThat(heldWrite).isNotNull()
    assertThat(old.isDestroyed()).isFalse()

    checkNotNull(heldWrite).invoke()
    h.settle(this)

    assertThat(old.isDestroyed()).isTrue()
    assertThat(h.delegate.renderScriptContext).isNotNull().isNotSameInstanceAs(old)
    assertThat(h.delegate.canDrawRetainedOutput()).isTrue()
  }

  @Test
  fun updateSurface_resizeWriteFails_releasesBothContexts() = runScreenshotTest {
    val h = Harness()
    setContent { h.Content() }
    h.settle(this)
    val old = checkNotNull(h.delegate.renderScriptContext)
    var replacement: RenderScriptContext? = null
    h.delegate.writeToSurface = { rs, _, _ ->
      replacement = rs
      throw IllegalStateException("injected")
    }

    h.layerSize = Size(108f, 100f)
    h.redraw(this)

    assertThat(replacement).isNotNull().isNotSameInstanceAs(old)
    assertThat(old.isDestroyed()).isTrue()
    assertThat(checkNotNull(replacement).isDestroyed()).isTrue()
    assertThat(h.delegate.renderScriptContext).isNull()
    // The previous output is kept.
    assertThat(h.delegate.canDrawRetainedOutput()).isTrue()
  }

  @Test
  fun updateSurface_bufferReceiveFails_releasesTheContextAndRetries() = runScreenshotTest {
    val h = Harness()
    val attempts = mutableListOf<RenderScriptContext>()
    h.onDelegate = { delegate ->
      delegate.writeToSurface = { rs, _, _ ->
        attempts += rs
        rs.receiveInputBuffer { throw RSRuntimeException("injected") }
      }
    }
    setContent { h.Content() }
    h.settle(this)

    assertThat(attempts.single().isDestroyed()).isTrue()
    assertThat(h.delegate.renderScriptContext).isNull()
    assertThat(h.delegate.canDrawRetainedOutput()).isFalse()

    h.delegate.writeToSurface = h.defaultWrite
    h.redraw(this)

    assertThat(h.delegate.canDrawRetainedOutput()).isTrue()
  }

  @Test
  fun updateSurface_blurFailsAfterAnOutput_keepsThePreviousOutputAndBitmap() = runScreenshotTest {
    val h = Harness()
    setContent { h.Content() }
    h.settle(this)
    val ctx = checkNotNull(h.delegate.renderScriptContext)
    val generation = ctx.outputBitmap.generationId
    assertThat(h.invalidations).isEqualTo(1)

    h.delegate.applyBlur = { _, _ -> throw RSRuntimeException("injected") }
    h.radius = 10.dp
    h.redraw(this)

    assertThat(ctx.isDestroyed()).isTrue()
    assertThat(h.delegate.renderScriptContext).isNull()
    // publishOutput did not run, so the published bitmap still holds the previous frame.
    assertThat(ctx.outputBitmap.generationId).isEqualTo(generation)
    assertThat(h.invalidations).isEqualTo(1)
    assertThat(h.delegate.canDrawRetainedOutput()).isTrue()
  }

  @Test
  fun updateSurface_sharedRenderScriptCreateFails_afterTrim_publishesNothingAndRetries() = runScreenshotTest {
    val h = Harness()
    setContent { h.Content() }
    h.settle(this)
    h.delegate.onTrimMemory(HostLifecycleScope(context), TrimMemoryLevel.MODERATE)
    assertThat(SharedRenderScript.currentForTest).isNull()

    SharedRenderScript.create = { throw RSRuntimeException("injected") }
    h.redraw(this)

    assertThat(h.delegate.canDrawRetainedOutput()).isFalse()
    assertThat(SharedRenderScript.referenceCountForTest).isEqualTo(0)

    SharedRenderScript.create = SharedRenderScript.defaultCreate
    h.redraw(this)

    assertThat(SharedRenderScript.currentForTest).isNotNull()
    assertThat(SharedRenderScript.referenceCountForTest).isEqualTo(1)
    assertThat(h.delegate.canDrawRetainedOutput()).isTrue()
  }

  @Test
  fun zeroRadius_sameSizeAndResizedUpdates_skipNativeWork() = runScreenshotTest {
    val h = Harness(radius = 0.dp)
    setContent { h.Content() }
    h.settle(this)
    val presentation = checkNotNull(h.delegate.presentationLayer.layer)

    fun assertCopied(invalidations: Int) {
      assertThat(h.delegate.canDrawRetainedOutput()).isTrue()
      assertThat(h.invalidations).isEqualTo(invalidations)
      assertThat(h.surfaceWrites).isEmpty()
      assertThat(h.blurs).isEqualTo(0)
      assertThat(h.delegate.renderScriptContext).isNull()
      assertThat(h.delegate.paddedLayer.layer).isNull()
      assertThat(h.delegate.presentationLayer.layer).isNotNull().isSameInstanceAs(presentation)
    }
    assertCopied(invalidations = 1)

    h.layerSize = Size(101f, 101f)
    h.redraw(this)
    assertCopied(invalidations = 2)

    h.layerSize = Size(108f, 100f)
    h.redraw(this)
    assertCopied(invalidations = 3)
  }

  @Test
  fun zeroRadius_afterPositiveRadius_keepsTheContextForTheNextBlurredUpdate() = runScreenshotTest {
    val h = Harness(radius = 0.dp)
    setContent { h.Content() }
    h.settle(this)

    h.radius = 8.dp
    h.redraw(this)
    assertThat(h.surfaceWrites).hasSize(1)
    val blurred = checkNotNull(h.delegate.renderScriptContext)

    h.radius = 0.dp
    h.redraw(this)
    assertThat(h.delegate.renderScriptContext).isSameInstanceAs(blurred)
    assertThat(blurred.isDestroyed()).isFalse()
    assertThat(h.surfaceWrites).hasSize(1)
    assertThat(h.delegate.canDrawRetainedOutput()).isTrue()
    assertThat(SharedRenderScript.currentForTest).isNotNull()

    h.radius = 8.dp
    h.redraw(this)
    assertThat(h.delegate.renderScriptContext).isSameInstanceAs(blurred)
    assertThat(h.surfaceWrites.last()).isSameInstanceAs(blurred)
    assertThat(h.delegate.canDrawRetainedOutput()).isTrue()

    h.delegate.onTrimMemory(HostLifecycleScope(context), TrimMemoryLevel.MODERATE)
    assertThat(blurred.isDestroyed()).isTrue()
  }

  @Test
  fun onTrimMemory_thenZeroRadius_doesNotReacquire_positiveRadiusDoes() = runScreenshotTest {
    val h = Harness()
    setContent { h.Content() }
    h.settle(this)
    h.delegate.onTrimMemory(HostLifecycleScope(context), TrimMemoryLevel.MODERATE)
    assertThat(SharedRenderScript.currentForTest).isNull()

    h.radius = 0.dp
    h.redraw(this)
    assertThat(h.delegate.canDrawRetainedOutput()).isTrue()
    assertThat(SharedRenderScript.currentForTest).isNull()

    h.radius = 8.dp
    h.redraw(this)
    assertThat(SharedRenderScript.currentForTest).isNotNull()
    assertThat(SharedRenderScript.referenceCountForTest).isEqualTo(1)
    assertThat(h.delegate.canDrawRetainedOutput()).isTrue()
  }

  private inner class Harness(radius: Dp = 8.dp) {
    val effect = BlurVisualEffect()
    private var tick by mutableStateOf(0)
    var layerSize by mutableStateOf(Size(100f, 100f))

    var radius: Dp = radius
      set(value) {
        field = value
        update()
      }

    /** Every context written to through the default write, in order. */
    val surfaceWrites = mutableListOf<RenderScriptContext>()
    var blurs = 0
      private set
    var invalidations = 0
      private set

    val defaultWrite: (RenderScriptContext, GraphicsLayer, Density) -> Unit =
      { rs, _, _ ->
        surfaceWrites += rs
        rs.receiveInputBuffer {}
      }

    /** Configures the delegate before its first draw. */
    var onDelegate: (RenderScriptBlurVisualEffectDelegate) -> Unit = {}
    private var current: RenderScriptBlurVisualEffectDelegate? = null
    val delegate get() = checkNotNull(current)

    init {
      update()
    }

    private fun update() {
      effect.update(
        HostLifecycleScope(context),
        BlurConfiguration(HazeBlurStyle { blurRadius(radius) }, HazePerformanceMode.Quality),
        HazeSampling.FullResolution,
      )
    }

    fun redraw(test: ScreenshotUiTest) {
      tick++
      settle(test)
    }

    /** Draws a frame, then waits for the update it started: its later steps resume off the frame. */
    fun settle(test: ScreenshotUiTest) = test.run {
      frame()
      val deadline = System.nanoTime() + 10_000_000_000L
      while (delegate.currentJob?.isActive == true && System.nanoTime() < deadline) {
        waitForIdle()
        Thread.sleep(10)
      }
      assertThat(delegate.currentJob?.isActive == true).isFalse()
    }

    @androidx.compose.runtime.Composable
    fun Content() {
      Box(Modifier.fillMaxSize()) {
        Box(Modifier.size(100.dp).hazeEffect(factory = factory, input = HazeInput.Content, style = tick))
      }
    }

    private val factory = object : HazeEffectFactory<Int> {
      override fun createRenderer(): HazeEffectRenderer<Int> = object : HazeEffectRenderer<Int> {
        private var own: RenderScriptBlurVisualEffectDelegate? = null

        override fun HazeEffectDrawScope.draw(style: Int) {
          val runtime = this as HazeEffectRuntimeDrawScope
          val delegate = own ?: RenderScriptBlurVisualEffectDelegate(
            effect,
            runtime.requireGraphicsContext(),
            runtime.requirePlatformContext(),
          ).also {
            it.writeToSurface = defaultWrite
            it.applyBlur = { _, _ -> blurs++ }
            onDelegate(it)
            own = it
            current = it
          }
          val size = this@Harness.layerSize
          with(delegate) {
            draw(
              object : HazeEffectRuntimeDrawScope by runtime {
                override val layerSize: Size = size
                override val hasDrawableInput: Boolean = true
                override fun invalidateDraw() {
                  invalidations++
                }
              },
            )
          }
        }

        override fun dispose() {
          own?.detach()
        }
      }
    }
  }
}
