// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze.sample

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThanOrEqualTo
import assertk.assertions.isLessThanOrEqualTo
import assertk.assertions.isNotEqualTo
import assertk.assertions.isSameInstanceAs
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.glass.GlassStyle
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
  fun choreographedSurfaceCentersMapDirectlyIntoScene() {
    val sceneSize = IntSize(320, 240)

    repeat(101) { step ->
      val frame = glassPlaygroundFrame(step / 100f)
      GlassPlaygroundSurfaceId.entries.forEach { id ->
        val normalizedCenter = frame.position(id)
        val center = resolvedPlaygroundSurfaceCenter(
          normalizedCenter = normalizedCenter,
          sceneSize = sceneSize,
          dragOffset = Offset.Zero,
        )
        assertThat(center, "$id center at $step").isEqualTo(
          Offset(
            x = normalizedCenter.x * sceneSize.width,
            y = normalizedCenter.y * sceneSize.height,
          ),
        )
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
  fun surfacesUseBuiltInRegularAndClearStyles() {
    assertThat(glassPlaygroundStyle(GlassPlaygroundSurfaceId.Lens))
      .isSameInstanceAs(GlassStyle.regular)
    assertThat(glassPlaygroundStyle(GlassPlaygroundSurfaceId.Pill))
      .isSameInstanceAs(GlassStyle.regular)
    assertThat(glassPlaygroundStyle(GlassPlaygroundSurfaceId.Card))
      .isSameInstanceAs(GlassStyle.regular)
    assertThat(glassPlaygroundStyle(GlassPlaygroundSurfaceId.Clear))
      .isSameInstanceAs(GlassStyle.clear)
  }

  @Test
  fun positionRejectsNoKnownSurface() {
    val frame = GlassPlaygroundFrame(
      backdropOffset = 0f,
      lightPosition = Offset.Zero,
      lensPosition = Offset(0.2f, 0.2f),
      pillPosition = Offset(0.4f, 0.4f),
      cardPosition = Offset(0.6f, 0.6f),
      clearPosition = Offset(0.8f, 0.8f),
    )
    assertThat(frame.position(GlassPlaygroundSurfaceId.Clear)).isEqualTo(Offset(0.8f, 0.8f))
  }
}
