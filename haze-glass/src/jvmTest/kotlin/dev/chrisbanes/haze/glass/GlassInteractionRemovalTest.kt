// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThan
import assertk.assertions.isNotNull
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectDrawScope
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeEffectRenderer
import dev.chrisbanes.haze.HazeEffectRendererBackdrop
import dev.chrisbanes.haze.HazeEffectRendererDrawHooks
import dev.chrisbanes.haze.HazeEffectRendererInteraction
import dev.chrisbanes.haze.HazeEffectRendererLifecycle
import dev.chrisbanes.haze.HazeEffectRendererRetainedOutput
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.test.ContextTest
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope

@OptIn(ExperimentalTestApi::class, ExperimentalHazeApi::class, InternalHazeApi::class)
class GlassInteractionRemovalTest : ContextTest() {
  @Test
  fun removeAllResponsesWhileTransparent_releasesStagesWithoutVisibleDraw() = runComposeUiTest {
    val fixture = attach(activeStyle())
    val delegate = fixture.delegate
    val optical = checkNotNull(delegate.layers.interactionOptical)
    val detail = checkNotNull(delegate.layers.interactionRefractionDetail)
    val lighting = checkNotNull(delegate.layers.interactionLighting)
    fixture.assertMaterialPixels(this)
    mainClock.autoAdvance = false
    runOnIdle { fixture.style.value = baseStyle().then { alpha(0f) } }
    mainClock.advanceTimeBy(100, ignoreFrameDuration = true)
    waitForIdle()
    assertThat(fixture.effect.interactionControllerForTest).isNotNull()
    assertThat(fixture.effect.currentInteractionState.hasOptics).isTrue()
    assertThat(optical.isReleased).isFalse()
    assertThat(detail.isReleased).isFalse()
    assertThat(lighting.isReleased).isFalse()
    mainClock.advanceTimeBy(500, ignoreFrameDuration = true)
    waitForIdle()
    assertThat(fixture.effect.interactionControllerForTest).isNull()
    assertThat(optical.isReleased).isTrue()
    assertThat(detail.isReleased).isTrue()
    assertThat(lighting.isReleased).isTrue()
    assertThat(delegate.layers.interactionOptical).isNull()
    assertThat(delegate.layers.interactionRefractionDetail).isNull()
    assertThat(delegate.layers.interactionLighting).isNull()
    assertThat(fixture.effect.shouldPrepareDraw(GlassNodeConfiguration(fixture.style.value, interactionSource = fixture.source))).isFalse()
    runOnIdle { fixture.style.value = baseStyle() }
    mainClock.advanceTimeByFrame()
    waitForIdle()
    fixture.assertMaterialPixels(this)
  }

  @Test
  fun removeAllResponses_preservesExpandedCaptureUntilOpticalExitCompletes() = runComposeUiTest {
    val base = GlassStyle.regular.then {
      optics(GlassOptics(refractionStrength = 0.4f, refractionDisplacement = 40.dp, blurRadius = OpticalSizeValue.Fixed(0.dp)))
      chromaticAberrationStrength(0f)
      edgeSoftness(0.dp)
      tint(Color.Blue.copy(alpha = 0.5f))
    }
    fun response(maximum: Float) = base.then {
      pressed { animate(tween(1), tween(500)) { refractionMultiplier(maximum) } }
    }
    val fixture = attach(response(1.8f))
    mainClock.autoAdvance = false
    val context = checkNotNull(fixture.effect.attachedContextForTest)
    val density = context.requireDensity()
    fun assertCapture(maximum: Float): IntSize {
      val padding = with(density) {
        calculateGlassSamplePaddingPx(0f, 40.dp.toPx(), 0.4f * maximum, 0f, 0f, 0f)
      }
      val expected = Rect(Offset.Zero, context.modifierSize).inflate(padding)
      assertThat(fixture.effect.calculateLayerBounds(Rect(Offset.Zero, context.modifierSize), density)).isEqualTo(expected)
      val expectedSize = IntSize(expected.width.roundToInt(), expected.height.roundToInt())
      val coordinates = checkNotNull(fixture.effect.preparedRender).params.coordinates
      assertThat(coordinates.sampleSize).isEqualTo(Size(expectedSize.width.toFloat(), expectedSize.height.toFloat()))
      assertThat(coordinates.materialOrigin).isEqualTo(Offset(padding, padding))
      assertThat(checkNotNull(fixture.delegate.layers.source).size).isEqualTo(expectedSize)
      return expectedSize
    }
    val retainedSize = assertCapture(1.8f)
    runOnIdle { fixture.style.value = response(1.2f) }
    mainClock.advanceTimeBy(100, ignoreFrameDuration = true)
    waitForIdle()
    assertCapture(1.2f)
    runOnIdle { fixture.style.value = base }
    repeat(9) {
      mainClock.advanceTimeBy(50, ignoreFrameDuration = true)
      waitForIdle()
      assertCapture(1.2f)
    }
    mainClock.advanceTimeBy(150, ignoreFrameDuration = true)
    waitForIdle()
    mainClock.advanceTimeByFrame()
    waitForIdle()
    val settledSize = assertCapture(1f)
    assertThat(settledSize.width).isLessThan(retainedSize.width)
    assertThat(fixture.effect.interactionControllerForTest).isNull()
    fixture.assertMaterialPixels(this)
  }

