// Copyright 2024, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.blur.BlurVisualEffect
import dev.chrisbanes.haze.blur.HazeBlurDefaults
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.LocalHazeBlurStyle
import dev.chrisbanes.haze.blur.blurEffect
import dev.chrisbanes.haze.test.ScreenshotTest
import dev.chrisbanes.haze.test.ScreenshotTheme
import dev.chrisbanes.haze.test.runScreenshotTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HazeScreenshotTest : ScreenshotTest() {

  @BeforeTest
  fun before() {
    HazeLogger.enabled = true
  }

  @Test
  fun creditCard() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      colorEffects = listOf(DefaultTint)
      blurRadius = 8.dp
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = blurVisualEffect)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_noStyle() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect()

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = blurVisualEffect)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_multiple() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      colorEffects = listOf(DefaultTint)
      blurRadius = 8.dp
    }
    val visualEffects = List(3) { BlurVisualEffect(blurVisualEffect) }

    setContent {
      ScreenshotTheme {
        CreditCardSample(
          visualEffect = visualEffects.first(),
          visualEffects = visualEffects,
          numberCards = 3,
        )
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_blurEnabled() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      colorEffects = listOf(DefaultTint)
      blurRadius = 8.dp
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = blurVisualEffect)
      }
    }

    waitForIdle()
    captureRoot("default")
    val defaultPixels = captureRootPixels().snapshot()

    blurVisualEffect.blurEnabled = false
    waitForIdle()
    captureRoot("disabled")
    val disabledPixels = captureRootPixels().snapshot()

    blurVisualEffect.blurEnabled = true
    waitForIdle()
    captureRoot("enabled")

    if (supportsRuntimeBlur) {
      val blurChangedPixelRatio = defaultPixels.changedPixelRatio(disabledPixels)
      assertTrue(
        blurChangedPixelRatio > 0.01f,
        "Expected disabling blur to affect more than 1% of pixels, got $blurChangedPixelRatio",
      )
    }
  }

  @Test
  fun creditCard_style() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      style = OverrideStyle
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = blurVisualEffect)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_compositionLocalStyle() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      blurRadius = 8.dp
    }

    setContent {
      ScreenshotTheme {
        CompositionLocalProvider(LocalHazeBlurStyle provides OverrideStyle) {
          CreditCardSample(visualEffect = blurVisualEffect)
        }
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_transparentTint() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      blurRadius = 8.dp
      colorEffects = listOf(HazeColorEffect.tint(Color.Transparent, HazeColorEffect.DefaultBlendMode))
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = blurVisualEffect)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_zeroBlurRadius() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      blurRadius = 0.dp
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = blurVisualEffect)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_mask() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      colorEffects = listOf(DefaultTint)
      blurRadius = 8.dp
      mask = VerticalMask
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = blurVisualEffect)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_alpha() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      colorEffects = listOf(DefaultTint)
      blurRadius = 8.dp
      alpha = 0.5f
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = blurVisualEffect)
      }
    }

    captureRoot()
    val initialPixels = captureRootPixels().snapshot()

    blurVisualEffect.alpha = 0.2f
    waitForIdle()
    captureRoot("20")
    val alpha20Pixels = captureRootPixels().snapshot()

    blurVisualEffect.alpha = 0.7f
    waitForIdle()
    captureRoot("70")
    val alpha70Pixels = captureRootPixels().snapshot()

    assertTrue(
      initialPixels.changedPixelRatio(alpha20Pixels) > 0.01f,
      "Expected changing alpha from the initial value to 0.2 to affect more than 1% of pixels",
    )
    assertTrue(
      alpha20Pixels.changedPixelRatio(alpha70Pixels) > 0.01f,
      "Expected changing alpha from 0.2 to 0.7 to affect more than 1% of pixels",
    )
    assertTrue(
      initialPixels.changedPixelRatio(alpha70Pixels) > 0.01f,
      "Expected changing alpha from the initial value to 0.7 to affect more than 1% of pixels",
    )
  }

  @Test
  fun creditCard_progressive_horiz() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      colorEffects = listOf(DefaultTint)
      blurRadius = 8.dp
      progressive = HazeProgressive.horizontalGradient()
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = blurVisualEffect)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_progressive_horiz_preferMask() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      colorEffects = listOf(DefaultTint)
      blurRadius = 8.dp
      progressive = HazeProgressive.horizontalGradient(preferPerformance = true)
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = blurVisualEffect)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_progressive_vertical() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      colorEffects = listOf(DefaultTint)
      blurRadius = 8.dp
      progressive = HazeProgressive.verticalGradient()
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = blurVisualEffect)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_progressive_vertical_multiple() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      colorEffects = listOf(DefaultTint)
      blurRadius = 8.dp
      progressive = HazeProgressive.verticalGradient()
    }
    val visualEffects = List(3) { BlurVisualEffect(blurVisualEffect) }

    setContent {
      ScreenshotTheme {
        CreditCardSample(
          visualEffect = visualEffects.first(),
          visualEffects = visualEffects,
          numberCards = 3,
        )
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_progressive_radial() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      colorEffects = listOf(DefaultTint)
      blurRadius = 8.dp
      progressive = HazeProgressive.RadialGradient()
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = blurVisualEffect)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_progressive_shader() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      colorEffects = listOf(DefaultTint)
      blurRadius = 8.dp
      progressive = HazeProgressive.Brush(
        Brush.sweepGradient(colors = listOf(Color.Transparent, Color.Black)),
      )
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = blurVisualEffect)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_childTint() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      colorEffects = listOf(
        HazeColorEffect.tint(
          Color.Magenta.copy(alpha = 0.5f),
          HazeColorEffect.DefaultBlendMode,
        ),
      )
      blurRadius = 8.dp
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = blurVisualEffect)
      }
    }

    waitForIdle()
    captureRoot("magenta")
    val magentaPixels = captureRootPixels().snapshot()

    blurVisualEffect.colorEffects = listOf(
      HazeColorEffect.tint(
        Color.Yellow.copy(alpha = 0.5f),
        HazeColorEffect.DefaultBlendMode,
      ),
    )
    waitForIdle()
    captureRoot("yellow")
    val yellowPixels = captureRootPixels().snapshot()

    blurVisualEffect.colorEffects = listOf(
      HazeColorEffect.tint(
        Color.Red.copy(alpha = 0.5f),
        HazeColorEffect.DefaultBlendMode,
      ),
    )
    waitForIdle()
    captureRoot("red")
    val redPixels = captureRootPixels().snapshot()

    assertTrue(
      magentaPixels.changedPixelRatio(yellowPixels) > 0.01f,
      "Expected changing child tint from magenta to yellow to affect more than 1% of pixels",
    )
    assertTrue(
      yellowPixels.changedPixelRatio(redPixels) > 0.01f,
      "Expected changing child tint from yellow to red to affect more than 1% of pixels",
    )
    assertTrue(
      magentaPixels.changedPixelRatio(redPixels) > 0.01f,
      "Expected changing child tint from magenta to red to affect more than 1% of pixels",
    )
  }

  @Test
  fun creditCard_roundedCorner_topStart() {
    roundedCornerTest(RoundedCornerShape(topStart = 32.dp))
  }

  @Test
  fun creditCard_roundedCorner_topEnd() {
    roundedCornerTest(RoundedCornerShape(topEnd = 32.dp))
  }

  @Test
  fun creditCard_roundedCorner_bottomEnd() {
    roundedCornerTest(RoundedCornerShape(bottomEnd = 32.dp))
  }

  @Test
  fun creditCard_roundedCorner_bottomStart() {
    roundedCornerTest(RoundedCornerShape(bottomStart = 32.dp))
  }

  @Test
  fun creditCard_conditional() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      colorEffects = listOf(DefaultTint)
      blurRadius = 8.dp
    }
    var enabled by mutableStateOf(true)

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = blurVisualEffect, enabled = enabled)
      }
    }

    waitForIdle()
    captureRoot("0_initial")

    enabled = false
    waitForIdle()
    captureRoot("1_disabled")

    enabled = true
    waitForIdle()
    captureRoot("2_reenabled")
  }

  private fun roundedCornerTest(roundedCornerShape: RoundedCornerShape) = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      colorEffects = listOf(DefaultTint)
      blurRadius = 8.dp
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = blurVisualEffect, shape = roundedCornerShape)
      }
    }
    captureRoot()
  }

  /**
   * This test does not currently produce the correct output on Skia platforms.
   * It works correctly when run on device, etc. It seems to be a timing setup thing in tests.
   *
   * My working theory is that state updates are ran immediately in the CMP UI tests, which
   * breaks how dependent graphics layers are invalidated. In non-tests, state updates are deferred
   * until the next 'pass'.
   *
   * This is being re-worked in CMP 1.8, so there's little point in investigating this too much:
   * https://youtrack.jetbrains.com/issue/CMP-6703
   */
  @Test
  fun creditCard_sourceContentChange() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      colorEffects = listOf(DefaultTint)
      blurRadius = 8.dp
    }
    var backgroundColors by mutableStateOf(listOf(Color.Blue, Color.Cyan))

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = blurVisualEffect, backgroundColors = backgroundColors)
      }
    }

    waitForIdle()
    captureRoot("blue")

    backgroundColors = listOf(Color.Yellow, Color.hsl(0.4f, 0.94f, 0.58f))
    waitForIdle()
    captureRoot("yellow")

    backgroundColors = listOf(Color.Red, Color.hsl(0.06f, 0.69f, 0.35f))
    waitForIdle()
    captureRoot("red")
  }

  @Test
  fun creditCard_brushTint() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      colorEffects = listOf(BrushTint)
      blurRadius = 8.dp
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = blurVisualEffect)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_brushTint_mask() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      colorEffects = listOf(BrushTint)
      blurRadius = 8.dp
      mask = VerticalMask
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = blurVisualEffect)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_brushTint_progressive() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      colorEffects = listOf(BrushTint)
      blurRadius = 8.dp
      progressive = HazeProgressive.verticalGradient()
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = blurVisualEffect)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_colorFilter_tint() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      colorEffects = listOf(
        HazeColorEffect.colorFilter(
          ColorFilter.tint(Color.Cyan, BlendMode.Modulate),
        ),
      )
      blurRadius = 8.dp
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = blurVisualEffect)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_colorFilter_colorMatrix() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      val saturation = 1.5f
      val invSat = 1f - saturation
      val lumR = 0.213f
      val lumG = 0.715f
      val lumB = 0.072f

      val colorMatrix = ColorMatrix(
        floatArrayOf(
          lumR * invSat + saturation, lumG * invSat, lumB * invSat, 0f, 0f,
          lumR * invSat, lumG * invSat + saturation, lumB * invSat, 0f, 0f,
          lumR * invSat, lumG * invSat, lumB * invSat + saturation, 0f, 0f,
          0f, 0f, 0f, 1f, 0f,
        ),
      )

      colorEffects = listOf(
        HazeColorEffect.colorFilter(
          ColorFilter.colorMatrix(colorMatrix),
        ),
      )
      blurRadius = 8.dp
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = blurVisualEffect)
      }
    }
    captureRoot()
  }

  @Test
  fun creditCard_colorFilter_lighting() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      colorEffects = listOf(
        HazeColorEffect.colorFilter(
          ColorFilter.lighting(
            multiply = Color(0xFF8080FF),
            add = Color(0x00000000),
          ),
        ),
      )
      blurRadius = 8.dp
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(visualEffect = blurVisualEffect)
      }
    }
    captureRoot()
  }

  @Test
  fun nested_content() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      colorEffects = listOf(DefaultTint)
      blurRadius = 8.dp
    }

    setContent {
      ScreenshotTheme {
        Box(Modifier.fillMaxSize()) {
          val outerHazeState = remember { HazeState() }

          Box(Modifier.hazeSource(outerHazeState)) {
            CreditCardSample(visualEffect = blurVisualEffect)
          }

          Box(
            modifier = Modifier
              .hazeEffect(state = outerHazeState) {
                blurEffect {
                  style = HazeBlurDefaults.style(
                    backgroundColor = Color.Blue,
                    tint = DefaultTint,
                    blurRadius = 8.dp,
                  )
                }
              }
              .align(Alignment.TopStart)
              .fillMaxWidth()
              .height(56.dp),
          )
        }
      }
    }
    captureRoot()
  }

  @Test
  fun horizontalPager_quarter() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      colorEffects = listOf(DefaultTint)
      blurRadius = 8.dp
    }
    val visualEffects = List(2) { BlurVisualEffect(blurVisualEffect) }

    setContent {
      ScreenshotTheme {
        CreditCardPagerSample(visualEffects = visualEffects, pagerPosition = .25f)
      }
    }
    captureRoot()
  }

  @Test
  fun horizontalPager_half() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      colorEffects = listOf(DefaultTint)
      blurRadius = 16.dp
    }
    val visualEffects = List(2) { BlurVisualEffect(blurVisualEffect) }

    setContent {
      ScreenshotTheme {
        CreditCardPagerSample(visualEffects = visualEffects, pagerPosition = .49f)
      }
    }
    captureRoot()
  }

  @Test
  fun horizontalPager_three_quarters() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      colorEffects = listOf(DefaultTint)
      blurRadius = 8.dp
    }
    val visualEffects = List(3) { BlurVisualEffect(blurVisualEffect) }

    setContent {
      ScreenshotTheme {
        CreditCardPagerSample(visualEffects = visualEffects, pagerPosition = .75f)
      }
    }
    captureRoot()
  }

  @Test
  fun horizontalPager_one_and_three_quarters() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      colorEffects = listOf(DefaultTint)
      blurRadius = 8.dp
    }
    val visualEffects = List(3) { BlurVisualEffect(blurVisualEffect) }

    setContent {
      ScreenshotTheme {
        CreditCardPagerSample(visualEffects = visualEffects, pagerPosition = 1.75f)
      }
    }
    captureRoot()
  }

  @Test
  fun horizontalPager_preservesStateAndEffectOwnershipAcrossPageChanges() = runScreenshotTest {
    val baseEffect = BlurVisualEffect().apply {
      colorEffects = listOf(DefaultTint)
      blurRadius = 8.dp
    }
    val threePageEffects = List(3) { BlurVisualEffect(baseEffect) }
    val twoPageEffects = List(2) { BlurVisualEffect(baseEffect) }
    var pageCount by mutableStateOf(3)
    var recompositionToken by mutableStateOf(0)
    var requestedPage by mutableStateOf(0)
    var pagerState: PagerState? = null
    val composedEffects = mutableMapOf<Int, VisualEffect>()

    setContent {
      ScreenshotTheme {
        val visualEffects = remember(pageCount) {
          if (pageCount == 2) twoPageEffects else threePageEffects
        }
        LaunchedEffect(requestedPage) {
          pagerState?.scrollToPage(requestedPage)
        }
        CreditCardPagerSample(
          visualEffects = visualEffects,
          pagerPosition = 0f,
          backgroundColors = if (recompositionToken == 0) {
            listOf(Color.Blue, Color.Cyan)
          } else {
            listOf(Color.Cyan, Color.Blue)
          },
          onPagerState = { pagerState = it },
          onPageComposed = { page, effect -> composedEffects[page] = effect },
        )
      }
    }

    waitForIdle()
    val initialPagerState = requireNotNull(pagerState)
    recompositionToken++
    waitForIdle()
    assertTrue(pagerState === initialPagerState)

    requestedPage = 2
    waitForIdle()
    assertEquals(2, initialPagerState.currentPage)
    assertTrue(composedEffects.keys.containsAll((0..2).toList()))
    assertTrue(composedEffects.all { (page, effect) -> effect === threePageEffects[page] })
    assertTrue(
      composedEffects[0] !== composedEffects[1] && composedEffects[1] !== composedEffects[2],
    )

    threePageEffects[0].blurRadius = 16.dp
    assertEquals(8.dp, threePageEffects[1].blurRadius)

    pageCount = 2
    waitForIdle()
    assertTrue(pagerState === initialPagerState)
    assertTrue(initialPagerState.currentPage < pageCount)

    pageCount = 3
    requestedPage = 0
    waitForIdle()
    assertTrue(pagerState === initialPagerState)
    assertTrue(composedEffects[0] === threePageEffects[0])
  }

  @Test
  fun layerTransformations() = runScreenshotTest {
    var offset by mutableStateOf(DpOffset.Zero)
    val blurVisualEffect = BlurVisualEffect()

    setContent {
      ScreenshotTheme {
        OverlayingContent(visualEffect = blurVisualEffect, topOffset = offset)
      }
    }

    captureRoot("center")

    offset = DpOffset(x = (-128).dp, y = 0.dp)
    captureRoot("left")

    offset = DpOffset(x = 0.dp, y = (-128).dp)
    captureRoot("top")

    offset = DpOffset(x = 128.dp, y = 0.dp)
    captureRoot("right")

    offset = DpOffset(x = 0.dp, y = 128.dp)
    captureRoot("bottom")
  }

  @Test
  fun creditCard_progressive_vertical_whiteBg() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      colorEffects = listOf(DefaultTint)
      blurRadius = 20.dp
      progressive = HazeProgressive.verticalGradient()
    }

    setContent {
      ScreenshotTheme {
        CreditCardSample(
          visualEffect = blurVisualEffect,
          backgroundColors = listOf(Color.White, Color.White),
        )
      }
    }
    captureRoot()
  }

  @Test
  fun edges() = runScreenshotTest {
    val blurVisualEffect = BlurVisualEffect().apply {
      colorEffects = listOf(DefaultTint)
      backgroundColor = Color.Transparent
    }

    setContent {
      ScreenshotTheme {
        ContentAtEdges(visualEffect = blurVisualEffect)
      }
    }
    captureRoot()
  }

  companion object {
    val DefaultTint = HazeColorEffect.tint(
      Color.White.copy(alpha = 0.1f),
      HazeColorEffect.DefaultBlendMode,
    )
    val OverrideStyle = HazeBlurStyle(
      colorEffects = listOf(
        HazeColorEffect.tint(
          Color.Red.copy(alpha = 0.5f),
          HazeColorEffect.DefaultBlendMode,
        ),
      ),
    )

    val BrushTint = HazeColorEffect.tint(
      brush = Brush.radialGradient(
        colors = listOf(
          Color.Yellow.copy(alpha = 0.5f),
          Color.Red.copy(alpha = 0.5f),
        ),
      ),
      blendMode = HazeColorEffect.DefaultBlendMode,
    )

    val VerticalMask = Brush.verticalGradient(
      0f to Color.Transparent,
      0.5f to Color.Black,
      1f to Color.Transparent,
    )
  }
}
