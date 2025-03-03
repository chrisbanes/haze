// Copyright 2023, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.Paint
import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Shader.TileMode.REPEAT
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.geometry.isUnspecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.concurrent.getOrSet
import kotlin.math.roundToInt

internal actual fun CompositionLocalConsumerModifierNode.createRenderEffect(params: RenderEffectParams): RenderEffect? {
  if (Build.VERSION.SDK_INT < 31) return null

  require(params.blurRadius >= 0.dp) { "blurRadius needs to be equal or greater than 0.dp" }

  val progressiveShader = params.progressive?.asBrush()?.toShader(params.contentBounds.size)

  val blur = when {
    params.blurRadius <= 0.dp -> AndroidRenderEffect.createOffsetEffect(0f, 0f)

    Build.VERSION.SDK_INT >= 33 && progressiveShader != null -> {
      // If we've been provided with a progressive/gradient blur shader, we need to use
      // our custom blur via a runtime shader
      createBlurImageFilterWithMask(
        blurRadiusPx = with(currentValueOf(LocalDensity)) { params.blurRadius.toPx() },
        bounds = params.contentBounds,
        mask = progressiveShader,
      )
    }

    else -> {
      try {
        val blurRadiusPx = with(currentValueOf(LocalDensity)) { params.blurRadius.toPx() }
        AndroidRenderEffect.createBlurEffect(blurRadiusPx, blurRadiusPx, Shader.TileMode.CLAMP)
      } catch (e: IllegalArgumentException) {
        throw IllegalArgumentException(
          "Error whilst calling RenderEffect.createBlurEffect. " +
            "This is likely because this device does not support a blur radius of ${params.blurRadius}dp",
          e,
        )
      }
    }
  }

  return blur
    .withNoise(currentValueOf(LocalContext), params.noiseFactor)
    .withTints(params.tints, params.contentBounds, params.tintAlphaModulate, progressiveShader)
    .withMask(params.mask, params.contentBounds)
    .asComposeRenderEffect()
}

/**
 * This is the technical minimum for blurring to work on Android.
 */
internal actual fun DrawScope.canUseGraphicLayers(): Boolean {
  return Build.VERSION.SDK_INT >= 31 && drawContext.canvas.nativeCanvas.isHardwareAccelerated
}

private val noiseTextureCache by unsynchronizedLazy { SimpleLruCache<Int, Bitmap>(3) }

private fun Context.getNoiseTexture(noiseFactor: Float): Bitmap {
  val noiseAlphaInt = (noiseFactor * 255).roundToInt().coerceIn(0, 255)
  val cached = noiseTextureCache[noiseAlphaInt]
  if (cached != null && !cached.isRecycled) {
    return cached
  }

  // We draw the noise with the given opacity
  return BitmapFactory.decodeResource(resources, R.drawable.haze_noise)
    .transform(alpha = noiseAlphaInt)
    .also { noiseTextureCache[noiseAlphaInt] = it }
}

@RequiresApi(31)
private fun AndroidRenderEffect.withNoise(
  context: Context,
  noiseFactor: Float,
): AndroidRenderEffect = when {
  noiseFactor >= 0.005f -> {
    val noiseShader = BitmapShader(context.getNoiseTexture(noiseFactor), REPEAT, REPEAT)
    AndroidRenderEffect.createBlendModeEffect(
      AndroidRenderEffect.createShaderEffect(noiseShader), // dst
      this, // src
      BlendMode.DST_ATOP, // blendMode
    )
  }

  else -> this
}

@RequiresApi(31)
private fun AndroidRenderEffect.withMask(
  mask: Brush?,
  contentBounds: Rect,
  blendMode: BlendMode = BlendMode.DST_IN,
): AndroidRenderEffect {
  val shader = mask?.toShader(contentBounds.size) ?: return this
  return blendWith(
    foreground = AndroidRenderEffect.createShaderEffect(shader),
    blendMode = blendMode,
    offset = contentBounds.topLeft,
  )
}

private fun Brush.toShader(size: Size): Shader? = when (this) {
  is ShaderBrush -> createShader(size)
  else -> null
}

@RequiresApi(31)
private fun AndroidRenderEffect.withTints(
  tints: List<HazeTint>,
  contentBounds: Rect,
  alphaModulate: Float = 1f,
  mask: Shader? = null,
): AndroidRenderEffect = tints.fold(this) { acc, tint ->
  acc.withTint(tint, contentBounds, alphaModulate, mask)
}

