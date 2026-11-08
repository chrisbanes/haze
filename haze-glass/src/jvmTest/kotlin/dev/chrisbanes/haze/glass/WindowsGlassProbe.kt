// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import java.awt.Dimension
import java.awt.EventQueue
import java.awt.Rectangle
import java.awt.Robot
import java.io.File
import java.security.MessageDigest
import javax.imageio.ImageIO
import javax.swing.JFrame
import kotlin.system.exitProcess
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skia.Shader
import org.jetbrains.skiko.SkiaLayer
import org.jetbrains.skiko.SkikoRenderDelegate

/** Native first-draw probe. Run explicitly, never as part of the ordinary JVM test suite. */
internal object WindowsGlassProbe {
  internal const val CONTROL_SOURCE =
    "half4 main(float2 p) { return half4(fract(p.x / 16.0), fract(p.y / 16.0), 0.5, 1.0); }"

  @JvmStatic
  fun main(args: Array<String>) {
    require(args.size == 6) { "Expected variant, continuous, members, output directory, width, height" }
    val output = File(args[3]).apply { mkdirs() }
    try {
      val variant = args[0]
      val continuous = args[1].toBooleanStrict()
      val members = args[2].toInt().also { require(it in 2..4) }
      val width = args[4].toInt().also { require(it in 1..240) }
      val height = args[5].toInt().also { require(it in 1..160) }
      val source = sourceFor(variant, continuous)
      File(output, "shader.sksl").writeText(source)
      val hash = MessageDigest.getInstance("SHA-256").digest(source.toByteArray())
        .joinToString("") { "%02x".format(it) }
      File(output, "identity.txt").writeText(
        "variant=$variant\ncontinuous=$continuous\nmembers=$members\nsha256=$hash\n" +
          "viewport=${width}x$height\njava=${System.getProperty("java.runtime.version")}\nos=${System.getProperty("os.name")}\n",
      )
      check(System.getProperty("os.name").startsWith("Windows")) { "Windows Direct3D required" }
      render(source, members, output, width, height)
      File(output, "verdict.txt").writeText("RENDER_PASS")
      exitProcess(0)
    } catch (failure: Throwable) {
      File(output, "error.txt").writeText(failure.stackTraceToString())
      System.err.println("GLASS_PROBE INVALID: ${failure.javaClass.simpleName}: ${failure.message}")
      exitProcess(2)
    }
  }

  internal fun sourceFor(variant: String, continuous: Boolean): String {
    require(variant in setOf("original", "guarded", "control")) { "Unknown variant: $variant" }
    if (variant == "control") return CONTROL_SOURCE
    val original = GlassShaders.buildOptical(merged = true, continuous = continuous)
    if (variant == "original") return original
    var source = original
    val needle = "if (float(i) >= memberCount) break;"
    val expected = source.windowed(needle.length).count { it == needle }
    check(expected > 0) { "No member loops to transform" }
    repeat(expected) {
      val start = source.indexOf(needle)
      check(start >= 0)
      var end = start + needle.length
      var depth = 0
      while (end < source.length) {
        if (source[end] == '}' && depth == 0) break
        if (source[end] == '{') depth++
        if (source[end] == '}') depth--
        end++
      }
      check(end < source.length) { "Unclosed member loop" }
      source = source.substring(0, start) + "if (float(i) < memberCount) {" +
        source.substring(start + needle.length, end) + "}\n" + source.substring(end)
    }
    check(needle !in source)
    return source
  }

  private fun render(source: String, members: Int, output: File, drawWidth: Int, drawHeight: Int) {
    val timings = File(output, "timings.csv").apply { writeText("phase,ms\n") }
    fun measure(phase: String, block: () -> Unit) {
      File(output, "phase.txt").writeText("$phase,${System.currentTimeMillis()}")
      println("GLASS_PROBE_START $phase")
      val start = System.nanoTime()
      block()
      val ms = (System.nanoTime() - start) / 1e6
      timings.appendText("$phase,$ms\n")
      println("GLASS_PROBE_END $phase $ms")
    }
    lateinit var effect: RuntimeEffect
    measure("effect") { effect = RuntimeEffect.makeForShader(source) }
    val robot = Robot()
    lateinit var frame: JFrame
    lateinit var layer: SkiaLayer
    var shader: Shader? = null
    measure("window") {
      EventQueue.invokeAndWait {
        layer = SkiaLayer()
        layer.renderDelegate = object : SkikoRenderDelegate {
          override fun onRender(canvas: Canvas, width: Int, height: Int, nanoTime: Long) {
            canvas.clear(0xFF303030.toInt())
            Paint().use { paint ->
              paint.shader = shader
              if (shader != null) canvas.drawRect(Rect.makeWH(drawWidth.toFloat(), drawHeight.toFloat()), paint)
            }
          }
        }
        frame = JFrame("Glass first-draw probe").apply {
          contentPane.add(layer)
          contentPane.preferredSize = Dimension(drawWidth, drawHeight)
          pack()
          setLocation(30, 30)
          isVisible = true
        }
      }
    }
    try {
      EventQueue.invokeAndWait {
        repeat(3) { layer.renderImmediately() }
        File(output, "renderer.txt").writeText("${layer.renderApi}\n${layer.renderInfo}\n")
        check(layer.renderApi.toString() == "DIRECT3D") { "Wrong backend: ${layer.renderApi}" }
        measure("construct") { shader = makeShader(effect, source, members, output) }
        measure("first-draw") { layer.renderImmediately() }
        repeat(6) { measure("warm-$it") { layer.renderImmediately() } }
      }
      robot.waitForIdle()
      lateinit var bounds: Rectangle
      EventQueue.invokeAndWait { bounds = Rectangle(layer.locationOnScreen, layer.size) }
      val capture = robot.createScreenCapture(bounds)
      ImageIO.write(capture, "png", File(output, "render.png"))
      val colors = (0 until capture.height).flatMap { y ->
        (0 until capture.width).map { x -> capture.getRGB(x, y) }
      }.toSet()
      check(colors.size > 3) { "Blank output: $colors" }
    } finally {
      EventQueue.invokeAndWait {
        layer.dispose()
        frame.dispose()
        shader?.close()
      }
      effect.close()
    }
  }

  private fun makeShader(effect: RuntimeEffect, source: String, members: Int, output: File): Shader {
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
      File(output, "uniforms.txt").appendText("$name=${value.joinToString()}\n")
      builder.uniform(name, value)
    }
    if ("uniform shader content;" in source) {
      File(output, "content.sksl").writeText(CONTROL_SOURCE)
      val content = RuntimeEffect.makeForShader(CONTROL_SOURCE)
      builder.child("content", RuntimeShaderBuilder(content).makeShader())
    }
    return builder.makeShader()
  }
}
