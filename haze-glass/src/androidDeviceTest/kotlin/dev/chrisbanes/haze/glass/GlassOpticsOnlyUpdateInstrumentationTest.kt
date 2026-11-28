// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.filters.SdkSuppress
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThanOrEqualTo
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import kotlin.math.abs
import org.junit.Rule
import org.junit.Test

/**
 * A Glass optics change that keeps the fused recording only replaces the optical layer's effect, so
 * HWUI reapplies it to the retained layers (#1918). The result must match a fresh render of the same
 * optics, including on the Balanced layer-blur path whose reduced blurred copy stays retained.
 */
// Runtime-shader Glass, which this covers, starts at API 33.
@SdkSuppress(minSdkVersion = 33)
class GlassOpticsOnlyUpdateInstrumentationTest {
  @get:Rule
  val rule = createAndroidComposeRule<ComponentActivity>()

  private class Scene(val refraction: Float, val tint: Color, val fresh: Boolean)

  private val scene = mutableStateOf(Scene(0f, Color.Transparent, fresh = false))
  private val retainedEffect = GlassRuntimeEffect()

  @Test
  fun tintChange_layerBlur_keepsRecordingAndMatchesFreshRender() =
    assertMatchesFreshRender(refraction = 0f, tint = Color.Red.copy(alpha = 0.3f), expectReuse = true)

  @Test
  fun tintChange_withRefraction_keepsRecordingAndMatchesFreshRender() =
    assertMatchesFreshRender(refraction = 0.2f, tint = Color.Red.copy(alpha = 0.3f), expectReuse = true)

  // A refraction change can move the sample, which re-records; either way it must match.
  @Test
  fun refractionChange_matchesFreshRender() =
    assertMatchesFreshRender(refraction = 0.2f, tint = Color.Transparent, nextRefraction = 0.6f, expectReuse = null)

  private fun assertMatchesFreshRender(
    refraction: Float,
    tint: Color,
    nextRefraction: Float = refraction,
    expectReuse: Boolean?,
  ) {
    scene.value = Scene(refraction, Color.Transparent, fresh = false)
    setContent()
    rule.waitForIdle()
    val initial = capture()
    val delegate = retainedEffect.delegate as RuntimeShaderGlassDelegate
    val render = checkNotNull(retainedEffect.preparedRender)
    assertThat(render.layerBlurFactor, "layer blur factor").isGreaterThan(0f)
    val records = delegate.opticalRecordCount

    rule.runOnIdle { scene.value = Scene(nextRefraction, tint, fresh = false) }
    rule.waitForIdle()
    val retained = capture()
    val retainedRecords = delegate.opticalRecordCount

    rule.runOnIdle { scene.value = Scene(nextRefraction, tint, fresh = true) }
    rule.waitForIdle()
    val fresh = capture()

    val change = retained.maxDifferenceFrom(initial)
    val difference = retained.maxDifferenceFrom(fresh)
    Log.i(TAG, "factor=${render.layerBlurFactor} records $records -> $retainedRecords change=$change difference=$difference")
    assertThat(change, "positive control: the optics change is visible (x255)").isGreaterThan(5f)
    if (expectReuse != null) {
      assertThat(retainedRecords - records, "optical records").isEqualTo(if (expectReuse) 0 else 1)
    }
    assertThat(difference, "retained vs fresh max channel difference (x255)").isLessThanOrEqualTo(2f)
  }

  private fun setContent() {
    rule.setContent {
      val current = scene.value
      // The fresh scene gets its own state and effect, so its capture shares nothing with the retained one.
      key(current.fresh) {
        val state = remember { HazeState() }
        val effect = remember { if (current.fresh) GlassRuntimeEffect() else retainedEffect }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          Canvas(Modifier.fillMaxSize().hazeSource(state)) {
            drawRect(Color(0xFF333333))
            val period = 24.dp.toPx()
            var x = 0f
            while (x < size.width) {
              drawRect(Color(0xFFCC8844), Offset(x, 0f), Size(period / 2f, size.height))
              x += period
            }
          }
          Box(
            Modifier.size(200.dp, 120.dp).testTag("glass").hazeGlass(
              factory = HazeEffectFactory { effect },
              input = HazeInput.Sources(state),
              style = GlassStyle.regular.then {
                optics(GlassOptics.regular.copy(refractionStrength = current.refraction))
                tint(current.tint)
              },
              performanceMode = HazePerformanceMode.Balanced,
              // Covers the whole sample, which the layer-blur path requires.
              expandLayerBounds = true,
              interactionSource = null,
            ),
          )
        }
      }
    }
  }

  private fun capture(): PixelMap = rule.onNodeWithTag("glass").captureToImage().toPixelMap()

  /** The largest per-channel absolute difference from [other], in 1/255 steps. */
  private fun PixelMap.maxDifferenceFrom(other: PixelMap): Float {
    var maximum = 0f
    for (x in 0 until width) {
      for (y in 0 until height) {
        val a = this[x, y]
        val b = other[x, y]
        maximum = maxOf(maximum, abs(a.red - b.red), abs(a.green - b.green), abs(a.blue - b.blue), abs(a.alpha - b.alpha))
      }
    }
    return maximum * 255f
  }

  private companion object {
    const val TAG = "GlassOpticsOnlyUpdate"
  }
}
