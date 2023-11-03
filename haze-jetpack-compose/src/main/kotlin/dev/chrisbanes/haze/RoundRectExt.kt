// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.geometry.RoundRect

internal fun RoundRect.inflate(delta: Float): RoundRect =
  RoundRect(
    left = left - delta,
    top = top - delta,
    right = right + delta,
    bottom = bottom + delta,
    topLeftCornerRadius = topLeftCornerRadius,
    topRightCornerRadius = topRightCornerRadius,
    bottomRightCornerRadius = bottomRightCornerRadius,
    bottomLeftCornerRadius = bottomLeftCornerRadius,
  )
