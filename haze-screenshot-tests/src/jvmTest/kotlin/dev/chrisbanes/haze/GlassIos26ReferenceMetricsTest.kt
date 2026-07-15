// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntRect
import java.awt.image.BufferedImage
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test

class GlassIos26ReferenceMetricsTest {

  @Test
  fun regularReferenceCenters_areDerivedFromImmutableResources() {
    val failures = mutableListOf<String>()
    GlassAppearance.entries.forEach { appearance ->
      val grid = resourceSnapshot("grid-${appearance.fileName}.png")
      val uniform = resourceSnapshot("uniform-${appearance.fileName}.png")
      GlassSurface.entries.forEach { surface ->
        val key = GlassReferenceKey(appearance, surface)
        val measured = measureGlassOpticalMetrics(
          grid,
          uniform,
          surface.referenceBounds,
          IntRect(0, 0, 1080, 192),
          48,
        )
        println("iOS 26 optical reference metrics $key: $measured")
        val expected = Ios26RegularReferenceMetrics.getValue(key)
        listOf(
          "displacement" to (measured.displacementPx to expected.displacementPx),
          "blur" to (measured.blurAttenuation to expected.blurAttenuation),
          "luma" to (measured.interiorLumaShift to expected.interiorLumaShift),
        ).forEach { (label, values) ->
          if (abs(values.first - values.second) > 1e-6f) {
            failures += "$key $label=${values.first}, expected=${values.second}"
          }
        }
      }
    }
    check(failures.isEmpty()) { failures.joinToString("\n") }
  }

  private fun resourceSnapshot(fileName: String): PixelSnapshot {
    val path = "/glass/ios26/$fileName"
    val image = checkNotNull(javaClass.getResourceAsStream(path)) { "Missing resource $path" }
      .use(ImageIO::read)
    check(image.width == 1080 && image.height == 2160) {
      "$path must be 1080x2160, was ${image.width}x${image.height}"
    }
    return image.snapshot()
  }
}

private fun BufferedImage.snapshot(): PixelSnapshot = PixelSnapshot(
  width,
  height,
  buildList(width * height) {
    for (y in 0 until height) {
      for (x in 0 until width) {
        val argb = getRGB(x, y)
        add(
          Color(
            red = ((argb ushr 16) and 0xff) / 255f,
            green = ((argb ushr 8) and 0xff) / 255f,
            blue = (argb and 0xff) / 255f,
            alpha = ((argb ushr 24) and 0xff) / 255f,
          ),
        )
      }
    }
  },
)
