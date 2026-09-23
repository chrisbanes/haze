// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PixelMap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveTo
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.roundToIntSize
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThan
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import dev.chrisbanes.haze.Bitmask
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeEffectRuntimeDrawScope
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.RuntimeShaderRenderEffectException
import dev.chrisbanes.haze.TrimMemoryLevel
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.test.ContextTest
import kotlin.math.sqrt
import kotlin.test.Test

@OptIn(
  ExperimentalTestApi::class,
  ExperimentalHazeApi::class,
  InternalHazeApi::class,
)
class RuntimeShaderGlassDelegateIntegrationTest : ContextTest() {
  private val attachedRuntimes = mutableMapOf<GlassRuntimeEffect, GlassRuntimeEffect>()
  private val rendererFactories =
    mutableMapOf<GlassRuntimeEffect, HazeEffectFactory<GlassNodeConfiguration>>()

  @Test
  fun sourceOnlyRecordRenewsAdaptiveDemandWithoutRepreparingUnchangedTier() = runComposeUiTest {
    val hazeState = HazeState()
    val color = mutableStateOf(Color.Red)
    val effect = activeDetailEffect()
    setContent {
      GlassAdaptiveHost {
        Box(Modifier.size(120.dp)) {
          Box(Modifier.fillMaxSize().hazeSource(hazeState).drawBehind { drawRect(color.value) })
          Box(
            Modifier.fillMaxSize().testTag("glass").testGlass(
              effect,
              input = HazeInput.Sources(hazeState),
              performanceMode = HazePerformanceMode.Adaptive,
            ),
          )
        }
      }
    }
    waitForIdle()
    val runtime = runtime(effect)
    val host = checkNotNull(runtime.adaptiveHostForTest)
    val prepared = checkNotNull(runtime.preparedRender)
    waitUntil(timeoutMillis = 5_000) { !host.hasActiveDemand }

    color.value = Color.Blue
    waitUntil(timeoutMillis = 5_000) { host.hasActiveDemand }
    waitForIdle()

    assertThat(runtime.preparedRender).isSameInstanceAs(prepared)
  }

  @Test
  fun sourceOnlyRecordAppliesChangedUsableTierOnceWithoutSourceFeedback() = runComposeUiTest {
    val hazeState = HazeState()
    val color = mutableStateOf(Color.Red)
    var sourceDraws = 0
    val effect = activeDetailEffect()
    setContent {
      GlassAdaptiveHost {
        Box(Modifier.size(120.dp)) {
          Box(
            Modifier.fillMaxSize().hazeSource(hazeState).drawBehind {
              sourceDraws++
              drawRect(color.value)
            },
          )
          Box(
            Modifier.fillMaxSize().testTag("glass").testGlass(
              effect,
              input = HazeInput.Sources(hazeState),
              performanceMode = HazePerformanceMode.Adaptive,
            ),
          )
        }
      }
    }
    waitForIdle()
    val runtime = runtime(effect)
    val host = checkNotNull(runtime.adaptiveHostForTest)
    val prepared = checkNotNull(runtime.preparedRender)
    val drawsBefore = sourceDraws
    color.value = Color.Blue
    waitUntil(timeoutMillis = 5_000) { host.hasActiveDemand }
    host.recordSustainedMisses()
    color.value = Color.Green
    waitForIdle()

    assertThat(sourceDraws).isEqualTo(drawsBefore + 2)
    assertThat((runtime.preparedRenderBudget as GlassRenderBudgetDecision.Runtime).scaleFactor)
      .isEqualTo(GlassInputScalePolicy.AGGRESSIVE_SCALE)
    assertThat(runtime.preparedRender).isNotSameInstanceAs(prepared)
  }

  @Test
  fun sourceOnlyRecordsRenewOnlySelectedAdaptiveConsumersAcrossHosts() = runComposeUiTest {
    val firstState = HazeState()
    val secondState = HazeState()
    val firstColor = mutableStateOf(Color.Red)
    val secondColor = mutableStateOf(Color.Blue)
    val showSibling = mutableStateOf(true)
    val first = activeDetailEffect()
    val sibling = activeDetailEffect()
    val other = activeDetailEffect()
    val fixed = activeDetailEffect()

    setContent {
      GlassAdaptiveHost {
        Box(Modifier.size(120.dp)) {
          Box(
            Modifier.fillMaxSize().hazeSource(firstState).drawBehind {
              drawRect(firstColor.value)
            },
          )
          Box(
            Modifier.fillMaxSize().testGlass(
              first,
              HazeInput.Sources(firstState),
              HazePerformanceMode.Adaptive,
            ),
          )
          if (showSibling.value) {
            Box(
              Modifier.fillMaxSize().testGlass(
                sibling,
                HazeInput.Sources(firstState),
                HazePerformanceMode.Adaptive,
              ),
            )
          }
          Box(
            Modifier.fillMaxSize().testGlass(
              fixed,
              HazeInput.Sources(firstState),
              HazePerformanceMode.Fixed(1f),
            ),
          )
        }
      }
      GlassAdaptiveHost {
        Box(Modifier.size(120.dp)) {
          Box(
            Modifier.fillMaxSize().hazeSource(secondState).drawBehind {
              drawRect(secondColor.value)
            },
          )
          Box(
            Modifier.fillMaxSize().testGlass(
              other,
              HazeInput.Sources(secondState),
              HazePerformanceMode.Adaptive,
            ),
          )
        }
      }
    }
    waitForIdle()
    val firstHost = checkNotNull(runtime(first).adaptiveHostForTest)
    val otherHost = checkNotNull(runtime(other).adaptiveHostForTest)
    assertThat(runtime(sibling).adaptiveHostForTest).isSameInstanceAs(firstHost)
    assertThat(otherHost === firstHost).isFalse()
    assertThat(runtime(fixed).adaptiveHostForTest).isNull()
    waitUntil(timeoutMillis = 5_000) { !firstHost.hasActiveDemand && !otherHost.hasActiveDemand }

    firstColor.value = Color.Green
    waitUntil(timeoutMillis = 5_000) { firstHost.activeDemandCount == 2 }
    assertThat(otherHost.hasActiveDemand).isFalse()
    assertThat(runtime(fixed).performanceModeForTest).isEqualTo(HazePerformanceMode.Fixed(1f))

    showSibling.value = false
    waitForIdle()
    assertThat(firstHost.subscriberCount).isEqualTo(1)
    waitUntil(timeoutMillis = 5_000) { !firstHost.hasActiveDemand }
    firstColor.value = Color.Yellow
    waitUntil(timeoutMillis = 5_000) { firstHost.activeDemandCount == 1 }

    secondColor.value = Color.Red
    waitUntil(timeoutMillis = 5_000) { otherHost.hasActiveDemand }
    assertThat(firstHost.subscriberCount).isEqualTo(1)
  }

  @Test
  fun backgroundOpacityChange_updatesRetainedBlurNormalization() = runComposeUiTest {
    val base = GlassStyle.regular.then {
      optics(GlassDefaults.optics.copy(progressive = HazeProgressive.verticalGradient()))
    }
    val effect = GlassRuntimeEffect()
    val style = mutableStateOf(base.then { backgroundColor(Color.White) })
    setContent { RuntimeGlassTestContent(effect, tag = "glass", style = style.value) }
    waitForIdle()
    val before = checkNotNull(runtime(effect).preparedRender?.blurKey)
    assertThat(before.sourceIsOpaque).isTrue()

    style.value = base.then { backgroundColor(Color.White.copy(alpha = 0.5f)) }
    waitForIdle()
    val translucent = checkNotNull(runtime(effect).preparedRender?.blurKey)
    assertThat(translucent.sourceIsOpaque).isFalse()
    assertThat(translucent).isNotEqualTo(before)

    style.value = base.then { backgroundColor(Color.Black) }
    waitForIdle()
    assertThat(checkNotNull(runtime(effect).preparedRender?.blurKey).sourceIsOpaque).isTrue()
  }

