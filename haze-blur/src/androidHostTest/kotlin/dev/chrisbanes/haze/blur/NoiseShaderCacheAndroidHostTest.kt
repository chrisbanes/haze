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
  fun get_sameTexture_returnsTheSameShader() {
    val cache = NoiseShaderCache()

    val first = cache.get(context)
    val second = cache.get(context)

    assertThat(second).isSameInstanceAs(first)
    assertThat(cache.buildCount).isEqualTo(1)
  }

  @Test
  fun get_afterClear_buildsANewShader() {
    val cache = NoiseShaderCache()
    val first = cache.get(context)

    cache.clear()

    assertThat(cache.get(context)).isNotSameInstanceAs(first)
    assertThat(cache.buildCount).isEqualTo(2)
  }

  @Test
  fun get_afterTheTextureIsRecycled_buildsANewShader() {
    val cache = NoiseShaderCache()
    val first = cache.get(context)

    context.getNoiseTexture().recycle()

    assertThat(cache.get(context)).isNotSameInstanceAs(first)
    assertThat(cache.buildCount).isEqualTo(2)
  }

  @Test
  fun cachedShader_hasAnIdentityLocalMatrix() {
    assertThat(NoiseShaderCache().get(context).matrix()).isEqualTo(Matrix().toShortString())
    assertThat(context.createNoiseShader().matrix()).isEqualTo(Matrix().toShortString())
  }

  private fun android.graphics.Shader.matrix() = Matrix().also { getLocalMatrix(it) }.toShortString()
}
