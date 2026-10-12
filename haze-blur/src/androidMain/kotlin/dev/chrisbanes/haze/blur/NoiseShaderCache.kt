// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader

/**
 * Keeps the noise [BitmapShader] a draw uses, so a draw does not allocate a shader and matrix each
 * time. The shader is rebuilt when the decoded noise texture or the scale changes, and is never
 * mutated after it is built.
 */
internal class NoiseShaderCache {
  private var texture: Bitmap? = null
  private var scale: Float = 0f
  private var shader: BitmapShader? = null

  /** How many shaders this cache has built. */
  var buildCount: Int = 0
    private set

  fun get(context: Context, scale: Float): BitmapShader {
    val currentTexture = context.getNoiseTexture()
    val normalizedScale = if (scale > 0f) scale else 1f
    shader?.takeIf { texture === currentTexture && this.scale == normalizedScale }?.let { return it }

    return context.createNoiseShader(normalizedScale).also {
      texture = currentTexture
      this.scale = normalizedScale
      shader = it
      buildCount++
    }
  }

  fun clear() {
    texture = null
    shader = null
  }
}
