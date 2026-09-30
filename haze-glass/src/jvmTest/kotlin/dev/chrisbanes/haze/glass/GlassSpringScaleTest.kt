// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.Interaction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotEqualTo
import assertk.assertions.isTrue
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeEffectContentTransform
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.InternalHazeApi
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope

@OptIn(ExperimentalTestApi::class, ExperimentalHazeApi::class, InternalHazeApi::class)
class GlassSpringScaleTest : ContextTest() {
  @Test
  fun nearZeroScale_rendersForBothTransformTargets() {
    listOf(GlassTransformTarget.MaterialOnly, GlassTransformTarget.MaterialAndContent).forEach { target ->
      runComposeUiTest {
        val source = MutableInteractionSource()
        val effect = GlassRuntimeEffect()
        val state = HazeState()
        val style = GlassStyle.regular.then { pressed { scale(Float.MIN_VALUE, Float.MIN_VALUE) } }
        setContent {
          Box(Modifier.size(120.dp)) {
            Box(Modifier.fillMaxSize().hazeSource(state).background(Color.Red))
            Box(
              Modifier.fillMaxSize().testTag("glass").hazeGlass(
                factory = HazeEffectFactory { effect },
                input = HazeInput.Sources(state),
                style = style,
                performanceMode = HazePerformanceMode.Quality,
                expandLayerBounds = true,
                interactionSource = source,
                interactionTransformTarget = target,
                interactionReducedMotionPolicy = GlassReducedMotionPolicy.Full,
              ),
            )
          }
        }
        waitForIdle()
        val press = PressInteraction.Press(Offset(60f, 60f))
        val scope = TestScope()
        scope.launch { source.emit(press) }
        scope.testScheduler.runCurrent()
        waitForIdle()
        onNodeWithTag("glass").captureToImage()
        val size = checkNotNull(effect.attachedContextForTest).modifierSize
        val selected = if (target == GlassTransformTarget.MaterialOnly) {
          effect.currentMaterialTransform(size)
        } else {
          effect.currentContentTransform()
        }
        val unselected = if (target == GlassTransformTarget.MaterialOnly) {
          effect.currentContentTransform()
        } else {
          effect.currentMaterialTransform(size)
        }
        assertThat(selected.scaleX).isEqualTo(Float.MIN_VALUE)
        assertThat(selected.scaleY).isEqualTo(Float.MIN_VALUE)
        assertThat(selected.pivot.x.isFinite() && selected.pivot.y.isFinite()).isTrue()
        assertThat(unselected).isEqualTo(HazeEffectContentTransform.Identity)
        scope.launch { source.emit(PressInteraction.Release(press)) }
        scope.testScheduler.runCurrent()
        waitForIdle()
        onNodeWithTag("glass").captureToImage()
        assertThat(effect.currentContentTransform()).isEqualTo(HazeEffectContentTransform.Identity)
        assertThat(effect.currentMaterialTransform(size)).isEqualTo(HazeEffectContentTransform.Identity)
      }
    }
  }

  @Test
  fun frameInvalidation_completesSourceBackedDrawForBothTargets() {
    listOf(GlassTransformTarget.MaterialOnly, GlassTransformTarget.MaterialAndContent).forEach { target ->
      runComposeUiTest {
        mainClock.autoAdvance = false
        val fixture = attach(target, GlassStyle.regular)
        assertThat(fixture.completedDraws).isGreaterThan(0)
        val before = fixture.completedDraws
        frames(fixture, 1)
        assertThat(fixture.completedDraws).isGreaterThan(before)
        val image = onNodeWithTag("glass").captureToImage()
        val center = image.toPixelMap()[image.width / 2, image.height / 2]
        assertThat(center.red).isGreaterThan(center.blue)
      }
    }
  }

  @Test
  fun springScale_rendersEntryExitAndRetargetingForBothTargets() {
    listOf(GlassTransformTarget.MaterialOnly, GlassTransformTarget.MaterialAndContent).forEach { target ->
      listOf(0.1f to 0.1f, 0.1f to 1f, 1f to 0.1f, 0.000001f to Float.MIN_VALUE).forEach { (x, y) ->
        runComposeUiTest {
          mainClock.autoAdvance = false
          val fixture = attach(
            target,
            GlassStyle.regular.then {
              pressed { animate(spring(dampingRatio = 0.5f, stiffness = 200f), tween(100)) { scale(x, y) } }
            },
          )
          val press = PressInteraction.Press(Offset(60f, 60f))
          fixture.emit(press)
          frames(fixture, 300)
          assertThat(fixture.transform().scaleX).isEqualTo(x)
          assertThat(fixture.transform().scaleY).isEqualTo(y)
          fixture.emit(PressInteraction.Release(press))
          frames(fixture, 300)
          assertThat(fixture.transform()).isEqualTo(HazeEffectContentTransform.Identity)
          if (x == 0.1f && y == 0.1f) {
            val first = PressInteraction.Press(Offset(60f, 60f))
            fixture.emit(first)
            frames(fixture, 12)
            fixture.emit(PressInteraction.Release(first))
            frames(fixture, 4)
            val second = PressInteraction.Press(Offset(60f, 60f))
            fixture.emit(second)
            frames(fixture, 8)
            fixture.emit(PressInteraction.Release(second))
            frames(fixture, 300)
            assertThat(fixture.transform()).isEqualTo(HazeEffectContentTransform.Identity)
          }
        }
      }
      runComposeUiTest {
        mainClock.autoAdvance = false
        val fixture = attach(
          target,
          GlassStyle.regular.then {
            hovered { scale(0.000001f, Float.MIN_VALUE) }
            pressed { animate(tween(100), spring(dampingRatio = 0.5f, stiffness = 200f)) { scale(0.9f, 0.8f) } }
          },
        )
        val hover = HoverInteraction.Enter()
        fixture.emit(hover)
        frames(fixture, 300)
        val press = PressInteraction.Press(Offset(60f, 60f))
        fixture.emit(press)
        frames(fixture, 300)
        fixture.emit(PressInteraction.Release(press))
        frames(fixture, 300)
        assertThat(fixture.transform().scaleX).isEqualTo(0.000001f)
        assertThat(fixture.transform().scaleY).isEqualTo(Float.MIN_VALUE)
        fixture.emit(HoverInteraction.Exit(hover))
        frames(fixture, 300)
        assertThat(fixture.transform()).isEqualTo(HazeEffectContentTransform.Identity)
      }
    }
  }

