// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.ExperimentalHazeApi::class, dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.glass

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isCloseTo
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isInstanceOf
import assertk.assertions.isLessThan
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isNull
import assertk.assertions.isTrue
import dev.chrisbanes.haze.HazeEffectFactory
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeProgressive
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.TrimMemoryLevel
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.runScreenshotTest
import org.junit.Test
import org.robolectric.annotation.Config

@Config(sdk = [31, 32])
class FallbackGlassBlurAndroidHostTest : ScreenshotTest() {
  @Test
  fun hardwareFallback_blursStripesAtTheirTruePosition() = runScreenshotTest(size = Size(400f, 300f)) {
    val state = HazeState()
    val blurred = GlassRuntimeEffect()
    val sharp = GlassRuntimeEffect()
    var hardware = false
    setContent {
      Box(Modifier.pxSize(400, 300).testTag("root").background(Color.White)) {
        Box(Modifier.pxSize(400, 300).hazeSource(state).background(Color.White)) {
          for (x in 0 until 400 step 20) {
            Box(Modifier.offset { IntOffset(x, 0) }.pxSize(10, 300).background(Color.Black))
          }
        }
        listOf(blurred to 40, sharp to 160).forEach { (effect, y) ->
          val radius = if (effect === blurred) 4.dp else 0.dp
          Box(
            Modifier.offset { IntOffset(100, y) }.pxSize(200, 100)
              .drawBehind { hardware = hardware || drawContext.canvas.nativeCanvas.isHardwareAccelerated }
              .hazeGlass(
                factory = HazeEffectFactory { effect },
                input = HazeInput.Sources(state),
                style = GlassStyle {
                  shape(RoundedCornerShape(0.dp))
                  optics(GlassOptics(blurRadius = OpticalSizeValue.Fixed(radius), depth = OpticalSizeValue.Fixed(1f)))
                  specularIntensity(0f)
                  edgeSoftness(0.dp)
                  edgeShadow(Color.Transparent)
                  ambientResponse(0f)
                  contrast(0f)
                  whitePoint(0f)
                  chromaMultiplier(1f)
                },
                performanceMode = null,
                expandLayerBounds = true,
                interactionSource = null,
              ),
          )
        }
      }
    }
    val pixels = captureRootPixels()
    assertThat(hardware).isTrue()
    assertThat(blurred.delegate).isInstanceOf<FallbackGlassDelegate>()
    assertThat((blurred.delegate as FallbackGlassDelegate).filteredInputDrawnForTest).isTrue()
    val blurContrast = pixels[115, 90].red - pixels[105, 90].red
    val sharpContrast = pixels[115, 210].red - pixels[105, 210].red
    assertThat(sharpContrast).isGreaterThan(0.5f)
    assertThat(blurContrast).isGreaterThan(0f)
    assertThat(blurContrast).isLessThan(sharpContrast * 0.8f)
    assertThat(pixels[85, 90]).isEqualTo(pixels[85, 210])
    assertThat(pixels[105, 210].red).isLessThan(pixels[115, 210].red)
  }

  @Test
  fun hardwareFallback_depthAndProgressiveMixRetainSharpInput() = runScreenshotTest(size = Size(400f, 520f)) {
    val state = HazeState()
    setContent {
      Box(Modifier.pxSize(400, 520).testTag("root").background(Color.White)) {
        Box(Modifier.pxSize(400, 520).hazeSource(state).background(Color.White)) {
          for (x in 0 until 400 step 20) {
            Box(Modifier.offset { IntOffset(x, 0) }.pxSize(10, 520).background(Color.Black))
          }
        }
        listOf(0f, 0.5f, 1f, 1f).forEachIndexed { index, depth ->
          val progressive = if (index == 3) HazeProgressive.verticalGradient() else null
          Box(
            Modifier.offset { IntOffset(100, 10 + index * 125) }.pxSize(200, 110)
              .hazeGlass(
                input = HazeInput.Sources(state),
                style = GlassStyle {
                  shape(RoundedCornerShape(0.dp))
                  optics(GlassOptics(blurRadius = OpticalSizeValue.Fixed(4.dp), depth = OpticalSizeValue.Fixed(depth), progressive = progressive))
                  specularIntensity(0f)
                  edgeSoftness(0.dp)
                  edgeShadow(Color.Transparent)
                  ambientResponse(0f)
                  contrast(0f)
                  whitePoint(0f)
                  chromaMultiplier(1f)
                },
                performanceMode = null,
                expandLayerBounds = true,
                interactionSource = null,
              ),
          )
        }
      }
    }
    val pixels = captureRootPixels()
    fun contrast(y: Int) = pixels[115, y].red - pixels[105, y].red
    val sharp = contrast(65)
    val half = contrast(190)
    val full = contrast(315)
    assertThat(sharp).isGreaterThan(0.5f)
    assertThat(half).isLessThan(sharp)
    assertThat(half).isGreaterThan(full)
    assertThat(contrast(395)).isGreaterThan(contrast(485))
    assertThat(contrast(440)).isLessThan(contrast(395))
    assertThat(contrast(440)).isGreaterThan(contrast(485))
  }

