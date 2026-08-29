// Copyright 2025, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalHazeApi::class, InternalHazeApi::class)

package dev.chrisbanes.haze

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isGreaterThan
import assertk.assertions.isLessThanOrEqualTo
import dev.chrisbanes.haze.glass.ChromaticAberrationMode
import dev.chrisbanes.haze.glass.GlassOptics
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.SizeValue
import dev.chrisbanes.haze.glass.SurfaceProfile
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotTheme
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.BeforeTest
import kotlin.test.Test

class GlassScreenshotTest : ScreenshotTest() {

  @BeforeTest
  fun before() {
    HazeLogger.enabled = true
  }

  @Test
  fun creditCard() = runScreenshotTest {
    val style = GlassStyle { tint(DefaultTint) }

    setContent {
      ScreenshotTheme {
        CreditCardGlassSample(style = style)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_noTint() = runScreenshotTest {
    val style = GlassStyle { tint(Color.Transparent) }

    setContent {
      ScreenshotTheme {
        CreditCardGlassSample(style = style)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_multiple() = runScreenshotTest {
    val style = GlassStyle {
      tint(DefaultTint)
      optics(refractionStrength = 0.45f)
    }
    val styles = List(3) { style }

    setContent {
      ScreenshotTheme {
        CreditCardGlassSample(
          style = styles.first(),
          styles = styles,
          numberCards = 3,
        )
      }
    }
    // Stacked runtime shaders amplify Skia's platform-specific pixel variance.
    captureRoot(unmatchedPixelThreshold = 0.016f)
  }

  @Test
  fun creditCard_style() = runScreenshotTest {
    setContent {
      ScreenshotTheme {
        CreditCardGlassSample(style = VibrantStyle)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_alpha() = runScreenshotTest {
    var style by mutableStateOf(
      GlassStyle {
        tint(DefaultTint)
        alpha(0.85f)
      },
    )

    setContent {
      ScreenshotTheme {
        CreditCardGlassSample(style = style)
      }
    }

    captureRoot()
    val initialPixels = captureRootPixels().snapshot()

    style = style.then { alpha(0.45f) }
    waitForIdle()
    captureRoot("45")

    style = style.then { alpha(0.15f) }
    waitForIdle()
    val lowAlphaPixels = captureRootPixels().snapshot()
    captureRoot("15")

    assertThat(
      initialPixels.changedPixelRatio(lowAlphaPixels),
      "changing alpha affected pixel ratio",
    ).isGreaterThan(0.01f)
  }

  @Test
  fun creditCard_edgeSoftness() = runScreenshotTest {
    var style by mutableStateOf(
      GlassStyle {
        tint(DefaultTint)
        edgeSoftness(0.dp)
      },
    )

    setContent {
      ScreenshotTheme {
        CreditCardGlassSample(style = style)
      }
    }

    captureRoot("sharp")

    style = style.then { edgeSoftness(18.dp) }
    waitForIdle()
    captureRoot("soft", unmatchedPixelThreshold = 0.01f)
  }

  @Test
  fun creditCard_refraction_depth() = runScreenshotTest {
    var style by mutableStateOf(
      GlassStyle {
        tint(DefaultTint)
        optics(refractionStrength = 0.25f, depth = 0.15f)
        specularIntensity(0.35f)
      },
    )

    setContent {
      ScreenshotTheme {
        CreditCardGlassSample(style = style)
      }
    }

    captureRoot("low")

    style = style.then {
      optics(refractionStrength = 0.6f, depth = 0.5f)
      specularIntensity(0.7f)
    }
    waitForIdle()
    captureRoot("high")
  }

  @Test
  fun creditCard_blurRadius() = runScreenshotTest {
    val shape = RoundedCornerShape(28.dp)
    val style = GlassStyle {
      tint(Color.White.copy(alpha = 0.08f))
      optics(refractionStrength = 0f, depth = 1f, blurRadius = 32.dp)
      specularIntensity(0f)
      ambientResponse(0f)
      edgeSoftness(0.dp)
      this.shape(shape)
    }

    setContent {
      ScreenshotTheme {
        GlassBlurRadiusSample(
          style = style,
          shape = shape,
        )
      }
    }

    captureRoot()
  }

  @Test
  fun creditCard_blurRadiusCapIsStableAcrossDensities() = runScreenshotTest {
    // Android <33 uses the fallback delegate, which intentionally has no semantic blur.
    if (!isRuntimeShaderRenderEffectSupported()) return@runScreenshotTest

    val shape = RoundedCornerShape(0.dp)
    var style by mutableStateOf(
      GlassStyle {
        tint(Color.White.copy(alpha = 0.08f))
        optics(
          GlassOptics(
            refractionStrength = 0f,
            depth = SizeValue.Fixed(1f),
            blurRadius = SizeValue.Fixed(0.dp),
          ),
        )
        specularIntensity(0f)
        ambientResponse(0f)
        edgeSoftness(0.dp)
        this.shape(shape)
      },
    )
    var density by mutableStateOf(Density(1f))

    setContent {
      CompositionLocalProvider(LocalDensity provides density) {
        ScreenshotTheme {
          GlassBlurRadiusSample(
            style = style,
            shape = shape,
            cardWidth = (520f / density.density).dp,
            cardHeight = (320f / density.density).dp,
            patternScale = 1f / density.density,
          )
        }
      }
    }

    val zeroBlurPixels = captureRootPixels().snapshot()

    style = style.then {
      optics(refractionStrength = 0f, depth = 1f, blurRadius = 100.dp)
    }
    waitForIdle()
    val densityOnePixels = captureRootPixels().snapshot()

    assertThat(
      zeroBlurPixels.changedPixelRatio(densityOnePixels),
      "capped blur changed pixel ratio",
    ).isGreaterThan(0.01f)

    density = Density(3f)
    waitForIdle()
    val densityThreePixels = captureRootPixels().snapshot()

    assertThat(
      densityOnePixels.changedPixelRatio(densityThreePixels),
      "above-cap blur changed pixel ratio across densities",
    ).isLessThanOrEqualTo(0.001f)
  }

  @Test
  fun creditCard_fallbackTintAndEdge() = runScreenshotTest {
    val shape = RoundedCornerShape(28.dp)
    val style = GlassStyle {
      tint(Color.White.copy(alpha = 0.18f))
      edgeSoftness(12.dp)
      specularIntensity(0.4f)
      ambientResponse(0.5f)
      this.shape(shape)
    }

    setContent {
      ScreenshotTheme {
        GlassBlurRadiusSample(
          style = style,
          shape = shape,
          clipShape = false,
        )
      }
    }

    captureRoot()
  }

  @Test
  fun creditCard_blurRadiusWithRefraction() = runScreenshotTest {
    val shape = RoundedCornerShape(28.dp)
    var style by mutableStateOf(
      GlassStyle {
        tint(Color.White.copy(alpha = 0.08f))
        optics(refractionStrength = 0.85f, depth = 0.9f, blurRadius = 0.dp)
        specularIntensity(0f)
        ambientResponse(0f)
        edgeSoftness(0.dp)
        this.shape(shape)
      },
    )

    setContent {
      ScreenshotTheme {
        GlassBlurRadiusSample(
          style = style,
          shape = shape,
        )
      }
    }

    captureRoot("zero")

    style = style.then {
      optics(refractionStrength = 0.85f, depth = 0.9f, blurRadius = 32.dp)
    }
    waitForIdle()
    captureRoot("strong")
  }

  @Test
  fun creditCard_lightPosition() = runScreenshotTest {
    var style by mutableStateOf(
      GlassStyle {
        tint(DefaultTint)
        lightPosition(Alignment.Center)
        specularIntensity(0.55f)
      },
    )

    setContent {
      ScreenshotTheme {
        CreditCardGlassSample(style = style)
      }
    }

    captureRoot("center")

    style = style.then { lightPosition(exactLightAlignment(-120, -80)) }
    waitForIdle()
    captureRoot("topLeft")

    style = style.then { lightPosition(exactLightAlignment(140, 120)) }
    waitForIdle()
    captureRoot("bottomRight")
  }

  @Test
  fun creditCard_movedCaptureMatchesFreshReconstruction() = runScreenshotTest {
    // Android <33 uses the fallback delegate, which intentionally has no semantic refraction.
    if (!isRuntimeShaderRenderEffectSupported()) return@runScreenshotTest

    val shape = RoundedCornerShape(0.dp)
    var effectOffset by mutableStateOf(IntOffset.Zero)
    var effectGeneration by mutableIntStateOf(0)

    setContent {
      ScreenshotTheme {
        key(effectGeneration) {
          val style = remember {
            GlassStyle {
              tint(Color.Transparent)
              optics(
                GlassOptics(
                  refractionStrength = 0.6f,
                  depth = SizeValue.Fixed(0.5f),
                  blurRadius = SizeValue.Fixed(0.dp),
                ),
              )
              specularIntensity(0f)
              ambientResponse(0f)
              edgeSoftness(0.dp)
              this.shape(shape)
            }
          }
          GlassBlurRadiusSample(
            style = style,
            shape = shape,
            effectOffset = effectOffset,
            cardWidth = 320.dp,
            cardHeight = 240.dp,
          )
        }
      }
    }

    val initialPixels = captureRootPixels().snapshot()
    effectOffset = IntOffset(140, 80)
    waitForIdle()
    val movedPixels = captureRootPixels().snapshot()

    effectGeneration++
    waitForIdle()
    val reconstructedPixels = captureRootPixels().snapshot()

    assertThat(
      initialPixels.changedPixelRatio(movedPixels),
      "moving the effect changed captured pixels",
    ).isGreaterThan(0.01f)
    assertThat(
      movedPixels.meanAbsoluteDifference(reconstructedPixels),
      "moved retained capture matches fresh reconstruction",
    ).isLessThanOrEqualTo(0.001f)
  }

  @Test
  fun creditCard_conditional_enabled() = runScreenshotTest {
    val style = GlassStyle { tint(DefaultTint) }
    var enabled by mutableStateOf(true)

    setContent {
      ScreenshotTheme {
        CreditCardGlassSample(style = style, enabled = enabled)
      }
    }

    captureRoot("enabled")

    enabled = false
    waitForIdle()
    captureRoot("disabled")

    enabled = true
    waitForIdle()
    captureRoot("re_enabled")
  }

  @Test
  fun creditCard_shape_refractionHeight() = runScreenshotTest {
    var style by mutableStateOf(
      GlassStyle {
        tint(DefaultTint)
        optics(refractionHeightFraction = 0.32f, depth = 0.45f)
        specularIntensity(0.6f)
        shape(RoundedCornerShape(24.dp))
      },
    )

    setContent {
      ScreenshotTheme {
        CreditCardGlassSample(style = style, shape = RoundedCornerShape(24.dp))
      }
    }

    captureRoot("rounded")

    style = style.then {
      optics(refractionHeightFraction = 0.18f, depth = 0.45f)
    }
    waitForIdle()
    captureRoot("shallow")
  }

  @Test
  fun creditCard_chromaticAberration() = runScreenshotTest {
    var style by mutableStateOf(
      GlassStyle {
        tint(DefaultTint)
        optics(refractionStrength = 0.8f, depth = 0.4f)
        chromaticAberrationStrength(0.0f)
        edgeSoftness(14.dp)
        shape(RoundedCornerShape(20.dp))
      },
    )

    setContent {
      ScreenshotTheme {
        CreditCardGlassSample(style = style, shape = RoundedCornerShape(20.dp))
      }
    }

    captureRoot("off")

    style = style.then { chromaticAberrationStrength(0.24f) }
    waitForIdle()
    captureRoot("on")
  }

  @Test
  fun creditCard_surfaceProfile() = runScreenshotTest {
    var style by mutableStateOf(
      GlassStyle {
        tint(DefaultTint)
        optics(refractionHeightFraction = 0.28f, depth = 0.4f)
        specularIntensity(0.5f)
        shape(RoundedCornerShape(24.dp))
        surfaceProfile(SurfaceProfile.Squircle)
      },
    )

    setContent {
      ScreenshotTheme {
        CreditCardGlassSample(style = style, shape = RoundedCornerShape(24.dp))
      }
    }

    captureRoot("squircle")

    style = style.then { surfaceProfile(SurfaceProfile.Concave) }
    waitForIdle()
    captureRoot("concave")

    style = style.then { surfaceProfile(SurfaceProfile.Lip) }
    waitForIdle()
    captureRoot("lip")
  }

  @Test
  fun creditCard_chromaticAberrationMode() = runScreenshotTest {
    var style by mutableStateOf(
      GlassStyle {
        tint(DefaultTint)
        optics(refractionStrength = 0.8f, depth = 0.45f)
        chromaticAberrationStrength(0.3f)
        edgeSoftness(14.dp)
        shape(RoundedCornerShape(20.dp))
        chromaticAberrationMode(ChromaticAberrationMode.Simple)
      },
    )

    setContent {
      ScreenshotTheme {
        CreditCardGlassSample(style = style, shape = RoundedCornerShape(20.dp))
      }
    }

    captureRoot("simple")

    style = style.then { chromaticAberrationMode(ChromaticAberrationMode.Full) }
    waitForIdle()
    captureRoot("full")
  }

  companion object {
    val DefaultTint = Color.White.copy(alpha = 0.1f)

    val VibrantStyle = GlassStyle {
      tint(Color(0xFF3F8CFF).copy(alpha = 0.35f))
      optics(
        GlassOptics(
          refractionStrength = 0.55f,
          depth = SizeValue.Fixed(0.4f),
        ),
      )
      specularIntensity(0.75f)
      ambientResponse(0.8f)
      lightPosition(exactLightAlignment(64, -48))
      edgeSoftness(14.dp)
    }
  }
}

@Composable
internal fun GlassBlurRadiusSample(
  style: GlassStyle,
  shape: RoundedCornerShape,
  clipShape: Boolean = true,
  cardWidth: Dp = 520.dp,
  cardHeight: Dp = 320.dp,
  effectOffset: IntOffset = IntOffset.Zero,
  patternScale: Float = 1f,
) {
  val hazeState = remember { HazeState() }

  Box(Modifier.fillMaxSize()) {
    Canvas(
      modifier = Modifier
        .fillMaxSize()
        .hazeSource(state = hazeState, zIndex = 0f),
    ) {
      drawRect(Color(0xFF101820))

      val stripeWidth = 18.dp.toPx() * patternScale
      var x = 0f
      var stripeIndex = 0
      while (x < size.width) {
        drawRect(
          color = StripeColors[stripeIndex % StripeColors.size],
          topLeft = Offset(x, 0f),
          size = Size(stripeWidth, size.height),
        )
        x += stripeWidth
        stripeIndex++
      }

      val lineSpacing = 34.dp.toPx() * patternScale
      val strokeWidth = 5.dp.toPx() * patternScale
      var y = 0f
      while (y < size.height) {
        drawLine(
          color = GridLineColor,
          start = Offset(0f, y),
          end = Offset(size.width, y),
          strokeWidth = strokeWidth,
        )
        y += lineSpacing
      }
    }

    Box(
      modifier = Modifier
        .align(Alignment.Center)
        .offset { effectOffset }
        .size(width = cardWidth, height = cardHeight)
        .then(if (clipShape) Modifier.clip(shape) else Modifier)
        .hazeGlass(
          input = HazeInput.Sources(hazeState),
          style = style,
        ),
    )
  }
}

private val StripeColors = listOf(
  Color(0xFF061A40),
  Color(0xFFFFD166),
  Color(0xFF118AB2),
  Color(0xFFEF476F),
  Color(0xFF06D6A0),
  Color(0xFFFFFFFF),
)

private val GridLineColor = Color(0xFF111111).copy(alpha = 0.65f)