  @Test
  fun removeWhitePointWithoutBaseDetail_preservesOpticalStageAndMaterialUntilSettled() = runComposeUiTest {
    val base = baseStyle().then { optics(GlassOptics(refractionStrength = 0f, refractionDetailIntensity = 0f, blurRadius = OpticalSizeValue.Fixed(0.dp))) }
    val initial = base.then { pressed { animate(tween(1), tween(500)) { whitePointDelta(0.2f) } } }
    val fixture = attach(initial)
    assertThat(fixture.delegate.layers.refractionDetail).isNull()
    assertRemoval(fixture, base, listOf(checkNotNull(fixture.delegate.layers.interactionOptical)))
    assertThat(fixture.delegate.layers.interactionOptical).isNull()
    assertThat(fixture.effect.interactionControllerForTest).isNull()
  }

  @Test
  fun removeRefraction_preservesInteractionDetailUntilSettledAndKeepsBaseDetail() = runComposeUiTest {
    val initial = baseStyle().then { pressed { animate(tween(1), tween(500)) { refractionMultiplier(1.8f) } } }
    val fixture = attach(initial)
    assertThat(checkNotNull(fixture.effect.preparedRender).refractionDetailKey).isNotNull()
    assertThat(fixture.delegate.layers.refractionDetail).isNotNull()
    val layers = listOf(
      checkNotNull(fixture.delegate.layers.interactionOptical),
      checkNotNull(fixture.delegate.layers.interactionRefractionDetail),
      checkNotNull(fixture.delegate.layers.interactionRefractionDetailCoverage),
      checkNotNull(fixture.delegate.layers.interactionRefractionComposite),
    )
    assertRemoval(fixture, baseStyle(), layers)
    assertThat(fixture.delegate.layers.interactionOptical).isNull()
    assertThat(fixture.delegate.layers.interactionRefractionDetail).isNull()
    assertThat(fixture.delegate.layers.interactionRefractionDetailCoverage).isNull()
    assertThat(fixture.delegate.layers.interactionRefractionComposite).isNull()
    assertThat(fixture.delegate.layers.refractionDetail).isNotNull()
    assertThat(checkNotNull(fixture.delegate.layers.refractionDetail).isReleased).isFalse()
  }

  @Test
  fun removeLighting_preservesForegroundUntilSettledWhileOpticsRemain() = runComposeUiTest {
    val fixture = attach(activeStyle())
    val optical = checkNotNull(fixture.delegate.layers.interactionOptical)
    val lighting = checkNotNull(fixture.delegate.layers.interactionLighting)
    val replacement = baseStyle().then {
      pressed {
        animate(tween(1), tween(500)) {
          refractionMultiplier(1.8f)
          whitePointDelta(0.2f)
        }
      }
    }
    assertRemoval(fixture, replacement, listOf(lighting))
    assertThat(fixture.delegate.layers.interactionLighting).isNull()
    assertThat(fixture.delegate.layers.interactionOptical).isSameInstanceAs(optical)
    assertThat(fixture.effect.currentInteractionState.hasOptics).isTrue()
  }

  @Test
  fun removeAllResponses_preservesMaterialAndReleasesEveryObsoleteStage() = runComposeUiTest {
    val fixture = attach(activeStyle())
    val layers = interactionLayers(fixture)
    assertRemoval(fixture, baseStyle(), layers)
    assertThat(fixture.delegate.layers.interactionOptical).isNull()
    assertThat(fixture.delegate.layers.interactionRefractionDetail).isNull()
    assertThat(fixture.delegate.layers.interactionLighting).isNull()
    assertThat(fixture.effect.interactionControllerForTest).isNull()
  }

  @Test
  fun rapidRemovalAndReintroduction_preservesMaterialAndFinishesFinalExit() = runComposeUiTest {
    val fixture = attach(activeStyle())
    val layers = interactionLayers(fixture)
    fixture.assertMaterialPixels(this)
    mainClock.autoAdvance = false
    runOnIdle { fixture.style.value = baseStyle() }
    mainClock.advanceTimeBy(100, ignoreFrameDuration = true)
    waitForIdle()
    fixture.assertMaterialPixels(this)
    layers.forEach { assertThat(it.isReleased).isFalse() }
    runOnIdle { fixture.style.value = activeStyle() }
    mainClock.advanceTimeBy(100, ignoreFrameDuration = true)
    waitForIdle()
    fixture.assertMaterialPixels(this)
    assertRemoval(fixture, baseStyle(), layers)
    assertThat(fixture.effect.interactionControllerForTest).isNull()
  }

