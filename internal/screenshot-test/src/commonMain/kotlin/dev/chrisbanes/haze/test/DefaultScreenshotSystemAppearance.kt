// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.test

import androidx.compose.runtime.Composable

@Composable
internal expect fun WithDefaultScreenshotSystemAppearance(
  content: @Composable () -> Unit,
)