  @Test
  fun edgeWidthChange_refreshesBothSamplingPassesAndZeroReleasesDetail() = runComposeUiTest {
    val effect = runtimeInteractiveEffect()
    val optics = GlassStyle.clearOptics
    val style = mutableStateOf(effect.style.then { optics(optics) })
    setContent { RuntimeGlassTestContent(effect, tag = "glass", style = style.value) }
    waitForIdle()
    onNodeWithTag("glass").performTouchInput { down(Offset(20f, 20f)) }
    mainClock.advanceTimeBy(500)
    waitForIdle()

    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate
    val before = checkNotNull(runtime(effect).preparedRender)
    val interactionRecords = delegate.interactionDetailRecordCount
    style.value = effect.style.then { optics(optics.copy(refractionProfile = RefractionProfile.Edge(14.dp))) }
    waitForIdle()
    val after = checkNotNull(runtime(effect).preparedRender)
    assertThat(after.opticalKey).isNotEqualTo(before.opticalKey)
    assertThat(after.refractionDetailKey).isNotEqualTo(before.refractionDetailKey)
    assertThat(after.blurKey).isEqualTo(before.blurKey)
    assertThat(after.rimKey).isEqualTo(before.rimKey)
    assertThat(delegate.interactionDetailRecordCount).isGreaterThan(interactionRecords)

    style.value = effect.style.then { optics(optics.copy(refractionProfile = RefractionProfile.Edge(0.dp))) }
    waitForIdle()
    val disabled = checkNotNull(runtime(effect).preparedRender)
    assertThat(disabled.refractionDetailKey).isNull()
    assertThat(disabled.plan.layers.any { it.kind == GlassRetainedLayerKind.RefractionDetail }).isFalse()
    assertThat(delegate.layers.refractionDetail).isNull()
  }

  @Test
  fun firstAlphaZeroFrame_skipsRuntimeShaderAndLayerGraphPreparation() = runComposeUiTest {
    var creationAttempts = 0
    val effect = activeDetailEffect().apply {
      style = style.then { alpha(0f) }
      runtimeEffectFactory = GlassRuntimeEffectFactory { create ->
        creationAttempts++
        create()
      }
    }

    val style = mutableStateOf(effect.style)
    setContent { RuntimeGlassTestContent(effect, tag = "glass", style = style.value) }
    waitForIdle()

    assertThat(runtime(effect).preparedRender).isNull()
    assertThat(runtime(effect).delegate).isInstanceOf<FallbackGlassDelegate>()
    assertThat(runtime(effect).dirtyTracker).isEqualTo(Bitmask())
    assertThat(creationAttempts).isEqualTo(0)

    style.value = style.value.then { alpha(0.5f) }
    waitForIdle()

    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate
    assertThat(creationAttempts).isGreaterThan(0)
    assertThat(delegate.sourceRecordCount).isGreaterThan(0)
  }

  @Test
  fun alphaZero_retainsOutputAndDefersSourceRefreshUntilVisible() = runComposeUiTest {
    val hazeState = HazeState()
    val sourceColor = mutableStateOf(Color.Red)
    val effect = activeDetailEffect().apply { style = style.then { alpha(0.5f) } }
    val style = mutableStateOf(effect.style)
    setContent {
      Box(Modifier.size(120.dp)) {
        Box(
          Modifier
            .fillMaxSize()
            .hazeSource(hazeState)
            .background(sourceColor.value),
        )
        Box(
          Modifier
            .fillMaxSize()
            .testTag("glass")
            .testGlass(effect, input = HazeInput.Sources(hazeState), style = style.value),
        )
      }
    }
    waitForIdle()

    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate
    val recordsBeforeZero = delegate.stageRecordCounts
    val sourceSnapshotBeforeZero = checkNotNull(delegate.lastSuccessfulSourceSnapshot)

    style.value = style.value.then { alpha(0f) }
    waitForIdle()

    assertThat(delegate.canDrawRetainedOutput()).isTrue()
    assertThat(delegate.stageRecordCounts).isEqualTo(recordsBeforeZero)
    assertThat(delegate.lastSuccessfulSourceSnapshot).isSameInstanceAs(sourceSnapshotBeforeZero)

    sourceColor.value = Color.Blue
    waitForIdle()

    assertThat(delegate.canDrawRetainedOutput()).isTrue()
    assertThat(delegate.stageRecordCounts).isEqualTo(recordsBeforeZero)
    assertThat(delegate.lastSuccessfulSourceSnapshot).isSameInstanceAs(sourceSnapshotBeforeZero)

    style.value = style.value.then { alpha(0.5f) }
    waitForIdle()

    assertThat(delegate.canDrawRetainedOutput()).isTrue()
    assertThat(delegate.sourceRecordCount).isGreaterThan(recordsBeforeZero.source)
    assertThat(delegate.lastSuccessfulSourceSnapshot).isNotSameInstanceAs(sourceSnapshotBeforeZero)
  }

  @Test
  fun nonFiniteCornerShape_runtimeUsesCanonicalSafeRadii() = runComposeUiTest {
    val effect = activeDetailEffect().apply {
      style = style.then { shape(invalidCornerShape(Float.NaN)) }
    }

    setContent {
      Box(
        Modifier
          .size(120.dp)
          .testTag("glass")
          .testGlass(effect),
      ) {
        Box(Modifier.fillMaxSize().background(Color.Red))
      }
    }
    waitForIdle()

    assertThat(runtime(effect).delegate is RuntimeShaderGlassDelegate).isTrue()
    val radii = checkNotNull(runtime(effect).preparedRender).params.cornerRadii
    assertThat(radii.values().all { it.isFinite() && it >= 0f }).isTrue()
  }

  @Test
  fun nonFiniteCornerShape_fallbackDrawUsesCanonicalSafeRadii() = runComposeUiTest {
    val effect = GlassRuntimeEffect().apply {
      style = style.then { shape(invalidCornerShape(Float.POSITIVE_INFINITY)) }
    }

    setContent {
      Box(
        Modifier
          .size(120.dp)
          .testTag("glass")
          .testGlass(effect),
      ) {
        Box(Modifier.fillMaxSize().background(Color.Red))
      }
    }
    waitForIdle()
    runtime(effect).delegate = FallbackGlassDelegate(runtime(effect))

    assertThat(runtime(effect).delegate is FallbackGlassDelegate).isTrue()
    onNodeWithTag("glass").captureToImage()
  }

  @Test
  fun configuredInteractiveEffect_allocatesStableInteractionStagesWhileIdle() = runComposeUiTest {
    val effect = runtimeInteractiveEffect()

    setContent { RuntimeGlassTestContent(effect, tag = "glass") }
    waitForIdle()

    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate
    assertThat(delegate.layers.hasInteractionOptical).isTrue()
    assertThat(delegate.layers.hasInteractionRefractionDetail).isTrue()
    assertThat(delegate.layers.hasInteractionRefractionDetailCoverage).isTrue()
    assertThat(delegate.layers.hasInteractionRefractionComposite).isTrue()
    assertThat(delegate.layers.hasInteractionLighting).isTrue()
  }

  @Test
  fun maximumRefraction_foregroundContentSelectsFallbackDelegate() = runComposeUiTest {
    val effect = GlassRuntimeEffect().apply {
      style = style.then {
        optics(
          GlassOptics(
            refractionStrength = 1f,
            refractionDisplacement = 16_384.dp,
            blurRadius = OpticalSizeValue.Fixed(0.dp),
          ),
        )
        edgeSoftness(0.dp)
        shape(RoundedCornerShape(0.dp))
      }
    }

    setContent {
      Box(
        Modifier
          .size(120.dp)
          .testTag("glass")
          .testGlass(effect),
      ) {
        Box(Modifier.fillMaxSize().background(Color.Red))
      }
    }
    waitForIdle()

    assertThat(runtime(effect).delegate is FallbackGlassDelegate).isTrue()
    assertThat(runtime(effect).preparedRender).isNull()
  }

