// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.skiaPaint
import androidx.compose.ui.graphics.toPixelMap
import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.doesNotContain
import assertk.assertions.isEqualTo
import assertk.assertions.isGreaterThan
import assertk.assertions.isInstanceOf
import assertk.assertions.isNotEqualTo
import java.io.File
import java.security.MessageDigest
import kotlin.test.Test
import org.jetbrains.skia.RuntimeEffect

class WindowsGlassProbeTest {
  @Test
  fun sourceFor_rejectsTruncatedVariant() {
    assertFailure { WindowsGlassProbe.sourceFor("g", continuous = true) }
      .isInstanceOf<IllegalArgumentException>()
  }

  @Test
  fun sourceFor_rejectsUnknownAndDuplicateCuts() {
    assertFailure { WindowsGlassProbe.sourceFor("original", continuous = true, cuts = "missing") }
      .isInstanceOf<IllegalStateException>()
    assertFailure { WindowsGlassProbe.sourceFor("original", continuous = true, cuts = "ambient,ambient") }
      .isInstanceOf<IllegalArgumentException>()
    assertFailure { WindowsGlassProbe.sourceFor("guarded", continuous = true, cuts = "ambient") }
      .isInstanceOf<IllegalArgumentException>()
  }

  @Test
  fun sourceFor_everyCumulativeCutCompiles() {
    val cuts = listOf(
      "ambient", "content-normal", "chroma", "member-optics", "sample-offset", "refraction",
      "grading", "tint", "edge-fade", "signed-distance", "member-weights",
    )
    for (count in 1..cuts.size) {
      val source = WindowsGlassProbe.cutSource(FROZEN_ORIGINAL, cuts.take(count).joinToString(","))
      RuntimeEffect.makeForShader(source).close()
    }
  }

  @Test
  fun sourceFor_refractionSubcutsCompileFromRetainedSource() {
    val retained = "ambient,content-normal,chroma,member-optics,sample-offset,grading,tint,edge-fade,signed-distance"
    val subcuts = listOf(
      "refraction",
      "member-weights",
      "standalone-refraction",
      "merged-refraction",
      "optical-height",
      "merged-gradient",
      "rect-gradient",
    )
    for (cut in subcuts) {
      val source = WindowsGlassProbe.cutSource(FROZEN_ORIGINAL, "$retained,$cut")
      RuntimeEffect.makeForShader(source).close()
    }
    val combined = WindowsGlassProbe.cutSource(FROZEN_ORIGINAL, retained + "," + subcuts.joinToString(","))
    RuntimeEffect.makeForShader(combined).close()
  }

  @Test
  fun sourceFor_compactMainPreservesRetainedFixturePixels() {
    val retained = "ambient,content-normal,chroma,member-optics,sample-offset,grading,tint,edge-fade,signed-distance,optical-height,rect-gradient"
    val reference = WindowsGlassProbe.cutSource(FROZEN_ORIGINAL, retained)
    val compact = WindowsGlassProbe.cutSource(FROZEN_ORIGINAL, "$retained,compact-main")
    val referencePixels = fixturePixels(reference, "reference")
    assertThat(referencePixels.distinct().size).isGreaterThan(3)
    assertThat(fixturePixels(compact, "compact")).isEqualTo(referencePixels)
    val noRefraction = WindowsGlassProbe.cutSource(FROZEN_ORIGINAL, "$retained,compact-main,refraction")
    assertThat(fixturePixels(noRefraction, "no-refraction")).isNotEqualTo(referencePixels)
    for (cut in listOf("refraction", "member-weights", "standalone-refraction", "merged-refraction", "merged-gradient")) {
      RuntimeEffect.makeForShader(WindowsGlassProbe.cutSource(FROZEN_ORIGINAL, "$retained,compact-main,$cut")).close()
    }
  }

  private fun fixturePixels(source: String, name: String): List<Color> {
    val image = ImageBitmap(64, 48)
    val output = File("build/outputs/glass-probe-parity/$name").apply { mkdirs() }
    RuntimeEffect.makeForShader(source).use { effect ->
      WindowsGlassProbe.makeShader(effect, source, 2, output).use { shader ->
        val paint = Paint().apply { skiaPaint.shader = shader }
        Canvas(image).drawRect(Rect(0f, 0f, 64f, 48f), paint)
      }
    }
    val pixels = image.toPixelMap()
    return List(64 * 48) { index -> pixels[index % 64, index / 64] }
  }

