// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import android.content.Context
import android.graphics.Matrix
import androidx.test.core.app.ApplicationProvider
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isSameInstanceAs
import dev.chrisbanes.haze.test.ContextTest
import kotlin.test.Test
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class NoiseShaderCacheAndroidHostTest : ContextTest() {
  private val context get() = ApplicationProvider.getApplicationContext<Context>()

  @Test
  fun get_sameTextureAndScale_returnsTheSameShader() {
    val cache = NoiseShaderCache()

    val first = cache.get(context, 0.5f)
    val second = cache.get(context, 0.5f)

    assertThat(second).isSameInstanceAs(first)
    assertThat(cache.buildCount).isEqualTo(1)
  }

  @Test
  fun get_changedScale_buildsANewShader() {
    val cache = NoiseShaderCache()

    val first = cache.get(context, 0.5f)
    val second = cache.get(context, 0.25f)

    assertThat(second).isNotSameInstanceAs(first)
    assertThat(cache.buildCount).isEqualTo(2)
  }

  @Test
  fun get_nonPositiveScale_sharesTheScaleOneShader() {
    val cache = NoiseShaderCache()

    val unit = cache.get(context, 1f)

    assertThat(cache.get(context, 0f)).isSameInstanceAs(unit)
    assertThat(cache.get(context, -2f)).isSameInstanceAs(unit)
    assertThat(cache.buildCount).isEqualTo(1)
  }

  @Test
  fun get_afterClear_buildsANewShader() {
    val cache = NoiseShaderCache()
    val first = cache.get(context, 1f)

    cache.clear()

    assertThat(cache.get(context, 1f)).isNotSameInstanceAs(first)
    assertThat(cache.buildCount).isEqualTo(2)
  }

  @Test
  fun get_afterTheTextureIsRecycled_buildsANewShader() {
    val cache = NoiseShaderCache()
    val first = cache.get(context, 1f)

    context.getNoiseTexture().recycle()

    assertThat(cache.get(context, 1f)).isNotSameInstanceAs(first)
    assertThat(cache.buildCount).isEqualTo(2)
  }

  @Test
  fun cachedShader_hasTheMatrixCreateNoiseShaderGives() {
    val cache = NoiseShaderCache()

    for (scale in listOf(1f, 0.5f)) {
      assertThat(cache.get(context, scale).matrix()).isEqualTo(context.createNoiseShader(scale).matrix())
    }
  }

  private fun android.graphics.Shader.matrix() = Matrix().also { getLocalMatrix(it) }.toShortString()
}