  @Test
  fun interactionFrames_updateDynamicStagesWithoutRecreatingBaseOpticalEffect() = runComposeUiTest {
    val effect = runtimeInteractiveEffect()
    setContent { RuntimeGlassTestContent(effect, tag = "glass") }
    waitForIdle()
    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate
    val opticalEffect = checkNotNull(delegate.opticalEffect)

    onNodeWithTag("glass").performTouchInput {
      down(Offset(20f, 20f))
      moveTo(Offset(80f, 60f))
    }
    mainClock.advanceTimeBy(500)
    waitForIdle()

    assertThat(delegate.opticalEffect).isSameInstanceAs(opticalEffect)
    assertThat(delegate.layers.hasInteractionOptical).isTrue()
    assertThat(delegate.layers.hasInteractionRefractionDetail).isTrue()
    assertThat(delegate.layers.hasInteractionRefractionDetailCoverage).isTrue()
    assertThat(delegate.layers.hasInteractionRefractionComposite).isTrue()
    assertThat(delegate.layers.hasInteractionLighting).isTrue()
  }

  @Test
  fun interactionOpticalEffect_reusesStableLayerEffectAndUpdatesNewTargets() = runComposeUiTest {
    val effect = runtimeInteractiveEffect()
    val style = mutableStateOf(effect.style)
    setContent { RuntimeGlassTestContent(effect, tag = "glass", style = style.value) }
    waitForIdle()

    runtime(effect).setPressedForTest(Offset(60f, 60f))
    waitForIdle()

    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate
    val layer = checkNotNull(delegate.layers.interactionOptical)
    val stableEffect = checkNotNull(layer.renderEffect)

    runtime(effect).setPressedForTest(Offset(60f, 60f))
    waitForIdle()

    assertThat(layer.renderEffect).isSameInstanceAs(stableEffect)

    style.value = style.value.then { ambientResponse(0.6f) }
    waitForIdle()

    assertThat(layer.renderEffect).isNotSameInstanceAs(stableEffect)

    delegate.layers.interactionOptical = null
    style.value = style.value.then { ambientResponse(0.5f) }
    waitForIdle()

    assertThat(checkNotNull(delegate.layers.interactionOptical).renderEffect).isNotNull()
  }

  @Test
  fun largePanel_interactionPatchRetainsBaseLayersAcrossFrames() = runComposeUiTest {
    val effect = activeDetailEffect().apply {
      style = style.then {
        pressed {
          animate(toSpec = tween(1), fromSpec = tween(1)) {
            lightingIntensity(1f)
            refractionMultiplier(1.08f)
            whitePointDelta(0.04f)
          }
        }
      }
      style = style.then {
        interactionLightRadiusFraction(0.25f)
        interactionPositionAnimationSpec(tween(1))
      }
      interactionReducedMotionPolicy = GlassReducedMotionPolicy.Full
    }
    setContent { RuntimeLargeGlassTestContent(effect) }
    waitForIdle()
    mainClock.autoAdvance = false

    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate
    val source = checkNotNull(delegate.layers.source)
    val optical = checkNotNull(delegate.layers.optical)
    val detail = checkNotNull(delegate.layers.refractionDetail)
    val decision = runtime(effect).preparedRenderBudget as GlassRenderBudgetDecision.Runtime
    val plannedKinds = checkNotNull(runtime(effect).preparedRender).plan.layers.map { it.kind }
    val positions = listOf(Offset(200f, 150f), Offset(500f, 300f), Offset(800f, 450f))

    positions.forEach { position ->
      runtime(effect).setPressedForTest(position)
      mainClock.advanceTimeByFrame()
      mainClock.advanceTimeByFrame()

      assertThat(runtime(effect).delegate).isSameInstanceAs(delegate)
      assertThat(delegate.layers.source).isSameInstanceAs(source)
      assertThat(delegate.layers.optical).isSameInstanceAs(optical)
      assertThat(delegate.layers.refractionDetail).isSameInstanceAs(detail)
      assertThat((runtime(effect).preparedRenderBudget as GlassRenderBudgetDecision.Runtime).scaleFactor)
        .isEqualTo(decision.scaleFactor)
      assertThat(checkNotNull(delegate.layers.interactionOptical).size.width).isLessThan(source.size.width)
      assertThat(checkNotNull(delegate.layers.interactionOptical).size.height).isLessThan(source.size.height)
      assertThat(checkNotNull(runtime(effect).preparedRender).plan.layers.map { it.kind }).isEqualTo(plannedKinds)
    }

    runtime(effect).setPressedForTest(positions.last(), pressed = false)
    repeat(3) {
      mainClock.advanceTimeByFrame()
      assertThat(runtime(effect).delegate).isSameInstanceAs(delegate)
      assertThat(delegate.layers.source).isSameInstanceAs(source)
      assertThat(delegate.layers.optical).isSameInstanceAs(optical)
      assertThat(delegate.layers.refractionDetail).isSameInstanceAs(detail)
      assertThat((runtime(effect).preparedRenderBudget as GlassRenderBudgetDecision.Runtime).scaleFactor)
        .isEqualTo(decision.scaleFactor)
      assertThat(checkNotNull(runtime(effect).preparedRender).plan.layers.map { it.kind }).isEqualTo(plannedKinds)
    }
    mainClock.autoAdvance = true
    setContent {}
    waitForIdle()
  }

  @Test
  fun adaptiveSampling_selectsTierFromPreparedRetainedWorkload() = runComposeUiTest {
    val smallEffect = activeDetailEffect()
    setContent {
      RuntimeGlassTestContent(
        effect = smallEffect,
        tag = "small",
        performanceMode = HazePerformanceMode.Adaptive,
      )
    }
    waitForIdle()

    assertThat(
      (runtime(smallEffect).preparedRenderBudget as GlassRenderBudgetDecision.Runtime).scaleFactor,
    ).isEqualTo(GlassInputScalePolicy.BALANCED_SCALE)

    val largeEffect = activeDetailEffect()
    setContent {
      RuntimeLargeGlassTestContent(
        effect = largeEffect,
        performanceMode = HazePerformanceMode.Adaptive,
      )
    }
    waitForIdle()

    assertThat(
      (runtime(largeEffect).preparedRenderBudget as GlassRenderBudgetDecision.Runtime).scaleFactor,
    ).isEqualTo(0.5f)
  }

