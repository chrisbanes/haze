// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.runtime.CompositionLocal
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isNull
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectLifecycleScope
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.HazeSampling
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.PlatformContext
import dev.chrisbanes.haze.TrimMemoryLevel
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope

@OptIn(ExperimentalHazeApi::class, InternalHazeApi::class)
class GlassRendererLifecycleTest {

  @Test
  fun factory_createsIndependentNodeOwnedRenderers() {
    val first = GlassHazeEffectFactory.createRenderer()
    val second = GlassHazeEffectFactory.createRenderer()

    assertThat(first).isInstanceOf<GlassRuntimeEffect>()
    assertThat(second).isInstanceOf<GlassRuntimeEffect>()
    assertThat(first).isNotSameInstanceAs(second)
  }

  @Test
  fun update_resolvesDefaultsThenLocalThenExplicitStyle() {
    val renderer = GlassRuntimeEffect()
    val scope = TrackingLifecycleScope(
      localStyle = GlassStyle { tint(Color.Red) },
    )
    renderer.attach(scope)

    renderer.update(
      scope = scope,
      style = GlassNodeConfiguration(
        style = GlassStyle {
          alpha(0.4f)
          contrast(0.2f)
        },
        interactionSource = null,
      ),
      sampling = HazeSampling.Default,
    )

    assertThat(renderer.tint).isEqualTo(Color.Red)
    assertThat(renderer.alpha).isEqualTo(0.4f)
    assertThat(renderer.contrast).isEqualTo(0.2f)
    renderer.detach()
  }

  @Test
  fun styleReplacement_omittedValuesFallBackWithoutReplacingRenderer() {
    val renderer = GlassRuntimeEffect()
    val scope = TrackingLifecycleScope(
      localStyle = GlassStyle { tint(Color.Red) },
    )
    renderer.attach(scope)
    renderer.update(
      scope,
      GlassNodeConfiguration(
        style = GlassStyle {
          tint(Color.Blue)
          alpha(0.4f)
        },
        interactionSource = null,
      ),
      HazeSampling.Default,
    )

    renderer.update(
      scope,
      GlassNodeConfiguration(
        style = GlassStyle { contrast(0.2f) },
        interactionSource = null,
      ),
      HazeSampling.Adaptive,
    )

    assertThat(renderer.tint).isEqualTo(Color.Red)
    assertThat(renderer.alpha).isEqualTo(GlassDefaults.alpha)
    assertThat(renderer.contrast).isEqualTo(0.2f)
    assertThat(renderer.interactionSource).isNull()
    renderer.detach()
  }

  @Test
  fun detach_releasesOwnedResourcesExactlyOnce() {
    val renderer = GlassRuntimeEffect()
    val delegate = CountingGlassDelegate()
    renderer.delegate = delegate
    renderer.attach(TrackingLifecycleScope())

    renderer.detach()
    renderer.detach()

    assertThat(delegate.releaseCalls).isEqualTo(1)
    assertThat(delegate.resourceReleaseCalls).isEqualTo(1)
  }

  @Test
  fun trimMemory_releasesRetainedResourcesWithoutDetachingRenderer() {
    val renderer = GlassRuntimeEffect()
    val delegate = CountingGlassDelegate()
    renderer.delegate = delegate
    renderer.attach(TrackingLifecycleScope())

    renderer.onTrimMemory(TrimMemoryLevel.UI_HIDDEN)

    assertThat(delegate.trimCalls).isEqualTo(1)
    assertThat(delegate.resourceReleaseCalls).isEqualTo(1)
    assertThat(delegate.releaseCalls).isEqualTo(0)
    renderer.detach()
  }
}

@OptIn(InternalHazeApi::class)
private class TrackingLifecycleScope(
  override val modifierSize: Size = Size(100f, 100f),
  var localStyle: GlassStyle = GlassDefaults.style,
) : HazeEffectLifecycleScope {
  override val coroutineScope: CoroutineScope = CoroutineScope(EmptyCoroutineContext)

  override fun requirePlatformContext(): PlatformContext = error("Unused in lifecycle test")

  override fun requireGraphicsContext(): GraphicsContext = error("Unused in lifecycle test")

  override fun requireDensity(): Density = Density(1f)

  @Suppress("UNCHECKED_CAST")
  override fun <T> currentValueOf(local: CompositionLocal<T>): T = when (local) {
    LocalGlassStyle -> localStyle
    LocalLayoutDirection -> LayoutDirection.Ltr
    else -> error("Unused composition local")
  } as T

  override fun invalidateDraw() = Unit

  override fun invalidateLayerBounds() = Unit
}

@OptIn(InternalHazeApi::class)
private class CountingGlassDelegate : GlassRuntimeEffect.Delegate, RetainedOutputDelegate {
  private var ownsResource = true

  var releaseCalls = 0
    private set

  var trimCalls = 0
    private set

  var resourceReleaseCalls = 0
    private set

  override fun DrawScope.draw(context: HazeEffectRuntimeDrawScope) = Unit

  override fun release() {
    releaseCalls++
    releaseResource()
  }

  override fun onTrimMemory(
    context: HazeEffectLifecycleScope,
    level: TrimMemoryLevel,
  ) {
    trimCalls++
    if (shouldReleaseRetainedGlass(level)) {
      releaseResource()
    }
  }

  override fun canDrawRetainedOutput(): Boolean = ownsResource

  override fun clearRetainedOutput() {
    releaseResource()
  }

  private fun releaseResource() {
    if (ownsResource) {
      ownsResource = false
      resourceReleaseCalls++
    }
  }
}
