// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.sample.GlassGalleryBackdropId
import dev.chrisbanes.haze.sample.GlassLabScreenshotContent
import dev.chrisbanes.haze.sample.GlassLabStyleId
import dev.chrisbanes.haze.sample.GlassPlaygroundSampleContent
import dev.chrisbanes.haze.sample.GlassPlaygroundSurfaceId
import dev.chrisbanes.haze.sample.GlassProductSampleContent
import dev.chrisbanes.haze.sample.SamplesTheme
import dev.chrisbanes.haze.test.ScreenshotTheme
import dev.chrisbanes.haze.test.ScreenshotUiTest

internal fun ScreenshotUiTest.captureGlassProductHero() {
  setContent {
    Box(Modifier.fillMaxSize().background(Color.White)) {
      GlassGalleryScreenshotTheme {
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
  val clearInteractionSource = MutableInteractionSource()
  setContent {
    Box(Modifier.fillMaxSize().background(Color.White)) {
      GlassGalleryScreenshotTheme {
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
          interactionSourceProvider = { id ->
            clearInteractionSource.takeIf { id == GlassPlaygroundSurfaceId.Clear }
          },
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
  captureRoot("clear")
  val press = PressInteraction.Press(Offset(90f, 56f))
  check(clearInteractionSource.tryEmit(press))
  waitForIdle()
  captureRoot("pressed")
  check(clearInteractionSource.tryEmit(PressInteraction.Release(press)))
  waitForIdle()
  displacedLens = Offset(120f, 72f)
  waitForIdle()
  captureRoot("dragged")
}

internal fun ScreenshotUiTest.captureGlassLabStyles() {
  var style by mutableStateOf(GlassLabStyleId.Regular)
  var backdrop by mutableStateOf(GlassGalleryBackdropId.Gallery)
  setContent {
    GlassGalleryScreenshotTheme {
      Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
      ) {
        GlassLabScreenshotContent(style = style, backdrop = backdrop)
      }
    }
  }

  waitForIdle()
  captureRoot("regular")
  style = GlassLabStyleId.Clear
  backdrop = GlassGalleryBackdropId.Grid
  waitForIdle()
  captureRoot("clear")
}

@Composable
private fun GlassGalleryScreenshotTheme(content: @Composable () -> Unit) {
  SamplesTheme(useDarkColors = true) {
    ScreenshotTheme(content)
  }
}