  @Test
  fun adaptiveHostTierIsConsumedOnNextDrawWithoutWakingSiblingOrChangingFixedScale() = runComposeUiTest {
    val firstEffect = activeDetailEffect()
    val secondEffect = activeDetailEffect()
    val fixedEffect = activeDetailEffect()
    val secondSourceColor = mutableStateOf(Color.Red)
    setContent {
      GlassAdaptiveHost {
        RuntimeGlassTestContent(firstEffect, tag = "first", performanceMode = HazePerformanceMode.Adaptive)
        RuntimeGlassTestContent(
          secondEffect,
          tag = "second",
          performanceMode = HazePerformanceMode.Adaptive,
          sourceColor = secondSourceColor.value,
        )
        RuntimeGlassTestContent(
          fixedEffect,
          tag = "fixed",
          performanceMode = HazePerformanceMode.Fixed(0.5f),
        )
      }
    }
    waitForIdle()

    val first = runtime(firstEffect)
    val second = runtime(secondEffect)
    val fixed = runtime(fixedEffect)
    val host = checkNotNull(first.adaptiveHostForTest)
    assertThat(second.adaptiveHostForTest).isSameInstanceAs(host)
    assertThat(fixed.adaptiveHostForTest).isNull()
    val secondPrepared = checkNotNull(second.preparedRender)
    val secondInputSnapshot = checkNotNull(second.observedInputSnapshotForTest)
    val firstPrepared = checkNotNull(first.preparedRender)
    val fixedScale = sqrt(0.625f)
    assertThat((fixed.preparedRenderBudget as GlassRenderBudgetDecision.Runtime).scaleFactor)
      .isEqualTo(fixedScale)

    runOnIdle {
      host.recordSustainedMisses()
      checkNotNull(first.attachedContextForTest).invalidateDraw()
    }
    waitForIdle()

    assertThat(host.decision.timingTier).isEqualTo(GlassAdaptiveTier.AGGRESSIVE)
    assertThat((first.preparedRenderBudget as GlassRenderBudgetDecision.Runtime).scaleFactor)
      .isEqualTo(GlassInputScalePolicy.AGGRESSIVE_SCALE)
    assertThat(checkNotNull(first.preparedRender)).isNotSameInstanceAs(firstPrepared)
    assertThat(second.preparedRender).isSameInstanceAs(secondPrepared)
    assertThat((second.preparedRenderBudget as GlassRenderBudgetDecision.Runtime).scaleFactor)
      .isEqualTo(GlassInputScalePolicy.BALANCED_SCALE)
    assertThat((fixed.preparedRenderBudget as GlassRenderBudgetDecision.Runtime).scaleFactor)
      .isEqualTo(fixedScale)

    runOnIdle { secondSourceColor.value = Color.Blue }
    waitForIdle()
    runOnIdle {
      host.recordSustainedMisses()
      checkNotNull(second.attachedContextForTest).invalidateDraw()
    }
    waitForIdle()
    assertThat(second.observedInputSnapshotForTest).isNotEqualTo(secondInputSnapshot)
    assertThat((second.preparedRenderBudget as GlassRenderBudgetDecision.Runtime).scaleFactor)
      .isEqualTo(GlassInputScalePolicy.AGGRESSIVE_SCALE)

    runOnIdle {
      host.recordSample(GlassFrameHealthSample(100, 1L, 1L, 17_000_000L), 300_000_002L)
      checkNotNull(first.attachedContextForTest).invalidateDraw()
    }
    waitForIdle()
    assertThat((first.preparedRenderBudget as GlassRenderBudgetDecision.Runtime).scaleFactor)
      .isEqualTo(GlassInputScalePolicy.BALANCED_SCALE)
    assertThat((fixed.preparedRenderBudget as GlassRenderBudgetDecision.Runtime).scaleFactor)
      .isEqualTo(fixedScale)
  }

  @Test
  fun fixedRenderModesKeepExactEndpointAndMidpointScalesUnderAdaptiveHostWrapper() = runComposeUiTest {
    val minimum = activeDetailEffect()
    val midpoint = activeDetailEffect()
    val maximum = activeDetailEffect()
    setContent {
      GlassAdaptiveHost {
        RuntimeGlassTestContent(minimum, "minimum", HazePerformanceMode.Fixed(0f))
        RuntimeGlassTestContent(midpoint, "midpoint", HazePerformanceMode.Fixed(0.5f))
        RuntimeGlassTestContent(maximum, "maximum", HazePerformanceMode.Fixed(1f))
      }
    }
    waitForIdle()

    assertThat((runtime(minimum).preparedRenderBudget as GlassRenderBudgetDecision.Runtime).scaleFactor)
      .isEqualTo(0.5f)
    assertThat((runtime(midpoint).preparedRenderBudget as GlassRenderBudgetDecision.Runtime).scaleFactor)
      .isEqualTo(sqrt(0.625f))
    assertThat((runtime(maximum).preparedRenderBudget as GlassRenderBudgetDecision.Runtime).scaleFactor)
      .isEqualTo(1f)
  }

  @Test
  fun movingInteractionWithinSamePatchSize_doesNotRerecordLightingContent() = runComposeUiTest {
    val effect = runtimeInteractiveEffect().apply {
      style = style.then {
        interactionLightRadiusFraction(0.25f)
        interactionPositionAnimationSpec(tween(1))
      }
      interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced
    }
    setContent { RuntimeLargeGlassTestContent(effect) }
    waitForIdle()

    runtime(effect).setPressedForTest(Offset(300f, 240f))
    waitForIdle()

    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate
    val recordsAfterPress = delegate.interactionLightingRecordCount

    runtime(effect).setPressedForTest(Offset(500f, 320f))
    waitForIdle()

    assertThat(delegate.interactionLightingRecordCount).isEqualTo(recordsAfterPress)
  }

  @Test
  fun movingInteractionWithinSamePatchSize_rerecordsLocalizedContent() = runComposeUiTest {
    val effect = runtimeInteractiveEffect().apply {
      style = style.then {
        interactionLightRadiusFraction(0.25f)
        interactionPositionAnimationSpec(tween(1))
      }
      interactionReducedMotionPolicy = GlassReducedMotionPolicy.Reduced
    }
    setContent { RuntimeLargeGlassTestContent(effect) }
    waitForIdle()

    runtime(effect).setPressedForTest(Offset(300f, 240f))
    waitForIdle()

    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate
    val source = checkNotNull(delegate.layers.source)
    val optical = checkNotNull(delegate.layers.optical)
    val interactionOpticalRecords = delegate.interactionOpticalRecordCount
    val interactionDetailRecords = delegate.interactionDetailRecordCount
    val interactionCompositeRecords = delegate.interactionCompositeRecordCount

    runtime(effect).setPressedForTest(Offset(500f, 320f))
    waitForIdle()

    assertThat(delegate.layers.source).isSameInstanceAs(source)
    assertThat(delegate.layers.optical).isSameInstanceAs(optical)
    assertThat(delegate.interactionOpticalRecordCount).isGreaterThan(interactionOpticalRecords)
    assertThat(delegate.interactionDetailRecordCount).isGreaterThan(interactionDetailRecords)
    assertThat(delegate.interactionCompositeRecordCount).isGreaterThan(interactionCompositeRecords)
  }

  @Test
  fun activeInteractionWithoutPatch_retainsBaseOutput() = runComposeUiTest {
    val effect = runtimeInteractiveEffect()
    setContent { RuntimeGlassTestContent(effect, tag = "glass") }
    waitForIdle()

    runtime(effect).setPressedForTest(Offset(60f, 60f))
    mainClock.advanceTimeBy(500)
    waitForIdle()

    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate
    RuntimeShaderGlassDelegate::class.java.getDeclaredField("preparedInteractionPatch").apply {
      isAccessible = true
      set(delegate, null)
    }
    delegate.layers.interactionOptical = null
    delegate.layers.interactionRefractionDetail = null
    delegate.layers.interactionLighting = null

    assertThat(runtime(effect).currentInteractionSignals.pressed).isTrue()
    assertThat(delegate.canDrawRetainedOutput()).isTrue()
  }

