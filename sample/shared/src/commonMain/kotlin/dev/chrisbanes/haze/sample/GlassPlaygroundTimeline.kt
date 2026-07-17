// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze.sample

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.glass.GlassDefaults
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassStyle
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

public enum class GlassPlaygroundSurfaceId {
  Lens,
  Pill,
  Card,
  Prism,
}

@Immutable
internal data class GlassPlaygroundFrame(
  val backdropOffset: Float,
  val lightPosition: Offset,
  val lensPosition: Offset,
  val pillPosition: Offset,
  val cardPosition: Offset,
  val prismPosition: Offset,
) {
  fun position(id: GlassPlaygroundSurfaceId): Offset = when (id) {
    GlassPlaygroundSurfaceId.Lens -> lensPosition
    GlassPlaygroundSurfaceId.Pill -> pillPosition
    GlassPlaygroundSurfaceId.Card -> cardPosition
    GlassPlaygroundSurfaceId.Prism -> prismPosition
  }
}

internal fun glassPlaygroundFrame(progress: Float): GlassPlaygroundFrame {
  require(progress.isFinite() && progress in 0f..1f)
  val wrappedProgress = if (progress == 1f) 0f else progress
  val angle = (wrappedProgress * 2f * PI).toFloat()
  fun wave(phase: Float): Float = sin((angle + phase).toDouble()).toFloat()
  fun orbitX(center: Float, radius: Float, phase: Float): Float =
    center + radius * cos((angle + phase).toDouble()).toFloat()

  return GlassPlaygroundFrame(
    backdropOffset = 0.08f * wave(0f),
    lightPosition = Offset(
      x = orbitX(center = 0.5f, radius = 0.35f, phase = 0f),
      y = 0.5f + 0.25f * wave(0f),
    ),
    lensPosition = Offset(
      x = orbitX(center = 0.5f, radius = 0.28f, phase = 0f),
      y = 0.24f + 0.08f * wave(0f),
    ),
    pillPosition = Offset(
      x = orbitX(center = 0.5f, radius = 0.25f, phase = PI.toFloat()),
      y = 0.5f + 0.12f * wave(PI.toFloat()),
    ),
    cardPosition = Offset(
      x = orbitX(center = 0.5f, radius = 0.2f, phase = (PI / 2).toFloat()),
      y = 0.72f + 0.06f * wave((PI / 2).toFloat()),
    ),
    prismPosition = Offset(
      x = orbitX(center = 0.5f, radius = 0.3f, phase = (3 * PI / 2).toFloat()),
      y = 0.42f + 0.1f * wave((3 * PI / 2).toFloat()),
    ),
  )
}

internal fun glassPlaygroundStyle(id: GlassPlaygroundSurfaceId): GlassStyle = when (id) {
  GlassPlaygroundSurfaceId.Lens,
  GlassPlaygroundSurfaceId.Pill,
  -> GlassDefaults.style.copy(optics = GlassOptics.Adaptive)
  GlassPlaygroundSurfaceId.Card -> glassLabPresetStyle(GlassLabPresetId.Deep)
  GlassPlaygroundSurfaceId.Prism -> glassLabPresetStyle(GlassLabPresetId.Prism)
}

internal fun glassPlaygroundShape(id: GlassPlaygroundSurfaceId): RoundedCornerShape = when (id) {
  GlassPlaygroundSurfaceId.Lens -> RoundedCornerShape(percent = 50)
  GlassPlaygroundSurfaceId.Pill -> RoundedCornerShape(percent = 50)
  GlassPlaygroundSurfaceId.Card -> RoundedCornerShape(32.dp)
  GlassPlaygroundSurfaceId.Prism -> RoundedCornerShape(24.dp)
}