  @Test
  fun hardwareFallback_toneChangesCapturedPixels() = runScreenshotTest(size = Size(400f, 650f)) {
    val state = HazeState()
    setContent {
      Box(Modifier.pxSize(400, 650).testTag("root").background(Color.White)) {
        Box(Modifier.pxSize(400, 650).hazeSource(state).background(Color.DarkGray))
        listOf(0f, 0.4f, -0.4f, 0f, 0.4f).forEachIndexed { index, white ->
          Box(
            Modifier.offset { IntOffset(100, 10 + index * 125) }.pxSize(200, 110)
              .hazeGlass(
                input = HazeInput.Sources(state),
                style = GlassStyle {
                  shape(RoundedCornerShape(0.dp))
                  optics(GlassOptics(blurRadius = OpticalSizeValue.Fixed(if (index == 4) 0.dp else 4.dp), depth = OpticalSizeValue.Fixed(if (index == 4) 0f else 1f)))
                  specularIntensity(0f)
                  edgeSoftness(0.dp)
                  edgeShadow(Color.Transparent)
                  ambientResponse(0f)
                  contrast(if (index == 3) 0.5f else 0f)
                  whitePoint(white)
                  chromaMultiplier(1f)
                },
                performanceMode = null,
                expandLayerBounds = true,
                interactionSource = null,
              ),
          )
        }
      }
    }
    val pixels = captureRootPixels()
    val neutral = pixels[150, 65].red
    assertThat(pixels[150, 190].red).isGreaterThan(neutral)
    assertThat(pixels[150, 315].red).isLessThan(neutral)
    assertThat(pixels[150, 440].red).isLessThan(neutral)
    assertThat(pixels[150, 565].red).isCloseTo(pixels[150, 190].red, 2f / 255f)
  }

  @Test
  fun hardwareFallback_mergedOverlapMatchesSingleFilteredSurface() = runScreenshotTest(size = Size(400f, 400f)) {
    val state = HazeState()
    val effects = List(2) { GlassRuntimeEffect() }
    val style = GlassStyle {
      shape(RoundedCornerShape(0.dp))
      optics(GlassOptics(blurRadius = OpticalSizeValue.Fixed(4.dp), depth = OpticalSizeValue.Fixed(1f)))
      specularIntensity(0f)
      edgeSoftness(0.dp)
      edgeShadow(Color.Transparent)
      ambientResponse(0f)
      contrast(0f)
      whitePoint(0f)
      chromaMultiplier(1f)
    }
    setContent {
      Box(Modifier.pxSize(400, 400).testTag("root").background(Color.White)) {
        Box(Modifier.pxSize(400, 400).hazeSource(state).background(Color.White)) {
          for (x in 0 until 400 step 20) {
            Box(Modifier.offset { IntOffset(x, 0) }.pxSize(10, 400).background(Color.Black))
          }
        }
        Box(Modifier.pxSize(400, 400).hazeGlassContainer(20.dp)) {
          listOf(90, 170).forEachIndexed { index, x ->
            Box(
              Modifier.offset { IntOffset(x, 40) }.pxSize(100, 100).hazeGlass(
                factory = HazeEffectFactory { effects[index] },
                input = HazeInput.Sources(state),
                style = style,
                performanceMode = null,
                expandLayerBounds = true,
                interactionSource = null,
              ),
            )
          }
        }
        Box(
          Modifier.offset { IntOffset(90, 220) }.pxSize(180, 100).hazeGlass(
            input = HazeInput.Sources(state),
            style = style,
            performanceMode = null,
            expandLayerBounds = true,
            interactionSource = null,
          ),
        )
      }
    }
    val pixels = captureRootPixels()
    val owner = checkNotNull(effects.first().memberBindingForTest?.node?.container)
    assertThat(owner.renderer.runtimeSurfaces.size).isEqualTo(1)
    val delegate = owner.renderer.runtimeSurfaces.single().delegate as FallbackGlassDelegate
    for (x in listOf(155, 175, 185, 205)) {
      assertThat(pixels[x, 90].red, "overlap at $x").isCloseTo(pixels[x, 270].red, 3f / 255f)
    }
    assertThat(delegate.filteredInputRecordCountForTest).isGreaterThan(0)
    val before = delegate.filteredInputRecordCountForTest
    checkNotNull(owner.renderer.runtimeSurfaces.single().attachedContextForTest).invalidateDraw()
    captureRootPixels()
    assertThat(delegate.filteredInputRecordCountForTest).isEqualTo(before + 1)
    assertThat(pixels[75, 90]).isEqualTo(pixels[75, 270])
  }

