// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.runtime.CompositionLocal
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.uikit.LocalUIView
import androidx.compose.ui.unit.Density
import assertk.assertThat
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isSameInstanceAs
import dev.chrisbanes.haze.HazeEffectLifecycleScope
import dev.chrisbanes.haze.PlatformContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import platform.UIKit.UIView

class GlassAdaptiveHostIosTest {

  @Test
  fun platformHostUsesCurrentComposeView() {
    val first = UIView()
    val second = UIView()
    val scope = IosHostScope(first)

    assertThat(platformGlassAdaptiveHost(scope)).isSameInstanceAs(first)
    scope.view = second
    assertThat(platformGlassAdaptiveHost(scope)).isSameInstanceAs(second)
    assertThat(second).isNotSameInstanceAs(first)
  }
}

private class IosHostScope(
  var view: UIView,
) : HazeEffectLifecycleScope {
  override val modifierSize: Size = Size(10f, 10f)
  override val coroutineScope: CoroutineScope = CoroutineScope(EmptyCoroutineContext)
  override fun requirePlatformContext(): PlatformContext = error("Unused")
  override fun requireGraphicsContext(): GraphicsContext = error("Unused")
  override fun requireDensity(): Density = Density(1f)

  @Suppress("UNCHECKED_CAST")
  override fun <T> currentValueOf(local: CompositionLocal<T>): T = when (local) {
    LocalUIView -> view
    else -> error("Unexpected local")
  } as T

  override fun invalidateDraw() = Unit
  override fun invalidateLayerBounds() = Unit
}