  @Test
  fun springScale_preservesNormalMotion() {
    listOf(GlassTransformTarget.MaterialOnly, GlassTransformTarget.MaterialAndContent).forEach { target ->
      runComposeUiTest {
        mainClock.autoAdvance = false
        val fixture = attach(
          target,
          GlassStyle.regular.then {
            pressed {
              animate(spring(dampingRatio = 0.5f, stiffness = 200f), spring(dampingRatio = 0.5f, stiffness = 200f)) {
                scale(0.9f, 0.8f)
              }
            }
          },
        )
        val press = PressInteraction.Press(Offset(60f, 60f))
        fixture.emit(press)
        frames(fixture, 8)
        assertThat(fixture.transform()).isNotEqualTo(HazeEffectContentTransform.Identity)
        frames(fixture, 300)
        assertThat(fixture.transform().scaleX).isEqualTo(0.9f)
        assertThat(fixture.transform().scaleY).isEqualTo(0.8f)
        fixture.emit(PressInteraction.Release(press))
        var overshot = false
        frames(fixture, 300) { overshot = overshot || it.scaleX > 1f || it.scaleY > 1f }
        assertThat(overshot).isTrue()
        assertThat(fixture.transform()).isEqualTo(HazeEffectContentTransform.Identity)
      }
    }
  }

  private class Fixture(
    val effect: GlassRuntimeEffect,
    val source: MutableInteractionSource,
    val target: GlassTransformTarget,
  ) {
    var completedDraws = 0

    fun emit(interaction: Interaction) {
      val scope = TestScope()
      scope.launch { source.emit(interaction) }
      scope.testScheduler.runCurrent()
    }

    fun transform(): HazeEffectContentTransform {
      val size = checkNotNull(effect.attachedContextForTest).modifierSize
      val content = effect.currentContentTransform()
      val material = effect.currentMaterialTransform(size)
      assertThat(if (target == GlassTransformTarget.MaterialOnly) content else material)
        .isEqualTo(HazeEffectContentTransform.Identity)
      return if (target == GlassTransformTarget.MaterialOnly) material else content
    }
  }

  private fun ComposeUiTest.attach(target: GlassTransformTarget, style: GlassStyle): Fixture {
    val fixture = Fixture(GlassRuntimeEffect(), MutableInteractionSource(), target)
    val state = HazeState()
    setContent {
      Box(Modifier.size(120.dp)) {
        Box(Modifier.fillMaxSize().hazeSource(state).background(Color.Red))
        Box(
          Modifier.fillMaxSize().testTag("glass").drawWithContent {
            drawContent()
            fixture.completedDraws++
          }.hazeGlass(
            factory = HazeEffectFactory { fixture.effect },
            input = HazeInput.Sources(state),
            style = style,
            performanceMode = HazePerformanceMode.Quality,
            expandLayerBounds = true,
            interactionSource = fixture.source,
            interactionTransformTarget = target,
            interactionReducedMotionPolicy = GlassReducedMotionPolicy.Full,
          ),
        )
      }
    }
    waitForIdle()
    return fixture
  }

  private fun ComposeUiTest.frames(
    fixture: Fixture,
    count: Int,
    sample: (HazeEffectContentTransform) -> Unit = {},
  ) {
    var capturedFloor = false
    var capturedOvershoot = false
    repeat(count) { frame ->
      val before = fixture.completedDraws
      checkNotNull(fixture.effect.attachedContextForTest).invalidateDraw()
      mainClock.advanceTimeByFrame()
      waitForIdle()
      // The observer increments only after source-backed Glass drawing succeeds.
      assertThat(fixture.completedDraws).isGreaterThan(before)
      val transform = fixture.transform()
      assertThat(transform.scaleX.isFinite() && transform.scaleX > 0f).isTrue()
      assertThat(transform.scaleY.isFinite() && transform.scaleY > 0f).isTrue()
      val floor = transform.scaleX == Float.MIN_VALUE || transform.scaleY == Float.MIN_VALUE
      val overshoot = transform.scaleX > 1f || transform.scaleY > 1f
      if (frame == 0 || frame == count - 1 || (floor && !capturedFloor) || (overshoot && !capturedOvershoot)) {
        onNodeWithTag("glass").captureToImage()
        capturedFloor = capturedFloor || floor
        capturedOvershoot = capturedOvershoot || overshoot
      }
      sample(transform)
    }
  }
}
