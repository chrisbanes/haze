// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, dev.chrisbanes.haze.ExperimentalHazeApi::class, dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.Assert
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThan
import assertk.assertions.isNotNull
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import assertk.assertions.support.expected
import assertk.assertions.support.show
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeSourceRetention
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.RuntimeShaderRenderEffectException
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.test.ContextTest
import kotlin.math.abs
import kotlin.test.Test
import kotlinx.coroutines.runBlocking

internal fun containerTestStyle(tint: Color, alpha: Float = 1f): GlassStyle = GlassStyle {
  optics(GlassOptics(refractionStrength = 0f, refractionDisplacement = 0.dp, refractionDetailIntensity = 0f, depth = OpticalSizeValue.Fixed(0f), blurRadius = OpticalSizeValue.Fixed(0.dp)))
  tint(tint)
  backgroundColor(Color.Transparent)
  shape(RoundedCornerShape(0.dp))
  edgeSoftness(0.dp)
  edgeShadow(Color.Transparent)
  specularIntensity(0f)
  ambientResponse(0f)
  chromaticAberrationStrength(0f)
  contrast(0f)
  whitePoint(0f)
  chromaMultiplier(1f)
  alpha(alpha)
}

class GlassContainerRenderTest : ContextTest() {
  @Test
  fun contentCluster_recordsBothMemberInputsInClusterCoordinates() = runComposeUiTest {
    val first = GlassRuntimeEffect()
    val second = GlassRuntimeEffect()
    setContent {
      Box(Modifier.size(360.dp, 240.dp).hazeGlassContainer(40.dp)) {
        Box(Modifier.offset(40.dp, 60.dp).size(100.dp, 80.dp).hazeGlass(HazeEffectFactory { first }, HazeInput.Content, containerTestStyle(Color.Transparent), HazePerformanceMode.Quality, true, null).background(Color.Red))
        Box(Modifier.offset(150.dp, 60.dp).size(100.dp, 80.dp).hazeGlass(HazeEffectFactory { second }, HazeInput.Content, containerTestStyle(Color.Transparent), HazePerformanceMode.Quality, true, null).background(Color.Blue))
      }
    }
    waitForIdle()
    val runtime = checkNotNull(first.memberBindingForTest?.node?.container).renderer.runtimeSurfaces.single()
    val coordinates = checkNotNull(runtime.preparedRender).params.coordinates
    val source = checkNotNull((runtime.delegate as RuntimeShaderGlassDelegate).layers.source)
    val pixels = runBlocking { source.toImageBitmap().toPixelMap() }
    // Material starts at owner(0,20): full spacing padding precedes both member captures.
    val firstX = (80f * coordinates.scaleFactor + coordinates.materialOrigin.x).toInt()
    val secondX = (200f * coordinates.scaleFactor + coordinates.materialOrigin.x).toInt()
    val y = (80f * coordinates.scaleFactor + coordinates.materialOrigin.y).toInt()
    assertThat(pixels[firstX, y]).isEqualTo(Color.Red)
    assertThat(pixels[secondX, y]).isEqualTo(Color.Blue)
  }

  @Test
  fun zeroAlphaCluster_skipsCaptureAndBecomesVisibleWithoutReattachment() = runComposeUiTest {
    val opacity = mutableStateOf(0f)
    val fixture = pair(extraStyle = GlassStyle { alpha(opacity.value) })
    val runtime = fixture.owner().renderer.runtimeSurfaces.single()
    assertThat(runtime.preparedRender).isNull()
    assertThat(onNodeWithTag("root").captureToImage().toPixelMap()[80, 100]).isEqualTo(Color.White)
    runOnIdle { opacity.value = 1f }
    waitForIdle()
    assertThat(fixture.owner().renderer.runtimeSurfaces.single()).isSameInstanceAs(runtime)
    assertThat(runtime.preparedRender).isNotNull()
    assertThat(onNodeWithTag("root").captureToImage().toPixelMap()[80, 100]).isEqualTo(Color.Red)
  }

