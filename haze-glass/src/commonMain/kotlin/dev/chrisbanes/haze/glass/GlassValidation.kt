// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified

internal const val UNIT_INTERVAL_DOMAIN: String = "finite and in 0f..1f"
internal const val SIGNED_UNIT_INTERVAL_DOMAIN: String = "finite and in -1f..1f"
internal const val DOUBLE_INTERVAL_DOMAIN: String = "finite and in 0f..2f"
internal const val NON_NEGATIVE_DOMAIN: String = "finite and non-negative"
internal const val NON_NEGATIVE_DP_DOMAIN: String = "specified, finite, and non-negative"
internal const val POSITIVE_AT_MOST_ONE_DOMAIN: String = "finite and in 0f < value <= 1f"

internal fun requireFinite(property: String, value: Float): Float {
  require(value.isFinite()) { "$property must be finite" }
  return value
}

internal fun requireFiniteInRange(
  property: String,
  value: Float,
  range: ClosedFloatingPointRange<Float>,
  domain: String,
): Float {
  require(value.isFinite() && value in range) { "$property must be $domain" }
  return value
}

internal fun requireFiniteNonNegative(property: String, value: Float): Float {
  require(value.isFinite() && value >= 0f) { "$property must be $NON_NEGATIVE_DOMAIN" }
  return value
}

internal fun requireSpecifiedFiniteNonNegative(property: String, value: Dp): Dp {
  require(value.isSpecified && value.value.isFinite() && value >= 0.dp) {
    "$property must be $NON_NEGATIVE_DP_DOMAIN"
  }
  return value
}

internal fun requirePositiveAtMostOne(property: String, value: Float): Float {
  require(value.isFinite() && value > 0f && value <= 1f) {
    "$property must be $POSITIVE_AT_MOST_ONE_DOMAIN"
  }
  return value
}
