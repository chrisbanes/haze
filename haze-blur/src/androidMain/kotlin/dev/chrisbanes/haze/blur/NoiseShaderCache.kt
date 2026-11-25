// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.blur

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader

/**
 * Keeps the noise [BitmapShader] a draw uses, so a draw does not allocate a shader each
 * time. The shader is rebuilt when the decoded noise texture changes, and is never
 * mutated after it is built.
 */
internal class NoiseShaderCache {
  private var texture: Bitmap? = null
  private var shader: BitmapShader? = null

  /** How many shaders this cache has built. */
  var buildCount: Int = 0
    private set

  fun get(context: Context): BitmapShader {
    val currentTexture = context.getNoiseTexture()
    shader?.takeIf { texture === currentTexture }?.let { return it }

    return context.createNoiseShader().also {
      texture = currentTexture
      shader = it
      buildCount++
    }
  }

  fun clear() {
    texture = null
    shader = null
  }
}
