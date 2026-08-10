// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.sample.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.chrisbanes.haze.sample.Samples

private const val FORCE_BLUR_EXTRA = "dev.chrisbanes.haze.sample.android.FORCE_BLUR"

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    enableEdgeToEdge()
    super.onCreate(savedInstanceState)

    setContent {
      Samples(
        appTitle = title.toString(),
        forceBlur = intent.getBooleanExtra(FORCE_BLUR_EXTRA, false),
      )
    }
  }
}
