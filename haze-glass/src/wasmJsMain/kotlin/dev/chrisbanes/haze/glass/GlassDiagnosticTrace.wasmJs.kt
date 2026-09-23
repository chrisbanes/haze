// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(kotlin.js.ExperimentalWasmJsInterop::class)

package dev.chrisbanes.haze.glass

import kotlin.math.roundToInt

private val cb10GlassDiagnosticEnabled: Boolean = isCb10GlassDiagnosticEnabled()

internal actual fun recordGlassDiagnosticAppliedScale(
  scale: Float,
  materialWidthPx: Float,
  materialHeightPx: Float,
) {
  if (!cb10GlassDiagnosticEnabled) return
  emitCb10GlassAppliedScalePermille(
    permille = (scale * 1_000).roundToInt(),
    materialWidthPx = materialWidthPx.roundToInt(),
    materialHeightPx = materialHeightPx.roundToInt(),
  )
}

@JsFun("() => globalThis.__cb10GlassDiagnostic === true")
private external fun isCb10GlassDiagnosticEnabled(): Boolean

@JsFun("(permille, width, height) => console.log('CB10GlassAppliedScalePermille=' + permille + ',materialPx=' + width + 'x' + height)")
private external fun emitCb10GlassAppliedScalePermille(
  permille: Int,
  materialWidthPx: Int,
  materialHeightPx: Int,
)
