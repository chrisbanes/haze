// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration

@Composable
internal actual fun WithGlassTestSystemAppearance(
  appearance: GlassTestSystemAppearance,
  content: @Composable () -> Unit,
) {
  val configuration = Configuration(LocalConfiguration.current).apply {
    uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or when (appearance) {
      GlassTestSystemAppearance.Light -> Configuration.UI_MODE_NIGHT_NO
      GlassTestSystemAppearance.Dark -> Configuration.UI_MODE_NIGHT_YES
    }
  }
  CompositionLocalProvider(LocalConfiguration provides configuration, content = content)
}
