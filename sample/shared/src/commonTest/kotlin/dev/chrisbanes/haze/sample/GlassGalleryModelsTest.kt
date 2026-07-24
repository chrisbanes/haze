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
import assertk.assertions.isNotNull
import assertk.assertions.isTrue
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.glass.ChromaticAberrationMode
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassVisualEffect
import kotlin.test.Test

class GlassGalleryModelsTest {
  @Test
  fun adaptivePreset_usesBuiltInAdaptiveOptics() {
    assertThat(glassLabPresetStyle(GlassLabPresetId.Adaptive).optics)
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
      glassLabPresetStyle(GlassLabPresetId.Prism).rendering.chromaticAberrationMode,
    ).isEqualTo(
      ChromaticAberrationMode.Full,
    )
    assertThat(
      glassLabPresetStyle(GlassLabPresetId.Prism).rendering.chromaticAberrationStrength,
    ).isEqualTo(
      0.22f,
    )
  }

  @Test
  fun editingAdaptiveStyle_changesSelectionToCustomAndLiteralOptics() {
    val edited = GlassLabState().editStyle { style ->
      style.copy(optics = GlassOptics.Absolute(refractionStrength = 0.6f))
    }

    assertThat(edited.preset).isEqualTo(GlassLabPresetId.Custom)
    assertThat(edited.style.optics).isNotNull().isInstanceOf<GlassOptics.Absolute>()
  }

  @Test
  fun interactionModes_configureExpectedPointerSlots() {
    val off = GlassVisualEffect()
    GlassLabInteractionMode.Off.applyTo(off)
    assertThat(off.observesPointerEvents).isFalse()

    val pressed = GlassVisualEffect()
    GlassLabInteractionMode.Pressed.applyTo(pressed)
    assertThat(pressed.observesPointerEvents).isTrue()
    pressed.clearPressed()
    assertThat(pressed.observesPointerEvents).isFalse()

    val all = GlassVisualEffect()
    GlassLabInteractionMode.All.applyTo(all)
    assertThat(all.observesPointerEvents).isTrue()
    all.clearPressed()
    assertThat(all.observesPointerEvents).isTrue()
    all.clearHovered()
    assertThat(all.observesPointerEvents).isFalse()
  }

  @Test
  fun reset_restoresCompleteInitialLabState() {
    val changed = GlassLabState(
      preset = GlassLabPresetId.Prism,
      backdrop = GlassGalleryBackdropId.Grid,
      interaction = GlassLabInteractionMode.Off,
      advancedExpanded = true,
      style = glassLabPresetStyle(GlassLabPresetId.Prism),
    )

    assertThat(changed.reset()).isEqualTo(GlassLabState())
  }
}

private fun absoluteOpticsFor(id: GlassLabPresetId): GlassOptics.Absolute {
  val optics = glassLabPresetStyle(id).optics
  assertThat(optics).isNotNull().isInstanceOf<GlassOptics.Absolute>()
  return optics as GlassOptics.Absolute
}
