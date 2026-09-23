// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import android.app.Activity
import android.app.Dialog as AndroidDialog
import android.view.View
import android.view.Window
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocal
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.window.Dialog
import assertk.assertThat
import assertk.assertions.isEqualTo
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
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class GlassAdaptiveHostAndroidTest {

  @OptIn(ExperimentalTestApi::class)
  @Test
  fun verifiedWindow_matchesActivityAndComposeDialogSeparately() =
    runAndroidComposeUiTest<ComponentActivity> {
      lateinit var activityView: View
      lateinit var dialogView: View
      setContent {
        val root = LocalView.current
        SideEffect { activityView = root }
        Dialog(onDismissRequest = {}) {
          val dialogRoot = LocalView.current
          SideEffect { dialogView = dialogRoot }
        }
      }
      waitForIdle()

      val activityWindow = verifiedGlassWindow(activityView)
      val dialogWindow = verifiedGlassWindow(dialogView)
      assertThat(activityWindow).isNotNull()
      assertThat(dialogWindow).isNotNull()
      assertThat(dialogWindow).isNotSameInstanceAs(activityWindow)
      assertThat(activityWindow?.decorView).isSameInstanceAs(activityView.rootView)
      assertThat(dialogWindow?.decorView).isSameInstanceAs(dialogView.rootView)
      assertThat(platformGlassAdaptiveTimingSource(AndroidHostScope(activityView), activityView.windowToken)).isNotNull()
      assertThat(platformGlassAdaptiveTimingSource(AndroidHostScope(dialogView), dialogView.windowToken)).isNotNull()
      assertThat(platformGlassAdaptiveTimingSource(AndroidHostScope(dialogView), activityView.windowToken)).isNull()
    }

  @Test
  fun verifiedWindow_rejectsNativeDialogAndDetachedView() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
    val otherActivity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
    val unmatchedView = View(activity)
    otherActivity.setContentView(unmatchedView)
    val dialog = AndroidDialog(activity)
    val dialogView = View(activity)
    dialog.setContentView(dialogView)
    dialog.show()

    assertThat(verifiedGlassWindow(dialogView)).isNull()
    assertThat(verifiedGlassWindow(View(activity))).isNull()
    assertThat(verifiedGlassWindow(unmatchedView)).isNull()
    assertThat(platformGlassAdaptiveTimingSource(AndroidHostScope(unmatchedView), unmatchedView.windowToken)).isNull()
    dialog.dismiss()
  }

  @Test
  fun androidTimingSource_attachesOnceAndRemovesItsListener() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
    var attached = 0
    var removed = 0
    var registered: Window.OnFrameMetricsAvailableListener? = null
    val source = AndroidGlassTimingSource(
      window = activity.window,
      addListener = {
        attached++
        registered = it
      },
      removeListener = {
        removed++
        assertThat(it).isSameInstanceAs(registered)
      },
    )

    assertThat(source.start { _, _ -> }).isEqualTo(true)
    assertThat(source.start { _, _ -> }).isEqualTo(false)
    assertThat(attached).isEqualTo(1)
    source.stop()
    source.stop()
    assertThat(removed).isEqualTo(1)
  }

  @Test
  fun androidFrameSample_excludesFirstDrawDroppedReportsAndMissingBudget() {
    val valid = androidGlassFrameSample(1, 100L, 16L, 17L, false, 0)
    val first = androidGlassFrameSample(2, 200L, 16L, 17L, true, 0)
    val dropped = androidGlassFrameSample(3, 300L, 16L, 17L, false, 1)
    val missingBudget = androidGlassFrameSample(4, 400L, 16L, 0L, false, 0)

    assertThat(valid.isValid).isEqualTo(true)
    assertThat(valid.durationNanos).isEqualTo(16L)
    assertThat(valid.budgetNanos).isEqualTo(17L)
    assertThat(first.isValid).isEqualTo(false)
    assertThat(dropped.isValid).isEqualTo(false)
    assertThat(missingBudget.isValid).isEqualTo(false)
  }

  @Config(sdk = [23])
  @Test
  fun timingSource_fallsBackBeforeFrameMetricsApi() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup().visible().get()
    val view = activity.window.decorView

    assertThat(platformGlassAdaptiveTimingSource(AndroidHostScope(view), view.windowToken)).isNull()
  }

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