  @Test
  fun frozenReproducer_matchesMeasuredSourceAndUniforms() {
    val source = WindowsGlassProbe.cutSource(FROZEN_ORIGINAL, RETAINED_CUTS)
    val hash = MessageDigest.getInstance("SHA-256").digest(File(FIXTURES, "retained.sksl").readBytes())
      .joinToString("") { "%02x".format(it) }
    assertThat(hash).isEqualTo("3bdf411bbbd5b324be57b374d141590b1f78c72a4cf9ce0fc74bba71fd320ebc")
    val output = File("build/outputs/glass-probe-parity/frozen").apply { mkdirs() }
    RuntimeEffect.makeForShader(source).use { effect ->
      WindowsGlassProbe.makeShader(effect, source, 2, output).close()
    }
    assertThat(File(output, "uniforms.txt").readText()).isEqualTo(File(FIXTURES, "uniforms.txt").readText())
    assertThat(File(FIXTURES, "content.sksl").readText()).isEqualTo(WindowsGlassProbe.CONTROL_SOURCE)
  }

  @Test
  fun frozenOriginal_matchesMeasuredGeneratorSourceAndUniforms() {
    val frozen = File(FIXTURES, "original.sksl")
    val hash = MessageDigest.getInstance("SHA-256").digest(frozen.readBytes())
      .joinToString("") { "%02x".format(it) }
    // Profiled original continuous 240x160 case of run 42534101240.
    assertThat(hash).isEqualTo("80410be10e8eb678519f41d6cc5c6bbf3c22b23aa5bff2213bc36ea1b33a10ab")
    val output = File("build/outputs/glass-probe-parity/frozen-original").apply { mkdirs() }
    RuntimeEffect.makeForShader(FROZEN_ORIGINAL).use { effect ->
      WindowsGlassProbe.makeShader(effect, FROZEN_ORIGINAL, 2, output).close()
    }
    assertThat(File(output, "uniforms.txt").readText()).isEqualTo(File(FIXTURES, "uniforms.txt").readText())
  }

  @Test
  fun cutSource_frozenOriginalProducesRetainedFixture() {
    assertThat(WindowsGlassProbe.cutSource(FROZEN_ORIGINAL, RETAINED_CUTS))
      .isEqualTo(File(FIXTURES, "retained.sksl").readText().replace("\r\n", "\n"))
    assertThat(WindowsGlassProbe.cutSource(FROZEN_ORIGINAL, "$RETAINED_CUTS,refraction"))
      .isEqualTo(File(FIXTURES, "control.sksl").readText().replace("\r\n", "\n"))
  }

  @Test
  fun sourceFor_originalIsProductionSource() {
    listOf(false, true).forEach { continuous ->
      assertThat(WindowsGlassProbe.sourceFor("original", continuous))
        .isEqualTo(GlassShaders.buildOptical(merged = true, continuous = continuous))
    }
  }

  @Test
  fun sourceFor_guardedTransformsEveryMemberLoopAndCompiles() {
    listOf(false, true).forEach { continuous ->
      val source = WindowsGlassProbe.sourceFor("guarded", continuous)
      assertThat(source).doesNotContain("if (float(i) >= memberCount) break;")
      RuntimeEffect.makeForShader(source).close()
    }
  }

  private companion object {
    val FIXTURES = File("../docs/agents/investigations/1767-repro/src/main/resources")

    // The measured source; cut tests anchor here so later GlassShaders changes cannot move them.
    val FROZEN_ORIGINAL = File(FIXTURES, "original.sksl").readText().replace("\r\n", "\n")
    const val RETAINED_CUTS =
      "ambient,content-normal,chroma,member-optics,sample-offset,grading,tint,edge-fade,signed-distance," +
        "optical-height,rect-gradient,compact-main,member-weights,standalone-refraction"
  }
}