  @Test
  fun hardwareFallback_inputChangeRefreshesEffectAtFixedGeometry() = runScreenshotTest(size = Size(300f, 200f)) {
    val state = HazeState()
    val color = mutableStateOf(Color.Red)
    val radius = mutableStateOf(4.dp)
    val effect = GlassRuntimeEffect()
    setContent {
      Box(Modifier.pxSize(300, 200).testTag("root").background(Color.White)) {
        Box(Modifier.pxSize(300, 200).hazeSource(state).background(color.value))
        Box(
          Modifier.offset { IntOffset(50, 40) }.pxSize(200, 120).hazeGlass(
            factory = HazeEffectFactory { effect },
            input = HazeInput.Sources(state),
            style = GlassStyle {
              shape(RoundedCornerShape(0.dp))
              optics(GlassOptics(blurRadius = OpticalSizeValue.Fixed(radius.value), depth = OpticalSizeValue.Fixed(1f)))
              specularIntensity(0f)
              edgeSoftness(0.dp)
              edgeShadow(Color.Transparent)
              ambientResponse(0f)
              contrast(0f)
              whitePoint(0f)
              chromaMultiplier(1f)
            },
            performanceMode = null,
            expandLayerBounds = true,
            interactionSource = null,
          ),
        )
      }
    }
    val first = captureRootPixels()[150, 100]
    val delegate = effect.delegate as FallbackGlassDelegate
    val firstEffect = checkNotNull(delegate.filteredInputEffectForTest)
    color.value = Color.Blue
    val second = captureRootPixels()[150, 100]
    assertThat(first.red).isGreaterThan(first.blue)
    assertThat(second.blue).isGreaterThan(second.red)
    if (Build.VERSION.SDK_INT == 31) {
      assertThat(checkNotNull(delegate.filteredInputEffectForTest)).isNotSameInstanceAs(firstEffect)
    }
    val oldLayer = checkNotNull(delegate.filteredInputLayerForTest)
    assertThat(oldLayer.isReleased).isFalse()
    val retainedInput = delegate.javaClass.getDeclaredField("filteredInput").run {
      isAccessible = true
      get(delegate) as FallbackGlassInput
    }
    val records = delegate.filteredInputRecordCountForTest
    assertThat(retainedInput.prepareForCapture(IntSize(3000, 3000), progressive = false, groupSize = null)).isFalse()
    assertThat(oldLayer.isReleased).isTrue()
    assertThat(delegate.filteredInputLayerForTest).isNull()
    assertThat(delegate.filteredInputRecordCountForTest).isEqualTo(records)
    color.value = Color.Red
    captureRootPixels()
    val restoredLayer = checkNotNull(delegate.filteredInputLayerForTest)
    radius.value = 0.dp
    captureRootPixels()
    assertThat(restoredLayer.isReleased).isTrue()
    radius.value = 4.dp
    captureRootPixels()
    val nextLayer = checkNotNull(delegate.filteredInputLayerForTest)
    effect.onTrimMemory(TrimMemoryLevel.MODERATE)
    assertThat(nextLayer.isReleased).isTrue()
  }

  @Test
  fun hardwareFallback_fractionalAlphaCompositesCapturedInputOnce() = runScreenshotTest(size = Size(400f, 400f)) {
    val state = HazeState()
    setContent {
      Box(Modifier.pxSize(400, 400).testTag("root").background(Color.Green)) {
        Box(Modifier.pxSize(400, 400).hazeSource(state).background(Color.Red.copy(alpha = 0.5f)))
        Box(Modifier.pxSize(400, 400).background(Color.Green))
        listOf(0f, 0.5f, 1f).forEachIndexed { index, opacity ->
          Box(
            Modifier.offset { IntOffset(100, 10 + index * 125) }.pxSize(200, 110).hazeGlass(
              input = HazeInput.Sources(state),
              style = GlassStyle {
                shape(RoundedCornerShape(0.dp))
                optics(GlassOptics(blurRadius = OpticalSizeValue.Fixed(4.dp), depth = OpticalSizeValue.Fixed(1f)))
                alpha(opacity)
                specularIntensity(0f)
                edgeSoftness(0.dp)
                edgeShadow(Color.Transparent)
                ambientResponse(0f)
                contrast(0f)
                whitePoint(0f)
                chromaMultiplier(1f)
              },
              performanceMode = null,
              expandLayerBounds = true,
              interactionSource = null,
            ),
          )
        }
      }
    }
    val pixels = captureRootPixels()
    val none = pixels[150, 65]
    val half = pixels[150, 190]
    val full = pixels[150, 315]
    assertThat(none).isEqualTo(Color.Green)
    assertThat(full.red).isCloseTo(0.5f, 2f / 255f)
    assertThat(half.red).isCloseTo(0.25f, 2f / 255f)
    assertThat(half.green).isCloseTo(0.75f, 2f / 255f)
  }

