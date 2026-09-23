// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import android.app.Activity
import android.view.View
import androidx.compose.runtime.CompositionLocal
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import assertk.assertThat
import assertk.assertions.isNotNull
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import dev.chrisbanes.haze.HazeEffectLifecycleScope
import dev.chrisbanes.haze.PlatformContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.test.Test
import kotlinx.coroutines.CoroutineScope
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class GlassAdaptiveHostAndroidTest {

  @Test
  fun platformHost_usesAttachedWindowTokenAndSeparatesWindows() {
    val firstActivity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
    val secondActivity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
    val childView = View(firstActivity)
    firstActivity.setContentView(childView)
    val scope = AndroidHostScope(firstActivity.window.decorView)

    val firstHost = platformGlassAdaptiveHost(scope)
    assertThat(firstHost).isNotNull()
    scope.view = childView
    assertThat(platformGlassAdaptiveHost(scope)).isSameInstanceAs(firstHost)

    scope.view = secondActivity.window.decorView
    val secondHost = platformGlassAdaptiveHost(scope)
    assertThat(secondHost).isNotNull()
    assertThat(secondHost).isNotSameInstanceAs(firstHost)
  }

  @Test
  fun platformHost_returnsNullWhenWindowUnavailable() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
    assertThat(platformGlassAdaptiveHost(AndroidHostScope(View(activity)))).isNull()
  }
}

private class AndroidHostScope(
  var view: View,
) : HazeEffectLifecycleScope {
  override val modifierSize: Size = Size(10f, 10f)
  override val coroutineScope: CoroutineScope = CoroutineScope(EmptyCoroutineContext)
  override fun requirePlatformContext(): PlatformContext = error("Unused")
  override fun requireGraphicsContext(): GraphicsContext = error("Unused")
  override fun requireDensity(): Density = Density(1f)

  @Suppress("UNCHECKED_CAST")
  override fun <T> currentValueOf(local: CompositionLocal<T>): T = when (local) {
    LocalView -> view
    else -> error("Unexpected local")
  } as T

  override fun invalidateDraw() = Unit
  override fun invalidateLayerBounds() = Unit
}
