// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import java.awt.Dimension
import java.awt.EventQueue
import java.awt.Rectangle
import java.awt.Robot
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JFrame
import kotlin.test.Test
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Shader
import org.jetbrains.skiko.SkiaLayer
import org.jetbrains.skiko.SkikoRenderDelegate

/** Temporary diagnostic: actual generated Glass programs, wall time through Direct3D present. */
class WindowsGlassProbeTest {
  @Test
  fun measure() {
    val variant = System.getProperty("glassProbeVariant", "original")
    val trial = System.getProperty("glassProbeTrial", "local")
    val output = File("build/outputs/glass-probe/$trial-$variant").apply { mkdirs() }
    val timings = File(output, "timings.csv").apply { writeText("case,members,phase,ms\n") }
    val robot = Robot()
    fun measure(case: String, members: Int, phase: String, block: () -> Unit) {
      println("GLASS_PROBE_START $case $members $phase")
      val start = System.nanoTime()
      block()
      val ms = (System.nanoTime() - start) / 1e6
      timings.appendText("$case,$members,$phase,$ms\n")
      println("GLASS_PROBE_END $case $members $phase $ms")
    }
    for (continuous in listOf(false, true)) {
      val sources = linkedMapOf(
        "optical" to GlassShaders.buildOptical(merged = true, continuous = continuous),
        "detail" to GlassShaders.buildRefractionDetail(merged = true, continuous = continuous),
        "coverage" to GlassShaders.buildRefractionDetail(coverageOnly = true, merged = true, continuous = continuous),
        "rim" to GlassShaders.buildRim(merged = true, continuous = continuous),
        "lighting" to GlassShaders.buildInteractionLighting(merged = true, continuous = continuous),
        "background" to GlassShaders.buildMergedBackground(continuous = continuous),
      )
      for ((name, original) in sources) {
        val case = "$name-$continuous"
        val source = if (variant == "guarded") guarded(original) else original
        File(output, "$case.sksl").writeText(source)
        lateinit var effect: RuntimeEffect
        measure(case, 0, "compile") { effect = RuntimeEffect.makeForShader(source) }
        for (members in listOf(2, 4)) {
          lateinit var frame: JFrame
          lateinit var layer: SkiaLayer
          var shader: Shader? = null
          EventQueue.invokeAndWait {
            layer = SkiaLayer()
            layer.renderDelegate = object : SkikoRenderDelegate {
              override fun onRender(canvas: Canvas, width: Int, height: Int, nanoTime: Long) {
                canvas.clear(0xFF303030.toInt())
                Paint().use { paint ->
                  paint.shader = shader
                  if (shader != null) canvas.drawRect(Rect.makeWH(240f, 160f), paint)
                }
              }
            }
            frame = JFrame("Glass $case $members").apply {
              contentPane.add(layer)
              contentPane.preferredSize = Dimension(240, 160)
              pack()
              setLocation(30, 30)
              isVisible = true
            }
          }
          try {
            EventQueue.invokeAndWait {
              repeat(3) { layer.renderImmediately() }
              val info = "$case,$members,${layer.renderApi},${layer.renderInfo}\n"
              File(output, "renderer.txt").appendText(info)
              if (System.getProperty("os.name").startsWith("Windows")) check(layer.renderApi.toString() == "DIRECT3D")
              measure(case, members, "construct") { shader = makeShader(effect, source, members) }
              repeat(6) { measure(case, members, "draw-$it") { layer.renderImmediately() } }
            }
            robot.waitForIdle()
            lateinit var bounds: Rectangle
            EventQueue.invokeAndWait { bounds = Rectangle(layer.locationOnScreen, layer.size) }
            val capture = robot.createScreenCapture(bounds)
            ImageIO.write(capture, "png", File(output, "$case-$members.png"))
            val colors = (0 until capture.height).flatMap { y -> (0 until capture.width).map { x -> capture.getRGB(x, y) } }.toSet()
            check(colors.size > 3) { "Blank output for $case: $colors" }
          } finally {
            EventQueue.invokeAndWait {
              layer.dispose()
              frame.dispose()
              shader?.close()
            }
          }
        }
        effect.close()
      }
    }
  }

  private fun guarded(original: String): String {
    var source = original
    val needle = "if (float(i) >= memberCount) break;"
    var count = 0
    while (needle in source) {
      val start = source.indexOf(needle)
      var end = start + needle.length
      var depth = 0
      while (true) {
        if (source[end] == '}' && depth == 0) break
        if (source[end] == '{') depth++
        if (source[end] == '}') depth--
        end++
      }
      source = source.substring(0, start) + "if (float(i) < memberCount) {" +
        source.substring(start + needle.length, end) + "}\n" + source.substring(end)
      count++
    }
    check(count > 0)
    return source
  }

