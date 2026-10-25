// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("DEPRECATION")
@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.blur

import android.content.Context
import android.renderscript.RSIllegalArgumentException
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.hasSize
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
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
import dev.chrisbanes.haze.TrimMemoryLevel
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.test.ContextTest
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotUiTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.Test
import org.junit.After
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RenderScriptReleaseAndroidHostTest : ContextTest() {
  private val context get() = ApplicationProvider.getApplicationContext<Context>()

  @Test
  fun getRenderScriptContext_sameSize_returnsTheSameContext() {
    val delegate = delegate()

    val first = delegate.getRenderScriptContext(IntSize(8, 8))

    assertThat(delegate.getRenderScriptContext(IntSize(8, 8))).isSameInstanceAs(first)
    assertThat(first.isDestroyed()).isFalse()
  }

  @Test
  fun getRenderScriptContext_newSize_releasesTheOldContextAndStoresTheNewOne() {
    val delegate = delegate()
    val old = delegate.getRenderScriptContext(IntSize(8, 8))

    val replacement = delegate.getRenderScriptContext(IntSize(16, 8))

    assertThat(replacement).isNotSameInstanceAs(old)
    assertThat(old.isDestroyed()).isTrue()
    assertThat(delegate.renderScriptContext).isSameInstanceAs(replacement)
  }

  @Test
  fun getRenderScriptContext_failedReplacement_leavesNoReleasedContextBehind() {
    val delegate = delegate()
    val old = delegate.getRenderScriptContext(IntSize(8, 8))

    assertFailure { delegate.getRenderScriptContext(IntSize(0, 8)) }
      .isInstanceOf<RSIllegalArgumentException>()

    assertThat(old.isDestroyed()).isTrue()
    assertThat(delegate.renderScriptContext).isNull()
  }

  @Test
  fun clearRetainedOutput_releasesAndNullsTheContext() {
    val delegate = delegate()
    val rs = delegate.getRenderScriptContext(IntSize(8, 8))

    delegate.clearRetainedOutput()

    assertThat(rs.isDestroyed()).isTrue()
    assertThat(delegate.renderScriptContext).isNull()
  }

  @Test
  fun invalidateRetainedOutput_releasesAndNullsTheContext() {
    val delegate = delegate()
    val rs = delegate.getRenderScriptContext(IntSize(8, 8))

    delegate.invalidateRetainedOutput()

    assertThat(rs.isDestroyed()).isTrue()
    assertThat(delegate.renderScriptContext).isNull()
  }

  @Test
  fun onTrimMemory_moderate_releasesAndNullsTheContext() {
    val delegate = delegate()
    val rs = delegate.getRenderScriptContext(IntSize(8, 8))
    val scope = HostLifecycleScope(context)

    delegate.onTrimMemory(scope, TrimMemoryLevel.MODERATE)

    assertThat(rs.isDestroyed()).isTrue()
    assertThat(delegate.renderScriptContext).isNull()
    assertThat(scope.invalidateDrawCalls).isEqualTo(1)
  }

  @Test
  fun onTrimMemory_background_keepsTheContext() {
    val delegate = delegate()
    val rs = delegate.getRenderScriptContext(IntSize(8, 8))
    val scope = HostLifecycleScope(context)

    delegate.onTrimMemory(scope, TrimMemoryLevel.BACKGROUND)

    assertThat(rs.isDestroyed()).isFalse()
    assertThat(delegate.renderScriptContext).isNotNull().isSameInstanceAs(rs)
    assertThat(scope.invalidateDrawCalls).isEqualTo(0)
  }

  @Test
  fun getRenderScriptContext_twoDelegates_shareOneRenderScript() {
    val first = delegate()
    val second = delegate()

    val firstContext = first.getRenderScriptContext(IntSize(8, 8))
    val secondContext = second.getRenderScriptContext(IntSize(8, 8))

    assertThat(secondContext.rs).isSameInstanceAs(firstContext.rs)
    assertThat(firstContext).isNotSameInstanceAs(secondContext)
  }

  @Test
  fun detach_lastDelegateDestroysSharedRenderScript() {
    val first = delegate()
    val second = delegate()
    val shared = first.getRenderScriptContext(IntSize(8, 8)).rs

    first.detach()

    // The other delegate still holds the shared context, and can still make allocations on it.
    assertThat(SharedRenderScript.currentForTest).isSameInstanceAs(shared)
    val remaining = second.getRenderScriptContext(IntSize(8, 8))
    assertThat(remaining.rs).isSameInstanceAs(shared)
    assertThat(remaining.isDestroyed()).isFalse()

    second.detach()

    assertThat(SharedRenderScript.currentForTest).isNull()
    assertThat(SharedRenderScript.referenceCountForTest).isEqualTo(0)
  }

  @Test
  fun detach_calledTwice_releasesTheReferenceOnce() {
    val first = delegate()
    val second = delegate()
    val shared = first.getRenderScriptContext(IntSize(8, 8)).rs

    first.detach()
    first.detach()

    assertThat(SharedRenderScript.currentForTest).isSameInstanceAs(shared)
    assertThat(SharedRenderScript.referenceCountForTest).isEqualTo(1)
    second.detach()
    assertThat(SharedRenderScript.currentForTest).isNull()
  }

  @Test
  fun onTrimMemory_moderate_keepsTheSharedRenderScriptForOtherDelegates() {
    val trimmed = delegate()
    val other = delegate()
    val shared = other.getRenderScriptContext(IntSize(8, 8)).rs
    val scope = HostLifecycleScope(context)

    trimmed.onTrimMemory(scope, TrimMemoryLevel.MODERATE)

    assertThat(SharedRenderScript.currentForTest).isSameInstanceAs(shared)
    assertThat(other.getRenderScriptContext(IntSize(8, 8)).isDestroyed()).isFalse()
    // The trimmed delegate takes a new reference when it next needs the context.
    assertThat(trimmed.getRenderScriptContext(IntSize(8, 8)).rs).isSameInstanceAs(shared)
  }

  @Test
  fun getRenderScriptContext_afterDetach_acquiresTheSharedRenderScriptAgain() {
    val delegate = delegate()
    delegate.detach()
    assertThat(SharedRenderScript.currentForTest).isNull()

    val rs = delegate.getRenderScriptContext(IntSize(8, 8)).rs

    assertThat(SharedRenderScript.currentForTest).isSameInstanceAs(rs)
  }

  private val delegates = mutableListOf<RenderScriptBlurVisualEffectDelegate>()

  @After
  fun detachDelegates() {
    delegates.forEach { it.detach() }
  }

  private fun delegate() = RenderScriptBlurVisualEffectDelegate(
    blurVisualEffect = BlurVisualEffect(),
    graphicsContext = UnusedGraphicsContext,
    platformContext = context,
  ).also(delegates::add)

  private fun RenderScriptContext.isDestroyed(): Boolean =
    RenderScriptContext::class.java.getDeclaredField("isDestroyed").run {
      isAccessible = true
      getBoolean(this@isDestroyed)
    }
}

