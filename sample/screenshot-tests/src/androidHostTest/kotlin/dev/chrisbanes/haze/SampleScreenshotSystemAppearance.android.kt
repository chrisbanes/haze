// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration

@Composable
internal actual fun WithSampleScreenshotSystemAppearance(
  appearance: SampleScreenshotSystemAppearance,
  content: @Composable () -> Unit,
) {
  val nightMode = when (appearance) {
    SampleScreenshotSystemAppearance.Light -> Configuration.UI_MODE_NIGHT_NO
    SampleScreenshotSystemAppearance.Dark -> Configuration.UI_MODE_NIGHT_YES
  }
  val configuration = Configuration(LocalConfiguration.current).apply {
    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or nightMode
  }
  CompositionLocalProvider(LocalConfiguration provides configuration, content = content)
}
