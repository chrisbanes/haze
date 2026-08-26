// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class)

package dev.chrisbanes.haze.sample

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.glass.ChromaticAberrationMode
import dev.chrisbanes.haze.glass.GlassDefaults
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.SurfaceProfile

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

public enum class GlassGalleryBackdropId {
  Gallery,
  Grid,
  Typography,
  Bands,
  Uniform,
}

public enum class GlassLabStyleId {
  Regular,
  Clear,
  Custom,
}

internal val SelectableGlassLabStyles = GlassLabStyleId.entries - GlassLabStyleId.Custom

@Immutable
internal data class GlassLabStyleValues(
  val tint: Color = GlassDefaults.tint,
  val optics: GlassOptics = GlassDefaults.optics,
  val specularIntensity: Float = GlassDefaults.specularIntensity,
  val ambientResponse: Float = GlassDefaults.ambientResponse,
  val alpha: Float = GlassDefaults.alpha,
  val contrast: Float = GlassDefaults.contrast,
  val whitePoint: Float = GlassDefaults.whitePoint,
  val chromaMultiplier: Float = GlassDefaults.chromaMultiplier,
  val edgeSoftness: androidx.compose.ui.unit.Dp = GlassDefaults.edgeSoftness,
  val contentNormalBlend: Float = GlassDefaults.contentNormalBlend,
  val surfaceProfile: SurfaceProfile = GlassDefaults.surfaceProfile,
  val chromaticAberrationStrength: Float = GlassDefaults.chromaticAberrationStrength,
  val chromaticAberrationMode: ChromaticAberrationMode = GlassDefaults.chromaticAberrationMode,
) {
  fun toStyle(): GlassStyle = GlassStyle {
    tint(tint)
    optics(optics)
    specularIntensity(specularIntensity)
    ambientResponse(ambientResponse)
    alpha(alpha)
    contrast(contrast)
    whitePoint(whitePoint)
    chromaMultiplier(chromaMultiplier)
    edgeSoftness(edgeSoftness)
    contentNormalBlend(contentNormalBlend)
    surfaceProfile(surfaceProfile)
    chromaticAberrationStrength(chromaticAberrationStrength)
    chromaticAberrationMode(chromaticAberrationMode)
  }
}

internal fun glassLabStyleValues(id: GlassLabStyleId): GlassLabStyleValues = when (id) {
  GlassLabStyleId.Regular -> GlassLabStyleValues()
  GlassLabStyleId.Clear -> GlassLabStyleValues(
    optics = GlassOptics.Fixed(
      refractionStrength = 0.85f,
      refractionHeightFraction = 0.22f,
      refractionDisplacement = 18.dp,
      depth = 0.1f,
      blurRadius = 2.dp,
    ),
    specularIntensity = 0.55f,
    ambientResponse = 0.42f,
    contrast = 0.08f,
    whitePoint = 0.02f,
    chromaMultiplier = 1.05f,
    edgeSoftness = 1.dp,
    contentNormalBlend = 0.1f,
    chromaticAberrationStrength = 0.04f,
  )
  GlassLabStyleId.Custom -> error("Custom style must come from GlassLabState")
}

private fun GlassLabStyleId.builtInStyle(): GlassStyle = when (this) {
  GlassLabStyleId.Regular -> GlassStyle.regular
  GlassLabStyleId.Clear -> GlassStyle.clear
  GlassLabStyleId.Custom -> error("Custom style must come from GlassLabState")
}

internal enum class GlassLabInteractionMode {
  Off,
  Pressed,
  All,
  ;

  val includesFocusedResponse: Boolean
    get() = this == All

  val style: GlassStyle
    get() = when (this) {
      Off -> GlassStyle
      Pressed -> GlassStyle { pressed { defaultPressResponse() } }
      All -> GlassStyle {
        hovered { defaultHoverResponse() }
        if (includesFocusedResponse) focused { defaultHoverResponse() }
        pressed { defaultPressResponse() }
      }
    }
}

private fun dev.chrisbanes.haze.glass.GlassInteractionScope.defaultHoverResponse() {
  animate(DefaultGlassHoverAnimationSpec, DefaultGlassReleaseAnimationSpec) {
    lightingIntensity(0.35f)
    refractionMultiplier(1.02f)
    whitePointDelta(0.01f)
    scale(1f)
  }
}

private fun dev.chrisbanes.haze.glass.GlassInteractionScope.defaultPressResponse() {
  animate(DefaultGlassPressAnimationSpec, DefaultGlassReleaseAnimationSpec) {
    lightingIntensity(1f)
    refractionMultiplier(1.08f)
    whitePointDelta(0.04f)
    scale(0.98f)
  }
}

@Immutable
internal data class GlassLabState(
  val styleId: GlassLabStyleId = GlassLabStyleId.Regular,
  val backdrop: GlassGalleryBackdropId = GlassGalleryBackdropId.Gallery,
  val interaction: GlassLabInteractionMode = GlassLabInteractionMode.All,
  val advancedExpanded: Boolean = false,
  private val baseStyle: GlassStyle = styleId.builtInStyle(),
  val styleValues: GlassLabStyleValues = glassLabStyleValues(styleId),
) {
  val style: GlassStyle = baseStyle.then(styleValues.toStyle())

  fun selectStyle(id: GlassLabStyleId): GlassLabState {
    require(id != GlassLabStyleId.Custom)
    return copy(
      styleId = id,
      baseStyle = id.builtInStyle(),
      styleValues = glassLabStyleValues(id),
    )
  }

  fun editStyle(transform: (GlassLabStyleValues) -> GlassLabStyleValues): GlassLabState {
    val values = transform(styleValues)
    return copy(
      styleId = GlassLabStyleId.Custom,
      styleValues = values,
    )
  }

  fun reset(): GlassLabState = GlassLabState()
}
