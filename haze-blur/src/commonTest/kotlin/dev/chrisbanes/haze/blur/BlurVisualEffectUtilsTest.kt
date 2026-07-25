// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import androidx.collection.LruCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotEqualTo
import assertk.assertions.isTrue
import dev.chrisbanes.haze.HazeProgressive
import kotlin.test.Test

class BlurVisualEffectUtilsTest {

  @Test
  fun clearIfInitialized_doesNotInitializeLazyValue() {
    var initialized = false
    val lazyCache = lazy(mode = LazyThreadSafetyMode.NONE) {
      initialized = true
      LruCache<Int, Int>(1)
    }

    clearIfInitialized(lazyCache) { it.evictAll() }

    assertThat(initialized).isFalse()
  }

  @Test
  fun clearIfInitialized_clearsInitializedValue() {
    val lazyCache = lazy(mode = LazyThreadSafetyMode.NONE) {
      LruCache<Int, Int>(2)
    }
    lazyCache.value.put(1, 1)

    var cleared = false
    clearIfInitialized(lazyCache) {
      cleared = true
      it.evictAll()
    }

    assertThat(cleared).isTrue()
    assertThat(lazyCache.value[1] == null).isTrue()
  }

  @Test
  fun renderEffectCacheKey_solidColorGraphIgnoresGeometry() {
    val colorEffects = listOf(HazeColorEffect.tint(Color.Red))
    val params = renderEffectParams(colorEffects = colorEffects)

    assertThat(params.renderEffectCacheKey(Density(2f))).isEqualTo(
      renderEffectParams(
        colorEffects = colorEffects,
        contentSize = Size(800f, 600f),
        contentOffset = Offset(24f, 12f),
      ).renderEffectCacheKey(Density(2f)),
    )
  }

  @Test
  fun renderEffectCacheKey_brushTintTracksSizeAndOffset() {
    val colorEffects = listOf(
      HazeColorEffect.tint(Brush.verticalGradient(listOf(Color.Red, Color.Blue))),
    )
    val params = renderEffectParams(colorEffects = colorEffects)
    val key = params.renderEffectCacheKey(Density(2f))

    assertThat(key).isNotEqualTo(
      renderEffectParams(
        colorEffects = colorEffects,
        contentSize = Size(800f, 600f),
      ).renderEffectCacheKey(Density(2f)),
    )
    assertThat(key).isNotEqualTo(
      renderEffectParams(
        colorEffects = colorEffects,
        contentOffset = Offset(24f, 12f),
      ).renderEffectCacheKey(Density(2f)),
    )
  }

  @Test
  fun renderEffectCacheKey_colorFilterTracksOffsetButNotSize() {
    val colorEffects = listOf(
      HazeColorEffect.colorFilter(ColorFilter.tint(Color.Red)),
    )
    val params = renderEffectParams(colorEffects = colorEffects)
    val key = params.renderEffectCacheKey(Density(2f))

    assertThat(key).isEqualTo(
      renderEffectParams(
        colorEffects = colorEffects,
        contentSize = Size(800f, 600f),
      ).renderEffectCacheKey(Density(2f)),
    )
    assertThat(key).isNotEqualTo(
      renderEffectParams(
        colorEffects = colorEffects,
        contentOffset = Offset(24f, 12f),
      ).renderEffectCacheKey(Density(2f)),
    )
  }

  @Test
  fun renderEffectCacheKey_maskAndProgressiveTrackGeometry() {
    val mask = Brush.verticalGradient(listOf(Color.Black, Color.Transparent))
    val progressive = HazeProgressive.verticalGradient(startIntensity = 1f, endIntensity = 0f)
    val masked = renderEffectParams(mask = mask)
    val progressiveParams = renderEffectParams(progressive = progressive)

    assertThat(masked.renderEffectCacheKey(Density(2f))).isNotEqualTo(
      renderEffectParams(
        contentSize = Size(800f, 600f),
        contentOffset = Offset(24f, 12f),
        mask = mask,
      ).renderEffectCacheKey(Density(2f)),
    )
    assertThat(progressiveParams.renderEffectCacheKey(Density(2f))).isNotEqualTo(
      renderEffectParams(
        contentSize = Size(800f, 600f),
        contentOffset = Offset(24f, 12f),
        progressive = progressive,
      ).renderEffectCacheKey(Density(2f)),
    )
  }

  @Test
  fun renderEffectCacheKey_tracksDensity() {
    val params = renderEffectParams()

    assertThat(params.renderEffectCacheKey(Density(1f)))
      .isNotEqualTo(params.renderEffectCacheKey(Density(2f)))
  }

  @Test
  fun renderEffectCacheKey_canonicalizesDisabledNoise() {
    val density = Density(2f)
    val key = renderEffectParams(noiseFactor = 0f).renderEffectCacheKey(density)

    assertThat(key)
      .isEqualTo(renderEffectParams(noiseFactor = -1f).renderEffectCacheKey(density))
    assertThat(key)
      .isEqualTo(renderEffectParams(noiseFactor = Float.NaN).renderEffectCacheKey(density))
    assertThat(key)
      .isNotEqualTo(renderEffectParams(noiseFactor = 0.15f).renderEffectCacheKey(density))
  }

  @Test
  fun hasVisibleNoise_onlyForPositiveFactors() {
    assertThat((-1f).hasVisibleNoise()).isFalse()
    assertThat(0f.hasVisibleNoise()).isFalse()
    assertThat(Float.NaN.hasVisibleNoise()).isFalse()
    assertThat(0.15f.hasVisibleNoise()).isTrue()
  }

  private fun renderEffectParams(
    colorEffects: List<HazeColorEffect> = listOf(HazeColorEffect.tint(Color.Red)),
    noiseFactor: Float = 0.15f,
    contentSize: Size = Size(640f, 480f),
    contentOffset: Offset = Offset(8f, 4f),
    mask: Brush? = null,
    progressive: HazeProgressive? = null,
  ) = RenderEffectParams(
    blurRadius = 20.dp,
    noiseFactor = noiseFactor,
    scale = 1f,
    contentSize = contentSize,
    contentOffset = contentOffset,
    colorEffects = colorEffects,
    mask = mask,
    progressive = progressive,
    blurTileMode = TileMode.Clamp,
  )
}