  @Test
  fun reducedMotion_removalSettlesWithoutObsoleteLayersOrController() = runComposeUiTest {
    val fixture = attach(activeStyle(), GlassReducedMotionPolicy.Reduced)
    val layers = interactionLayers(fixture)
    fixture.assertMaterialPixels(this)
    runOnIdle { fixture.style.value = baseStyle() }
    waitForIdle()
    layers.forEach { assertThat(it.isReleased).isTrue() }
    assertThat(fixture.effect.interactionControllerForTest).isNull()
    assertThat(fixture.delegate.layers.interactionOptical).isNull()
    assertThat(fixture.delegate.layers.interactionLighting).isNull()
    fixture.assertMaterialPixels(this)
  }

  @Test
  fun removeAllResponsesWhileTransparent_sourceUnavailablePreservesRetainedMaterial() = runComposeUiTest {
    assertHiddenSourceRemoval(1f)
  }

  @Test
  fun removeAllResponsesWhileTransparent_sourceUnavailableRestoresPartialAlphaMaterial() = runComposeUiTest {
    assertHiddenSourceRemoval(0.5f)
  }

  private fun ComposeUiTest.assertHiddenSourceRemoval(restoredAlpha: Float) {
    val base = baseStyle().then { alpha(restoredAlpha) }
    val fixture = attach(base, observeInput = true)
    val reference = fixture.assertMaterialPixels(this)
    runOnIdle { fixture.style.value = activeStyle().then { alpha(restoredAlpha) } }
    waitForIdle()
    press(fixture)
    fixture.assertMaterialPixels(this)
    val delegate = fixture.delegate
    val snapshot = checkNotNull(delegate.lastSuccessfulSourceSnapshot)
    val source = checkNotNull(delegate.layers.source)
    val optical = checkNotNull(delegate.layers.optical)
    val detail = checkNotNull(delegate.layers.refractionDetail)
    val sourceRecords = delegate.sourceRecordCount
    val obsolete = interactionLayers(fixture)
    val oldGroup = checkNotNull(delegate.layers.groupAlpha.layer)
    mainClock.autoAdvance = false
    runOnIdle { fixture.style.value = baseStyle().then { alpha(0f) } }
    repeat(2) {
      mainClock.advanceTimeByFrame()
      waitForIdle()
    }
    mainClock.advanceTimeBy(100, ignoreFrameDuration = true)
    waitForIdle()
    assertThat(fixture.effect.interactionControllerForTest).isNotNull()
    assertThat(fixture.effect.currentInteractionState.hasOptics).isTrue()
    assertThat(fixture.effect.currentInteractionState.hasLighting).isTrue()
    assertThat(fixture.effect.currentInteractionState.whitePointDelta).isGreaterThan(0f)
    assertThat(fixture.effect.currentInteractionState.refractionMultiplier).isGreaterThan(1f)
    assertThat(fixture.effect.currentInteractionState.lightingIntensity).isGreaterThan(0f)
    obsolete.forEach { assertThat(it.isReleased).isFalse() }
    runOnIdle { fixture.sourceEnabled.value = false }
    repeat(2) {
      mainClock.advanceTimeByFrame()
      waitForIdle()
    }
    assertThat(fixture.sourceEnabled.value).isFalse()
    assertThat(fixture.observer.hasDrawableInput).isFalse()
    assertThat(fixture.observer.inputSnapshot).isNull()
    mainClock.advanceTimeBy(500, ignoreFrameDuration = true)
    waitForIdle()
    assertThat(fixture.effect.interactionControllerForTest).isNull()
    obsolete.forEach { assertThat(it.isReleased).isTrue() }
    assertObsoleteInteractionLayersNull(delegate)
    assertThat(oldGroup.isReleased).isTrue()
    assertThat(fixture.effect.shouldPrepareDraw(GlassNodeConfiguration(fixture.style.value, interactionSource = fixture.source))).isFalse()
    assertThat(delegate.canDrawRetainedOutput(), "hidden settled retained availability").isTrue()
    assertThat(delegate.lastSuccessfulSourceSnapshot).isSameInstanceAs(snapshot)
    assertThat(delegate.layers.source).isSameInstanceAs(source)
    assertThat(delegate.layers.optical).isSameInstanceAs(optical)
    assertThat(delegate.layers.refractionDetail).isSameInstanceAs(detail)
    listOf(source, optical, detail).forEach { assertThat(it.isReleased).isFalse() }
    assertThat(delegate.sourceRecordCount).isEqualTo(sourceRecords)
    runOnIdle { fixture.style.value = base }
    repeat(2) {
      mainClock.advanceTimeByFrame()
      waitForIdle()
    }
    assertThat(fixture.observer.hasDrawableInput).isFalse()
    assertThat(fixture.observer.inputSnapshot).isNull()
    assertThat(fixture.delegate).isSameInstanceAs(delegate)
    assertThat(delegate.lastSuccessfulSourceSnapshot).isSameInstanceAs(snapshot)
    assertThat(delegate.sourceRecordCount).isEqualTo(sourceRecords)
    val current = checkNotNull(fixture.effect.preparedRender)
    assertThat(current.alpha).isEqualTo(restoredAlpha)
    assertIdentityUniforms(current.interactionUniforms)
    val restored = fixture.assertMaterialPixels(this)
    if (restoredAlpha == 0.5f) {
      assertColorNear(restored, reference)
      val group = checkNotNull(delegate.layers.groupAlpha.layer)
      assertThat(group).isNotSameInstanceAs(oldGroup)
      assertThat(group.isReleased).isFalse()
    }
  }

