// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import android.os.Trace
import kotlin.math.roundToLong

internal actual fun recordGlassDiagnosticAppliedScale(scale: Float) {
  if (System.getProperty("dev.chrisbanes.haze.cb10AllocationDiagnostic") != "true") return
  Trace.setCounter("CB10GlassAppliedScalePermille", (scale * 1_000).roundToLong())
}
