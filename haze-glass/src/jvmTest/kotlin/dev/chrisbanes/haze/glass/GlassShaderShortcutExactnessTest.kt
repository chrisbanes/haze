// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

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
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isNotEqualTo
import dev.chrisbanes.haze.createRuntimeShaderRenderEffect
import kotlin.test.Test
import org.jetbrains.skia.RuntimeEffect

/**
 * Renders the complete optical shader with and without each shortcut that claims to change no
 * pixel, and requires identical output. Each case also renders a variant that marks where the
 * shortcut fires, so a shortcut that never fires cannot pass.
 */
class GlassShaderShortcutExactnessTest {

  @Test
  fun flatInteriorLightingSkip_circular_matchesFullSampling() =
    assertFlatInteriorSkipIsExact(RoundedCornerShape(RADIUS.dp), merged = false)

  @Test
  fun flatInteriorLightingSkip_mergedCircular_matchesFullSampling() =
    assertFlatInteriorSkipIsExact(RoundedCornerShape(RADIUS.dp), merged = true)

  private fun assertFlatInteriorSkipIsExact(shape: RoundedCornerShape, merged: Boolean) {
    val scene = scene(shape, merged)
    val source = scene.source()
    // A bound no fragment reaches, so the four height samples always run.
    val neverSkips = source.replace(FLAT_BOUND, "1e9")
    assertThat(neverSkips, "the skip's bound is in the source").isNotEqualTo(source)
    // A large gradient where the skip fires, so those fragments light differently.
    val marksSkips = source.replace(SKIP_RETURN) { it.groupValues[1] + "vec2(1000.0)" }
    assertThat(marksSkips, "the skip's return is in the source").isNotEqualTo(source)

    val expected = scene.render(neverSkips)
    assertThat(differingPixels(scene.render(source), expected), "pixels changed by the skip").isEqualTo(0)
    assertThat(differingPixels(scene.render(marksSkips), expected), "fragments the skip covers")
      .isGreaterThan(MIN_COVERED_PIXELS)
  }

  @Test
  fun farMemberSkip_mergedCircular_matchesFullField() =
    assertFarMemberSkipIsExact(RoundedCornerShape(RADIUS.dp))

  private fun assertFarMemberSkipIsExact(shape: RoundedCornerShape) {
    val scene = scene(shape, merged = true)
    val source = scene.source()
    // A threshold no member reaches, so every member joins the smooth minimum.
    val neverSkips = source.replace(FAR_MEMBER_THRESHOLD, ">= 1e9) continue;")
    assertThat(neverSkips, "the far-member test is in the source").isNotEqualTo(source)
    // Skips every member after the first, which leaves a one-member field.
    val alwaysSkips = source.replace(FAR_MEMBER_THRESHOLD, ">= -1e9) continue;")

    val expected = scene.render(neverSkips)
    assertThat(differingPixels(scene.render(source), expected), "pixels changed by the skip").isEqualTo(0)
    assertThat(differingPixels(scene.render(alwaysSkips), expected), "pixels that depend on later members")
      .isGreaterThan(MIN_COVERED_PIXELS)
  }

  private class Scene(
    val key: GlassOpticalEffectKey,
    val field: GlassMergedField?,
    val continuous: Boolean,
  ) {
    fun source() = GlassShaders.buildOptical(merged = field != null, continuous = continuous)

    fun render(source: String): IntArray {
      val filter = createRuntimeShaderRenderEffect(RuntimeEffect.makeForShader(source), arrayOf("content"), arrayOf(null)) {
        setOpticalUniforms(key)
        field?.let { setMergedFieldUniforms(it, includeTint = true, includeOptics = true) }
      }
      val width = SCENE_SIZE.width.toInt()
      val height = SCENE_SIZE.height.toInt()
      val image = ImageBitmap(width, height)
      with(Canvas(image)) {
        val bounds = Rect(Offset.Zero, SCENE_SIZE)
        saveLayer(bounds, Paint().apply { skiaPaint.imageFilter = filter })
        // Stripes give refraction and lighting something to move.
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
  }

  private fun scene(shape: RoundedCornerShape, merged: Boolean): Scene {
    val effect = GlassRuntimeEffect().apply { style = GlassStyle { shape(shape) } }
    val density = Density(1f)
    val resolved = resolveGlassStyle(effect, SCENE_SIZE, density, LayoutDirection.Ltr)
    val key = buildGlassRenderParams(resolved, GlassCoordinates(SCENE_SIZE, Offset.Zero, SCENE_SIZE, 1f))
      .opticalEffectKey()
    if (!merged) return Scene(key, field = null, continuous = key.cornerRadii.isContinuous)

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
    return Scene(key, field, field.isContinuous)
  }

  private fun differingPixels(actual: IntArray, expected: IntArray): Int =
    actual.indices.count { actual[it] != expected[it] }

  private companion object {
    const val RADIUS = 40f
    const val GAP = 12f
    const val FLAT_BOUND = "sampleStep + 1.0"
    const val MIN_COVERED_PIXELS = 1000
    const val FAR_MEMBER_THRESHOLD = ">= max(memberSmoothing[i], 0.0)) continue;"
    val SCENE_SIZE = Size(480f, 320f)
    val SKIP_RETURN = Regex("""(sampleStep \+ 1\.0\) return )vec2\(0\.0\)""")
  }
}