  @Test
  fun unavailableSources_respectBothClusterRetentionPolicies() {
    for (retention in listOf(HazeSourceRetention.KeepLastFrame, HazeSourceRetention.ClearWhenUnavailable)) {
      runComposeUiTest {
        val state = HazeState()
        val available = mutableStateOf(true)
        val first = GlassRuntimeEffect()
        val second = GlassRuntimeEffect()
        val input = HazeInput.Sources(state, retention = retention)
        setContent {
          Box(Modifier.size(360.dp, 240.dp).testTag("root").background(Color.White)) {
            if (available.value) Box(Modifier.size(360.dp, 240.dp).hazeSource(state).background(Color.Red))
            Box(Modifier.size(360.dp, 240.dp).background(Color.White))
            Box(Modifier.size(360.dp, 240.dp).hazeGlassContainer(40.dp)) {
              Box(Modifier.offset(40.dp, 60.dp).size(100.dp, 80.dp).hazeGlass(HazeEffectFactory { first }, input, containerTestStyle(Color.Transparent), HazePerformanceMode.Quality, true, null))
              Box(Modifier.offset(150.dp, 60.dp).size(100.dp, 80.dp).hazeGlass(HazeEffectFactory { second }, input, containerTestStyle(Color.Transparent), HazePerformanceMode.Quality, true, null))
            }
          }
        }
        waitForIdle()
        assertThat(onNodeWithTag("root").captureToImage().toPixelMap()[80, 100]).isEqualTo(Color.Red)
        runOnIdle { available.value = false }
        waitForIdle()
        val expected = if (retention == HazeSourceRetention.KeepLastFrame) Color.Red else Color.White
        assertThat(onNodeWithTag("root").captureToImage().toPixelMap()[80, 100]).isEqualTo(expected)
      }
    }
  }

  @Test
  fun transientNativeComposition_countsBufferAlongsideRetainedPlans() = runComposeUiTest {
    val fixture = pair()
    val renderer = fixture.owner().renderer
    assertThat(renderer.canComposeNativeSurfaces(Rect(0f, 0f, 360f, 240f))).isTrue()
    // The buffer alone exactly fits; any intersecting retained stage pushes the group over budget.
    assertThat(renderer.canComposeNativeSurfaces(Rect(0f, 0f, 4096f, 4096f))).isFalse()
  }

  @Test
  fun overBudgetCluster_drawsHardUnionAndRecoversWithoutDetach() = runComposeUiTest {
    val displacement = mutableStateOf(16_384)
    val fixture = pair(
      extraStyle = GlassStyle {
        optics(GlassOptics(refractionStrength = 1f, refractionDisplacement = displacement.value.dp, refractionDetailIntensity = 0f, depth = OpticalSizeValue.Fixed(0f), blurRadius = OpticalSizeValue.Fixed(0.dp)))
      },
    )
    val runtime = fixture.owner().renderer.runtimeSurfaces.single()
    assertThat(runtime.preparedRenderBudget).isInstanceOf<GlassRenderBudgetDecision.Fallback>()
    assertThat(runtime.delegate).isInstanceOf<FallbackGlassDelegate>()
    val fallbackPixels = onNodeWithTag("root").captureToImage().toPixelMap()
    assertThat(fallbackPixels[145, 100]).isEqualTo(Color.White)
    assertThat(fallbackPixels[80, 100].red).isGreaterThan(0.8f)
    runOnIdle { displacement.value = 0 }
    waitForIdle()
    assertThat(fixture.owner().renderer.runtimeSurfaces.single()).isSameInstanceAs(runtime)
    assertThat(runtime.preparedRenderBudget).isInstanceOf<GlassRenderBudgetDecision.Runtime>()
    assertThat(runtime.delegate).isInstanceOf<RuntimeShaderGlassDelegate>()
    assertThat(onNodeWithTag("root").captureToImage().toPixelMap()[145, 100].green).isLessThan(0.2f)
  }

  @Test
  fun changedSourceState_clearsPriorRetainedPixelsWithUnchangedMembershipAndBounds() = runComposeUiTest {
    val oldState = HazeState()
    val selected = mutableStateOf(oldState)
    val first = GlassRuntimeEffect()
    val second = GlassRuntimeEffect()
    setContent {
      Box(Modifier.size(360.dp, 240.dp).testTag("root").background(Color.White)) {
        Box(Modifier.size(360.dp, 240.dp).hazeSource(oldState).background(Color.Red))
        Box(Modifier.size(360.dp, 240.dp).background(Color.White))
        Box(Modifier.size(360.dp, 240.dp).hazeGlassContainer(40.dp)) {
          Box(Modifier.offset(40.dp, 60.dp).size(100.dp, 80.dp).hazeGlass(HazeEffectFactory { first }, HazeInput.Sources(selected.value), containerTestStyle(Color.Transparent), HazePerformanceMode.Quality, true, null))
          Box(Modifier.offset(150.dp, 60.dp).size(100.dp, 80.dp).hazeGlass(HazeEffectFactory { second }, HazeInput.Sources(selected.value), containerTestStyle(Color.Transparent), HazePerformanceMode.Quality, true, null))
        }
      }
    }
    waitForIdle()
    val owner = checkNotNull(first.memberBindingForTest?.node?.container)
    val runtime = owner.renderer.runtimeSurfaces.single()
    assertThat(onNodeWithTag("root").captureToImage().toPixelMap()[80, 100]).isEqualTo(Color.Red)
    runOnIdle { selected.value = HazeState() }
    waitForIdle()
    assertThat(owner.renderer.runtimeSurfaces.single()).isSameInstanceAs(runtime)
    assertThat(onNodeWithTag("root").captureToImage().toPixelMap()[80, 100]).isEqualTo(Color.White)
  }

