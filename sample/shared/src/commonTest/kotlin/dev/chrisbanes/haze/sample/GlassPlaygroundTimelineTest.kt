// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze.sample

import androidx.compose.ui.geometry.Offset
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.glass.GlassOptics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GlassPlaygroundTimelineTest {
  @Test
  fun timeline_isSeamlessAtLoopBoundary() {
    assertEquals(glassPlaygroundFrame(0f), glassPlaygroundFrame(1f))
  }

  @Test
  fun keyFrames_keepEverySurfaceInsideNormalizedBounds() {
    listOf(0f, 0.2f, 0.4f, 0.6f, 0.8f, 1f).forEach { progress ->
      val frame = glassPlaygroundFrame(progress)
      GlassPlaygroundSurfaceId.entries.forEach { id ->
        val position = frame.position(id)
        assertTrue(position.x in 0.1f..0.9f, "$id x out of bounds at $progress: $position")
        assertTrue(position.y in 0.1f..0.9f, "$id y out of bounds at $progress: $position")
      }
    }
  }

  @Test
  fun backdropAndLight_moveAcrossTheLoop() {
    val opening = glassPlaygroundFrame(0f)
    val quarter = glassPlaygroundFrame(0.25f)
    assertTrue(opening.backdropOffset != quarter.backdropOffset)
    assertTrue(opening.lightPosition != quarter.lightPosition)
  }

  @Test
  fun smallSurfacesUseAdaptiveAndFeatureSurfacesUseLiteralOptics() {
    assertEquals(GlassOptics.Adaptive, glassPlaygroundStyle(GlassPlaygroundSurfaceId.Lens).optics)
    assertEquals(GlassOptics.Adaptive, glassPlaygroundStyle(GlassPlaygroundSurfaceId.Pill).optics)
    assertTrue(glassPlaygroundStyle(GlassPlaygroundSurfaceId.Card).optics is GlassOptics.Absolute)
    assertTrue(glassPlaygroundStyle(GlassPlaygroundSurfaceId.Prism).optics is GlassOptics.Absolute)
  }

  @Test
  fun positionRejectsNoKnownSurface() {
    val frame = GlassPlaygroundFrame(
      backdropOffset = 0f,
      lightPosition = Offset.Zero,
      lensPosition = Offset(0.2f, 0.2f),
      pillPosition = Offset(0.4f, 0.4f),
      cardPosition = Offset(0.6f, 0.6f),
      prismPosition = Offset(0.8f, 0.8f),
    )
    assertEquals(Offset(0.8f, 0.8f), frame.position(GlassPlaygroundSurfaceId.Prism))
  }
}
