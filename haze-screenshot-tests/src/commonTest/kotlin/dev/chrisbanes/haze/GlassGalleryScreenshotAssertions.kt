// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.sample.GlassGalleryBackdropId
import dev.chrisbanes.haze.sample.GlassLabPresetId
import dev.chrisbanes.haze.sample.GlassLabScreenshotContent
import dev.chrisbanes.haze.sample.GlassPlaygroundSampleContent
import dev.chrisbanes.haze.sample.GlassPlaygroundSurfaceId
import dev.chrisbanes.haze.sample.GlassProductSampleContent
import dev.chrisbanes.haze.sample.SamplesTheme
import dev.chrisbanes.haze.test.ScreenshotUiTest

internal fun ScreenshotUiTest.captureGlassProductHero() {
  setContent {
    Box(Modifier.fillMaxSize().background(Color.White)) {
      SamplesTheme(useDarkColors = true) {
        GlassProductSampleContent(
          selectedArtworkIndex = 0,
          favorite = false,
          recordingMode = true,
          onArtworkSelected = {},
          onFavoriteChanged = {},
          onRecordingModeChanged = {},
          onBack = {},
        )
      }
    }
  }
  waitForIdle()
  captureRoot()
}

internal fun ScreenshotUiTest.captureGlassPlaygroundBeats() {
  var progress by mutableFloatStateOf(0f)
  var displacedLens by mutableStateOf(Offset.Zero)
  setContent {
    Box(Modifier.fillMaxSize().background(Color.White)) {
      SamplesTheme(useDarkColors = true) {
        GlassPlaygroundSampleContent(
          progressProvider = { progress },
          dragOffsetProvider = { id ->
            if (id == GlassPlaygroundSurfaceId.Lens) displacedLens else Offset.Zero
          },
          isPlaying = false,
          recordingMode = true,
          onPlayPause = {},
          onReset = {},
          onRecordingModeChanged = {},
          onBack = {},
          onDragStart = {},
          onDrag = { _, _ -> },
          onDragEnd = {},
        )
      }
    }
  }

  waitForIdle()
  captureRoot("opening")
  progress = 0.2f
  waitForIdle()
  captureRoot("typography")
  progress = 0.5f
  waitForIdle()
  captureRoot("depth")
  progress = 0.8f
  waitForIdle()
  captureRoot("prism")
  displacedLens = Offset(120f, 72f)
  waitForIdle()
  captureRoot("dragged")
}

internal fun ScreenshotUiTest.captureGlassLabPresets() {
  var preset by mutableStateOf(GlassLabPresetId.Adaptive)
  var backdrop by mutableStateOf(GlassGalleryBackdropId.Gallery)
  setContent {
    Box(Modifier.fillMaxSize().background(Color.White)) {
      SamplesTheme(useDarkColors = true) {
        GlassLabScreenshotContent(preset = preset, backdrop = backdrop)
      }
    }
  }

  waitForIdle()
  captureRoot("adaptive")
  preset = GlassLabPresetId.Frosted
  backdrop = GlassGalleryBackdropId.Grid
  waitForIdle()
  captureRoot("frosted")
  preset = GlassLabPresetId.Prism
  backdrop = GlassGalleryBackdropId.Bands
  waitForIdle()
  captureRoot("prism")
}
