// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeViewport

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
  val request = resolveSampleLaunch(sampleEmbedQuery(), Samples)
  ComposeViewport(viewportContainerId = "Sample") {
    PageLoadNotify()
    when (request) {
      SampleLaunchRequest.Normal -> Samples("Haze Samples")
      is SampleLaunchRequest.Selected -> if (request.embedded) {
        EmbeddedSample(request)
      } else {
        Samples(
          appTitle = "Haze Samples",
          initialSelection = request,
          useDarkColors = when (request.theme) {
            SampleEmbedTheme.System -> isSystemInDarkTheme()
            SampleEmbedTheme.Light -> false
            SampleEmbedTheme.Dark -> true
          },
        )
      }
      is SampleLaunchRequest.Invalid -> InvalidSampleEmbed()
    }
  }
}

external fun onLoadFinished()
external fun queryParameter(name: String): String?

private fun sampleEmbedQuery(): Map<String, String> = buildMap {
  listOf("embed", "sample", "effect", "theme").forEach { name ->
    queryParameter(name)?.let { put(name, it) }
  }
}

@Composable
private fun InvalidSampleEmbed() {
  SamplesTheme {
    Surface(modifier = Modifier.fillMaxSize()) {
      Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxSize(),
      ) {
        Text("Invalid sample link")
        Text("Check the sample and effect query parameters.")
      }
    }
  }
}

@Composable
fun PageLoadNotify() {
  LaunchedEffect(Unit) {
    onLoadFinished()
  }
}
