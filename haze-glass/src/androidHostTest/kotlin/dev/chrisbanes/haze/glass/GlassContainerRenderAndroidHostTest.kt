// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.ExperimentalHazeApi::class, dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeLogger
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.math.roundToInt
import org.junit.Test
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

class GlassContainerRenderAndroidHostTest : ScreenshotTest() {
  @Test
  @Config(sdk = [35])
  fun sdk35_clusterUsesOneRuntimeAndRetainsBridgeAtBalancedScale() = assertCluster()

  @Test
  @Config(sdk = [37])
  fun sdk37_clusterUsesOneRuntimeAndRetainsBridgeAtBalancedScale() = assertCluster()

  private fun assertCluster() = runScreenshotTest(size = Size(360f, 240f)) {
    val state = HazeState()
    val effects = List(2) { GlassRuntimeEffect() }
    setContent {
      Box(Modifier.size(360.dp, 240.dp).testTag("root").background(Color.White)) {
        Box(Modifier.size(360.dp, 240.dp).hazeSource(state).background(Color.White))
        Box(Modifier.size(360.dp, 240.dp).hazeGlassContainer(40.dp)) {
          effects.forEachIndexed { index, effect ->
            Box(
              Modifier.offset((40 + index * 110).dp, 60.dp).size(100.dp, 80.dp)
                .hazeGlass(HazeEffectFactory { effect }, HazeInput.Sources(state), style(Color.Green), HazePerformanceMode.Balanced, true, null),
            )
          }
        }
      }
    }
    val pixels = captureRootPixels()
    val bounds = onNodeWithTag("root").fetchSemanticsNode().boundsInRoot
    val pixel = pixels[(bounds.left + 145f * bounds.width / 360f).roundToInt(), (bounds.top + 100f * bounds.height / 240f).roundToInt()]
    assertThat(pixel.green).isGreaterThan(0.95f)
    assertThat(pixel.red).isLessThan(0.1f)
    assertThat(pixel.blue).isLessThan(0.1f)
    val owner = checkNotNull(effects.first().memberBindingForTest?.node?.container)
    assertThat(owner.renderer.runtimeSurfaces.size).isEqualTo(1)
    val runtime = owner.renderer.runtimeSurfaces.single()
    val params = checkNotNull(runtime.preparedRender).params
    assertThat(checkNotNull(params.mergedField).members.size).isEqualTo(2)
    assertThat(params.coordinates.scaleFactor).isEqualTo(HazePerformanceMode.Balanced.resolveGlassInputScale())
    assertThat((runtime.delegate as RuntimeShaderGlassDelegate).layers.source?.isReleased).isEqualTo(false)
  }

  @Test
  @Config(sdk = [35])
  fun capDiagnostic_emitsOnceUntilExcessMembershipChanges() = runScreenshotTest(size = Size(780f, 180f)) {
    val previous = HazeLogger.enabled
    HazeLogger.enabled = true
    ShadowLog.clear()
    try {
      val count = mutableStateOf(6)
      val effects = List(7) { GlassRuntimeEffect() }
      val state = HazeState()
      setContent {
        Box(Modifier.size(780.dp, 180.dp).background(Color.White)) {
          Box(Modifier.size(780.dp, 180.dp).hazeSource(state).background(Color.White))
          Box(Modifier.size(780.dp, 180.dp).hazeGlassContainer(40.dp)) {
            repeat(count.value) { index ->
              Box(
                Modifier.offset((10 + index * 110).dp, 50.dp).size(100.dp, 80.dp)
                  .hazeGlass(HazeEffectFactory { effects[index] }, HazeInput.Sources(state), style(Color.Blue), HazePerformanceMode.Quality, true, null),
              )
            }
          }
        }
      }
      captureRootPixels()
      val afterAttach = ShadowLog.getLogsForTag("GlassContainer").count { "cap 4 by 2" in it.msg }
      assertThat(afterAttach).isEqualTo(1)
      repeat(2) {
        composeTestRule.runOnIdle { composeTestRule.activity.window.decorView.invalidate() }
        waitForIdle()
      }
      assertThat(ShadowLog.getLogsForTag("GlassContainer").count { "cap 4 by 2" in it.msg }).isEqualTo(1)
      composeTestRule.runOnIdle { count.value = 7 }
      waitForIdle()
      assertThat(ShadowLog.getLogsForTag("GlassContainer").count { "cap 4 by 3" in it.msg }).isEqualTo(1)
    } finally {
      HazeLogger.enabled = previous
    }
  }

  private fun style(colour: Color) = GlassStyle {
    optics(GlassOptics(refractionStrength = 0f, refractionDisplacement = 0.dp, refractionDetailIntensity = 0f, depth = OpticalSizeValue.Fixed(0f), blurRadius = OpticalSizeValue.Fixed(0.dp)))
    tint(colour)
    shape(RoundedCornerShape(0.dp))
    edgeSoftness(0.dp)
    edgeShadow(Color.Transparent)
    specularIntensity(0f)
    ambientResponse(0f)
    chromaticAberrationStrength(0f)
  }
}
