// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import dev.chrisbanes.haze.sample.Navigator
import dev.chrisbanes.haze.sample.Sample
import dev.chrisbanes.haze.sample.Samples

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)

    setContent {
      val navigator = remember { Navigator() }

      BackHandler { navigator.navigateUp() }

      Samples(
        appTitle = title.toString(),
        navigator = navigator,
        samples = Samples + AndroidSamples,
      )
    }
  }
}

private val AndroidSamples = listOf(
  Sample("ExoPlayer") { ExoPlayerSample(it) },
)
