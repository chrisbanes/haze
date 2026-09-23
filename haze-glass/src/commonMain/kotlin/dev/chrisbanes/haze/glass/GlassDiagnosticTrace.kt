// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

/** Emits only when a CB-10 platform diagnostic explicitly opts in. */
internal expect fun recordGlassDiagnosticAppliedScale(
  scale: Float,
  materialWidthPx: Float,
  materialHeightPx: Float,
)
