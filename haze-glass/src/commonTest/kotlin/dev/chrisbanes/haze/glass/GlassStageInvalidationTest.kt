// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import dev.chrisbanes.haze.HazeEffectInputSnapshot
import kotlin.test.Test

class GlassStageInvalidationTest {

  @Test
  fun calculateStageInvalidation_firstPipelineInvalidatesAllRelevantStages() {
    assertThat(calculateStageInvalidation(previous = null, current = inputs(), sourceChanged = false))
      .isEqualTo(
        GlassStageInvalidation(
          blur = true,
          depth = true,
          optical = true,
          detail = true,
          rim = true,
        ),
      )
  }

  @Test
  fun calculateStageInvalidation_alphaOnlyFrameWithUnchangedInputsReusesAllStages() {
    val inputs = inputs()

    assertThat(calculateStageInvalidation(inputs, inputs, sourceChanged = false))
      .isEqualTo(GlassStageInvalidation.None)
  }

  @Test
  fun calculateStageInvalidation_sourceChangeInvalidatesSourceDependentStagesOnly() {
    val inputs = inputs()

    assertThat(calculateStageInvalidation(inputs, inputs, sourceChanged = true))
      .isEqualTo(
        GlassStageInvalidation(
          blur = true,
          depth = true,
          optical = true,
          detail = true,
          rim = false,
        ),
      )
  }

  @Test
  fun calculateStageInvalidation_blurKeyChangeInvalidatesBlurDepthAndOptical() {
    val previous = inputs()
    val current = previous.copy(blur = Any())

    assertThat(calculateStageInvalidation(previous, current, sourceChanged = false))
      .isEqualTo(
        GlassStageInvalidation(
          blur = true,
          depth = true,
          optical = true,
          detail = false,
          rim = false,
        ),
      )
  }

  @Test
  fun calculateStageInvalidation_depthChangeInvalidatesDepthAndOptical() {
    val previous = inputs()
    val current = previous.copy(depth = 2f)

    assertThat(calculateStageInvalidation(previous, current, sourceChanged = false))
      .isEqualTo(
        GlassStageInvalidation(
          blur = false,
          depth = true,
          optical = true,
          detail = false,
          rim = false,
        ),
      )
  }

  @Test
  fun calculateStageInvalidation_opticalKeyChangeInvalidatesOpticalOnly() {
    val previous = inputs()
    val current = previous.copy(optical = Any())

    assertThat(calculateStageInvalidation(previous, current, sourceChanged = false))
      .isEqualTo(
        GlassStageInvalidation(
          blur = false,
          depth = false,
          optical = true,
          detail = false,
          rim = false,
        ),
      )
  }

  @Test
  fun calculateStageInvalidation_detailKeyChangeInvalidatesDetailOnly() {
    val previous = inputs()
    val current = previous.copy(detail = Any())

    assertThat(calculateStageInvalidation(previous, current, sourceChanged = false))
      .isEqualTo(
        GlassStageInvalidation(
          blur = false,
          depth = false,
          optical = false,
          detail = true,
          rim = false,
        ),
      )
  }

  @Test
  fun calculateStageInvalidation_rimKeyChangeInvalidatesRimOnly() {
    val previous = inputs()
    val current = previous.copy(rim = Any())

    assertThat(calculateStageInvalidation(previous, current, sourceChanged = false))
      .isEqualTo(
        GlassStageInvalidation(
          blur = false,
          depth = false,
          optical = false,
          detail = false,
          rim = true,
        ),
      )
  }

  @Test
  fun calculateStageInvalidation_inactiveBlurAndRimAreNotDirtyOnFirstPipeline() {
    assertThat(
      calculateStageInvalidation(
        previous = null,
        current = GlassStageInputs(
          blur = null,
          depth = 1f,
          optical = Any(),
          detail = null,
          rim = null,
        ),
        sourceChanged = false,
      ),
    ).isEqualTo(
      GlassStageInvalidation(
        blur = false,
        depth = true,
        optical = true,
        detail = false,
        rim = false,
      ),
    )
  }

  @Test
  fun calculateStageInvalidation_sourceChangeWithInactiveBlurInvalidatesDepthAndOptical() {
    val inputs = inputs().copy(blur = null)

    assertThat(calculateStageInvalidation(inputs, inputs, sourceChanged = true))
      .isEqualTo(
        GlassStageInvalidation(
          blur = false,
          depth = true,
          optical = true,
          detail = true,
          rim = false,
        ),
      )
  }

  @Test
  fun calculateStageInvalidation_disablingBlurInvalidatesDepthAndOptical() {
    val previous = inputs()
    val current = previous.copy(blur = null)

    assertThat(calculateStageInvalidation(previous, current, sourceChanged = false))
      .isEqualTo(
        GlassStageInvalidation(
          blur = false,
          depth = true,
          optical = true,
          detail = false,
          rim = false,
        ),
      )
  }

  @Test
  fun calculateRequiredStageInvalidation_missingActiveLayersForceTheirStagesAndDependentsDirty() {
    val inputs = inputs()
    val allAvailable = GlassStageAvailability(
      blur = true,
      depth = true,
      optical = true,
      detail = true,
      rim = true,
    )
    listOf(
      allAvailable.copy(blur = false) to
        GlassStageInvalidation.None.copy(blur = true, depth = true, optical = true),
      allAvailable.copy(depth = false) to
        GlassStageInvalidation.None.copy(depth = true, optical = true),
      allAvailable.copy(optical = false) to
        GlassStageInvalidation.None.copy(optical = true),
      allAvailable.copy(detail = false) to
        GlassStageInvalidation.None.copy(detail = true),
      allAvailable.copy(rim = false) to
        GlassStageInvalidation.None.copy(rim = true),
    ).forEach { (availability, expected) ->
      assertThat(
        calculateRequiredStageInvalidation(
          previous = inputs,
          current = inputs,
          sourceChanged = false,
          availability = availability,
        ),
      ).isEqualTo(expected)
    }
  }

