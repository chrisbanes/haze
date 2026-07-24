// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNull
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
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
  fun calculateStageInvalidation_depthMergeChangeInvalidatesOpticalOnly() {
    val previous = inputs()
    val current = previous.copy(mergeDepthIntoOptical = true)

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
  fun resolveGlassSourceState_unknownVersionIsDrawableButHasNoSnapshot() {
    val state = resolveGlassSourceState(
      captureScale = .5f,
      layerSize = Size(100f, 80f),
      layerOffset = Offset(4f, 2f),
      areas = listOf(sourceArea(contentVersion = null)),
    )

    assertThat(state.hasDrawableSource).isTrue()
    assertThat(state.snapshot).isNull()
  }

  @Test
  fun resolveGlassSourceState_noDrawableAreasIsDistinctFromUnknownVersion() {
    val state = resolveGlassSourceState(
      captureScale = 1f,
      layerSize = Size(100f, 80f),
      layerOffset = Offset.Zero,
      areas = listOf(sourceArea(contentLayerIdentity = null)),
    )

    assertThat(state.hasDrawableSource).isFalse()
    assertThat(state.snapshot).isNull()
  }

  @Test
  fun resolveGlassSourceState_snapshotTracksCaptureGeometryAndOrderedAreaIdentity() {
    val base = resolveGlassSourceState(
      captureScale = 1f,
      layerSize = Size(100f, 80f),
      layerOffset = Offset(4f, 2f),
      areas = listOf(sourceArea()),
    ).snapshot!!

    assertThat(base).isEqualTo(base)
    assertThat(snapshot(captureScale = .5f)).isNotEqualTo(base)
    assertThat(snapshot(layerSize = Size(120f, 80f))).isNotEqualTo(base)
    assertThat(snapshot(layerOffset = Offset(8f, 2f))).isNotEqualTo(base)
    assertThat(snapshot(contentVersion = 2L)).isNotEqualTo(base)
    assertThat(snapshot(position = Offset(2f, 3f))).isNotEqualTo(base)
    assertThat(snapshot(size = Size(40f, 24f))).isNotEqualTo(base)
    assertThat(snapshot(areaIdentity = EqualIdentity())).isNotEqualTo(base)
    assertThat(snapshot(contentLayerIdentity = EqualIdentity())).isNotEqualTo(base)
  }

  @Test
  fun resolveGlassSourceState_snapshotPreservesAreaOrder() {
    val first = sourceArea(areaIdentity = "first", contentLayerIdentity = "first-layer")
    val second = sourceArea(areaIdentity = "second", contentLayerIdentity = "second-layer")

    val ordered = stateOf(first, second).snapshot!!
    val reversed = stateOf(second, first).snapshot!!

    assertThat(ordered).isNotEqualTo(reversed)
  }

  @Test
  fun resolveGlassSourceState_separatelyAllocatedEquivalentAreasProduceEqualSnapshots() {
    val areaIdentity = Any()
    val contentLayerIdentity = Any()

    val first = stateOf(
      sourceArea(areaIdentity = areaIdentity, contentLayerIdentity = contentLayerIdentity),
    ).snapshot!!
    val second = stateOf(
      sourceArea(areaIdentity = areaIdentity, contentLayerIdentity = contentLayerIdentity),
    ).snapshot!!

    assertThat(first).isEqualTo(second)
  }

  @Test
  fun resolveGlassSourceState_unchangedInputsReuseAcceptedSnapshot() {
    val first = stateOf(sourceArea()).snapshot!!
    val second = resolveGlassSourceState(
      captureScale = 1f,
      layerSize = Size(100f, 80f),
      layerOffset = Offset.Zero,
      areas = listOf(sourceArea()),
      previousSnapshot = first,
    ).snapshot

    assertThat(second).isSameInstanceAs(first)
  }

  @Test
  fun resolveGlassSourceState_valueEqualAreaIdentitiesProduceUnequalSnapshots() {
    val contentLayerIdentity = Any()
    val first = stateOf(
      sourceArea(areaIdentity = EqualIdentity(), contentLayerIdentity = contentLayerIdentity),
    ).snapshot!!
    val second = stateOf(
      sourceArea(areaIdentity = EqualIdentity(), contentLayerIdentity = contentLayerIdentity),
    ).snapshot!!

    assertThat(first).isNotEqualTo(second)
  }

  @Test
  fun resolveGlassSourceState_valueEqualContentLayerIdentitiesProduceUnequalSnapshots() {
    val areaIdentity = Any()
    val first = stateOf(
      sourceArea(areaIdentity = areaIdentity, contentLayerIdentity = EqualIdentity()),
    ).snapshot!!
    val second = stateOf(
      sourceArea(areaIdentity = areaIdentity, contentLayerIdentity = EqualIdentity()),
    ).snapshot!!

    assertThat(first).isNotEqualTo(second)
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
    layerOffset: Offset = Offset(4f, 2f),
    areaIdentity: Any = GlassStageInvalidationTest.areaIdentity,
    contentLayerIdentity: Any? = GlassStageInvalidationTest.contentLayerIdentity,
    contentVersion: Long? = 1L,
    position: Offset = Offset(1f, 3f),
    size: Size = Size(30f, 24f),
  ) = resolveGlassSourceState(
    captureScale = captureScale,
    layerSize = layerSize,
    layerOffset = layerOffset,
    areas = listOf(sourceArea(areaIdentity, contentLayerIdentity, contentVersion, position, size)),
  ).snapshot!!

  private fun stateOf(vararg areas: GlassSourceArea) = resolveGlassSourceState(
    captureScale = 1f,
    layerSize = Size(100f, 80f),
    layerOffset = Offset.Zero,
    areas = areas.toList(),
  )

  private fun sourceArea(
    areaIdentity: Any = GlassStageInvalidationTest.areaIdentity,
    contentLayerIdentity: Any? = GlassStageInvalidationTest.contentLayerIdentity,
    contentVersion: Long? = 1L,
    position: Offset = Offset(1f, 3f),
    size: Size = Size(30f, 24f),
  ) = GlassSourceArea(areaIdentity, contentLayerIdentity, contentVersion, position, size)

  private class EqualIdentity {
    override fun equals(other: Any?) = other is EqualIdentity
    override fun hashCode() = 0
  }

  private companion object {
    val areaIdentity = Any()
    val contentLayerIdentity = Any()
  }
}
