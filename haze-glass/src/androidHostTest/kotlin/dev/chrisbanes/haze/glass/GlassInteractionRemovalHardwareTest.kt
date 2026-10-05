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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
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
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeEffectDrawScope
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
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import org.junit.Test
import org.robolectric.annotation.Config

@OptIn(ExperimentalHazeApi::class, InternalHazeApi::class)
@Config(sdk = [37])
class GlassInteractionRemovalHardwareTest : ScreenshotTest() {
  @Test
  fun sourceBackedFusedMaterial_hardwareCaptureDistinguishesSource() = runScreenshotTest {
    val state = HazeState()
    val effect = GlassRuntimeEffect()
    val factory = HazeEffectFactory<GlassNodeConfiguration> { effect }
    setContent {
      Box(Modifier.fillMaxSize().hazeSource(state).background(Color.Red))
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
          Modifier.size(120.dp).testTag("material").hazeGlass(
            factory = factory,
            input = HazeInput.Sources(state),
            interactionSource = null,
            style = GlassStyle.regular.then { tint(Color.Blue.copy(alpha = 0.5f)) },
            performanceMode = HazePerformanceMode.Quality,
            interactionReducedMotionPolicy = GlassReducedMotionPolicy.Full,
            expandLayerBounds = true,
          ),
        )
        Box(Modifier.align(Alignment.TopStart).size(80.dp).testTag("source-control"))
      }
    }
    composeTestRule.runOnIdle { composeTestRule.activity.window.decorView.invalidate() }
    waitForIdle()
    assertThat(effect.delegate).isInstanceOf<RuntimeShaderGlassDelegate>()
    val delegate = effect.delegate as RuntimeShaderGlassDelegate
    assertThat(delegate.layers.source).isNotNull()
    assertThat(delegate.layers.optical).isNotNull()
    assertThat(delegate.lastSuccessfulSourceSnapshot).isNotNull()
    assertThat(delegate.layers.interactionOptical).isNull()
    assertThat(delegate.layers.interactionRefractionDetail).isNull()
    val pixels = captureRootPixels()
    val controlBounds = onNodeWithTag("source-control").fetchSemanticsNode().boundsInRoot
    val materialBounds = onNodeWithTag("material").fetchSemanticsNode().boundsInRoot
    val control = pixels[controlBounds.center.x.roundToInt(), controlBounds.center.y.roundToInt()]
    val material = pixels[materialBounds.center.x.roundToInt(), materialBounds.center.y.roundToInt()]
    println("P1 hardware source=$control material=$material sourceSnapshot=${delegate.lastSuccessfulSourceSnapshot}")
    assertThat(control.red, "source control red").isGreaterThan(0.9f)
    assertThat(control.blue, "source control blue").isLessThan(0.1f)
    assertThat(material.blue, "material blue distinguishes source").isGreaterThan(control.blue + 0.2f)
  }

  @Test
  fun removeWhitePoint_hardwareMaterialSurvivesFusedExit() = assertHardwareRemoval(Removal.WhitePoint)

  @Test
  fun removeRefraction_hardwareMaterialSurvivesFusedExit() = assertHardwareRemoval(Removal.Refraction)

  @Test
  fun removeLighting_hardwareMaterialSurvivesForegroundExit() = assertHardwareRemoval(Removal.Lighting)

  @Test
  fun removeAllResponses_hardwareMaterialSurvivesUntilControllerSettles() = assertHardwareRemoval(Removal.All)

  @Test
  fun rapidReplacement_hardwareMaterialSurvivesInterruptedExits() = assertHardwareRemoval(Removal.All, rapid = true)

  @Test
  fun reducedMotion_hardwareMaterialSurvivesImmediateCleanup() = assertHardwareRemoval(Removal.All, reduced = true)

  @Test
  fun removeAllResponsesWhileTransparent_hardwareCompletionReleasesLighting() = assertHardwareRemoval(Removal.All, transparent = true)


  @Test
  fun removeAllResponsesWhileTransparent_sourceUnavailableRestoresHardwareMaterial() = runScreenshotTest {
    val state = HazeState()
    val effect = GlassRuntimeEffect()
    val observer = InputObservingGlassRenderer(effect)
    val sourceEnabled = mutableStateOf(true)
    val style = mutableStateOf(baseStyle(Removal.All))
    val source = MutableInteractionSource()
    val factory = HazeEffectFactory<GlassNodeConfiguration> { observer }
    setContent {
      Box(Modifier.fillMaxSize().then(if (sourceEnabled.value) Modifier.hazeSource(state) else Modifier).background(Color.Red))
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(Modifier.size(120.dp).testTag("material").hazeGlass(
          factory = factory, input = HazeInput.Sources(state), style = style.value,
          performanceMode = HazePerformanceMode.Quality, expandLayerBounds = true,
          interactionSource = source, interactionReducedMotionPolicy = GlassReducedMotionPolicy.Full,
        ))
        Box(Modifier.align(Alignment.TopStart).size(80.dp).testTag("source-control"))
      }
    }
    waitForIdle()
    fun assertMaterial(phase: String) {
      composeTestRule.runOnIdle { composeTestRule.activity.window.decorView.invalidate() }
      waitForIdle()
      val pixels = captureRootPixels()
      val controlBounds = onNodeWithTag("source-control").fetchSemanticsNode().boundsInRoot
      val materialBounds = onNodeWithTag("material").fetchSemanticsNode().boundsInRoot
      val control = pixels[controlBounds.center.x.roundToInt(), controlBounds.center.y.roundToInt()]
      val material = pixels[materialBounds.center.x.roundToInt(), materialBounds.center.y.roundToInt()]
      println("R1 hardware phase=$phase source=$control material=$material")
      val currentDelegate = effect.delegate as RuntimeShaderGlassDelegate
      println("R1 hardware state phase=$phase hasDrawableInput=${observer.hasDrawableInput} snapshot=${observer.inputSnapshot} availability=${currentDelegate.canDrawRetainedOutput()} records=${currentDelegate.stageRecordCounts} drawCalls=${observer.drawCalls} prepareCalls=${observer.prepareCalls} sizeInvalidations=${observer.sizeInvalidations} geometry=${observer.layerSize} uniforms=${effect.preparedRender?.interactionUniforms} preparedAlpha=${effect.preparedRender?.alpha} sourceLayer=${currentDelegate.layers.source} opticalLayer=${currentDelegate.layers.optical}")
      assertThat(control.red).isGreaterThan(0.9f)
      assertThat(control.blue).isLessThan(0.1f)
      assertThat(material.blue).isGreaterThan(control.blue + 0.2f)
    }
    assertMaterial("base-reference")
    composeTestRule.runOnIdle { style.value = initialStyle(Removal.All) }
    waitForIdle()
    val scope = TestScope()
    scope.launch { source.emit(PressInteraction.Press(Offset(40f, 40f))) }
    scope.testScheduler.runCurrent()
    waitForIdle()
    assertMaterial("active-entry")
    val delegate = effect.delegate as RuntimeShaderGlassDelegate
    assertThat(delegate.fusedShader).isNotNull()
    assertThat(delegate.layers.interactionOptical).isNull()
    assertThat(delegate.layers.interactionRefractionDetail).isNull()
    val snapshot = checkNotNull(delegate.lastSuccessfulSourceSnapshot)
    val sourceLayer = checkNotNull(delegate.layers.source)
    val optical = checkNotNull(delegate.layers.optical)
    val records = delegate.sourceRecordCount
    val lighting = checkNotNull(delegate.layers.interactionLighting)
    composeTestRule.mainClock.autoAdvance = false
    fun sync() { repeat(2) { composeTestRule.mainClock.advanceTimeByFrame(); waitForIdle() } }
    composeTestRule.runOnIdle { style.value = baseStyle(Removal.All).then { alpha(0f) } }
    sync()
    composeTestRule.mainClock.advanceTimeBy(100, ignoreFrameDuration = true)
    waitForIdle()
    assertThat(effect.interactionControllerForTest).isNotNull()
    assertThat(effect.currentInteractionState.hasOptics).isTrue()
    assertThat(effect.currentInteractionState.hasLighting).isTrue()
    assertThat(effect.currentInteractionState.whitePointDelta).isGreaterThan(0f)
    assertThat(effect.currentInteractionState.refractionMultiplier).isGreaterThan(1f)
    assertThat(effect.currentInteractionState.lightingIntensity).isGreaterThan(0f)
    assertThat(lighting.isReleased).isFalse()
    composeTestRule.runOnIdle { sourceEnabled.value = false }
    sync()
    assertThat(sourceEnabled.value).isFalse()
    assertThat(observer.hasDrawableInput).isFalse()
    assertThat(observer.inputSnapshot).isNull()
    composeTestRule.mainClock.advanceTimeBy(500, ignoreFrameDuration = true)
    waitForIdle()
    assertThat(effect.interactionControllerForTest).isNull()
    assertThat(lighting.isReleased).isTrue()
    assertThat(delegate.layers.interactionLighting).isNull()
    assertThat(effect.shouldPrepareDraw(GlassNodeConfiguration(style.value, interactionSource = source))).isFalse()
    assertThat(delegate.canDrawRetainedOutput(), "hidden settled fused retained availability").isTrue()
    assertThat(delegate.lastSuccessfulSourceSnapshot).isSameInstanceAs(snapshot)
    assertThat(delegate.layers.source).isSameInstanceAs(sourceLayer)
    assertThat(delegate.layers.optical).isSameInstanceAs(optical)
    assertThat(sourceLayer.isReleased).isFalse()
    assertThat(optical.isReleased).isFalse()
    assertThat(delegate.sourceRecordCount).isEqualTo(records)
    composeTestRule.runOnIdle { style.value = baseStyle(Removal.All) }
    sync()
    assertThat(observer.hasDrawableInput).isFalse()
    assertThat(observer.inputSnapshot).isNull()
    assertThat(effect.delegate).isSameInstanceAs(delegate)
    assertThat(delegate.lastSuccessfulSourceSnapshot).isSameInstanceAs(snapshot)
    assertThat(delegate.sourceRecordCount).isEqualTo(records)
    val uniforms = checkNotNull(effect.preparedRender).interactionUniforms
    assertThat(uniforms.whitePointDelta).isEqualTo(0f)
    assertThat(uniforms.refractionMultiplier).isEqualTo(1f)
    assertThat(uniforms.lightingIntensity).isEqualTo(0f)
    assertMaterial("restored-unavailable-source")
  }


  @Test
  fun baseOnlyWhileTransparent_sourceUnavailableRestoresHardwareMaterial() = runScreenshotTest {
    val state = HazeState()
    val effect = GlassRuntimeEffect()
    val observer = InputObservingGlassRenderer(effect)
    val sourceEnabled = mutableStateOf(true)
    val base = baseStyle(Removal.All)
    val style = mutableStateOf(base)
    val factory = HazeEffectFactory<GlassNodeConfiguration> { observer }
    setContent {
      Box(Modifier.fillMaxSize().then(if (sourceEnabled.value) Modifier.hazeSource(state) else Modifier).background(Color.Red))
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(Modifier.size(120.dp).testTag("material").hazeGlass(
          factory = factory, input = HazeInput.Sources(state), style = style.value,
          performanceMode = HazePerformanceMode.Quality, expandLayerBounds = true,
          interactionSource = null, interactionReducedMotionPolicy = GlassReducedMotionPolicy.Full,
        ))
        Box(Modifier.align(Alignment.TopStart).size(80.dp).testTag("source-control"))
      }
    }
    waitForIdle()
    val delegate = effect.delegate as RuntimeShaderGlassDelegate
    fun assertMaterial(phase: String) {
      composeTestRule.runOnIdle { composeTestRule.activity.window.decorView.invalidate() }
      waitForIdle()
      val pixels = captureRootPixels()
      val controlBounds = onNodeWithTag("source-control").fetchSemanticsNode().boundsInRoot
      val materialBounds = onNodeWithTag("material").fetchSemanticsNode().boundsInRoot
      val control = pixels[controlBounds.center.x.roundToInt(), controlBounds.center.y.roundToInt()]
      val material = pixels[materialBounds.center.x.roundToInt(), materialBounds.center.y.roundToInt()]
      println("base-only hardware phase=$phase source=$control material=$material hasInput=${observer.hasDrawableInput} available=${delegate.canDrawRetainedOutput()} records=${delegate.stageRecordCounts} draws=${observer.drawCalls} prepares=${observer.prepareCalls} sizeInvalidations=${observer.sizeInvalidations} geometry=${observer.layerSize} alpha=${effect.preparedRender?.alpha}")
      assertThat(control.red).isGreaterThan(0.9f)
      assertThat(control.blue).isLessThan(0.1f)
      assertThat(material.blue).isGreaterThan(control.blue + 0.2f)
    }
    assertMaterial("reference")
    assertThat(effect.interactionControllerForTest).isNull()
    val snapshot = checkNotNull(delegate.lastSuccessfulSourceSnapshot)
    val sourceLayer = checkNotNull(delegate.layers.source)
    val optical = checkNotNull(delegate.layers.optical)
    val geometry = observer.layerSize
    val records = delegate.sourceRecordCount
    composeTestRule.mainClock.autoAdvance = false
    fun sync() { repeat(2) { composeTestRule.mainClock.advanceTimeByFrame(); waitForIdle() } }
    composeTestRule.runOnIdle { style.value = base.then { alpha(0f) } }
    sync()
    composeTestRule.runOnIdle { sourceEnabled.value = false }
    sync()
    assertThat(observer.hasDrawableInput).isFalse()
    assertThat(observer.inputSnapshot).isNull()
    composeTestRule.mainClock.advanceTimeBy(600, ignoreFrameDuration = true)
    waitForIdle()
    assertThat(effect.interactionControllerForTest).isNull()
    assertThat(delegate.canDrawRetainedOutput()).isTrue()
    assertThat(delegate.lastSuccessfulSourceSnapshot).isSameInstanceAs(snapshot)
    assertThat(delegate.layers.source).isSameInstanceAs(sourceLayer)
    assertThat(delegate.layers.optical).isSameInstanceAs(optical)
    assertThat(delegate.sourceRecordCount).isEqualTo(records)
    assertThat(observer.layerSize).isEqualTo(geometry)
    composeTestRule.runOnIdle { style.value = base }
    sync()
    assertThat(observer.hasDrawableInput).isFalse()
    assertThat(observer.inputSnapshot).isNull()
    assertThat(delegate.lastSuccessfulSourceSnapshot).isSameInstanceAs(snapshot)
    assertThat(delegate.sourceRecordCount).isEqualTo(records)
    assertMaterial("restored-unavailable-source-no-completion")
  }

  private enum class Removal { WhitePoint, Refraction, Lighting, All }

  private fun baseStyle(removal: Removal) = GlassStyle.regular.then {
    optics(
      GlassOptics(
        refractionStrength = if (removal == Removal.WhitePoint) 0f else 0.5f,
        refractionDetailIntensity = if (removal == Removal.WhitePoint) 0f else 0.75f,
        refractionDisplacement = 20.dp,
        blurRadius = OpticalSizeValue.Fixed(0.dp),
      ),
    )
    tint(Color.Blue.copy(alpha = 0.5f))
  }

  private fun initialStyle(removal: Removal) = baseStyle(removal).then {
    pressed {
      animate(tween(1), tween(500)) {
        if (removal != Removal.Refraction) whitePointDelta(0.2f)
        if (removal == Removal.Refraction || removal == Removal.All) refractionMultiplier(1.8f)
        if (removal == Removal.Lighting || removal == Removal.All) lightingIntensity(0.5f)
      }
    }
  }

  private fun assertHardwareRemoval(removal: Removal, rapid: Boolean = false, reduced: Boolean = false, transparent: Boolean = false) = runScreenshotTest {
    val state = HazeState()
    val effect = GlassRuntimeEffect()
    val source = MutableInteractionSource()
    val style = mutableStateOf(initialStyle(removal))
    val factory = HazeEffectFactory<GlassNodeConfiguration> { effect }
    val replacement = baseStyle(removal).then {
      if (removal == Removal.Lighting) pressed { animate(tween(1), tween(500)) { whitePointDelta(0.2f) } }
      if (transparent) alpha(0f)
    }
    setContent {
      Box(Modifier.fillMaxSize().hazeSource(state).background(Color.Red))
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
          Modifier.size(120.dp).testTag("material").hazeGlass(
            factory = factory,
            input = HazeInput.Sources(state),
            style = style.value,
            performanceMode = HazePerformanceMode.Quality,
            expandLayerBounds = true,
            interactionSource = source,
            interactionReducedMotionPolicy = if (reduced) GlassReducedMotionPolicy.Reduced else GlassReducedMotionPolicy.Full,
          ),
        )
        Box(Modifier.align(Alignment.TopStart).size(80.dp).testTag("source-control"))
      }
    }
    val scope = TestScope()
    scope.launch { source.emit(PressInteraction.Press(Offset(40f, 40f))) }
    scope.testScheduler.runCurrent()
    waitForIdle()
    val delegate = effect.delegate as RuntimeShaderGlassDelegate
    fun assertFusedGraph() {
      assertThat(delegate.layers.source).isNotNull()
      assertThat(delegate.layers.optical).isNotNull()
      assertThat(delegate.lastSuccessfulSourceSnapshot).isNotNull()
      assertThat(delegate.fusedShader).isNotNull()
      assertThat(delegate.layers.interactionOptical).isNull()
      assertThat(delegate.layers.interactionRefractionDetail).isNull()
      assertThat(delegate.layers.interactionRefractionDetailCoverage).isNull()
      assertThat(delegate.layers.interactionRefractionComposite).isNull()
    }
    fun assertMaterialPixels(phase: String) {
      composeTestRule.runOnIdle { composeTestRule.activity.window.decorView.invalidate() }
      waitForIdle()
      val pixels = captureRootPixels()
      val controlBounds = onNodeWithTag("source-control").fetchSemanticsNode().boundsInRoot
      val materialBounds = onNodeWithTag("material").fetchSemanticsNode().boundsInRoot
      val control = pixels[controlBounds.center.x.roundToInt(), controlBounds.center.y.roundToInt()]
      val material = pixels[materialBounds.center.x.roundToInt(), materialBounds.center.y.roundToInt()]
      println("hardware removal=$removal phase=$phase source=$control material=$material")
      assertThat(control.red).isGreaterThan(0.9f)
      assertThat(control.blue).isLessThan(0.1f)
      assertThat(material.blue).isGreaterThan(control.blue + 0.2f)
    }
    assertFusedGraph()
    assertMaterialPixels("entry")
    assertThat(effect.currentInteractionState.hasOptics).isTrue()
    val lighting = delegate.layers.interactionLighting
    if (removal == Removal.Lighting || removal == Removal.All) assertThat(lighting).isNotNull()
    composeTestRule.mainClock.autoAdvance = false
    fun replace(next: GlassStyle) {
      composeTestRule.runOnIdle { style.value = next }
      repeat(2) {
        composeTestRule.mainClock.advanceTimeByFrame()
        waitForIdle()
      }
    }
    replace(replacement)
    composeTestRule.mainClock.advanceTimeBy(100, ignoreFrameDuration = true)
    waitForIdle()
    if (!reduced) {
      assertThat(effect.currentInteractionState.hasOptics).isTrue()
      assertFusedGraph()
      lighting?.let {
        assertThat(delegate.layers.interactionLighting).isSameInstanceAs(it)
        assertThat(it.isReleased).isFalse()
      }
    }
    if (!transparent) assertMaterialPixels("100ms")
    if (rapid) {
      replace(initialStyle(removal))
      composeTestRule.mainClock.advanceTimeBy(100, ignoreFrameDuration = true)
      waitForIdle()
      assertFusedGraph()
      assertMaterialPixels("reintroduced")
      replace(replacement)
      composeTestRule.mainClock.advanceTimeBy(100, ignoreFrameDuration = true)
      waitForIdle()
      assertFusedGraph()
      assertMaterialPixels("final-exit")
    }
    composeTestRule.mainClock.advanceTimeBy(500, ignoreFrameDuration = true)
    waitForIdle()
    if (transparent) {
      assertThat(effect.interactionControllerForTest).isNull()
      assertThat(checkNotNull(lighting).isReleased).isTrue()
      assertThat(delegate.layers.interactionLighting).isNull()
      assertThat(delegate.layers.interactionOptical).isNull()
      assertThat(delegate.layers.interactionRefractionDetail).isNull()
      replace(baseStyle(removal))
      composeTestRule.mainClock.advanceTimeByFrame()
      waitForIdle()
    } else {
      assertThat(effect.currentInteractionState.hasOptics).isEqualTo(removal == Removal.Lighting)
      assertThat(effect.currentInteractionState.hasLighting).isFalse()
      if (removal != Removal.Lighting) assertThat(effect.interactionControllerForTest).isNull()
      lighting?.let { assertThat(it.isReleased).isTrue() }
      assertThat(delegate.layers.interactionLighting).isNull()
    }
    assertFusedGraph()
    assertMaterialPixels("settled")
  }

  private class InputObservingGlassRenderer(private val effect: GlassRuntimeEffect) :
    HazeEffectRenderer<GlassNodeConfiguration> by effect,
    HazeEffectRendererLifecycle<GlassNodeConfiguration> by effect,
    HazeEffectRendererDrawHooks<GlassNodeConfiguration> by effect,
    HazeEffectRendererRetainedOutput by effect,
    HazeEffectRendererInteraction by effect,
    HazeEffectRendererBackdrop<GlassNodeConfiguration> by effect {
    private lateinit var inputScope: HazeEffectRuntimeDrawScope
    var drawCalls = 0
    var prepareCalls = 0
    var sizeInvalidations = 0
    val layerSize get() = inputScope.layerSize

    override fun HazeEffectDrawScope.draw(style: GlassNodeConfiguration) {
      drawCalls++
      with(effect) { draw(style) }
    }

    override fun invalidateRetainedOutput() {
      sizeInvalidations++
      effect.invalidateRetainedOutput()
    }

    val hasDrawableInput get() = inputScope.hasDrawableInput
    val inputSnapshot get() = inputScope.inputSnapshot

    override fun HazeEffectRuntimeDrawScope.prepareDraw(style: GlassNodeConfiguration) {
      prepareCalls++
      inputScope = this
      with(effect) { prepareDraw(style) }
    }
  }
}