  private fun makeShader(effect: RuntimeEffect, source: String, members: Int): Shader {
    val builder = RuntimeShaderBuilder(effect)
    val rects = floatArrayOf(15f, 25f, 95f, 50f, 118f, 25f, 95f, 50f, 15f, 83f, 95f, 50f, 118f, 83f, 95f, 50f)
    val explicit = mapOf(
      "materialOrigin" to floatArrayOf(0f, 0f),
      "materialSize" to floatArrayOf(240f, 160f),
      "sampleSize" to floatArrayOf(240f, 160f),
      "memberRects" to rects,
      "memberLayoutRects" to rects,
      "memberRadii" to FloatArray(16) { 20f },
      "memberCornerExponents" to FloatArray(16) { 4f },
      "cornerRadii" to FloatArray(4) { 20f },
      "cornerExponents" to FloatArray(4) { 4f },
      "memberSmoothing" to floatArrayOf(0f, 24f, 24f, 24f),
      "memberCount" to floatArrayOf(members.toFloat()),
      "memberStandaloneWeights" to floatArrayOf(0.4f, 0.8f, 0.3f, 0.6f),
      "memberLightPositions" to floatArrayOf(55f, 45f, 160f, 45f, 55f, 105f, 160f, 105f),
      "memberOpticalPositions" to floatArrayOf(55f, 45f, 160f, 45f, 55f, 105f, 160f, 105f),
      "memberLightRadii" to FloatArray(4) { 70f },
      "memberOpticalRadii" to FloatArray(4) { 70f },
      "memberLightIntensities" to floatArrayOf(1f, 0.7f, 0.4f, 0.9f),
      "memberRefractionMultipliers" to floatArrayOf(1.2f, 1.4f, 1.1f, 1.3f),
      "memberWhitePointDeltas" to FloatArray(4) { 0.1f },
      "lightPosition" to floatArrayOf(40f, 10f),
      "tintColor" to floatArrayOf(0.1f, 0.3f, 0.7f, 0.2f),
      "edgeShadow" to floatArrayOf(0.1f, 0.1f, 0.2f, 0.4f),
      "edgeOutline" to floatArrayOf(0.7f, 0.8f, 1f, 0.6f),
    )
    val scalars = mapOf(
      "sampleStep" to 1f, "refractionStrength" to 8f, "refractionFoldStrength" to 0.3f,
      "ambientResponse" to 0.3f, "edgeSoftness" to 2f, "refractionHeight" to 15f,
      "edgeRefractionWidth" to 12f, "chromaticAberrationStrength" to 1f, "surfaceProfile" to 0f,
      "chromaticAberrationMode" to 1f, "contrast" to 1f, "whitePoint" to 1f,
      "chromaMultiplier" to 1f, "refractionScale" to 1f, "contentNormalBlend" to 0.5f,
      "fresnelExponent" to 2f, "detailWidth" to 8f, "detailIntensity" to 1f,
      "detailVisibility" to 1f, "specularIntensity" to 0.7f, "specularExponent" to 4f,
      "rimHighlightIntensity" to 0.7f, "rimHighlightWidth" to 3f, "rimHighlightSides" to 0.5f,
      "edgeOutlineWidth" to 1f,
    )
    val declarations = Regex("uniform\\s+(?:float[24]?|vec[24])\\s+(\\w+)(?:\\[\\d+])?;")
    for (match in declarations.findAll(source)) {
      val name = match.groupValues[1]
      val value = explicit[name] ?: scalars[name]?.let { floatArrayOf(it) }
        ?: if (name.startsWith("memberTint") || name.startsWith("memberBackground")) {
          val i = name.last().digitToInt()
          floatArrayOf(0.2f + i * 0.2f, 0.7f - i * 0.1f, 0.3f + i * 0.1f, 0.7f)
        } else {
          error("Missing fixture uniform: $name")
        }
      builder.uniform(name, value)
    }
    if ("uniform shader content;" in source) {
      val content = RuntimeEffect.makeForShader("half4 main(float2 p) { return half4(fract(p.x / 16.0), fract(p.y / 16.0), 0.5, 1.0); }")
      builder.child("content", RuntimeShaderBuilder(content).makeShader())
    }
    return builder.makeShader()
  }
}
