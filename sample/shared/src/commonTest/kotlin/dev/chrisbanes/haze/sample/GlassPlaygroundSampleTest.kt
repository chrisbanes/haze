// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isTrue
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.glass.GlassTransformPivot
import dev.chrisbanes.haze.glass.GlassTransformTarget
import dev.chrisbanes.haze.glass.GlassVisualEffect
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test

@OptIn(ExperimentalHazeApi::class)
class GlassPlaygroundSampleTest : ContextTest() {
  @Test
  fun playgroundInteraction_keepsHoverAfterPressAndUsesPointerContentTransform() {
    val source = MutableInteractionSource()
    val effect = GlassVisualEffect()

    effect.configurePlaygroundInteraction(source)

    assertThat(effect.interactionSource).isEqualTo(source)
    assertThat(effect.interactionTransformTarget).isEqualTo(GlassTransformTarget.MaterialAndContent)
    assertThat(effect.interactionTransformPivot).isEqualTo(GlassTransformPivot.Pointer)
    assertThat(effect.observesPointerEvents).isTrue()

    effect.clearPressed()
    assertThat(effect.observesPointerEvents).isTrue()
    effect.clearHovered()
    assertThat(effect.observesPointerEvents).isFalse()
  }

  @Test
  fun resolvedCenterContainsBaseSurfaceBeforeApplyingUnboundedDrag() {
    val base = resolvedPlaygroundSurfaceCenter(
      normalizedCenter = Offset(0.1f, 0.9f),
      sceneSize = IntSize(320, 240),
      surfaceSize = IntSize(280, 180),
      dragOffset = Offset.Zero,
    )
    val dragged = resolvedPlaygroundSurfaceCenter(
      normalizedCenter = Offset(0.1f, 0.9f),
      sceneSize = IntSize(320, 240),
      surfaceSize = IntSize(280, 180),
      dragOffset = Offset(-200f, 200f),
    )

    assertThat(base).isEqualTo(Offset(140f, 150f))
    assertThat(dragged).isEqualTo(Offset(-60f, 350f))
  }

  @Test
  fun localLightSubtractsResolvedSurfaceOriginIncludingDrag() {
    val light = resolvePlaygroundSurfaceLightPosition(
      normalizedLight = Offset(0.75f, 0.25f),
      normalizedCenter = Offset(0.5f, 0.5f),
      sceneSize = IntSize(1_000, 800),
      surfaceSize = IntSize(200, 100),
      dragOffset = Offset(40f, -20f),
    )

    assertThat(light).isEqualTo(Offset(310f, -130f))
  }
}
