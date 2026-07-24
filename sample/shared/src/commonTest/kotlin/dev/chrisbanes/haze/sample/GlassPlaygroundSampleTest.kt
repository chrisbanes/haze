// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.glass.GlassTransformPivot
import dev.chrisbanes.haze.glass.GlassTransformTarget
import dev.chrisbanes.haze.glass.GlassVisualEffect
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalHazeApi::class)
class GlassPlaygroundSampleTest : ContextTest() {
  @Test
  fun playgroundInteraction_keepsHoverAfterPressAndUsesPointerContentTransform() {
    val source = MutableInteractionSource()
    val effect = GlassVisualEffect()

    effect.configurePlaygroundInteraction(source)

    assertEquals(source, effect.interactionSource)
    assertEquals(GlassTransformTarget.MaterialAndContent, effect.interactionTransformTarget)
    assertEquals(GlassTransformPivot.Pointer, effect.interactionTransformPivot)
    assertTrue(effect.observesPointerEvents)

    effect.clearPressed()
    assertTrue(effect.observesPointerEvents)
    effect.clearHovered()
    assertEquals(false, effect.observesPointerEvents)
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

    assertEquals(Offset(140f, 150f), base)
    assertEquals(Offset(-60f, 350f), dragged)
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

    assertEquals(Offset(310f, -130f), light)
  }
}