  @Test
  fun merge_bridgeColoursAndSingleCapture_useOneIndependentRuntime() = runComposeUiTest {
    val fixture = pair(gap = 19)
    val owner = fixture.owner()
    assertThat(owner.renderer.surfaceCount).isEqualTo(1)
    val runtime = owner.renderer.runtimeSurfaces.single()
    assertThat(runtime).isInstanceOf<GlassRuntimeEffect>()
    assertThat(runtime === fixture.first).isEqualTo(false)
    val delegate = runtime.delegate as RuntimeShaderGlassDelegate
    assertThat(delegate.layers.source).isNotNull()
    val pixels = onNodeWithTag("root").captureToImage().toPixelMap()
    assertThat(pixels[80, 100].red).isGreaterThan(0.9f)
    assertThat(pixels[220, 100].blue).isGreaterThan(0.9f)
    val bridge = pixels[149, 100]
    assertThat(bridge.red).isGreaterThan(0.3f)
    assertThat(bridge.blue).isGreaterThan(0.3f)
    // Gap19 is just inside the strict boundary and its edge pixel has partial coverage.
    assertThat(bridge.green).isLessThan(0.8f)
    assertThat(pixels[45, 65]).isEqualTo(Color.Green)
    assertThat(fixture.first.mergedField).isNull()
    assertThat(fixture.second.mergedField).isNull()
  }

  @Test
  fun balanced_coordinatesAndCaptureScaleOnce() = runComposeUiTest {
    val fixture = pair(gap = 10, performance = HazePerformanceMode.Balanced)
    val runtime = fixture.owner().renderer.runtimeSurfaces.single()
    val params = checkNotNull(runtime.preparedRender).params
    val scale = HazePerformanceMode.Balanced.resolveGlassInputScale()
    assertThat(params.coordinates.scaleFactor).isEqualTo(scale)
    assertThat(checkNotNull(params.mergedField).members.first().rect)
      .isEqualTo(Rect(40f * scale, 40f * scale, 140f * scale, 120f * scale))
    assertThat(params.coordinates.materialSize.width).isEqualTo(290f * scale)
    assertThat(params.coordinates.materialSize.height).isEqualTo(160f * scale)
  }

  @Test
  fun splitMoveMergeAndDetach_releaseObsoleteSurfaceResources() = runComposeUiTest {
    val gap = mutableStateOf(10)
    val enabled = mutableStateOf(true)
    val fixture = pair(gapState = gap, enabled = enabled)
    val owner = fixture.owner()
    val firstRuntime = owner.renderer.runtimeSurfaces.single()
    val source = checkNotNull((firstRuntime.delegate as RuntimeShaderGlassDelegate).layers.source)
    runOnIdle { gap.value = 30 }
    waitForIdle()
    assertThat(owner.renderer.surfaceCount).isEqualTo(2)
    assertThat(owner.renderer.runtimeSurfaces.size).isEqualTo(0)
    assertThat(source.isReleased).isTrue()
    assertThat(onNodeWithTag("root").captureToImage().toPixelMap()[155, 100]).isEqualTo(Color.White)
    runOnIdle { gap.value = 5 }
    waitForIdle()
    val retainedRuntime = owner.renderer.runtimeSurfaces.single()
    runOnIdle { gap.value = 10 }
    waitForIdle()
    assertThat(owner.renderer.runtimeSurfaces.single()).isSameInstanceAs(retainedRuntime)
    val retainedSource = checkNotNull((retainedRuntime.delegate as RuntimeShaderGlassDelegate).layers.source)
    runOnIdle { enabled.value = false }
    waitForIdle()
    assertThat(retainedSource.isReleased).isTrue()
    assertThat(fixture.first.memberBindingForTest?.node?.container).isNull()
  }

