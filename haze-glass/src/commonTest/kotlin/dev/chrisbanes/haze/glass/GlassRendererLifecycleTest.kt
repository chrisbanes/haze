// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateObserver
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isNull
import assertk.assertions.isTrue
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectLifecycleScope
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeSampling
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.LocalHazePerformanceMode
import dev.chrisbanes.haze.PlatformContext
import dev.chrisbanes.haze.TrimMemoryLevel
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope

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
  fun mergedFieldOpticalSizeChange_invalidatesLayerBoundsAndPadding() {
    val renderer = GlassRuntimeEffect().apply { appearanceReader = { GlassSystemAppearance.Light } }
    val scope = TrackingLifecycleScope()
    renderer.attach(scope)
    fun field(opticalSide: Float) = mergedField(
      mergedMember(0f, 0f, 40f, 40f),
      opticalSize = Size(opticalSide, opticalSide),
    )
    val rect = Rect(Offset.Zero, scope.modifierSize)

    renderer.mergedField = field(opticalSide = 20f)
    val invalidations = scope.layerBoundsInvalidations
    val smallBounds = renderer.calculateLayerBounds(rect, Density(1f))
    // The node keeps its bounds; only the optical size, and so the size-dependent padding, changes.
    renderer.mergedField = field(opticalSide = 400f)

    assertThat(scope.layerBoundsInvalidations).isEqualTo(invalidations + 1)
    assertThat(renderer.calculateLayerBounds(rect, Density(1f))).isNotEqualTo(smallBounds)
    renderer.detach()
  }

  @Test
  fun update_resolvesDefaultsThenLocalThenExplicitStyle() {
    val renderer = GlassRuntimeEffect().apply { appearanceReader = { GlassSystemAppearance.Light } }
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
    val renderer = GlassRuntimeEffect().apply { appearanceReader = { GlassSystemAppearance.Light } }
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
  fun interactionConfigurationOutsideUpdate_invalidatesDrawAndReachesController() {
    val renderer = GlassRuntimeEffect().apply {
      appearanceReader = { GlassSystemAppearance.Light }
      style = GlassStyle { pressed { scale(0.9f) } }
    }
    val testScope = TestScope()
    val scope = TrackingLifecycleScope(coroutineScope = testScope)
    renderer.attach(scope)
    val configuration = GlassNodeConfiguration(
      style = GlassStyle { pressed { scale(0.9f) } },
      interactionSource = null,
    )
    var observedChanges = 0
    val observer = SnapshotStateObserver { it() }
    val onChanged: (GlassRuntimeEffect) -> Unit = { observedChanges++ }
    observer.start()
    try {
      observer.observeReads(renderer, onChanged) {
        renderer.update(scope, configuration, HazeSampling.Default)
      }
      Snapshot.sendApplyNotifications()
      observedChanges = 0

      // The Interaction bit is still pending, so none of these re-run the observed update. Each
      // change must still reach the draw phase.
      val before = scope.drawInvalidations
      renderer.interactionSource = MutableInteractionSource()
      renderer.interactionTransformTarget = GlassTransformTarget.MaterialAndContent
      renderer.interactionTransformPivot = GlassTransformPivot.Center
      renderer.interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced
      assertThat(scope.drawInvalidations).isEqualTo(before + 4)
      assertThat(checkNotNull(renderer.interactionControllerForTest).configurationForTest.reducedMotion)
        .isTrue()

      // An equal value is not a change.
      renderer.interactionTransformTarget = GlassTransformTarget.MaterialAndContent
      assertThat(scope.drawInvalidations).isEqualTo(before + 4)

      // After a draw resets the tracker, the next change re-runs the observed update once.
      Snapshot.sendApplyNotifications()
      observedChanges = 0
      renderer.resetDirtyTracker()
      renderer.interactionTransformPivot = GlassTransformPivot.Pointer
      Snapshot.sendApplyNotifications()
      assertThat(observedChanges).isEqualTo(1)
    } finally {
      observer.stop()
      renderer.detach()
      testScope.cancel()
    }
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
  var localStyle: GlassStyle = GlassStyle,
  var accessibilitySettings: GlassAccessibilitySettings = GlassAccessibilitySettings(),
  override val coroutineScope: CoroutineScope = CoroutineScope(EmptyCoroutineContext),
) : HazeEffectLifecycleScope {
  override fun requirePlatformContext(): PlatformContext = error("Unused in lifecycle test")

  override fun requireGraphicsContext(): GraphicsContext = error("Unused in lifecycle test")

  override fun requireDensity(): Density = Density(1f)

  @Suppress("UNCHECKED_CAST")
  override fun <T> currentValueOf(local: CompositionLocal<T>): T = when (local) {
    LocalHazePerformanceMode -> HazePerformanceMode.Default
    LocalGlassStyle -> localStyle
    LocalGlassAccessibilitySettings -> accessibilitySettings
    LocalLayoutDirection -> LayoutDirection.Ltr
    else -> error("Unused composition local")
  } as T

  var layerBoundsInvalidations = 0
    private set

  var drawInvalidations = 0
    private set

  override fun invalidateDraw() {
    drawInvalidations++
  }

  override fun invalidateLayerBounds() {
    layerBoundsInvalidations++
  }
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
