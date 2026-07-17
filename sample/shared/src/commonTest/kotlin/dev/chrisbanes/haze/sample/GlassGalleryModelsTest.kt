@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze.sample

import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.glass.ChromaticAberrationMode
import dev.chrisbanes.haze.glass.GlassOptics
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class GlassGalleryModelsTest {
  @Test
  fun galleryArtworks_haveStableUniqueIds() {
    assertEquals(4, GalleryArtworks.size)
    assertEquals(4, GalleryArtworks.map { it.id }.toSet().size)
    assertTrue(GalleryArtworks.all { it.title.isNotBlank() && it.description.isNotBlank() })
  }

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
  fun reset_restoresCompleteInitialLabState() {
    val changed = GlassLabState(
      preset = GlassLabPresetId.Prism,
      backdrop = GlassGalleryBackdropId.Grid,
      advancedExpanded = true,
      style = glassLabPresetStyle(GlassLabPresetId.Prism),
    )

    assertEquals(GlassLabState(), changed.reset())
  }
}
