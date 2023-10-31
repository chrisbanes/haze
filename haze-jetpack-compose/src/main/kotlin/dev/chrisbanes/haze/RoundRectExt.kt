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
