// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.runtime.Composable

internal enum class SampleScreenshotSystemAppearance { Light, Dark }

@Composable
internal expect fun WithSampleScreenshotSystemAppearance(
  appearance: SampleScreenshotSystemAppearance,
  content: @Composable () -> Unit,
)
