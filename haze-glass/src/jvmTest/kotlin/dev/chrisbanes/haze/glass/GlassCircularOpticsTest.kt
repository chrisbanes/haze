// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.skiaPaint
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import assertk.assertThat
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isGreaterThan
import assertk.assertions.isTrue
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.RuntimeShaderUniformProvider
import dev.chrisbanes.haze.createRuntimeShaderRenderEffect
import kotlin.test.Test
import org.jetbrains.skia.RuntimeEffect

/**
 * The circular-optics fused program keeps the continuous silhouette: over an opaque source its
 * alpha is its coverage, which must match the continuous program exactly, while its colours change
 * where refraction and lighting see the circular corners. Only modes below Balanced select it.
 */
class GlassCircularOpticsTest {

  @Test
  fun buildFusedWithCircularOptics_single_keepsContinuousCoverage() = assertKeepsCoverage(merged = false)

  @Test
  fun buildFusedWithCircularOptics_merged_keepsContinuousCoverage() = assertKeepsCoverage(merged = true)

  @Test
  fun buildFusedWithCircularOptics_declaresEveryContinuousUniform() {
    // Android throws on setting an undeclared uniform, and the continuous setters still run.
    for (merged in listOf(false, true)) {
      for (sharpDetail in listOf(false, true)) {
        val continuous = uniformNames(GlassShaders.buildFused(sharpDetail = sharpDetail, merged = merged, continuous = true))
        val circularOptics = uniformNames(GlassShaders.buildFusedWithCircularOptics(sharpDetail = sharpDetail, merged = merged))
        assertThat(continuous - circularOptics, "undeclared uniforms, merged=$merged sharpDetail=$sharpDetail").isEmpty()
      }
    }
  }

  @Test
  fun usesCircularCornerOptics_onlyBelowBalanced() {
    assertThat(HazePerformanceMode.Performance.usesCircularCornerOptics()).isTrue()
    assertThat(HazePerformanceMode.Fixed(0.49f).usesCircularCornerOptics()).isTrue()
    assertThat(HazePerformanceMode.Balanced.usesCircularCornerOptics()).isFalse()
    assertThat(HazePerformanceMode.Default.usesCircularCornerOptics()).isFalse()
    assertThat(HazePerformanceMode.Quality.usesCircularCornerOptics()).isFalse()
  }

  @Test
  fun depthInputKey_selectsCircularOpticsOnlyForContinuousCornersBelowBalanced() {
    for (merged in listOf(false, true)) {
      assertThat(selectsCircularOptics(ContinuousRoundedCornerShape(RADIUS.dp), merged, circularCornerOptics = true), "continuous merged=$merged")
        .isTrue()
      assertThat(selectsCircularOptics(ContinuousRoundedCornerShape(RADIUS.dp), merged, circularCornerOptics = false), "continuous at Balanced merged=$merged")
        .isFalse()
      assertThat(selectsCircularOptics(RoundedCornerShape(RADIUS.dp), merged, circularCornerOptics = true), "circular merged=$merged")
        .isFalse()
    }
  }

  private fun selectsCircularOptics(shape: CornerBasedShape, merged: Boolean, circularCornerOptics: Boolean): Boolean {
    val optical = scene(merged, shape, circularCornerOptics).first
    return GlassFusedEffectKey(
      blur = null,
      depth = 0f,
      optical = optical,
      detail = null,
      interaction = null,
      backdropBackground = null,
      backdropMergedBackground = null,
    ).depthInputKey(interactionOptics = false).circularOptics
  }

  private fun uniformNames(source: String): Set<String> =
    Regex("""uniform\s+(?:shader|\w+)\s+(\w+)""").findAll(source).map { it.groupValues[1] }.toSet()

  private fun assertKeepsCoverage(merged: Boolean) {
    val (key, field) = scene(merged)
    assertThat(continuousCorners(key.cornerRadii, field), "scene uses continuous corners").isTrue()
    val continuous = render(GlassShaders.buildFused(sharpDetail = false, merged = merged, continuous = true)) {
      setOpticalUniforms(key)
    }
    val circularOptics = render(GlassShaders.buildFusedWithCircularOptics(sharpDetail = false, merged = merged)) {
      setOpticalUniforms(key)
      setCircularOpticsUniforms(key)
    }
    val alphaDiffers = continuous.indices.count { (continuous[it] ushr 24) != (circularOptics[it] ushr 24) }
    assertThat(alphaDiffers, "pixels whose coverage changed").isEqualTo(0)
    assertThat(continuous.indices.count { continuous[it] != circularOptics[it] }, "pixels the optics changed")
      .isGreaterThan(0)
  }

  private fun scene(
    merged: Boolean,
    shape: CornerBasedShape = ContinuousRoundedCornerShape(RADIUS.dp),
    circularCornerOptics: Boolean = false,
  ): Pair<GlassOpticalEffectKey, GlassMergedField?> {
    val effect = GlassRuntimeEffect().apply {
      style = GlassStyle {
        when (shape) {
          is ContinuousRoundedCornerShape -> shape(shape)
          is RoundedCornerShape -> shape(shape)
          else -> error("unsupported shape $shape")
        }
      }
    }
    val density = Density(1f)
    val resolved = resolveGlassStyle(effect, SCENE_SIZE, density, LayoutDirection.Ltr)
    val coordinates = GlassCoordinates(SCENE_SIZE, Offset.Zero, SCENE_SIZE, 1f)
    val base = buildGlassRenderParams(resolved, coordinates, circularCornerOptics).opticalEffectKey()
    if (!merged) return base to null

    // A 2x2 cluster, as in the source_update_container_4 benchmark rows.
    val memberSize = Size((SCENE_SIZE.width - GAP) / 2f, (SCENE_SIZE.height - GAP) / 2f)
    val radii = shape.resolveCornerRadiiPx(memberSize, density, LayoutDirection.Ltr)
    val field = GlassMergedField(
      members = listOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f).map { (column, row) ->
        val origin = Offset(column * (memberSize.width + GAP), row * (memberSize.height + GAP))
        GlassMergedMember(Rect(origin, memberSize), radii, Color.Transparent, Color.Transparent)
      },
      mergeDistancePx = 32f,
      opticalSize = memberSize,
    )
    return base.copy(mergedField = field) to field
  }

  private fun render(source: String, uniforms: RuntimeShaderUniformProvider.() -> Unit): IntArray {
    val filter = createRuntimeShaderRenderEffect(RuntimeEffect.makeForShader(source), arrayOf("content"), arrayOf(null), uniforms)
    val width = SCENE_SIZE.width.toInt()
    val height = SCENE_SIZE.height.toInt()
    val image = ImageBitmap(width, height)
    with(Canvas(image)) {
      val bounds = Rect(Offset.Zero, SCENE_SIZE)
      saveLayer(bounds, Paint().apply { skiaPaint.imageFilter = filter })
      for (x in 0 until width step 16) {
        drawRect(
          Rect(x.toFloat(), 0f, x + 16f, SCENE_SIZE.height),
          Paint().apply { color = if (x % 32 == 0) Color(0xFF2F6FDE) else Color(0xFFE8C547) },
        )
      }
      restore()
    }
    return IntArray(width * height).also { image.toPixelMap().buffer.copyInto(it) }
  }

  private companion object {
    const val RADIUS = 40f
    const val GAP = 12f
    val SCENE_SIZE = Size(480f, 320f)
  }
}