  private fun assertColorNear(actual: Color, expected: Color) {
    listOf(actual.red to expected.red, actual.green to expected.green, actual.blue to expected.blue, actual.alpha to expected.alpha).forEach { (a, e) ->
      assertThat(kotlin.math.abs(a - e)).isLessThan(0.04f)
    }
  }

  private fun assertIdentityUniforms(uniforms: GlassInteractionUniforms) {
    assertThat(uniforms.whitePointDelta).isEqualTo(0f)
    assertThat(uniforms.refractionMultiplier).isEqualTo(1f)
    assertThat(uniforms.lightingIntensity).isEqualTo(0f)
  }

  private fun assertObsoleteInteractionLayersNull(delegate: RuntimeShaderGlassDelegate) {
    assertThat(delegate.layers.interactionOptical).isNull()
    assertThat(delegate.layers.interactionRefractionDetail).isNull()
    assertThat(delegate.layers.interactionRefractionDetailCoverage).isNull()
    assertThat(delegate.layers.interactionRefractionComposite).isNull()
    assertThat(delegate.layers.interactionLighting).isNull()
  }

  @Test
  fun removeOpticsWhileTransparent_keepsDeclaredLightingAndRetainedBase() = runComposeUiTest {
    val fixture = attach(fixedCaptureActiveStyle(), observeInput = true)
    fixture.assertMaterialPixels(this)
    assertThat(fixture.effect.currentInteractionState.whitePointDelta).isGreaterThan(0f)
    assertThat(fixture.effect.currentInteractionState.refractionMultiplier).isEqualTo(1f)
    val base = RetainedBase(fixture)
    val optical = checkNotNull(fixture.delegate.layers.interactionOptical)
    val detail = checkNotNull(fixture.delegate.layers.interactionRefractionDetail)
    val coverage = checkNotNull(fixture.delegate.layers.interactionRefractionDetailCoverage)
    val composite = checkNotNull(fixture.delegate.layers.interactionRefractionComposite)
    val group = checkNotNull(fixture.delegate.layers.groupAlpha.layer)
    val lighting = checkNotNull(fixture.delegate.layers.interactionLighting)
    mainClock.autoAdvance = false
    replaceHidden(fixture, lightingStyle())
    mainClock.advanceTimeBy(100, ignoreFrameDuration = true)
    waitForIdle()
    assertThat(fixture.effect.currentInteractionState.hasOptics).isTrue()
    assertThat(lighting.isReleased).isFalse()
    mainClock.advanceTimeBy(500, ignoreFrameDuration = true)
    waitForIdle()
    listOf(optical, detail, coverage, composite, group).forEach { assertThat(it.isReleased).isTrue() }
    assertThat(fixture.delegate.layers.interactionOptical).isNull()
    assertThat(fixture.delegate.layers.interactionRefractionDetail).isNull()
    assertThat(fixture.delegate.layers.interactionRefractionDetailCoverage).isNull()
    assertThat(fixture.delegate.layers.interactionRefractionComposite).isNull()
    assertThat(fixture.delegate.layers.groupAlpha.layer).isNull()
    assertThat(fixture.delegate.layers.interactionLighting).isSameInstanceAs(lighting)
    assertThat(lighting.isReleased).isFalse()
    assertThat(fixture.effect.interactionControllerForTest).isNotNull()
    assertThat(fixture.effect.currentInteractionState.hasLighting).isTrue()
    base.assertPreserved(fixture.delegate)
    runOnIdle { fixture.style.value = lightingStyle() }
    syncFrames()
    fixture.assertMaterialPixels(this)
  }

  @Test
  fun removeLightingWhileTransparent_keepsIndependentPartialAlphaBaseGroup() = runComposeUiTest {
    val referenceStyle = baseStyle().then { alpha(0.5f) }
    val fixture = attach(referenceStyle, observeInput = true)
    val reference = fixture.assertMaterialPixels(this)
    runOnIdle { fixture.style.value = lightingStyle().then { alpha(0.5f) } }
    waitForIdle()
    press(fixture)
    fixture.assertMaterialPixels(this)
    assertThat(fixture.delegate.layers.interactionOptical).isNull()
    val base = RetainedBase(fixture)
    val group = checkNotNull(fixture.delegate.layers.groupAlpha.layer)
    val lighting = checkNotNull(fixture.delegate.layers.interactionLighting)
    mainClock.autoAdvance = false
    replaceHidden(fixture, baseStyle())
    mainClock.advanceTimeBy(100, ignoreFrameDuration = true)
    waitForIdle()
    assertThat(fixture.effect.currentInteractionState.hasLighting).isTrue()
    assertThat(lighting.isReleased).isFalse()
    mainClock.advanceTimeBy(500, ignoreFrameDuration = true)
    waitForIdle()
    assertThat(lighting.isReleased).isTrue()
    assertThat(fixture.delegate.layers.interactionLighting).isNull()
    assertThat(fixture.delegate.layers.groupAlpha.layer).isSameInstanceAs(group)
    assertThat(group.isReleased).isFalse()
    assertThat(fixture.effect.interactionControllerForTest).isNull()
    base.assertPreserved(fixture.delegate)
    runOnIdle { fixture.sourceEnabled.value = false }
    syncFrames()
    assertThat(fixture.observer.hasDrawableInput).isFalse()
    assertThat(fixture.observer.inputSnapshot).isNull()
    runOnIdle { fixture.style.value = referenceStyle }
    syncFrames()
    assertThat(fixture.delegate.lastSuccessfulSourceSnapshot).isSameInstanceAs(base.snapshot)
    assertThat(fixture.delegate.sourceRecordCount).isEqualTo(base.records)
    assertThat(checkNotNull(fixture.effect.preparedRender).alpha).isEqualTo(0.5f)
    assertIdentityUniforms(checkNotNull(fixture.effect.preparedRender).interactionUniforms)
    assertColorNear(fixture.assertMaterialPixels(this), reference)
  }

