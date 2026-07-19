// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze.sample

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.glass.ChromaticAberrationMode
import dev.chrisbanes.haze.glass.GlassDefaults
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.SurfaceProfile

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

public enum class GlassGalleryBackdropId {
  Gallery,
  Grid,
  Typography,
  Bands,
  Uniform,
}

public enum class GlassLabPresetId {
  Adaptive,
  Clear,
  Frosted,
  Deep,
  Prism,
  Custom,
}

internal val SelectableGlassLabPresets = GlassLabPresetId.entries - GlassLabPresetId.Custom

public fun glassLabPresetStyle(id: GlassLabPresetId): GlassStyle = when (id) {
  GlassLabPresetId.Adaptive -> GlassDefaults.style
  GlassLabPresetId.Clear -> GlassDefaults.style.copy(
    tint = Color.White.copy(alpha = 0.06f),
    optics = GlassOptics.Absolute(
      refractionStrength = 0.85f,
      refractionHeight = 0.22f,
      refractionScale = 18f,
      depth = 0.1f,
      blurRadius = 2.dp,
    ),
    lighting = GlassDefaults.style.lighting.copy(
      specularIntensity = 0.55f,
      ambientResponse = 0.42f,
    ),
    color = GlassDefaults.style.color.copy(
      alpha = 1f,
      contrast = 0.08f,
      whitePoint = 0.02f,
      chromaMultiplier = 1.05f,
    ),
    rendering = GlassDefaults.style.rendering.copy(
      edgeSoftness = 2.dp,
      contentNormalBlend = 0.15f,
      surfaceProfile = SurfaceProfile.Circle,
      chromaticAberrationStrength = 0f,
      chromaticAberrationMode = ChromaticAberrationMode.Simple,
    ),
  )
  GlassLabPresetId.Frosted -> GlassDefaults.style.copy(
    tint = Color.White.copy(alpha = 0.18f),
    optics = GlassOptics.Absolute(
      refractionStrength = 0.45f,
      refractionHeight = 0.18f,
      refractionScale = 10f,
      depth = 0.9f,
      blurRadius = 24.dp,
    ),
    lighting = GlassDefaults.style.lighting.copy(
      specularIntensity = 0.35f,
      ambientResponse = 0.55f,
    ),
    color = GlassDefaults.style.color.copy(
      alpha = 1f,
      contrast = -0.08f,
      whitePoint = 0.08f,
      chromaMultiplier = 0.72f,
    ),
    rendering = GlassDefaults.style.rendering.copy(
      edgeSoftness = 8.dp,
      contentNormalBlend = 0.08f,
      surfaceProfile = SurfaceProfile.Circle,
      chromaticAberrationStrength = 0f,
      chromaticAberrationMode = ChromaticAberrationMode.Simple,
    ),
  )
  GlassLabPresetId.Deep -> GlassDefaults.style.copy(
    tint = Color.White.copy(alpha = 0.1f),
    optics = GlassOptics.Absolute(
      refractionStrength = 0.9f,
      refractionHeight = 0.32f,
      refractionScale = 20f,
      depth = 0.78f,
      blurRadius = 16.dp,
    ),
    lighting = GlassDefaults.style.lighting.copy(
      specularIntensity = 0.75f,
      ambientResponse = 0.62f,
    ),
    color = GlassDefaults.style.color.copy(
      alpha = 1f,
      contrast = 0.05f,
      whitePoint = 0.02f,
      chromaMultiplier = 1f,
    ),
    rendering = GlassDefaults.style.rendering.copy(
      edgeSoftness = 10.dp,
      contentNormalBlend = 0.2f,
      surfaceProfile = SurfaceProfile.Squircle,
      chromaticAberrationStrength = 0.05f,
      chromaticAberrationMode = ChromaticAberrationMode.Simple,
    ),
  )
  GlassLabPresetId.Prism -> GlassDefaults.style.copy(
    tint = Color.White.copy(alpha = 0.08f),
    optics = GlassOptics.Absolute(
      refractionStrength = 0.82f,
      refractionHeight = 0.28f,
      refractionScale = 18f,
      depth = 0.35f,
      blurRadius = 8.dp,
    ),
    lighting = GlassDefaults.style.lighting.copy(
      specularIntensity = 0.72f,
      ambientResponse = 0.58f,
    ),
    color = GlassDefaults.style.color.copy(
      alpha = 1f,
      contrast = 0.1f,
      whitePoint = 0.02f,
      chromaMultiplier = 1.15f,
    ),
    rendering = GlassDefaults.style.rendering.copy(
      edgeSoftness = 8.dp,
      contentNormalBlend = 0.18f,
      surfaceProfile = SurfaceProfile.Squircle,
      chromaticAberrationStrength = 0.22f,
      chromaticAberrationMode = ChromaticAberrationMode.Full,
    ),
  )
  GlassLabPresetId.Custom -> error("Custom style must come from GlassLabState")
}

@Immutable
internal data class GlassLabState(
  val preset: GlassLabPresetId = GlassLabPresetId.Adaptive,
  val backdrop: GlassGalleryBackdropId = GlassGalleryBackdropId.Gallery,
  val advancedExpanded: Boolean = false,
  val style: GlassStyle = glassLabPresetStyle(GlassLabPresetId.Adaptive),
) {
  fun selectPreset(id: GlassLabPresetId): GlassLabState {
    require(id != GlassLabPresetId.Custom)
    return copy(preset = id, style = glassLabPresetStyle(id))
  }

  fun editStyle(transform: (GlassStyle) -> GlassStyle): GlassLabState =
    copy(preset = GlassLabPresetId.Custom, style = transform(style))

  fun reset(): GlassLabState = GlassLabState()
}
