// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Renders a merged field through the Android fused renderer.
 *
 * Robolectric SDK 33 cannot create runtime-shader RenderEffects or compile `content.eval`, so the
 * host proof runs at SDK 35 and 37. API 33 devices rely on the prototype's device evidence and
 * CI's managed-device run.
 */
@RunWith(RobolectricTestRunner::class)
@OptIn(ExperimentalTestApi::class, ExperimentalHazeApi::class, InternalHazeApi::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class MergedGlassFusedAndroidHostTest : ContextTest() {
  @Test
  @Config(sdk = [35])
  fun sdk35_bridgeCoversMidpointOnlyBelowHalfMergeDistance() = assertBridgeCoverage()

  @Test
  @Config(sdk = [37])
  fun sdk37_bridgeCoversMidpointOnlyBelowHalfMergeDistance() = assertBridgeCoverage()

  @Test
  @Config(sdk = [37])
  fun sdk37_unsettingFieldRestoresSingleSurface() {
    val restored = midpointColor(gapDp = 6f, unsetAfterwards = true)
    // The single surface uses its own transparent-tinted style, so the green member tint is gone.
    assertThat(restored.red, "restored red: $restored").isGreaterThan(0.5f)
  }

  private fun assertBridgeCoverage() {
    // A bridged midpoint shows the members' opaque green tint, darkened slightly by refraction
    // detail. A separate midpoint is outside the glass, so no tint reaches it.
    val bridged = midpointColor(gapDp = 6f)
    assertThat(bridged.green, "bridged green: $bridged").isGreaterThan(0.85f)
    assertThat(bridged.red, "bridged red: $bridged").isLessThan(0.05f)
    val separate = midpointColor(gapDp = 10f)
    assertThat(separate.red, "separate red: $separate").isGreaterThan(0.5f)
  }

  private fun midpointColor(gapDp: Float, unsetAfterwards: Boolean = false): Color {
    var color = Color.Unspecified
    runAndroidComposeUiTest<ComponentActivity> {
      val effect = GlassRuntimeEffect().apply {
        style = GlassStyle {
          ambientResponse(0f)
          edgeSoftness(0.dp)
          specularIntensity(0f)
        }
      }
      setContent {
        Box(
          Modifier.size(width = 120.dp, height = 40.dp).testTag(TAG).hazeGlass(
            factory = HazeEffectFactory { effect },
            input = HazeInput.Content,
            style = effect.style,
            performanceMode = HazePerformanceMode.Quality,
            expandLayerBounds = true,
            interactionSource = null,
          ),
        ) {
          Box(Modifier.fillMaxSize().background(Color.Red))
        }
      }
      val density = runOnIdle { checkNotNull(activity).resources.displayMetrics.density }
      // Two 50x20 dp capsules 10 dp from the top, with 16 dp merge distance: they bridge below 8
      // dp.
      val gap = gapDp * density
      val width = 50f * density
      val height = 20f * density
      val top = 10f * density
      val left = (120f * density - width * 2f - gap) / 2f
      runOnIdle {
        effect.mergedField = mergedField(
          mergedMember(left, top, width, height, radius = height / 2f, tint = Color.Green),
          mergedMember(left + width + gap, top, width, height, radius = height / 2f, tint = Color.Green),
          mergeDistancePx = 16f * density,
          opticalSize = Size(width, height),
        )
      }
      waitForIdle()
      onNodeWithTag(TAG).captureToImage()
      assertThat((effect.delegate as? RuntimeShaderGlassDelegate)?.fusedShader).isNotNull()
      assertThat(effect.preparedRender?.opticalKey?.mergedField).isNotNull()
      if (unsetAfterwards) {
        runOnIdle { effect.mergedField = null }
        waitForIdle()
        assertThat(effect.preparedRender?.opticalKey?.mergedField).isNull()
      }
      val pixels = onNodeWithTag(TAG).captureToImage().toPixelMap()
      color = pixels[pixels.width / 2, pixels.height / 2]
    }
    return color
  }

  private companion object {
    const val TAG = "merged-glass"
  }
}
