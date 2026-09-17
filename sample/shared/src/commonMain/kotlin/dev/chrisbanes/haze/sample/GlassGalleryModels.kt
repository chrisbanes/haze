// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze.sample

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import dev.chrisbanes.haze.ExperimentalHazeApi

internal val DefaultGlassHoverAnimationSpec: FiniteAnimationSpec<Float> = spring(
  dampingRatio = 1f,
  stiffness = Spring.StiffnessMediumLow,
)

internal val DefaultGlassPressAnimationSpec: FiniteAnimationSpec<Float> = spring(
  dampingRatio = 0.82f,
  stiffness = Spring.StiffnessMedium,
)

internal val DefaultGlassReleaseAnimationSpec: FiniteAnimationSpec<Float> = spring(
  dampingRatio = 0.72f,
  stiffness = Spring.StiffnessMediumLow,
)

@Immutable
internal data class GalleryArtwork(
  val title: String,
  val subtitle: String,
  val description: String,
  val colors: List<Color>,
  val accent: Color,
  val foreground: Color,
)

internal val GalleryArtworks = listOf(
  GalleryArtwork(
    title = "Chromatic Bloom",
    subtitle = "Studies in refracted colour",
    description = "A magenta and cyan poster with concentric circles and fine grid lines",
    colors = listOf(Color(0xFF4B1FFF), Color(0xFFFF3D9A), Color(0xFFFFB44A)),
    accent = Color(0xFF72F5FF),
    foreground = Color.White,
  ),
  GalleryArtwork(
    title = "Signal Garden",
    subtitle = "Organic systems, synthetic light",
    description = "An emerald and ultraviolet poster with vertical signal bars",
    colors = listOf(Color(0xFF041E1A), Color(0xFF00B979), Color(0xFF9A63FF)),
    accent = Color(0xFFE8FF5A),
    foreground = Color.White,
  ),
  GalleryArtwork(
    title = "Blue Hour",
    subtitle = "Quiet geometry after sunset",
    description = "A deep blue poster with coral geometry and narrow horizontal rules",
    colors = listOf(Color(0xFF04133A), Color(0xFF0E67D1), Color(0xFF1FD6C5)),
    accent = Color(0xFFFF6B6B),
    foreground = Color.White,
  ),
  GalleryArtwork(
    title = "Solar Type",
    subtitle = "Letterforms in orbital motion",
    description = "A warm orange poster with black typography and electric blue details",
    colors = listOf(Color(0xFFFF4D00), Color(0xFFFFC400), Color(0xFFFFF1A8)),
    accent = Color(0xFF0057FF),
    foreground = Color(0xFF15100B),
  ),
)
