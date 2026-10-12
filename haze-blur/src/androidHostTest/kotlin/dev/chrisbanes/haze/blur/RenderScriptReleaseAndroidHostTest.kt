// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:Suppress("DEPRECATION")
@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.blur

import android.content.Context
import android.renderscript.RSIllegalArgumentException
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.test.core.app.ApplicationProvider
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotNull
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import dev.chrisbanes.haze.TrimMemoryLevel
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test
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

  private fun delegate() = RenderScriptBlurVisualEffectDelegate(
    blurVisualEffect = BlurVisualEffect(),
    graphicsContext = UnusedGraphicsContext,
    platformContext = context,
  )

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
