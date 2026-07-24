// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze.sample

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotNull
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.glass.GlassOptics
import kotlin.test.Test

class GlassPlaygroundTimelineTest {
  @Test
  fun timeline_isSeamlessAtLoopBoundary() {
    assertThat(glassPlaygroundFrame(1f)).isEqualTo(glassPlaygroundFrame(0f))
  }

  @Test
  fun keyFrames_keepEverySurfaceInsideNormalizedBounds() {
    listOf(0f, 0.2f, 0.4f, 0.6f, 0.8f, 1f).forEach { progress ->
      val frame = glassPlaygroundFrame(progress)
      GlassPlaygroundSurfaceId.entries.forEach { id ->
        val position = frame.position(id)
        assertThat(position.x, "$id x at $progress").isGreaterThanOrEqualTo(0.1f)
        assertThat(position.x, "$id x at $progress").isLessThanOrEqualTo(0.9f)
        assertThat(position.y, "$id y at $progress").isGreaterThanOrEqualTo(0.1f)
        assertThat(position.y, "$id y at $progress").isLessThanOrEqualTo(0.9f)
      }
    }
  }

  @Test
  fun choreographedSurfaceCentersKeepFixedSizesInsideCompactScene() {
    val sceneSize = IntSize(320, 240)
    val density = Density(1f)

    repeat(101) { step ->
      val frame = glassPlaygroundFrame(step / 100f)
      GlassPlaygroundSurfaceId.entries.forEach { id ->
        val surfaceSize = with(density) {
          val size = playgroundSurfaceSize(id)
          IntSize(size.width.roundToPx(), size.height.roundToPx())
        }
        val center = resolvedPlaygroundSurfaceCenter(
          normalizedCenter = frame.position(id),
          sceneSize = sceneSize,
          surfaceSize = surfaceSize,
          dragOffset = Offset.Zero,
        )
        assertThat(center.x - surfaceSize.width / 2f, "$id left edge at $step")
          .isGreaterThanOrEqualTo(0f)
        assertThat(center.x + surfaceSize.width / 2f, "$id right edge at $step")
          .isLessThanOrEqualTo(sceneSize.width.toFloat())
        assertThat(center.y - surfaceSize.height / 2f, "$id top edge at $step")
          .isGreaterThanOrEqualTo(0f)
        assertThat(center.y + surfaceSize.height / 2f, "$id bottom edge at $step")
          .isLessThanOrEqualTo(sceneSize.height.toFloat())
      }
    }
  }

  @Test
  fun backdropAndLight_moveAcrossTheLoop() {
    val opening = glassPlaygroundFrame(0f)
    val quarter = glassPlaygroundFrame(0.25f)
    assertThat(opening.backdropOffset).isNotEqualTo(quarter.backdropOffset)
    assertThat(opening.lightPosition).isNotEqualTo(quarter.lightPosition)
  }

  @Test
  fun smallSurfacesUseAdaptiveAndFeatureSurfacesUseLiteralOptics() {
    assertThat(glassPlaygroundStyle(GlassPlaygroundSurfaceId.Lens).optics)
      .isEqualTo(GlassOptics.Adaptive)
    assertThat(glassPlaygroundStyle(GlassPlaygroundSurfaceId.Pill).optics)
      .isEqualTo(GlassOptics.Adaptive)
    assertThat(glassPlaygroundStyle(GlassPlaygroundSurfaceId.Card).optics)
      .isNotNull()
      .isInstanceOf<GlassOptics.Absolute>()
    assertThat(glassPlaygroundStyle(GlassPlaygroundSurfaceId.Prism).optics)
      .isNotNull()
      .isInstanceOf<GlassOptics.Absolute>()
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
    assertThat(frame.position(GlassPlaygroundSurfaceId.Prism)).isEqualTo(Offset(0.8f, 0.8f))
  }
}
