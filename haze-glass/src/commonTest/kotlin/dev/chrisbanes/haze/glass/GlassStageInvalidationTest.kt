// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEqualTo
import assertk.assertions.isNotSameInstanceAs
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
  fun calculateStageInvalidation_unchangedFrameSharesNone() {
    val inputs = inputs()

    assertThat(calculateStageInvalidation(inputs, inputs, sourceChanged = false))
      .isSameInstanceAs(GlassStageInvalidation.None)
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
    ).isSameInstanceAs(GlassStageInvalidation.None)
  }

  @Test
  fun calculateStageInvalidation_interactionUniformChangeInvalidatesOpticalOnly() {
    val previous = inputs().copy(opticalInteraction = interactionUniforms())
    val current = previous.copy(opticalInteraction = interactionUniforms().copy(radiusPx = 90f))

    assertThat(calculateStageInvalidation(previous, current, sourceChanged = false))
      .isEqualTo(GlassStageInvalidation.None.copy(optical = true))
    assertThat(
      calculateStageInvalidation(previous, previous.copy(opticalInteraction = null), sourceChanged = false),
    ).isEqualTo(GlassStageInvalidation.None.copy(optical = true))
  }

  @Test
  fun resolveGlassStageInputs_equalInputsReusePreviousInstance() {
    val blur = TestKey(1)
    val previous = resolveGlassStageInputs(
      previous = null,
      blur = blur,
      depth = 1f,
      optical = TestKey(2),
      opticalInteraction = interactionUniforms(),
      detail = TestKey(3),
      rim = TestKey(4),
    )

    val resolved = resolveGlassStageInputs(
      previous = previous,
      blur = TestKey(1),
      depth = 1f,
      optical = TestKey(2),
      opticalInteraction = interactionUniforms(),
      detail = TestKey(3),
      rim = TestKey(4),
    )

    assertThat(resolved).isSameInstanceAs(previous)
  }

  @Test
  fun resolveGlassStageInputs_changedInputBuildsNewInstance() {
    val previous = GlassStageInputs(
      blur = TestKey(1),
      depth = 1f,
      optical = TestKey(2),
      detail = TestKey(3),
      rim = TestKey(4),
      opticalInteraction = interactionUniforms(),
    )
    listOf(
      previous.copy(blur = TestKey(10)),
      previous.copy(blur = null),
      previous.copy(depth = 2f),
      previous.copy(optical = TestKey(20)),
      previous.copy(opticalInteraction = interactionUniforms().copy(radiusPx = 90f)),
      previous.copy(opticalInteraction = null),
      previous.copy(detail = TestKey(30)),
      previous.copy(rim = TestKey(40)),
      previous.copy(rim = null),
    ).forEach { expected ->
      val resolved = resolveGlassStageInputs(
        previous = previous,
        blur = expected.blur,
        depth = expected.depth,
        optical = expected.optical,
        opticalInteraction = expected.opticalInteraction,
        detail = expected.detail,
        rim = expected.rim,
      )

      assertThat(resolved).isNotSameInstanceAs(previous)
      assertThat(resolved).isEqualTo(expected)
    }
  }

  @Test
  fun resolveGlassStageInputs_nullPreviousBuildsInstance() {
    val resolved = resolveGlassStageInputs(
      previous = null,
      blur = null,
      depth = 1f,
      optical = TestKey(2),
      opticalInteraction = null,
      detail = null,
      rim = null,
    )

    assertThat(resolved).isEqualTo(
      GlassStageInputs(blur = null, depth = 1f, optical = TestKey(2), detail = null, rim = null),
    )
  }

  @Test
  fun resolveGlassStageInputs_depthEqualityMatchesDataClassEquality() {
    fun resolve(previous: GlassStageInputs, depth: Float) = resolveGlassStageInputs(
      previous = previous,
      blur = previous.blur,
      depth = depth,
      optical = previous.optical,
      opticalInteraction = null,
      detail = previous.detail,
      rim = previous.rim,
    )
    val nan = inputs().copy(depth = Float.NaN)
    val zero = inputs().copy(depth = 0f)

    assertThat(resolve(nan, Float.NaN)).isSameInstanceAs(nan)
    assertThat(resolve(zero, -0f)).isNotSameInstanceAs(zero)
    assertThat(nan.hasSameBaseAs(nan.copy())).isTrue()
    assertThat(zero.hasSameBaseAs(zero.copy(depth = -0f))).isFalse()
  }

  @Test
  fun hasSameBaseAs_ignoresOnlyRim() {
    val base = GlassStageInputs(
      blur = TestKey(1),
      depth = 1f,
      optical = TestKey(2),
      detail = TestKey(3),
      rim = TestKey(4),
      opticalInteraction = interactionUniforms(),
    )

    assertThat(base.hasSameBaseAs(base.copy(rim = TestKey(40)))).isTrue()
    assertThat(base.hasSameBaseAs(base.copy(rim = null))).isTrue()
    assertThat(base.hasSameBaseAs(base.copy(blur = TestKey(10)))).isFalse()
    assertThat(base.hasSameBaseAs(base.copy(depth = 2f))).isFalse()
    assertThat(base.hasSameBaseAs(base.copy(optical = TestKey(20)))).isFalse()
    assertThat(base.hasSameBaseAs(base.copy(opticalInteraction = null))).isFalse()
    assertThat(base.hasSameBaseAs(base.copy(detail = TestKey(30)))).isFalse()
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
  fun resolveGlassRuntimeSourceState_unchangedInputsReuseTheSameState() {
    val first = resolveGlassRuntimeSourceState(
      captureScale = 1f,
      layerSize = Size(100f, 80f),
      layerOffset = Offset.Zero,
      hasDrawableInput = true,
      inputSnapshot = TestInputSnapshot(1),
    )
    val second = resolveGlassRuntimeSourceState(
      captureScale = 1f,
      layerSize = Size(100f, 80f),
      layerOffset = Offset.Zero,
      hasDrawableInput = true,
      inputSnapshot = TestInputSnapshot(1),
      previousSnapshot = first.snapshot,
    )

    assertThat(second).isSameInstanceAs(first)
    assertThat(second.hasDrawableSource).isTrue()
    assertThat(second.snapshot).isSameInstanceAs(first.snapshot)
  }

  @Test
  fun resolveGlassRuntimeSourceState_changedInputBuildsNewState() {
    val first = snapshot(inputSnapshot = TestInputSnapshot(1))
    val second = resolveGlassRuntimeSourceState(
      captureScale = 1f,
      layerSize = Size(100f, 80f),
      layerOffset = Offset.Zero,
      hasDrawableInput = true,
      inputSnapshot = TestInputSnapshot(2),
      previousSnapshot = first,
    )

    assertThat(second.snapshot).isNotSameInstanceAs(first)
    assertThat(second.snapshot).isEqualTo(snapshot(inputSnapshot = TestInputSnapshot(2)))
  }

  @Test
  fun resolveGlassRuntimeSourceState_noDrawableInputAndUnknownSnapshotShareConstants() {
    fun resolve(hasDrawableInput: Boolean) = resolveGlassRuntimeSourceState(
      captureScale = 1f,
      layerSize = Size(100f, 80f),
      layerOffset = Offset.Zero,
      hasDrawableInput = hasDrawableInput,
      inputSnapshot = null,
    )

    assertThat(resolve(hasDrawableInput = false)).isSameInstanceAs(GlassRuntimeSourceState.NoDrawableSource)
    assertThat(resolve(hasDrawableInput = true)).isSameInstanceAs(GlassRuntimeSourceState.UnknownSnapshot)
  }

  @Test
  fun snapshotMatches_agreesWithPokoEquality() {
    val red = mergedField(mergedMember(0f, 0f, 40f, 40f, backgroundColor = Color.Red))
    val blue = mergedField(mergedMember(0f, 0f, 40f, 40f, backgroundColor = Color.Blue))
    val base = snapshot(
      captureScale = Float.NaN,
      inputSnapshot = TestInputSnapshot(1),
      mergedBackground = red,
    )
    listOf(
      snapshot(captureScale = Float.NaN, inputSnapshot = TestInputSnapshot(1), mergedBackground = red),
      snapshot(captureScale = 0f, inputSnapshot = TestInputSnapshot(1), mergedBackground = red),
      snapshot(captureScale = .5f, inputSnapshot = TestInputSnapshot(1), mergedBackground = red),
      snapshot(captureScale = Float.NaN, layerSize = Size(120f, 80f), mergedBackground = red),
      snapshot(captureScale = Float.NaN, layerOffset = Offset(8f, 2f), mergedBackground = red),
      snapshot(captureScale = Float.NaN, inputSnapshot = TestInputSnapshot(2), mergedBackground = red),
      snapshot(captureScale = Float.NaN, backgroundColor = Color.White, mergedBackground = red),
      snapshot(captureScale = Float.NaN, mergedBackground = blue),
      snapshot(captureScale = Float.NaN, mergedBackground = null),
    ).forEach { other ->
      assertThat(
        base.matches(
          captureScale = other.captureScale,
          layerSize = other.layerSize,
          layerOffset = other.layerOffset,
          inputSnapshot = other.inputSnapshot,
          backgroundColor = other.backgroundColor,
          mergedBackground = other.mergedBackground,
        ),
      ).isEqualTo(base == other)
    }
    val negativeZero = snapshot(captureScale = -0f)
    assertThat(
      snapshot(captureScale = 0f).matches(
        captureScale = -0f,
        layerSize = negativeZero.layerSize,
        layerOffset = negativeZero.layerOffset,
        inputSnapshot = negativeZero.inputSnapshot,
        backgroundColor = negativeZero.backgroundColor,
        mergedBackground = negativeZero.mergedBackground,
      ),
    ).isEqualTo(snapshot(captureScale = 0f) == negativeZero)
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

  @Test
  fun calculateStageInvalidation_mergedGeometryChangeRedrawsOpticalDetailAndRimButReusesBlur() {
    val previous = mergedStageInputs(gap = 10f)
    val current = mergedStageInputs(gap = 30f)

    assertThat(calculateStageInvalidation(previous, current, sourceChanged = false))
      .isEqualTo(
        GlassStageInvalidation(
          blur = false,
          depth = false,
          optical = true,
          detail = true,
          rim = true,
        ),
      )
  }

  @Test
  fun resolveGlassRuntimeSourceState_mergedBackgroundChangeRecapturesSource() {
    val red = mergedField(mergedMember(0f, 0f, 40f, 40f, backgroundColor = Color.Red))
    val blue = mergedField(mergedMember(0f, 0f, 40f, 40f, backgroundColor = Color.Blue))
    val first = snapshot(mergedBackground = red)
    assertThat(snapshot(mergedBackground = red)).isEqualTo(first)
    assertThat(snapshot(mergedBackground = blue)).isNotEqualTo(first)
  }

  private fun mergedStageInputs(gap: Float): GlassStageInputs {
    val size = Size(300f, 100f)
    val effect = GlassRuntimeEffect().apply {
      mergedField = mergedField(mergedMember(0f, 0f, 100f, 40f), mergedMember(100f + gap, 0f, 100f, 40f))
    }
    val style = resolveGlassStyle(effect, size, Density(1f), LayoutDirection.Ltr)
    val params = buildGlassRenderParams(style, GlassCoordinates(size, Offset.Zero, size, 1f))
    return GlassStageInputs(
      blur = params.blurEffectKey(),
      depth = params.depth,
      optical = params.opticalEffectKey(),
      detail = params.refractionDetailEffectKey(),
      rim = params.rimEffectKey(),
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
    mergedBackground: GlassMergedField? = null,
  ) = resolveGlassRuntimeSourceState(
    captureScale = captureScale,
    layerSize = layerSize,
    layerOffset = layerOffset,
    hasDrawableInput = true,
    inputSnapshot = inputSnapshot,
    backgroundColor = backgroundColor,
    mergedBackground = mergedBackground,
  ).snapshot!!

  private fun interactionUniforms() = GlassInteractionUniforms(
    position = Offset(20f, 20f),
    radiusPx = 70f,
    lightingIntensity = 0f,
    refractionMultiplier = 1f,
    whitePointDelta = 0f,
  )

  private data class TestKey(val value: Int)

  private data class TestInputSnapshot(val value: Int) : HazeEffectInputSnapshot
}