  @Test
  fun sequentialHiddenResponseRemoval_sourceUnavailablePreservesRetainedMaterial() = runComposeUiTest {
    val fixture = attach(fixedCaptureActiveStyle(), observeInput = true)
    fixture.assertMaterialPixels(this)
    assertThat(fixture.effect.currentInteractionState.whitePointDelta).isGreaterThan(0f)
    assertThat(fixture.effect.currentInteractionState.refractionMultiplier).isEqualTo(1f)
    val base = RetainedBase(fixture)
    val optical = checkNotNull(fixture.delegate.layers.interactionOptical)
    val detail = checkNotNull(fixture.delegate.layers.interactionRefractionDetail)
    val coverage = checkNotNull(fixture.delegate.layers.interactionRefractionDetailCoverage)
    val composite = checkNotNull(fixture.delegate.layers.interactionRefractionComposite)
    val group = checkNotNull(fixture.delegate.layers.groupAlpha.layer)
    val lighting = checkNotNull(fixture.delegate.layers.interactionLighting)
    mainClock.autoAdvance = false
    replaceHidden(fixture, lightingStyle())
    mainClock.advanceTimeBy(100, ignoreFrameDuration = true)
    waitForIdle()
    assertThat(fixture.effect.currentInteractionState.hasOptics).isTrue()
    listOf(optical, detail, coverage, composite, lighting).forEach { assertThat(it.isReleased).isFalse() }
    mainClock.advanceTimeBy(500, ignoreFrameDuration = true)
    waitForIdle()
    listOf(optical, detail, coverage, composite, group).forEach { assertThat(it.isReleased).isTrue() }
    assertThat(fixture.delegate.layers.interactionOptical).isNull()
    assertThat(fixture.delegate.layers.interactionRefractionDetail).isNull()
    assertThat(fixture.delegate.layers.interactionRefractionDetailCoverage).isNull()
    assertThat(fixture.delegate.layers.interactionRefractionComposite).isNull()
    assertThat(fixture.delegate.layers.groupAlpha.layer).isNull()
    assertThat(fixture.delegate.layers.interactionLighting).isSameInstanceAs(lighting)
    assertThat(lighting.isReleased).isFalse()
    assertThat(fixture.effect.interactionControllerForTest).isNotNull()
    assertThat(fixture.effect.currentInteractionState.hasLighting).isTrue()
    base.assertPreserved(fixture.delegate)
    runOnIdle { fixture.sourceEnabled.value = false }
    syncFrames()
    assertThat(fixture.sourceEnabled.value).isFalse()
    assertThat(fixture.observer.hasDrawableInput).isFalse()
    assertThat(fixture.observer.inputSnapshot).isNull()
    replaceHidden(fixture, baseStyle())
    mainClock.advanceTimeBy(100, ignoreFrameDuration = true)
    waitForIdle()
    assertThat(fixture.effect.currentInteractionState.hasLighting).isTrue()
    assertThat(fixture.delegate.layers.interactionLighting).isSameInstanceAs(lighting)
    assertThat(lighting.isReleased).isFalse()
    mainClock.advanceTimeBy(500, ignoreFrameDuration = true)
    waitForIdle()
    assertThat(lighting.isReleased).isTrue()
    assertObsoleteInteractionLayersNull(fixture.delegate)
    assertThat(fixture.effect.interactionControllerForTest).isNull()
    base.assertPreserved(fixture.delegate)
    runOnIdle { fixture.style.value = baseStyle() }
    syncFrames()
    assertThat(fixture.observer.hasDrawableInput).isFalse()
    assertThat(fixture.observer.inputSnapshot).isNull()
    assertThat(fixture.delegate.lastSuccessfulSourceSnapshot).isSameInstanceAs(base.snapshot)
    assertThat(fixture.delegate.sourceRecordCount).isEqualTo(base.records)
    assertIdentityUniforms(checkNotNull(fixture.effect.preparedRender).interactionUniforms)
    fixture.assertMaterialPixels(this)
  }

