// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.test

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration

@Composable
internal actual fun WithDefaultScreenshotSystemAppearance(
  content: @Composable () -> Unit,
) {
  val configuration = Configuration(LocalConfiguration.current).apply {
    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or Configuration.UI_MODE_NIGHT_NO
  }
  CompositionLocalProvider(LocalConfiguration provides configuration, content = content)
}