  @Test
  fun activeInteraction_liveAndBaseUniformChangesRetainInteractionShaderHandles() =
    runComposeUiTest {
      val effect = runtimeInteractiveEffect()
      val style = mutableStateOf(effect.style)
      setContent { RuntimeGlassTestContent(effect, tag = "glass", style = style.value) }
      waitForIdle()

      onNodeWithTag("glass").performTouchInput { down(Offset(20f, 20f)) }
      mainClock.advanceTimeBy(500)
      waitForIdle()

      val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate
      val opticalEffect = delegate.interactionShaderHandle("interactionOpticalEffect")
      val detailEffect = delegate.interactionShaderHandle("interactionDetailEffect")
      val lightingEffect = delegate.interactionShaderHandle("interactionLightingEffect")

      runtime(effect).setPressedForTest(Offset(80f, 60f))
      mainClock.advanceTimeBy(16)
      waitForIdle()
      style.value = style.value.then {
        ambientResponse(0.6f)
        optics(effect.optics.copy(refractionDisplacement = 18.dp))
      }
      waitForIdle()

      assertThat(delegate.interactionShaderHandle("interactionOpticalEffect"))
        .isSameInstanceAs(opticalEffect)
      assertThat(delegate.interactionShaderHandle("interactionDetailEffect"))
        .isSameInstanceAs(detailEffect)
      assertThat(delegate.interactionShaderHandle("interactionLightingEffect"))
        .isSameInstanceAs(lightingEffect)

      assertThat(delegate.rimBrushProvider).isNotNull()
      runtime(effect).onTrimMemory(TrimMemoryLevel.UI_HIDDEN)
      assertThat(delegate.rimBrushProvider).isNull()
      assertThat(delegate.interactionShaderHandleOrNull("interactionOpticalEffect")).isNull()
      assertThat(delegate.interactionShaderHandleOrNull("interactionDetailEffect")).isNull()
      assertThat(delegate.interactionShaderHandleOrNull("interactionLightingEffect")).isNull()
    }

  @Test
  fun runtimeConstructionFailure_preparesFallbackBeforeContentBehindDecision() = runComposeUiTest {
    var creationAttempts = 0
    val effect = activeDetailEffect().apply {
      runtimeEffectFactory = GlassRuntimeEffectFactory {
        creationAttempts++
        throw RuntimeShaderRenderEffectException(
          IllegalArgumentException("broken runtime effect"),
        )
      }
    }
    val style = mutableStateOf(effect.style)
    setContent { RuntimeForegroundGlassTestContent(effect, tag = "glass", style = style.value) }
    waitForIdle()

    assertThat(runtime(effect).delegate).isInstanceOf<FallbackGlassDelegate>()
    assertThat(creationAttempts).isEqualTo(1)
    val failureFrameCenter = onNodeWithTag("glass").captureToImage().toPixelMap()[60, 60]
    assertThat(failureFrameCenter.alpha).isGreaterThan(0.9f)
    assertThat(failureFrameCenter.red).isGreaterThan(0.9f)
    val attemptsAfterDowngrade = creationAttempts

    style.value = style.value.then { tint(Color.Blue.copy(alpha = 0.5f)) }
    waitForIdle()

    assertThat(runtime(effect).delegate).isInstanceOf<FallbackGlassDelegate>()
    assertThat(creationAttempts).isEqualTo(attemptsAfterDowngrade)
    onNodeWithTag("glass").captureToImage()
  }

  @Test
  fun runtimeDrawFailure_drawsFallbackInTheFailureFrame() = runComposeUiTest {
    val effect = activeDetailEffect().apply { style = style.then { tint(Color.Blue) } }
    setContent { RuntimeGlassTestContent(effect, tag = "glass") }
    waitForIdle()

    val runtime = runtime(effect)
    assertThat(runtime.delegate).isInstanceOf<RuntimeShaderGlassDelegate>()
    runtime.delegate = FailingRuntimeShaderGlassDelegate()
    checkNotNull(runtime.attachedContextForTest).invalidateDraw()
    waitForIdle()

    val failureFrameCenter = onNodeWithTag("glass").captureToImage().toPixelMap()[60, 60]
    assertThat(runtime.delegate).isInstanceOf<FallbackGlassDelegate>()
    assertThat(failureFrameCenter.blue).isGreaterThan(0.9f)
    assertThat(failureFrameCenter.red).isLessThan(0.1f)
  }

  @Test
  fun runtimeDrawFailure_preservesFullOpacityContentInputInTheFailureFrame() = runComposeUiTest {
    val effect = activeDetailEffect().apply { style = style.then { alpha(0.5f) } }
    setContent { RuntimeContentGlassTestContent(effect, tag = "glass") }
    waitForIdle()

    val runtime = runtime(effect)
    assertThat(runtime.delegate).isInstanceOf<RuntimeShaderGlassDelegate>()
    runtime.delegate = FailingRuntimeShaderGlassDelegate()
    checkNotNull(runtime.attachedContextForTest).invalidateDraw()
    waitForIdle()

    val failureFrameCenter = onNodeWithTag("glass").captureToImage().toPixelMap()[60, 60]
    assertThat(runtime.delegate).isInstanceOf<FallbackGlassDelegate>()
    assertThat(failureFrameCenter.red).isGreaterThan(0.9f)
    assertThat(failureFrameCenter.alpha).isGreaterThan(0.9f)
  }

  @Test
  fun interactionRuntimeEffects_constructBeforeDraw() = runComposeUiTest {
    var creationAttempts = 0
    val effect = runtimeInteractiveEffect().apply {
      runtimeEffectFactory = GlassRuntimeEffectFactory { create ->
        creationAttempts++
        create()
      }
    }
    setContent { RuntimeGlassTestContent(effect, tag = "glass") }
    waitForIdle()
    val attemptsAfterPreparation = creationAttempts

    runtime(effect).setPressedForTest(Offset(60f, 60f))
    mainClock.advanceTimeBy(500)
    waitForIdle()

    assertThat(runtime(effect).delegate).isInstanceOf<RuntimeShaderGlassDelegate>()
    assertThat(creationAttempts).isEqualTo(attemptsAfterPreparation)
  }

  @Test
  fun heldInteraction_sourceContentChangeUpdatesPixels() = runComposeUiTest {
    val hazeState = HazeState()
    val sourceColor = mutableStateOf(Color.Red)
    val effect = runtimeInteractiveEffect().apply {
      style = style.then {
        ambientResponse(0f)
        contrast(0f)
        contentNormalBlend(0f)
        whitePoint(0f)
        chromaMultiplier(1f)
      }
    }
    setContent {
      Box(Modifier.size(120.dp)) {
        Box(
          Modifier
            .fillMaxSize()
            .hazeSource(hazeState)
            .background(sourceColor.value),
        )
        Box(Modifier.fillMaxSize().background(Color.Black))
        Box(
          Modifier
            .fillMaxSize()
            .testTag("glass")
            .testGlass(effect, input = HazeInput.Sources(hazeState)),
        )
      }
    }
    waitForIdle()

    onNodeWithTag("glass").performTouchInput {
      down(Offset(20f, 20f))
    }
    mainClock.advanceTimeBy(500)
    waitForIdle()
    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate
    assertThat(delegate.interactionOpticalRecordCount).isGreaterThan(0)
    assertThat(delegate.interactionDetailRecordCount).isGreaterThan(0)
    assertThat(delegate.interactionCompositeRecordCount).isGreaterThan(0)

    fun centerPixel(): Color = onNodeWithTag("glass").captureToImage().toPixelMap().let {
      it[it.width / 2, it.height / 2]
    }
    val initial = centerPixel()
    assertThat(initial.red).isGreaterThan(initial.blue + 0.5f)

    sourceColor.value = Color.Blue
    waitForIdle()

    assertThat(runtime(effect).currentInteractionSignals.pressed).isTrue()
    // Retained layers may propagate new source pixels without recording every stage again.
    val updated = centerPixel()
    assertThat(updated.blue).isGreaterThan(updated.red + 0.5f)

    sourceColor.value = Color.Red
    waitForIdle()
    val restored = centerPixel()
    assertThat(restored.red).isGreaterThan(restored.blue + 0.5f)
  }