  @Test
  fun fractionalAlpha_overlapCompositesOncePerSurface() = runComposeUiTest {
    val fixture = pair(gap = -40, firstTint = Color.Blue, secondTint = Color.Blue, alpha = 0.5f)
    assertThat(fixture.owner().renderer.surfaceCount).isEqualTo(1)
    val pixel = onNodeWithTag("root").captureToImage().toPixelMap()[120, 100]
    assertThat(pixel.blue).isGreaterThan(0.95f)
    assertThat(pixel.green).isGreaterThan(0.45f)
    assertThat(pixel.green).isLessThan(0.55f)
  }

  @Test
  fun unavailableRuntime_drawsClusterAsHardUnionWithoutBridge() = runComposeUiTest {
    val fixture = pair(gap = 10, unavailable = true)
    val renderer = fixture.owner().renderer
    assertThat(renderer.surfaceCount).isEqualTo(1)
    assertThat(renderer.runtimeSurfaces.single().delegate).isInstanceOf<FallbackGlassDelegate>()
    val pixels = onNodeWithTag("root").captureToImage().toPixelMap()
    assertThat(pixels[145, 100]).isEqualTo(Color.White)
    assertThat(pixels[80, 100].red).isGreaterThan(0.8f)
    assertThat(pixels[210, 100].blue).isGreaterThan(0.8f)
    assertThat(fixture.first.opticalSizeOverride).isEqualTo(fixture.second.opticalSizeOverride)
  }

  @Test
  fun unavailableRuntime_overlappingMembersDrawTintOnce() = runComposeUiTest {
    val tint = Color.Blue.copy(alpha = 0.5f)
    pair(gap = -40, firstTint = tint, secondTint = tint, unavailable = true)
    val pixels = onNodeWithTag("root").captureToImage().toPixelMap()
    assertThat(pixels[120, 100]).isCloseTo(pixels[70, 100], tolerance = 1f / 255f)
    assertThat(pixels[120, 100].green).isGreaterThan(0.45f)
    assertThat(pixels[120, 100].green).isLessThan(0.55f)
  }

  @Test
  fun unavailableRuntime_overlapUsesFirstMemberColours() = runComposeUiTest {
    pair(gap = -40, firstTint = Color.Red.copy(alpha = 0.5f), secondTint = Color.Blue.copy(alpha = 0.5f), unavailable = true)
    val pixels = onNodeWithTag("root").captureToImage().toPixelMap()
    assertThat(pixels[120, 100]).isCloseTo(pixels[70, 100], tolerance = 1f / 255f)
    assertThat(pixels[180, 100].blue).isGreaterThan(pixels[180, 100].red)
  }

  @Test
  fun unavailableRuntime_fractionalAlphaOverlapCompositesOnce() = runComposeUiTest {
    val fixture = pair(gap = -40, firstTint = Color.Blue, secondTint = Color.Blue, alpha = 0.5f, unavailable = true)
    assertThat(fixture.owner().renderer.surfaceCount).isEqualTo(1)
    val pixel = onNodeWithTag("root").captureToImage().toPixelMap()[120, 100]
    assertThat(pixel.blue).isGreaterThan(0.95f)
    assertThat(pixel.green).isGreaterThan(0.45f)
    assertThat(pixel.green).isLessThan(0.55f)
  }

  @Test
  fun unavailableRuntime_curvedOverlapHasNoSeam() = runComposeUiTest {
    val effects = List(2) { GlassRuntimeEffect() }
    effects.forEach { effect ->
      effect.runtimeEffectFactory = GlassRuntimeEffectFactory { throw RuntimeShaderRenderEffectException(IllegalStateException("test unavailable runtime")) }
    }
    val style = containerTestStyle(Color.Blue.copy(alpha = 0.5f)).then(GlassStyle { shape(RoundedCornerShape(12.dp)) })
    val state = HazeState()
    setContent {
      Box(Modifier.size(360.dp, 240.dp).testTag("root").background(Color.White)) {
        Box(Modifier.size(360.dp, 240.dp).hazeSource(state).background(Color.White))
        Box(Modifier.size(360.dp, 240.dp).hazeGlassContainer(40.dp)) {
          Box(Modifier.offset(40.dp, 60.dp).size(100.dp, 80.dp).hazeGlass(HazeEffectFactory { effects[0] }, HazeInput.Sources(state), style, HazePerformanceMode.Quality, true, null))
          Box(Modifier.offset(100.dp, 80.dp).size(100.dp, 80.dp).hazeGlass(HazeEffectFactory { effects[1] }, HazeInput.Sources(state), style, HazePerformanceMode.Quality, true, null))
        }
      }
    }
    waitForIdle()
    val pixels = onNodeWithTag("root").captureToImage().toPixelMap()
    // The first member's bottom-right arc lies inside the second member; it must leave no seam.
    val interior = pixels[120, 110]
    for (x in 128..139) {
      for (y in 128..139) {
        assertThat(pixels[x, y]).isCloseTo(interior, tolerance = 2f / 255f)
      }
    }
  }

