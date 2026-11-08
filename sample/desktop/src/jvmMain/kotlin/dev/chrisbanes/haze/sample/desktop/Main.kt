// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.chrisbanes.haze.sample.EmbeddedSample
import dev.chrisbanes.haze.sample.SampleEmbedTheme
import dev.chrisbanes.haze.sample.SampleLaunchRequest
import dev.chrisbanes.haze.sample.Samples
import dev.chrisbanes.haze.sample.resolveSampleLaunch

fun main() {
  // Optionally open one sample at a fixed 16:9 size, for consistent screen recordings. See
  // sample/desktop/build.gradle.kts for the Gradle properties that set these.
  val query = listOf("embed", "sample", "effect", "theme")
    .mapNotNull { name -> System.getProperty("haze.$name")?.let { name to it } }
    .toMap()
  val request = resolveSampleLaunch(query, Samples)
  if (request is SampleLaunchRequest.Invalid) error("Invalid sample launch: ${request.message}")

  application {
    val windowState = if (request is SampleLaunchRequest.Selected) {
      rememberWindowState(size = DpSize(1280.dp, 720.dp), position = WindowPosition(Alignment.Center))
    } else {
      rememberWindowState()
    }
    Window(
      title = "Haze Sample",
      state = windowState,
      onCloseRequest = ::exitApplication,
    ) {
      when {
        request is SampleLaunchRequest.Selected && request.embedded -> EmbeddedSample(request)
        request is SampleLaunchRequest.Selected -> Samples(
          appTitle = "Haze Samples",
          initialSelection = request,
          useDarkColors = when (request.theme) {
            SampleEmbedTheme.System -> isSystemInDarkTheme()
            SampleEmbedTheme.Light -> false
            SampleEmbedTheme.Dark -> true
          },
        )
        else -> Samples("Haze Samples")
      }
    }
  }
}