  @Test
  fun backgroundColorStyleChangeRecordsSource() = runComposeUiTest {
    val effect = activeDetailEffect()
    val style = mutableStateOf<GlassStyle>(GlassStyle)
    setContent { RuntimeGlassTestContent(effect, tag = "glass", style = style.value) }
    waitForIdle()

    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate
    val sourceRecordsBeforeChange = delegate.sourceRecordCount
    val snapshotBeforeChange = checkNotNull(delegate.lastSuccessfulSourceSnapshot)

    style.value = GlassStyle { backgroundColor(Color.White) }
    waitForIdle()

    assertThat(delegate.sourceRecordCount).isGreaterThan(sourceRecordsBeforeChange)
    assertThat(checkNotNull(delegate.lastSuccessfulSourceSnapshot).backgroundColor)
      .isEqualTo(Color.White)
    assertThat(checkNotNull(delegate.lastSuccessfulSourceSnapshot))
      .isNotEqualTo(snapshotBeforeChange)
  }

  @Test
  fun backgroundColorStyleChangeDuringSourceGapClearsRetainedOutput() = runComposeUiTest {
    val hazeState = HazeState()
    val showSource = mutableStateOf(true)
    val style = mutableStateOf(GlassStyle { backgroundColor(Color.Red) })
    val effect = activeDetailEffect()

    setContent {
      Box(Modifier.size(120.dp)) {
        if (showSource.value) {
          Box(
            Modifier
              .fillMaxSize()
              .background(Color.Red)
              .hazeSource(hazeState),
          )
        }
        Box(
          Modifier
            .fillMaxSize()
            .testGlass(
              effect = effect,
              input = HazeInput.Sources(hazeState),
              style = style.value,
            ),
        )
      }
    }

    waitForIdle()
    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate

    showSource.value = false
    waitForIdle()
    assertThat(delegate.canDrawRetainedOutput()).isTrue()

    style.value = GlassStyle { backgroundColor(Color.Blue) }
    waitForIdle()

    assertThat(delegate.canDrawRetainedOutput()).isFalse()
    assertThat(delegate.lastSuccessfulSourceSnapshot).isNull()

    showSource.value = true
    waitForIdle()

    assertThat(delegate.canDrawRetainedOutput()).isTrue()
    assertThat(checkNotNull(delegate.lastSuccessfulSourceSnapshot).backgroundColor)
      .isEqualTo(Color.Blue)
  }

  @Test
  fun activeDetail_recordsAndSurvivesRetainedSourceGap() = runComposeUiTest {
    val hazeState = HazeState()
    val showSource = mutableStateOf(true)
    val effect = activeDetailEffect()

    setContent {
      Box(Modifier.size(120.dp)) {
        if (showSource.value) {
          Box(
            Modifier
              .fillMaxSize()
              .background(Color.Red)
              .hazeSource(hazeState),
          )
        }
        Box(
          Modifier
            .fillMaxSize()
            .testGlass(effect, input = HazeInput.Sources(hazeState)),
        )
      }
    }

    waitForIdle()
    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate
    val detailLayer = checkNotNull(delegate.layers.refractionDetail)
    val detailKey = checkNotNull(delegate.lastSuccessfulStageInputs?.detail)
    val sourceSnapshot = checkNotNull(delegate.lastSuccessfulSourceSnapshot)
    assertThat(delegate.layers.hasRefractionDetail).isTrue()
    assertThat(delegate.canDrawRetainedOutput()).isTrue()

    showSource.value = false
    waitForIdle()

    assertThat(delegate.layers.refractionDetail).isSameInstanceAs(detailLayer)
    assertThat(runtime(effect).delegate).isSameInstanceAs(delegate)
    assertThat(delegate.lastSuccessfulSourceSnapshot).isSameInstanceAs(sourceSnapshot)
    assertThat(delegate.lastSuccessfulStageInputs?.detail).isNotNull().isEqualTo(detailKey)
    assertThat(delegate.layers.hasRefractionDetail).isTrue()
    assertThat(delegate.canDrawRetainedOutput()).isTrue()
  }

  @Test
  fun activeDetail_recordsAllDetailPipelineLayers() = runComposeUiTest {
    val effect = activeDetailEffect()

    val style = mutableStateOf(effect.style)
    setContent { RuntimeGlassTestContent(effect, tag = "glass", style = style.value) }
    waitForIdle()

    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate
    val beforeDetail = delegate.detailRecordCount

    style.value = style.value.then { optics(effect.optics.copy(refractionDisplacement = 18.dp)) }
    waitForIdle()

    assertThat(delegate.detailRecordCount).isEqualTo(beforeDetail + 3)
  }

  @Test
  fun zeroRefractionScale_doesNotAllocateOrRecordDetail() = runComposeUiTest {
    val hazeState = HazeState()
    val effect = activeDetailEffect().apply {
      style = style.then { optics(optics.copy(refractionDisplacement = 0.dp)) }
    }

    setContent {
      Box(Modifier.size(120.dp)) {
        Box(
          Modifier
            .fillMaxSize()
            .background(Color.Red)
            .hazeSource(hazeState),
        )
        Box(
          Modifier
            .fillMaxSize()
            .testGlass(effect, input = HazeInput.Sources(hazeState)),
        )
      }
    }

    waitForIdle()
    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate
    assertThat(delegate.lastSuccessfulStageInputs?.detail).isNull()
    assertThat(delegate.layers.hasRefractionDetail).isFalse()
    assertThat(delegate.canDrawRetainedOutput()).isTrue()
  }

  @Test
  fun epsilonRefractionStrength_doesNotAllocateOrRecordDetail() = runComposeUiTest {
    val hazeState = HazeState()
    val effect = activeDetailEffect().apply {
      style = style.then { optics(optics.copy(refractionStrength = 1e-6f)) }
    }

    setContent {
      Box(Modifier.size(120.dp)) {
        Box(Modifier.fillMaxSize().background(Color.Red).hazeSource(hazeState))
        Box(
          Modifier.fillMaxSize().testGlass(effect, input = HazeInput.Sources(hazeState)),
        )
      }
    }

    waitForIdle()
    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate
    assertThat(delegate.lastSuccessfulStageInputs?.detail).isNull()
    assertThat(delegate.layers.hasRefractionDetail).isFalse()
    assertThat(delegate.canDrawRetainedOutput()).isTrue()
  }

  @Test
  fun lowVisibleRefractionStrength_allocatesAndRecordsDetail() = runComposeUiTest {
    val hazeState = HazeState()
    val effect = activeDetailEffect().apply {
      style = style.then { optics(optics.copy(refractionStrength = .1f)) }
    }

    setContent {
      Box(Modifier.size(120.dp)) {
        Box(Modifier.fillMaxSize().background(Color.Red).hazeSource(hazeState))
        Box(
          Modifier.fillMaxSize().testGlass(effect, input = HazeInput.Sources(hazeState)),
        )
      }
    }

    waitForIdle()
    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate
    assertThat(delegate.lastSuccessfulStageInputs?.detail).isNotNull()
    assertThat(delegate.layers.hasRefractionDetail).isTrue()
    assertThat(delegate.canDrawRetainedOutput()).isTrue()
  }

  @Test
  fun effectAlpha_isAppliedToOneGroupedOpticalAndDetailOutput() = runComposeUiTest {
    val hazeState = HazeState()
    val effect = activeDetailEffect().apply { style = style.then { alpha(0.5f) } }

    setContent {
      Box(Modifier.size(120.dp)) {
        Box(
          Modifier
            .fillMaxSize()
            .background(Color.Red)
            .hazeSource(hazeState),
        )
        Box(
          Modifier
            .fillMaxSize()
            .testGlass(effect, input = HazeInput.Sources(hazeState)),
        )
      }
    }

    waitForIdle()
    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate
    assertThat(checkNotNull(delegate.layers.optical).alpha).isEqualTo(1f)
    assertThat(checkNotNull(delegate.layers.refractionDetail).alpha).isEqualTo(1f)
  }

