// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze.sample

import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.glass.ChromaticAberrationMode
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassVisualEffect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GlassGalleryModelsTest {
  @Test
  fun adaptivePreset_usesBuiltInAdaptiveOptics() {
    assertEquals(GlassOptics.Adaptive, glassLabPresetStyle(GlassLabPresetId.Adaptive).optics)
  }

  @Test
  fun literalPresets_haveDistinctCompleteOptics() {
    val clear = assertIs<GlassOptics.Absolute>(glassLabPresetStyle(GlassLabPresetId.Clear).optics)
    val frosted = assertIs<GlassOptics.Absolute>(glassLabPresetStyle(GlassLabPresetId.Frosted).optics)
    val deep = assertIs<GlassOptics.Absolute>(glassLabPresetStyle(GlassLabPresetId.Deep).optics)
    val prism = assertIs<GlassOptics.Absolute>(glassLabPresetStyle(GlassLabPresetId.Prism).optics)

    assertTrue(clear.blurRadius < frosted.blurRadius)
    assertTrue(clear.depth < deep.depth)
    assertNotEquals(deep, prism)
    assertEquals(
      ChromaticAberrationMode.Full,
      glassLabPresetStyle(GlassLabPresetId.Prism).rendering.chromaticAberrationMode,
    )
    assertEquals(
      0.22f,
      glassLabPresetStyle(GlassLabPresetId.Prism).rendering.chromaticAberrationStrength,
    )
  }

  @Test
  fun editingAdaptiveStyle_changesSelectionToCustomAndLiteralOptics() {
    val edited = GlassLabState().editStyle { style ->
      style.copy(optics = GlassOptics.Absolute(refractionStrength = 0.6f))
    }

    assertEquals(GlassLabPresetId.Custom, edited.preset)
    assertIs<GlassOptics.Absolute>(edited.style.optics)
  }

  @Test
  fun interactionModes_configureExpectedPointerSlots() {
    val off = GlassVisualEffect()
    GlassLabInteractionMode.Off.applyTo(off)
    assertFalse(off.observesPointerEvents)

    val pressed = GlassVisualEffect()
    GlassLabInteractionMode.Pressed.applyTo(pressed)
    assertTrue(pressed.observesPointerEvents)
    pressed.clearPressed()
    assertFalse(pressed.observesPointerEvents)

    val all = GlassVisualEffect()
    GlassLabInteractionMode.All.applyTo(all)
    assertTrue(all.observesPointerEvents)
    all.clearPressed()
    assertTrue(all.observesPointerEvents)
    all.clearHovered()
    assertFalse(all.observesPointerEvents)
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

    assertEquals(GlassLabState(), changed.reset())
  }
}
