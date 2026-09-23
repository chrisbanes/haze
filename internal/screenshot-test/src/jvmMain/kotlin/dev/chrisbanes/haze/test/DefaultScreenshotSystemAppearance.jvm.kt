// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(androidx.compose.ui.InternalComposeUiApi::class)
@file:Suppress("DEPRECATION")

package dev.chrisbanes.haze.test

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.LocalSystemTheme
import androidx.compose.ui.SystemTheme

@Composable
internal actual fun WithDefaultScreenshotSystemAppearance(
  content: @Composable () -> Unit,
) {
  CompositionLocalProvider(LocalSystemTheme provides SystemTheme.Light, content = content)
}