  @Test
  fun cap_firstFourCreateOneClusterAndExtrasRemainOrdinarySingletons() = runComposeUiTest {
    val effects = List(6) { GlassRuntimeEffect() }
    val state = HazeState()
    setContent {
      Box(Modifier.size(740.dp, 180.dp).testTag("root").background(Color.White)) {
        Box(Modifier.size(740.dp, 180.dp).hazeSource(state).background(Color.White))
        Box(Modifier.size(740.dp, 180.dp).hazeGlassContainer(40.dp)) {
          effects.forEachIndexed { index, effect ->
            Box(
              Modifier.offset((10 + index * 110).dp, 50.dp).size(100.dp, 80.dp)
                .hazeGlass(HazeEffectFactory { effect }, HazeInput.Sources(state), containerTestStyle(Color.Blue), HazePerformanceMode.Quality, true, null),
            )
          }
        }
      }
    }
    waitForIdle()
    val owner = checkNotNull(effects.first().memberBindingForTest?.node?.container)
    assertThat(owner.renderer.surfaceCount).isEqualTo(3)
    assertThat(owner.renderer.runtimeSurfaces.size).isEqualTo(1)
    assertThat(checkNotNull(owner.renderer.runtimeSurfaces.single().mergedField).members.size).isEqualTo(4)
    assertThat(effects[4].mergedField).isNull()
    val pixels = onNodeWithTag("root").captureToImage().toPixelMap()
    assertThat(pixels[445, 90]).isEqualTo(Color.White)
    assertThat(pixels[555, 90]).isEqualTo(Color.White)
  }

  private fun Assert<Color>.isCloseTo(expected: Color, tolerance: Float) = given { actual ->
    val difference = maxOf(
      abs(actual.red - expected.red),
      abs(actual.green - expected.green),
      abs(actual.blue - expected.blue),
      abs(actual.alpha - expected.alpha),
    )
    if (difference <= tolerance) return@given
    expected("to be within $tolerance of ${show(expected)} but was ${show(actual)}")
  }

  private class PairFixture(val first: GlassRuntimeEffect, val second: GlassRuntimeEffect) {
    fun owner() = checkNotNull(first.memberBindingForTest?.node?.container)
  }

  private fun ComposeUiTest.pair(
    gap: Int = 10,
    gapState: androidx.compose.runtime.MutableState<Int> = mutableStateOf(gap),
    enabled: androidx.compose.runtime.MutableState<Boolean> = mutableStateOf(true),
    performance: HazePerformanceMode = HazePerformanceMode.Quality,
    firstTint: Color = Color.Red,
    secondTint: Color = Color.Blue,
    alpha: Float = 1f,
    unavailable: Boolean = false,
    extraStyle: GlassStyle = GlassStyle { },
  ): PairFixture {
    val first = GlassRuntimeEffect()
    val second = GlassRuntimeEffect()
    if (unavailable) {
      listOf(first, second).forEach { effect ->
        effect.runtimeEffectFactory = GlassRuntimeEffectFactory { throw RuntimeShaderRenderEffectException(IllegalStateException("test unavailable runtime")) }
      }
    }
    val state = HazeState()
    setContent {
      Box(Modifier.size(360.dp, 240.dp).testTag("root").background(Color.White)) {
        Box(Modifier.size(360.dp, 240.dp).hazeSource(state).background(Color.White))
        Box(Modifier.size(360.dp, 240.dp).then(if (enabled.value) Modifier.hazeGlassContainer(40.dp) else Modifier)) {
          Box(Modifier.offset(40.dp, 60.dp).size(100.dp, 80.dp).hazeGlass(HazeEffectFactory { first }, HazeInput.Sources(state), containerTestStyle(firstTint, alpha).then(extraStyle), performance, true, null)) {
            Box(Modifier.size(10.dp).background(Color.Green))
          }
          Box(Modifier.offset((140 + gapState.value).dp, 60.dp).size(100.dp, 80.dp).hazeGlass(HazeEffectFactory { second }, HazeInput.Sources(state), containerTestStyle(secondTint, alpha).then(extraStyle), performance, true, null))
        }
      }
    }
    waitForIdle()
    return PairFixture(first, second)
  }
}
