// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze.sample

import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThan
import assertk.assertions.isNotEqualTo
import assertk.assertions.isTrue
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.glass.ChromaticAberrationMode
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassStyle
import kotlin.test.Test

class GlassGalleryModelsTest {
  @Test
  fun adaptivePreset_usesBuiltInAdaptiveOptics() {
    assertThat(GlassLabState().styleValues.optics)
      .isEqualTo(GlassOptics.Adaptive)
  }

  @Test
  fun literalPresets_haveDistinctCompleteOptics() {
    val clear = absoluteOpticsFor(GlassLabPresetId.Clear)
    val frosted = absoluteOpticsFor(GlassLabPresetId.Frosted)
    val deep = absoluteOpticsFor(GlassLabPresetId.Deep)
    val prism = absoluteOpticsFor(GlassLabPresetId.Prism)

    assertThat(clear.blurRadius).isLessThan(frosted.blurRadius)
    assertThat(clear.depth).isLessThan(deep.depth)
    assertThat(prism).isNotEqualTo(deep)
    assertThat(
      GlassLabState(preset = GlassLabPresetId.Prism).styleValues.chromaticAberrationMode,
    ).isEqualTo(
      ChromaticAberrationMode.Full,
    )
    assertThat(
      GlassLabState(preset = GlassLabPresetId.Prism).styleValues.chromaticAberrationStrength,
    ).isEqualTo(
      0.22f,
    )
  }

  @Test
  fun editingAdaptiveStyle_changesSelectionToCustomAndLiteralOptics() {
    val edited = GlassLabState().editStyle { values ->
      values.copy(optics = GlassOptics.Absolute(refractionStrength = 0.6f))
    }

    assertThat(edited.preset).isEqualTo(GlassLabPresetId.Custom)
    assertThat(edited.styleValues.optics).isInstanceOf<GlassOptics.Absolute>()
  }

  @Test
  fun interactionModes_produceDeclarativeStyles() {
    assertThat(GlassLabInteractionMode.Off.style).isEqualTo(GlassStyle)
    assertThat(GlassLabInteractionMode.Pressed.style).isNotEqualTo(GlassStyle)
    assertThat(GlassLabInteractionMode.All.style).isNotEqualTo(GlassStyle)
    assertThat(GlassLabInteractionMode.All.includesFocusedResponse).isTrue()
    assertThat(GlassLabInteractionMode.Pressed.includesFocusedResponse).isFalse()
  }

  @Test
  fun reset_restoresCompleteInitialLabState() {
    val changed = GlassLabState(
      preset = GlassLabPresetId.Prism,
      backdrop = GlassGalleryBackdropId.Grid,
      interaction = GlassLabInteractionMode.Off,
      advancedExpanded = true,
      styleValues = GlassLabStyleValues(optics = GlassOptics.Absolute()),
    )

    assertThat(changed.reset()).isEqualTo(GlassLabState())
  }
}

private fun absoluteOpticsFor(id: GlassLabPresetId): GlassOptics.Absolute {
  val optics = GlassLabState(preset = id).styleValues.optics
  assertThat(optics).isInstanceOf<GlassOptics.Absolute>()
  return optics as GlassOptics.Absolute
}