@RequiresApi(31)
private fun AndroidRenderEffect.withTint(
  tint: HazeTint,
  contentBounds: Rect,
  alphaModulate: Float = 1f,
  mask: Shader? = null,
): AndroidRenderEffect {
  if (!tint.isSpecified) return this

  val tintBrush = tint.brush?.toShader(contentBounds.size)
  if (tintBrush != null) {
    val brushEffect = when {
      alphaModulate >= 1f -> {
        AndroidRenderEffect.createShaderEffect(tintBrush)
      }
      else -> {
        // If we need to modulate the alpha, we'll need to wrap it in a ColorFilter
        AndroidRenderEffect.createColorFilterEffect(
          BlendModeColorFilter(Color.Blue.copy(alpha = alphaModulate).toArgb(), BlendMode.SRC_IN),
          AndroidRenderEffect.createShaderEffect(tintBrush),
        )
      }
    }

    return if (mask != null) {
      blendWith(
        foreground = AndroidRenderEffect.createBlendModeEffect(
          AndroidRenderEffect.createShaderEffect(mask),
          brushEffect,
          BlendMode.SRC_IN,
        ),
        blendMode = tint.blendMode.toAndroidBlendMode(),
        offset = contentBounds.topLeft,
      )
    } else {
      blendWith(
        foreground = brushEffect,
        blendMode = tint.blendMode.toAndroidBlendMode(),
        offset = contentBounds.topLeft,
      )
    }
  }

  val tintColor = when {
    alphaModulate < 1f -> tint.color.copy(alpha = tint.color.alpha * alphaModulate)
    else -> tint.color
  }
  if (tintColor.alpha >= 0.005f) {
    return if (mask != null) {
      return blendWith(
        foreground = AndroidRenderEffect.createColorFilterEffect(
          BlendModeColorFilter(tintColor.toArgb(), BlendMode.SRC_IN),
          AndroidRenderEffect.createShaderEffect(mask),
        ),
        blendMode = tint.blendMode.toAndroidBlendMode(),
      )
    } else {
      AndroidRenderEffect.createColorFilterEffect(
        BlendModeColorFilter(tintColor.toArgb(), tint.blendMode.toAndroidBlendMode()),
        this,
      )
    }
  }

  return this
}

@RequiresApi(31)
private fun AndroidRenderEffect.blendWith(
  foreground: AndroidRenderEffect,
  blendMode: BlendMode,
  offset: Offset = Offset.Zero,
): AndroidRenderEffect = AndroidRenderEffect.createBlendModeEffect(
  /* dst */
  this,
  /* src */
  when {
    offset.isUnspecified -> foreground
    offset == Offset.Zero -> foreground
    // We need to offset the shader to the bounds
    else -> AndroidRenderEffect.createOffsetEffect(offset.x, offset.y, foreground)
  },
  /* blendMode */
  blendMode,
)

@RequiresApi(33)
private fun createBlurImageFilterWithMask(
  blurRadiusPx: Float,
  bounds: Rect,
  mask: Shader,
): AndroidRenderEffect {
  fun shader(vertical: Boolean): AndroidRenderEffect {
    val shader = RuntimeShader(if (vertical) VERTICAL_BLUR_SKSL else HORIZONTAL_BLUR_SKSL).apply {
      setFloatUniform("blurRadius", blurRadiusPx)
      setFloatUniform("crop", bounds.left, bounds.top, bounds.right, bounds.bottom)
      setInputShader("mask", mask)
    }
    return AndroidRenderEffect.createRuntimeShaderEffect(shader, "content")
  }

  // Our blur runtime shader is separated, therefore requires two passes, one in each direction
  return shader(vertical = false).chainWith(shader(vertical = true))
}

@RequiresApi(31)
private fun AndroidRenderEffect.chainWith(imageFilter: AndroidRenderEffect): AndroidRenderEffect {
  return AndroidRenderEffect.createChainEffect(imageFilter, this)
}

/**
 * Returns a copy of the current [Bitmap], drawn with the given [alpha] value.
 *
 * There might be a better way to do this via a [BlendMode], but none of the results looked as
 * good.
 */
private fun Bitmap.transform(alpha: Int): Bitmap {
  val paint = paintLocal.getOrSet { Paint() }
  paint.reset()
  paint.alpha = alpha

  val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
  android.graphics.Canvas(bitmap).apply {
    drawBitmap(this@transform, 0f, 0f, paint)
  }
  return bitmap
}

private val paintLocal = ThreadLocal<Paint>()