  @Test
  fun hiddenInteractionCleanup_repeatedCallsPreserveRetainedBase() = runComposeUiTest {
    val fixture = attach(fixedCaptureActiveStyle(), observeInput = true)
    fixture.assertMaterialPixels(this)
    val base = RetainedBase(fixture)
    mainClock.autoAdvance = false
    replaceHidden(fixture, baseStyle())
    runOnIdle { fixture.sourceEnabled.value = false }
    syncFrames()
    assertThat(fixture.observer.hasDrawableInput).isFalse()
    assertThat(fixture.observer.inputSnapshot).isNull()
    mainClock.advanceTimeBy(600, ignoreFrameDuration = true)
    waitForIdle()
    assertThat(fixture.effect.interactionControllerForTest).isNull()
    base.assertPreserved(fixture.delegate)
    assertObsoleteInteractionLayersNull(fixture.delegate)
    repeat(2) {
      fixture.delegate.releaseObsoleteInteractionOutput(GlassInteractionTopology(hasOptics = false, hasLighting = false, maxRefractionMultiplier = 1f))
      base.assertPreserved(fixture.delegate)
      assertObsoleteInteractionLayersNull(fixture.delegate)
    }
  }

  @Test
  fun scaleOnlyRemovalWhileTransparent_restoredMaterialDraws() = runComposeUiTest {
    val fixture = attach(baseStyle().then { pressed { animate(tween(1), tween(500)) { scale(0.9f) } } })
    fixture.assertMaterialPixels(this)
    val topology = checkNotNull(fixture.effect.interactionControllerForTest).renderTopology
    mainClock.autoAdvance = false
    replaceHidden(fixture, baseStyle())
    mainClock.advanceTimeBy(600, ignoreFrameDuration = true)
    waitForIdle()
    assertThat(fixture.effect.interactionControllerForTest).isNull()
    assertThat(topology.hasOptics).isFalse()
    assertThat(topology.hasLighting).isFalse()
    runOnIdle { fixture.style.value = baseStyle() }
    syncFrames()
    assertThat(fixture.delegate.canDrawRetainedOutput()).isTrue()
    fixture.assertMaterialPixels(this)
  }

  @Test
  fun pressAndReleaseWithDeclaredResponse_settlesWithoutRedrawingUnchangedState() = runComposeUiTest {
    val effect = GlassRuntimeEffect()
    val renderer = InputObservingGlassRenderer(effect)
    val state = HazeState()
    val source = MutableInteractionSource()
    setContent {
      Box(Modifier.size(120.dp)) {
        Box(Modifier.fillMaxSize().hazeSource(state).background(Color.Red))
        Box(
          Modifier.fillMaxSize().hazeGlass(
            factory = HazeEffectFactory { renderer },
            expandLayerBounds = true,
            interactionSource = source,
            input = HazeInput.Sources(state),
            style = activeStyle(),
            performanceMode = HazePerformanceMode.Quality,
            interactionReducedMotionPolicy = GlassReducedMotionPolicy.Full,
          ),
        )
      }
    }
    waitForIdle()
    mainClock.autoAdvance = false
    val pressDraw = renderer.drawnStates.size
    val press = PressInteraction.Press(Offset(40f, 40f))
    val scope = TestScope()
    scope.launch { source.emit(press) }
    scope.testScheduler.runCurrent()
    repeat(10) {
      mainClock.advanceTimeByFrame()
      waitForIdle()
    }
    val controller = checkNotNull(effect.interactionControllerForTest)
    assertThat(controller.hasRunningResponseAnimations).isFalse()
    val topology = controller.renderTopology
    val pressed = controller.renderState
    val entryDraws = renderer.drawnStates.drop(pressDraw)
    assertThat(entryDraws.count { it == pressed }, "pressed draws in $entryDraws").isEqualTo(1)
    val releaseDraw = renderer.drawnStates.size
    scope.launch { source.emit(PressInteraction.Release(press)) }
    scope.testScheduler.runCurrent()
    repeat(60) {
      mainClock.advanceTimeByFrame()
      waitForIdle()
    }
    assertThat(controller.hasRunningResponseAnimations).isFalse()
    assertThat(effect.interactionControllerForTest).isSameInstanceAs(controller)
    assertThat(controller.renderTopology).isSameInstanceAs(topology)
    val settled = controller.renderState
    assertThat(settled.lightingIntensity).isEqualTo(0f)
    val exitDraws = renderer.drawnStates.drop(releaseDraw)
    // The exit's last animation frame draws the settled state; completion must not draw it again.
    assertThat(exitDraws.count { it == settled }, "settled draws in $exitDraws").isEqualTo(1)
  }

  private class RetainedBase(private val fixture: Fixture) {
    private val delegate = fixture.delegate
    private val layerSize = fixture.observer.layerSize
    val snapshot = checkNotNull(delegate.lastSuccessfulSourceSnapshot)
    val inputs = checkNotNull(delegate.lastSuccessfulStageInputs)
    val source = checkNotNull(delegate.layers.source)
    val optical = checkNotNull(delegate.layers.optical)
    val detail = checkNotNull(delegate.layers.refractionDetail)
    val records = delegate.sourceRecordCount