  @Test
  fun calculateRequiredStageInvalidation_ignoresUnavailableInactiveBlurAndRimLayers() {
    val inputs = inputs().copy(blur = null, detail = null, rim = null)

    assertThat(
      calculateRequiredStageInvalidation(
        previous = inputs,
        current = inputs,
        sourceChanged = false,
        availability = GlassStageAvailability(
          blur = false,
          depth = true,
          optical = true,
          detail = false,
          rim = false,
        ),
      ),
    ).isEqualTo(GlassStageInvalidation.None)
  }

  @Test
  fun calculateRequiredStageInvalidation_interactionUniformOnlyChangeReusesBaseStages() {
    val previousUniforms = GlassInteractionUniforms(
      position = Offset(20f, 20f),
      radiusPx = 70f,
      lightingIntensity = 0f,
      refractionMultiplier = 1f,
      whitePointDelta = 0f,
    )
    val currentUniforms = previousUniforms.copy(
      position = Offset(80f, 60f),
      lightingIntensity = 1f,
      refractionMultiplier = 1.08f,
      whitePointDelta = 0.04f,
    )
    val inputs = inputs()

    assertThat(currentUniforms).isNotEqualTo(previousUniforms)
    assertThat(
      calculateRequiredStageInvalidation(
        previous = inputs,
        current = inputs,
        sourceChanged = false,
        availability = GlassStageAvailability(
          blur = true,
          depth = true,
          optical = true,
          detail = true,
          rim = true,
        ),
      ),
    ).isEqualTo(GlassStageInvalidation.None)
  }

  @Test
  fun resolveGlassRuntimeSourceState_drawableInputWithoutSnapshotForcesRecapture() {
    val state = resolveGlassRuntimeSourceState(
      captureScale = .5f,
      layerSize = Size(100f, 80f),
      layerOffset = Offset(4f, 2f),
      hasDrawableInput = true,
      inputSnapshot = null,
    )

    assertThat(state.hasDrawableSource).isTrue()
    assertThat(state.snapshot).isNull()
  }

  @Test
  fun resolveGlassRuntimeSourceState_noDrawableInputIsDistinctFromUnknownSnapshot() {
    val state = resolveGlassRuntimeSourceState(
      captureScale = 1f,
      layerSize = Size(100f, 80f),
      layerOffset = Offset.Zero,
      hasDrawableInput = false,
      inputSnapshot = null,
    )

    assertThat(state.hasDrawableSource).isFalse()
    assertThat(state.snapshot).isNull()
  }

  @Test
  fun resolveGlassRuntimeSourceState_snapshotTracksCaptureGeometryAndOpaqueInput() {
    val input = TestInputSnapshot(1)
    val base = snapshot(inputSnapshot = input)

    assertThat(base).isEqualTo(base)
    assertThat(snapshot(captureScale = .5f)).isNotEqualTo(base)
    assertThat(snapshot(layerSize = Size(120f, 80f))).isNotEqualTo(base)
    assertThat(snapshot(layerOffset = Offset(8f, 2f))).isNotEqualTo(base)
    assertThat(snapshot(inputSnapshot = TestInputSnapshot(2))).isNotEqualTo(base)
    assertThat(snapshot(backgroundColor = Color.White)).isNotEqualTo(base)
  }

  @Test
  fun resolveGlassRuntimeSourceState_unchangedInputsReuseAcceptedSnapshot() {
    val input = TestInputSnapshot(1)
    val first = snapshot(inputSnapshot = input)
    val second = resolveGlassRuntimeSourceState(
      captureScale = 1f,
      layerSize = Size(100f, 80f),
      layerOffset = Offset.Zero,
      hasDrawableInput = true,
      inputSnapshot = input,
      previousSnapshot = first,
    ).snapshot

    assertThat(second).isSameInstanceAs(first)
  }

  @Test
  fun resolveGlassRuntimeSourceState_changedInputInvalidatesSourceDependentStages() {
    val captured = snapshot(inputSnapshot = TestInputSnapshot(1))
    val changed = snapshot(inputSnapshot = TestInputSnapshot(2))
    val inputs = inputs()

    assertThat(
      calculateStageInvalidation(
        previous = inputs,
        current = inputs,
        sourceChanged = changed != captured,
      ),
    ).isEqualTo(
      GlassStageInvalidation(
        blur = true,
        depth = true,
        optical = true,
        detail = true,
        rim = false,
      ),
    )
  }

  private fun inputs() = GlassStageInputs(
    blur = Any(),
    depth = 1f,
    optical = Any(),
    detail = Any(),
    rim = Any(),
  )

  private fun snapshot(
    captureScale: Float = 1f,
    layerSize: Size = Size(100f, 80f),
    layerOffset: Offset = Offset.Zero,
    inputSnapshot: HazeEffectInputSnapshot = TestInputSnapshot(1),
    backgroundColor: Color = Color.Transparent,
  ) = resolveGlassRuntimeSourceState(
    captureScale = captureScale,
    layerSize = layerSize,
    layerOffset = layerOffset,
    hasDrawableInput = true,
    inputSnapshot = inputSnapshot,
    backgroundColor = backgroundColor,
  ).snapshot!!

  private data class TestInputSnapshot(val value: Int) : HazeEffectInputSnapshot
}
