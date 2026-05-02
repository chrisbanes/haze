// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

internal fun canUseRenderEffect(sdkInt: Int, isHardwareAccelerated: Boolean): Boolean {
  return sdkInt >= 31 && isHardwareAccelerated
}