    fun assertPreserved(delegate: RuntimeShaderGlassDelegate) {
      assertThat(fixture.observer.layerSize, "unchanged actual capture geometry").isEqualTo(layerSize)
      assertThat(delegate.canDrawRetainedOutput()).isTrue()
      assertThat(delegate.lastSuccessfulSourceSnapshot).isSameInstanceAs(snapshot)
      assertThat(delegate.lastSuccessfulStageInputs).isSameInstanceAs(inputs)
      assertThat(delegate.layers.source).isSameInstanceAs(source)
      assertThat(delegate.layers.optical).isSameInstanceAs(optical)
      assertThat(delegate.layers.refractionDetail).isSameInstanceAs(detail)
      listOf(source, optical, detail).forEach { assertThat(it.isReleased).isFalse() }
      assertThat(delegate.sourceRecordCount).isEqualTo(records)
    }
  }

  private fun fixedCaptureActiveStyle() = baseStyle().then {
    pressed {
      animate(tween(1), tween(500)) {
        lightingIntensity(0.5f)
        whitePointDelta(0.2f)
      }
    }
  }

  private fun lightingStyle() = baseStyle().then {
    pressed { animate(tween(1), tween(500)) { lightingIntensity(0.5f) } }
  }

  private fun ComposeUiTest.syncFrames() {
    repeat(2) {
      mainClock.advanceTimeByFrame()
      waitForIdle()
    }
  }

  private fun ComposeUiTest.replaceHidden(fixture: Fixture, style: GlassStyle) {
    runOnIdle { fixture.style.value = style.then { alpha(0f) } }
    syncFrames()
  }

  private fun ComposeUiTest.press(fixture: Fixture) {
    val scope = TestScope()
    scope.launch { fixture.source.emit(PressInteraction.Press(Offset(40f, 40f))) }
    scope.testScheduler.runCurrent()
    waitForIdle()
  }

  private fun interactionLayers(fixture: Fixture) = listOf(
    checkNotNull(fixture.delegate.layers.interactionOptical),
    checkNotNull(fixture.delegate.layers.interactionRefractionDetail),
    checkNotNull(fixture.delegate.layers.interactionRefractionDetailCoverage),
    checkNotNull(fixture.delegate.layers.interactionRefractionComposite),
    checkNotNull(fixture.delegate.layers.interactionLighting),
  )

  private fun ComposeUiTest.assertRemoval(fixture: Fixture, replacement: GlassStyle, layers: List<GraphicsLayer>) {
    fixture.assertMaterialPixels(this)
    assertThat(fixture.effect.currentInteractionState.hasOptics || fixture.effect.currentInteractionState.hasLighting).isTrue()
    mainClock.autoAdvance = false
    runOnIdle { fixture.style.value = replacement }
    repeat(9) {
      mainClock.advanceTimeBy(50, ignoreFrameDuration = true)
      waitForIdle()
      assertThat(fixture.delegate.canDrawRetainedOutput()).isTrue()
      val currentStages = listOfNotNull(
        fixture.delegate.layers.interactionOptical,
        fixture.delegate.layers.interactionRefractionDetail,
        fixture.delegate.layers.interactionRefractionDetailCoverage,
        fixture.delegate.layers.interactionRefractionComposite,
        fixture.delegate.layers.interactionLighting,
      )
      layers.forEach {
        assertThat(it.isReleased).isFalse()
        assertThat(currentStages).contains(it)
      }
      fixture.assertMaterialPixels(this)
    }
    mainClock.advanceTimeBy(150, ignoreFrameDuration = true)
    waitForIdle()
    assertThat(fixture.delegate.canDrawRetainedOutput()).isTrue()
    layers.forEach { assertThat(it.isReleased).isTrue() }
    fixture.assertMaterialPixels(this)
  }

  private fun baseStyle() = GlassStyle.regular.then {
    optics(GlassOptics(refractionStrength = 0.5f, refractionDisplacement = 20.dp, blurRadius = OpticalSizeValue.Fixed(0.dp)))
    tint(Color.Blue.copy(alpha = 0.5f))
  }

  private fun activeStyle() = baseStyle().then {
    pressed {
      animate(tween(1), tween(500)) {
        lightingIntensity(0.5f)
        refractionMultiplier(1.8f)
        whitePointDelta(0.2f)
      }
    }
  }

  private class Fixture(initialStyle: GlassStyle, observeInput: Boolean = false) {
    val effect = GlassRuntimeEffect()
    val source = MutableInteractionSource()
    val style = mutableStateOf(initialStyle)
    val observer = InputObservingGlassRenderer(effect)
    val sourceEnabled = mutableStateOf(true)
    val factory = HazeEffectFactory<GlassNodeConfiguration> { if (observeInput) observer else effect }
    val delegate get() = effect.delegate as RuntimeShaderGlassDelegate