/** These tests create no layers, so every call fails loudly. */
private object UnusedGraphicsContext : GraphicsContext {
  override fun createGraphicsLayer(): GraphicsLayer = error("Unused")
  override fun releaseGraphicsLayer(layer: GraphicsLayer) = error("Unused")
}

/**
 * A surface write that throws must not count as output. The host has no input Surface, so the
 * delegate's write is replaced and a full draw runs the update that calls it.
 */
class RenderScriptSurfaceFailureAndroidHostTest : ScreenshotTest() {
  @Test
  @Config(sdk = [35])
  fun updateSurface_surfaceDrawThrows_doesNotReportOutputUpdated() = runScreenshotTest {
    val h = Harness()
    setContent { h.Content() }
    h.settle(this)

    assertThat(h.attempts).hasSize(1)
    // A reported output asks for a redraw to present it, and becomes drawable.
    assertThat(h.invalidations).isEqualTo(0)
    assertThat(h.delegate.canDrawRetainedOutput()).isFalse()
  }

  @Test
  @Config(sdk = [35])
  fun updateSurface_surfaceDrawThrows_leavesCaptureKeyUncommittedAndContextReleased() =
    runScreenshotTest {
      val h = Harness()
      setContent { h.Content() }
      h.settle(this)

      assertThat(h.attempts[0].isDestroyed()).isTrue()
      assertThat(h.delegate.renderScriptContext).isNull()

      // The same capture key is drawn again: a committed key would skip the update.
      h.redraw(this)
      assertThat(h.attempts).hasSize(2)
    }

  @Test
  @Config(sdk = [35])
  fun updateSurface_afterSurfaceDrawFailure_nextDrawRetriesWithNewContext() = runScreenshotTest {
    val h = Harness()
    setContent { h.Content() }
    h.settle(this)
    val failed = h.attempts.single()

    h.redraw(this)

    assertThat(h.attempts).hasSize(2)
    assertThat(h.attempts[1]).isNotSameInstanceAs(failed)
    assertThat(h.delegate.renderScriptContext).isNull()
  }

  private inner class Harness {
    val factory = FailingSurfaceFactory()
    private var tick by mutableStateOf(0)

    val delegate get() = checkNotNull(factory.delegate)
    val attempts get() = factory.attempts
    val invalidations get() = factory.invalidations

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
  }

  private inner class FailingSurfaceFactory : HazeEffectFactory<Int> {
    /** Every context the delegate asked to write to, in order. */
    val attempts = mutableListOf<RenderScriptContext>()
    var invalidations = 0
      private set
    var delegate: RenderScriptBlurVisualEffectDelegate? = null
      private set

    override fun createRenderer(): HazeEffectRenderer<Int> = object : HazeEffectRenderer<Int> {
      private var own: RenderScriptBlurVisualEffectDelegate? = null

      override fun HazeEffectDrawScope.draw(style: Int) {
        val runtime = this as HazeEffectRuntimeDrawScope
        val current = own ?: RenderScriptBlurVisualEffectDelegate(
          BlurVisualEffect(),
          runtime.requireGraphicsContext(),
          runtime.requirePlatformContext(),
        ).also {
          it.writeToSurface = { rs, _, _ ->
            attempts += rs
            throw IllegalStateException("Surface destroyed")
          }
          own = it
          delegate = it
        }
        with(current) {
          draw(
            object : HazeEffectRuntimeDrawScope by runtime {
              override val layerSize: Size = Size(100f, 100f)
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

  private fun RenderScriptContext.isDestroyed(): Boolean =
    RenderScriptContext::class.java.getDeclaredField("isDestroyed").run {
      isAccessible = true
      getBoolean(this@isDestroyed)
    }
}
