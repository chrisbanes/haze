// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

public enum class TrimMemoryLevel(public val severity: Int) {
  UI_HIDDEN(severity = 10),
  BACKGROUND(severity = 20),
  MODERATE(severity = 40),
  COMPLETE(severity = 80),
}