  @Test
  fun fractionalAlpha_inputScalingUsesOutputSizedGroupLayerAndPlan() = runComposeUiTest {
    val effect = activeDetailEffect().apply { style = style.then { alpha(0.5f) } }

    setContent {
      RuntimeGlassTestContent(
        effect = effect,
        tag = "glass",
        performanceMode = HazePerformanceMode.Fixed(0.25f),
      )
    }
    waitForIdle()

    val outputSize = checkNotNull(runtime(effect).attachedContextForTest)
      .modifierSize
      .roundToIntSize()
    val groupLayer = checkNotNull((runtime(effect).delegate as RuntimeShaderGlassDelegate).layers.groupAlpha.layer)
    val groupPlan = checkNotNull(runtime(effect).preparedRender).plan.layers.single {
      it.kind == GlassRetainedLayerKind.GroupComposite
    }

    assertThat(groupLayer.size).isEqualTo(outputSize)
    assertThat(groupPlan.size).isEqualTo(outputSize)
  }

  @Test
  fun foregroundUniformChanges_retainShadersAndRecordOnlyAffectedStages() = runComposeUiTest {
    val effect = animatedStageEffect()
    val style = mutableStateOf(effect.style)
    setContent { RuntimeForegroundGlassTestContent(effect, style = style.value) }
    waitForIdle()
    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate

    val beforeAlpha = delegate.stageRecordCounts
    style.value = style.value.then { alpha(0.5f) }
    waitForIdle()

    assertThat(delegate.stageRecordCounts).isEqualTo(beforeAlpha)

    val opticalShader = delegate.opticalShader
    style.value = style.value.then { ambientResponse(0.6f) }
    waitForIdle()

    assertThat(delegate.opticalShader).isSameInstanceAs(opticalShader)
    assertThat(delegate.opticalRecordCount).isEqualTo(beforeAlpha.optical + 1)
    assertThat(delegate.blurRecordCount).isEqualTo(beforeAlpha.blur)
    assertThat(delegate.depthRecordCount).isEqualTo(beforeAlpha.depth)

    val beforeRim = delegate.rimRecordCount
    val rimShader = delegate.rimShader
    val rimBrushProvider = checkNotNull(delegate.rimBrushProvider)
    assertThat(delegate.layers.rim?.renderEffect).isNull()
    style.value = style.value.then { lightPosition(exactLightAlignment(Offset(10f, 20f))) }
    waitForIdle()

    assertThat(delegate.rimShader).isSameInstanceAs(rimShader)
    assertThat(delegate.rimBrushProvider).isSameInstanceAs(rimBrushProvider)
    assertThat(delegate.layers.rim?.renderEffect).isNull()
    assertThat(delegate.rimRecordCount).isEqualTo(beforeRim + 1)
  }

  @Test
  fun uniformNativeBlurAndDetailChanges_cacheAndReplaceRenderEffects() = runComposeUiTest {
    val effect = retainedBlurEffect()
    val style = mutableStateOf(effect.style)
    setContent { RuntimeForegroundGlassTestContent(effect, style = style.value) }
    waitForIdle()
    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate

    val detailShader = checkNotNull(delegate.refractionDetailShader)
    val nativeBlurEffect = checkNotNull(delegate.layers.blurred?.renderEffect)
    val detailEffect = checkNotNull(delegate.layers.refractionDetail?.renderEffect)

    style.value = style.value.then {
      optics(
        effect.optics.copy(
          refractionDisplacement = 18.dp,
        ),
      )
    }
    waitForIdle()

    assertThat(delegate.refractionDetailShader).isSameInstanceAs(detailShader)
    assertThat(delegate.layers.blurHorizontal).isNull()
    assertThat(delegate.layers.blurred?.renderEffect).isSameInstanceAs(nativeBlurEffect)
    assertThat(delegate.layers.refractionDetail?.renderEffect).isNotSameInstanceAs(detailEffect)

    style.value = style.value.then {
      optics(
        effect.optics.copy(
          blurRadius = OpticalSizeValue.Fixed(36.dp),
          refractionDisplacement = 18.dp,
        ),
      )
    }
    waitForIdle()

    assertThat(delegate.refractionDetailShader).isSameInstanceAs(detailShader)
    assertThat(delegate.layers.blurred?.renderEffect).isNotSameInstanceAs(nativeBlurEffect)
  }

  @Test
  fun progressiveBlurChanges_retainShadersAndReplaceRenderEffects() = runComposeUiTest {
    val effect = retainedBlurEffect(
      progressive = HazeProgressive.verticalGradient(
        startIntensity = 0f,
        endIntensity = 1f,
      ),
    )
    val style = mutableStateOf(effect.style)
    setContent { RuntimeForegroundGlassTestContent(effect, style = style.value) }
    waitForIdle()
    val delegate = runtime(effect).delegate as RuntimeShaderGlassDelegate

    val horizontalShader = checkNotNull(delegate.progressiveBlurHorizontalShader)
    val verticalShader = checkNotNull(delegate.progressiveBlurVerticalShader)
    val horizontalEffect = checkNotNull(delegate.layers.blurHorizontal?.renderEffect)
    val verticalEffect = checkNotNull(delegate.layers.blurred?.renderEffect)

    style.value = style.value.then {
      optics(
        effect.optics.copy(
          blurRadius = OpticalSizeValue.Fixed(34.dp),
          progressive = HazeProgressive.verticalGradient(
            startIntensity = 0.1f,
            endIntensity = 0.9f,
          ),
        ),
      )
    }
    waitForIdle()

    assertThat(delegate.progressiveBlurHorizontalShader).isSameInstanceAs(horizontalShader)
    assertThat(delegate.progressiveBlurVerticalShader).isSameInstanceAs(verticalShader)
    assertThat(delegate.layers.blurHorizontal?.renderEffect).isNotSameInstanceAs(horizontalEffect)
    assertThat(delegate.layers.blurred?.renderEffect).isNotSameInstanceAs(verticalEffect)
  }

  @Test
  fun wideNativeBlur_smoothsCheckerboardAcrossCenterAndInsetEdges() = runComposeUiTest {
    val effect = GlassRuntimeEffect()
    fun blurStyle(radius: androidx.compose.ui.unit.Dp) = GlassStyle {
      optics(
        GlassOptics(
          refractionStrength = 0f,
          refractionDisplacement = 0.dp,
          depth = OpticalSizeValue.Fixed(1f),
          blurRadius = OpticalSizeValue.Fixed(radius),
        ),
      )
      backgroundColor(Color.Transparent)
      tint(Color.Transparent)
      ambientResponse(0f)
      contentNormalBlend(0f)
      contrast(0f)
      whitePoint(0f)
      chromaMultiplier(1f)
      shape(RoundedCornerShape(0.dp))
      edgeSoftness(0.dp)
      edgeShadow(Color.Transparent)
      specularIntensity(0f)
    }
    val style = mutableStateOf(blurStyle(0.dp))
    setContent {
      val hazeState = remember { HazeState() }
      Box(Modifier.size(480.dp)) {
        Canvas(Modifier.fillMaxSize().hazeSource(hazeState)) {
          var top = 0f
          while (top < size.height) {
            var left = 0f
            while (left < size.width) {
              val cellSize = when {
                top < size.height * 0.5f && left < size.width * 0.5f -> 8f
                top < size.height * 0.5f -> 24f
                left < size.width * 0.5f -> 32f
                else -> 8f
              }
              val evenCell = ((left / cellSize).toInt() + (top / cellSize).toInt()) % 2 == 0
              drawRect(
                color = if (evenCell) Color.White else Color.Black,
                topLeft = Offset(left, top),
                size = Size(cellSize, cellSize),
              )
              left += cellSize
            }
            top += 8f
          }
        }
        Box(
          Modifier
            .size(120.dp)
            .align(Alignment.Center)
            .testTag("glass")
            .testGlass(effect, input = HazeInput.Sources(hazeState), style = style.value),
        )
      }
    }
    waitForIdle()

    fun range(pixels: PixelMap, left: IntRange, top: IntRange): Float {
      val samples = buildList {
        for (y in top step 4) {
          for (x in left step 4) add(pixels[x, y].red)
        }
      }
      return samples.max() - samples.min()
    }
    val sharp = onNodeWithTag("glass").captureToImage().toPixelMap()
    assertThat(range(sharp, 28..48, 28..48)).isGreaterThan(0.5f)

    style.value = blurStyle(84.dp)
    waitForIdle()

    val pixels = onNodeWithTag("glass").captureToImage().toPixelMap()

    val runtime = runtime(effect)
    val delegate = runtime.delegate as RuntimeShaderGlassDelegate
    assertThat(delegate.layers.hasBlurHorizontal).isFalse()
    assertThat(delegate.layers.blurred?.renderEffect).isNotNull()
    val prepared = checkNotNull(runtime.preparedRender)
    assertThat(prepared.plan.layers.any { it.kind == GlassRetainedLayerKind.BlurHorizontal }).isFalse()
    listOf(
      range(pixels, 28..48, 28..48), // 8px period
      range(pixels, 76..100, 28..52), // 24px period
      range(pixels, 28..52, 76..100), // 32px period
      range(pixels, 4..12, 28..52), // inset edge strip, outside the antialias rim
    ).forEach { range ->
      assertThat(range).isLessThan(0.08f)
    }
  }