  @Test
  fun hardwareFallback_saturationChangesColourWithoutChangingAlpha() = runScreenshotTest(size = Size(400f, 275f)) {
    val state = HazeState()
    setContent {
      Box(Modifier.pxSize(400, 275).testTag("root").background(Color.White)) {
        Box(Modifier.pxSize(400, 275).hazeSource(state).background(Color(0.8f, 0.3f, 0.1f)))
        listOf(0f, 1f).forEachIndexed { index, chroma ->
          Box(
            Modifier.offset { IntOffset(100, 10 + index * 125) }.pxSize(200, 110).hazeGlass(
              input = HazeInput.Sources(state),
              style = GlassStyle {
                shape(RoundedCornerShape(0.dp))
                optics(GlassOptics(blurRadius = OpticalSizeValue.Fixed(4.dp), depth = OpticalSizeValue.Fixed(1f)))
                specularIntensity(0f)
                edgeSoftness(0.dp)
                edgeShadow(Color.Transparent)
                ambientResponse(0f)
                contrast(0f)
                whitePoint(0f)
                chromaMultiplier(chroma)
              },
              performanceMode = null,
              expandLayerBounds = true,
              interactionSource = null,
            ),
          )
        }
      }
    }
    val pixels = captureRootPixels()
    val grey = pixels[150, 65]
    val colour = pixels[150, 190]
    assertThat(grey.red).isCloseTo(grey.green, 2f / 255f)
    assertThat(grey.green).isCloseTo(grey.blue, 2f / 255f)
    assertThat(colour.red).isGreaterThan(colour.green)
    assertThat(colour.green).isGreaterThan(colour.blue)
    assertThat(grey.alpha).isEqualTo(colour.alpha)
  }

  @Test
  fun hardwareFallback_progressiveToneAppliesAfterSharpAndBlurMix() = runScreenshotTest(size = Size(400f, 275f)) {
    val state = HazeState()
    setContent {
      Box(Modifier.pxSize(400, 275).testTag("root").background(Color.White)) {
        Box(Modifier.pxSize(400, 275).hazeSource(state).background(Color.White)) {
          for (x in 0 until 400 step 20) {
            Box(Modifier.offset { IntOffset(x, 0) }.pxSize(10, 275).background(Color.Black))
          }
        }
        listOf(0f, 1f).forEachIndexed { index, tone ->
          Box(
            Modifier.offset { IntOffset(100, 10 + index * 125) }.pxSize(200, 110).hazeGlass(
              input = HazeInput.Sources(state),
              style = GlassStyle {
                shape(RoundedCornerShape(0.dp))
                optics(
                  GlassOptics(
                    blurRadius = OpticalSizeValue.Fixed(4.dp),
                    depth = OpticalSizeValue.Fixed(1f),
                    progressive = HazeProgressive.verticalGradient(startIntensity = 0.5f, endIntensity = 0.5f),
                  ),
                )
                specularIntensity(0f)
                edgeSoftness(0.dp)
                edgeShadow(Color.Transparent)
                ambientResponse(0f)
                contrast(tone)
                whitePoint(0f)
                chromaMultiplier(1f)
              },
              performanceMode = null,
              expandLayerBounds = true,
              interactionSource = null,
            ),
          )
        }
      }
    }
    val pixels = captureRootPixels()
    for (x in listOf(105, 115)) {
      val mixed = pixels[x, 65].red
      val expected = ((mixed - 0.5f) * 2f + 0.5f).coerceIn(0f, 1f)
      assertThat(pixels[x, 190].red).isCloseTo(expected, 3f / 255f)
    }
  }

  @Composable
  private fun Modifier.pxSize(width: Int, height: Int): Modifier = with(LocalDensity.current) {
    size(width.toDp(), height.toDp())
  }
}
