// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class, dev.chrisbanes.haze.ExperimentalHazeApi::class, dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEmpty
import assertk.assertions.isTrue
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

class GlassGroupShapeTest : ContextTest() {
  @Test
  fun groupedFirstMemberDetach_interpolatesCornerRadiiThroughRetract() = runComposeUiTest {
    val departingRadius = 2f
    val remainingRadius = 16f
    val firstPresent = mutableStateOf(true)
    val first = GlassRuntimeEffect()
    val second = GlassRuntimeEffect()
    val state = HazeState()
    setContent {
      Box(Modifier.size(400.dp, 200.dp).background(Color.White)) {
        Box(Modifier.size(400.dp, 200.dp).hazeSource(state).background(Color.White))
        Box(
          Modifier.size(400.dp, 200.dp).hazeGlassContainer(
            40.dp,
            memberTransitionSpec = tween(300, easing = LinearEasing),
            memberReducedMotionPolicy = GlassReducedMotionPolicy.Full,
          ),
        ) {
          // The members are 10dp apart, so the first leaves by retracting into the second.
          if (firstPresent.value) {
            Box(
              Modifier.offset(40.dp, 60.dp).size(100.dp, 40.dp).hazeGlass(
                HazeEffectFactory { first },
                HazeInput.Sources(state),
                containerTestStyle(Color.Red).then(GlassStyle { shape(RoundedCornerShape(departingRadius.dp)) }),
                HazePerformanceMode.Quality,
                true,
                null,
                groupKey = "group",
              ),
            )
          }
          Box(
            Modifier.offset(150.dp, 60.dp).size(100.dp, 40.dp).hazeGlass(
              HazeEffectFactory { second },
              HazeInput.Sources(state),
              containerTestStyle(Color.Blue).then(GlassStyle { shape(RoundedCornerShape(remainingRadius.dp)) }),
              HazePerformanceMode.Quality,
              true,
              null,
              groupKey = "group",
            ),
          )
        }
      }
    }
    waitForIdle()
    mainClock.autoAdvance = false
    // A frame must draw first, or the container still suppresses transitions.
    mainClock.advanceTimeBy(16)
    waitForIdle()
    val owner = checkNotNull(second.memberBindingForTest?.node?.container)
    fun radii(): Float? = owner.renderer.runtimeSurfaces.singleOrNull()?.mergedField?.members?.singleOrNull()?.cornerRadii?.topLeft
    fun retracting() = owner.model.surfaces.flatMap { it.members }.any { it.identity is GlassMemberGhost }
    assertThat(radii()).isEqualTo(departingRadius)

    runOnIdle { firstPresent.value = false }
    val whileRetracting = mutableListOf<Float>()
    repeat(40) {
      mainClock.advanceTimeBy(16)
      waitForIdle()
      if (retracting()) radii()?.let(whileRetracting::add)
    }

    assertThat(whileRetracting).isNotEmpty()
    // The group's shape follows the retract between the departing and the remaining shape, rather
    // than holding the departed one until the ghost ends.
    assertThat(whileRetracting.any { it > departingRadius && it < remainingRadius }).isTrue()
    mainClock.advanceTimeBy(2_000)
    waitForIdle()
    assertThat(retracting()).isFalse()
  }
}