  private fun activeDetailEffect() = GlassRuntimeEffect().apply {
    style = style.then {
      optics(
        GlassOptics(
          refractionStrength = 0.5f,
          refractionDisplacement = 20.dp,
          blurRadius = OpticalSizeValue.Fixed(0.dp),
        ),
      )
      specularIntensity(0f)
    }
  }

  private fun runtimeInteractiveEffect() = activeDetailEffect().apply {
    style = style.then {
      pressed {
        lightingIntensity(1f)
        refractionMultiplier(1.08f)
        whitePointDelta(0.04f)
      }
    }
    interactionReducedMotionPolicy = GlassReducedMotionPolicy.Full
  }

  private fun animatedStageEffect() = GlassRuntimeEffect().apply {
    style = style.then {
      optics(
        GlassOptics(
          refractionStrength = 0.5f,
          refractionDisplacement = 20.dp,
          depth = OpticalSizeValue.Fixed(0.5f),
          blurRadius = OpticalSizeValue.Fixed(14.dp),
        ),
      )
      specularIntensity(1f)
      ambientResponse(0.5f)
      lightPosition(Alignment.Center)
    }
  }

  private fun retainedBlurEffect(
    progressive: HazeProgressive? = null,
  ) = GlassRuntimeEffect().apply {
    style = style.then {
      optics(
        GlassOptics(
          refractionStrength = 0.5f,
          refractionDisplacement = 20.dp,
          depth = OpticalSizeValue.Fixed(0.5f),
          blurRadius = OpticalSizeValue.Fixed(38.5.dp),
          progressive = progressive,
        ),
      )
      specularIntensity(0f)
    }
  }

  private fun Modifier.testGlass(
    effect: GlassRuntimeEffect,
    input: HazeInput = HazeInput.Content,
    performanceMode: HazePerformanceMode = HazePerformanceMode.Quality,
    style: GlassStyle = effect.style,
  ): Modifier = hazeGlass(
    factory = rendererFactories.getOrPut(effect) {
      HazeEffectFactory {
        effect.also { attachedRuntimes[effect] = it }
      }
    },
    input = input,
    style = style,
    performanceMode = performanceMode,
    expandLayerBounds = true,
    interactionSource = effect.interactionSource,
    interactionTransformTarget = effect.interactionTransformTarget,
    interactionTransformPivot = effect.interactionTransformPivot,
    interactionReducedMotionPolicy = effect.interactionReducedMotionPolicy,
  )

  private fun runtime(effect: GlassRuntimeEffect): GlassRuntimeEffect =
    checkNotNull(attachedRuntimes[effect])

  @Composable
  private fun RuntimeForegroundGlassTestContent(
    effect: GlassRuntimeEffect,
    tag: String? = null,
    style: GlassStyle = effect.style,
  ) {
    Box(
      Modifier
        .size(120.dp)
        .then(if (tag != null) Modifier.testTag(tag) else Modifier)
        .testGlass(effect, style = style),
    ) {
      Box(Modifier.fillMaxSize().background(Color.Red))
    }
  }

  private fun invalidCornerShape(radius: Float) = RoundedCornerShape(
    object : CornerSize {
      override fun toPx(shapeSize: Size, density: Density): Float = radius
    },
  )

  private fun CornerRadii.values(): List<Float> = listOf(
    topLeft,
    topRight,
    bottomRight,
    bottomLeft,
  )

  private class FailingRuntimeShaderGlassDelegate : GlassRuntimeEffect.Delegate {
    override fun DrawScope.prepareDraw(context: HazeEffectRuntimeDrawScope) = Unit

    override fun DrawScope.draw(context: HazeEffectRuntimeDrawScope): Nothing {
      throw RuntimeShaderRenderEffectException(IllegalArgumentException("draw failure"))
    }
  }

  private fun RuntimeShaderGlassDelegate.interactionShaderHandle(fieldName: String): Any {
    return checkNotNull(interactionShaderHandleOrNull(fieldName))
  }

  private fun RuntimeShaderGlassDelegate.interactionShaderHandleOrNull(fieldName: String): Any? {
    val field = RuntimeShaderGlassDelegate::class.java.getDeclaredField(fieldName)
    field.isAccessible = true
    return field.get(this)
  }

  @Composable
  private fun RuntimeGlassTestContent(
    effect: GlassRuntimeEffect,
    tag: String,
    performanceMode: HazePerformanceMode = HazePerformanceMode.Quality,
    style: GlassStyle = effect.style,
    sourceColor: Color = Color.Red,
  ) {
    val hazeState = remember { HazeState() }
    Box(Modifier.size(120.dp)) {
      Box(Modifier.fillMaxSize().hazeSource(hazeState).background(sourceColor))
      Box(
        Modifier
          .fillMaxSize()
          .testTag(tag)
          .testGlass(
            effect = effect,
            input = HazeInput.Sources(hazeState),
            performanceMode = performanceMode,
            style = style,
          ),
      )
    }
  }

  @Composable
  private fun RuntimeContentGlassTestContent(
    effect: GlassRuntimeEffect,
    tag: String,
  ) {
    Box(
      Modifier
        .size(120.dp)
        .testTag(tag)
        .testGlass(effect),
    ) {
      Box(Modifier.fillMaxSize().background(Color.Red))
    }
  }

  @Composable
  private fun RuntimeLargeGlassTestContent(
    effect: GlassRuntimeEffect,
    performanceMode: HazePerformanceMode = HazePerformanceMode.Quality,
  ) {
    val hazeState = remember { HazeState() }
    Box(Modifier.size(1100.dp, 650.dp)) {
      Box(Modifier.fillMaxSize().background(Color.Red).hazeSource(hazeState))
      Box(
        Modifier
          .fillMaxSize()
          .testGlass(
            effect = effect,
            input = HazeInput.Sources(hazeState),
            performanceMode = performanceMode,
          ),
      )
    }
  }
}

private fun GlassAdaptiveHost.recordSustainedMisses() {
  controller.reset()
  repeat(30) { index ->
    val timestamp = (index + 1) * 20_000_000L
    recordSample(
      GlassFrameHealthSample(index.toLong(), timestamp, 16_000_000L, 17_000_000L),
      timestamp,
    )
  }
  repeat(31) { index ->
    val timestamp = 620_000_000L + index * 34_000_000L
    recordSample(
      GlassFrameHealthSample((index + 30).toLong(), timestamp, 20_000_000L, 17_000_000L),
      timestamp,
    )
  }
}
