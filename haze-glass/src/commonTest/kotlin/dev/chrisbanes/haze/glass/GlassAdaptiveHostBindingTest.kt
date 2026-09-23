// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.runtime.CompositionLocal
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import dev.chrisbanes.haze.HazeEffectLifecycleScope
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeSampling
import dev.chrisbanes.haze.LocalHazePerformanceMode
import dev.chrisbanes.haze.PlatformContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope

class GlassAdaptiveHostBindingTest {

  @Test
  fun bindings_shareExplicitHostAndReleaseOnlyAfterLastDetach() {
    val registry = GlassAdaptiveHostRegistry()
    val owner = BindingLifecycleOwner()
    owner.start()
    val token = GlassAdaptiveHostToken()
    val scope = BindingScope(token, owner)
    val first = GlassAdaptiveHostBinding(registry)
    val second = GlassAdaptiveHostBinding(registry)

    first.update(scope, adaptive = true)
    second.update(scope, adaptive = true)
    assertThat(first.host).isSameInstanceAs(second.host)
    assertThat(registry.hostCount).isEqualTo(1)

    first.detach()
    assertThat(registry.hostCount).isEqualTo(1)
    second.detach()
    assertThat(registry.hostCount).isEqualTo(0)
  }

  @Test
  fun binding_rebindsAndFallsBackWhenExplicitHostDisappears() {
    val registry = GlassAdaptiveHostRegistry()
    val owner = BindingLifecycleOwner()
    owner.start()
    val scope = BindingScope(GlassAdaptiveHostToken(), owner)
    val binding = GlassAdaptiveHostBinding(registry)

    binding.update(scope, adaptive = true)
    val original = binding.host
    scope.token = GlassAdaptiveHostToken()
    binding.update(scope, adaptive = true)
    assertThat(registry.hostCount).isEqualTo(1)
    assertThat(binding.host === original).isEqualTo(false)

    scope.token = null
    binding.update(scope, adaptive = true)
    assertThat(binding.host).isNull()
    assertThat(registry.hostCount).isEqualTo(0)
  }

  @Test
  fun disposedHostCanBeReplacedByAnotherHost() {
    val registry = GlassAdaptiveHostRegistry()
    val owner = BindingLifecycleOwner()
    owner.start()
    val firstToken = GlassAdaptiveHostToken()
    val scope = BindingScope(firstToken, owner)
    val binding = GlassAdaptiveHostBinding(registry)
    binding.update(scope, adaptive = true)

    registry.disposeHost(firstToken)
    assertThat(registry.hostCount).isEqualTo(0)

    val secondToken = GlassAdaptiveHostToken()
    scope.token = secondToken
    binding.update(scope, adaptive = true)

    assertThat(binding.host?.key).isSameInstanceAs(secondToken)
    assertThat(registry.hostCount).isEqualTo(1)
    binding.detach()
  }

  @Test
  fun rendererRegistrationReleasesOnFixedModeAndDetach() {
    val baseline = glassAdaptiveHostRegistry.hostCount
    val owner = BindingLifecycleOwner()
    owner.start()
    val scope = BindingScope(GlassAdaptiveHostToken(), owner)
    val first = GlassRuntimeEffect().apply {
      appearanceReader = { GlassSystemAppearance.Light }
    }
    val second = GlassRuntimeEffect().apply {
      appearanceReader = { GlassSystemAppearance.Light }
    }

    first.attach(scope)
    second.attach(scope)
    val adaptive = GlassNodeConfiguration(style = GlassStyle, interactionSource = null)
    first.update(scope, adaptive, HazeSampling.Adaptive)
    second.update(scope, adaptive, HazeSampling.Adaptive)
    assertThat(glassAdaptiveHostRegistry.hostCount).isEqualTo(baseline + 1)

    first.update(
      scope,
      GlassNodeConfiguration(
        style = GlassStyle,
        performanceMode = HazePerformanceMode.Fixed(1f),
        interactionSource = null,
      ),
      HazeSampling.Adaptive,
    )
    assertThat(glassAdaptiveHostRegistry.hostCount).isEqualTo(baseline + 1)

    second.detach()
    assertThat(glassAdaptiveHostRegistry.hostCount).isEqualTo(baseline)
    first.detach()
    assertThat(glassAdaptiveHostRegistry.hostCount).isEqualTo(baseline)
  }
}

private class BindingLifecycleOwner : LifecycleOwner {
  private val registry = LifecycleRegistry.createUnsafe(this)
  override val lifecycle: Lifecycle get() = registry

  fun start() {
    registry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
    registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
  }
}

private class BindingScope(
  var token: GlassAdaptiveHostToken?,
  private val owner: LifecycleOwner,
) : HazeEffectLifecycleScope {
  override val modifierSize: Size = Size(10f, 10f)
  override val coroutineScope: CoroutineScope = CoroutineScope(EmptyCoroutineContext)
  override fun requirePlatformContext(): PlatformContext = error("Unused")
  override fun requireGraphicsContext(): GraphicsContext = error("Unused")
  override fun requireDensity(): Density = Density(1f)

  @Suppress("UNCHECKED_CAST")
  override fun <T> currentValueOf(local: CompositionLocal<T>): T = when (local) {
    LocalGlassAdaptiveHostToken -> token
    LocalLifecycleOwner -> owner
    LocalHazePerformanceMode -> HazePerformanceMode.Adaptive
    LocalGlassStyle -> GlassStyle
    LocalGlassAccessibilitySettings -> GlassAccessibilitySettings()
    LocalLayoutDirection -> LayoutDirection.Ltr
    else -> error("Unavailable local")
  } as T

  override fun invalidateDraw() = Unit
  override fun invalidateLayerBounds() = Unit
}