    fun assertMaterialPixels(test: ComposeUiTest): Color {
      val controlPixels = test.onNodeWithTag("control").captureToImage().toPixelMap()
      val materialPixels = test.onNodeWithTag("material").captureToImage().toPixelMap()
      val control = controlPixels[controlPixels.width / 2, controlPixels.height / 2]
      val material = materialPixels[materialPixels.width / 2, materialPixels.height / 2]
      assertThat(control.red).isGreaterThan(0.9f)
      assertThat(control.blue).isLessThan(0.1f)
      assertThat(material.blue).isGreaterThan(control.blue + 0.2f)
      return material
    }
  }

  private fun ComposeUiTest.attach(style: GlassStyle, policy: GlassReducedMotionPolicy = GlassReducedMotionPolicy.Full, observeInput: Boolean = false): Fixture {
    val fixture = Fixture(style, observeInput)
    val state = HazeState()
    setContent {
      Box(Modifier.size(384.dp)) {
        Box(Modifier.fillMaxSize().then(if (fixture.sourceEnabled.value) Modifier.hazeSource(state) else Modifier).background(Color.Red))
        Box(Modifier.align(Alignment.TopStart).size(80.dp).testTag("control"))
        Box(
          Modifier.align(Alignment.Center).size(120.dp).testTag("material").hazeGlass(
            factory = fixture.factory,
            input = HazeInput.Sources(state),
            style = fixture.style.value,
            performanceMode = HazePerformanceMode.Quality,
            expandLayerBounds = true,
            interactionSource = fixture.source,
            interactionReducedMotionPolicy = policy,
          ),
        )
      }
    }
    waitForIdle()
    press(fixture)
    return fixture
  }

  @Test
  fun removeOpticalResponseWhilePressed_preservesOutputUntilExitCompletes() = runComposeUiTest {
    val effect = GlassRuntimeEffect()
    val state = HazeState()
    val source = MutableInteractionSource()
    val scope = TestScope()
    val factory = HazeEffectFactory<GlassNodeConfiguration> { effect }
    val style = mutableStateOf(
      GlassStyle.regular.then {
        pressed {
          lightingIntensity(0.5f)
          animate(tween(1), tween(5000)) { whitePointDelta(0.2f) }
        }
      },
    )
    setContent {
      Box(Modifier.size(120.dp)) {
        Box(Modifier.fillMaxSize().hazeSource(state).background(Color.Red))
        Box(
          Modifier.fillMaxSize().hazeGlass(
            factory = factory,
            expandLayerBounds = true,
            interactionSource = source,
            input = HazeInput.Sources(state),
            style = style.value,
            performanceMode = HazePerformanceMode.Quality,
            interactionReducedMotionPolicy = GlassReducedMotionPolicy.Full,
          ),
        )
      }
    }
    waitForIdle()
    scope.launch { source.emit(PressInteraction.Press(Offset(40f, 40f))) }
    scope.testScheduler.runCurrent()
    waitForIdle()
    val delegate = effect.delegate as RuntimeShaderGlassDelegate
    val optical = checkNotNull(delegate.layers.interactionOptical)
    assertThat(effect.currentInteractionState.hasOptics).isTrue()
    assertThat(delegate.canDrawRetainedOutput()).isTrue()
    mainClock.autoAdvance = false
    runOnIdle { style.value = GlassStyle.regular.then { pressed { lightingIntensity(0.5f) } } }
    repeat(49) {
      mainClock.advanceTimeBy(100, ignoreFrameDuration = true)
      waitForIdle()
      assertThat(effect.currentInteractionState.hasOptics, "optics at ${it + 1}00ms").isTrue()
      assertThat(delegate.layers.interactionOptical).isSameInstanceAs(optical)
      assertThat(delegate.canDrawRetainedOutput(), "output at ${it + 1}00ms").isTrue()
    }
    mainClock.advanceTimeBy(200)
    waitForIdle()
    assertThat(delegate.layers.interactionOptical).isNull()
    assertThat(optical.isReleased).isTrue()
    assertThat(delegate.canDrawRetainedOutput()).isTrue()
  }

  private class InputObservingGlassRenderer(private val effect: GlassRuntimeEffect) :
    HazeEffectRenderer<GlassNodeConfiguration> by effect,
    HazeEffectRendererLifecycle<GlassNodeConfiguration> by effect,
    HazeEffectRendererDrawHooks<GlassNodeConfiguration> by effect,
    HazeEffectRendererRetainedOutput by effect,
    HazeEffectRendererInteraction by effect,
    HazeEffectRendererBackdrop<GlassNodeConfiguration> by effect {
    private lateinit var inputScope: HazeEffectRuntimeDrawScope
    val layerSize get() = inputScope.layerSize
    val hasDrawableInput get() = inputScope.hasDrawableInput
    val inputSnapshot get() = inputScope.inputSnapshot
    val drawnStates = mutableListOf<GlassInteractionRenderState>()

    override fun HazeEffectDrawScope.draw(style: GlassNodeConfiguration) {
      drawnStates += effect.currentInteractionState
      with(effect) { draw(style) }
    }

    override fun HazeEffectRuntimeDrawScope.prepareDraw(style: GlassNodeConfiguration) {
      inputScope = this
      with(effect) { prepareDraw(style) }
    }
  }
}
