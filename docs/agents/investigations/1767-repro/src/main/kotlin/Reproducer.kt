// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

import java.awt.Dimension
import java.awt.EventQueue
import java.awt.Rectangle
import java.awt.Robot
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JFrame
import kotlin.system.exitProcess
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import org.jetbrains.skiko.SkiaLayer
import org.jetbrains.skiko.SkikoRenderDelegate

private fun resource(name: String): String =
  checkNotNull(object {}.javaClass.getResource("/$name")).readText()

fun main(args: Array<String>) {
  require(args.size == 2 && args[0] in setOf("retained", "control")) { "Expected retained|control and output directory" }
  val output = File(args[1]).apply { mkdirs() }
  try {
    check(System.getProperty("os.name").startsWith("Windows")) { "Windows Direct3D required" }
    val source = resource("${args[0]}.sksl")
    File(output, "shader.sksl").writeText(source)
    val timings = File(output, "timings.csv").apply { writeText("phase,ms\n") }
    fun measure(phase: String, block: () -> Unit) {
      File(output, "phase.txt").writeText("$phase,${System.currentTimeMillis()}")
      val start = System.nanoTime()
      block()
      timings.appendText("$phase,${(System.nanoTime() - start) / 1e6}\n")
    }
    val effect = RuntimeEffect.makeForShader(source)
    val contentEffect = RuntimeEffect.makeForShader(resource("content.sksl"))
    val content = RuntimeShaderBuilder(contentEffect).makeShader()
    val builder = RuntimeShaderBuilder(effect)
    for (line in resource("uniforms.txt").lineSequence().filter { it.isNotBlank() }) {
      val (name, values) = line.split('=', limit = 2)
      builder.uniform(name, values.split(',').map { it.trim().toFloat() }.toFloatArray())
    }
    builder.child("content", content)
    val shader = builder.makeShader()
    val robot = Robot()
    lateinit var frame: JFrame
    lateinit var layer: SkiaLayer
    var drawShader = false
    EventQueue.invokeAndWait {
      layer = SkiaLayer()
      layer.renderDelegate = object : SkikoRenderDelegate {
        override fun onRender(canvas: Canvas, width: Int, height: Int, nanoTime: Long) {
          canvas.clear(0xFF303030.toInt())
          if (drawShader) {
            Paint().use { paint ->
              paint.shader = shader
              canvas.drawRect(Rect.makeWH(64f, 48f), paint)
            }
          }
        }
      }
      frame = JFrame("Direct3D cold draw").apply {
        contentPane.add(layer)
        contentPane.preferredSize = Dimension(64, 48)
        pack()
        setLocation(30, 30)
        isVisible = true
      }
    }
    try {
      EventQueue.invokeAndWait {
        repeat(3) { layer.renderImmediately() }
        File(output, "renderer.txt").writeText("${layer.renderApi}\n${layer.renderInfo}\n")
        check(layer.renderApi.toString() == "DIRECT3D") { "Wrong backend: ${layer.renderApi}" }
        drawShader = true
        measure("first-draw") { layer.renderImmediately() }
        repeat(6) { measure("warm-$it") { layer.renderImmediately() } }
      }
      robot.waitForIdle()
      lateinit var bounds: Rectangle
      EventQueue.invokeAndWait { bounds = Rectangle(layer.locationOnScreen, layer.size) }
      val image = robot.createScreenCapture(bounds)
      ImageIO.write(image, "png", File(output, "render.png"))
      check((0 until image.height).flatMap { y ->
        (0 until image.width).map { x -> image.getRGB(x, y) }
      }.distinct().size > 3) { "Blank output" }
    } finally {
      EventQueue.invokeAndWait { layer.dispose(); frame.dispose() }
      shader.close()
      content.close()
      contentEffect.close()
      effect.close()
    }
    exitProcess(0)
  } catch (failure: Throwable) {
    File(output, "error.txt").writeText(failure.stackTraceToString())
    exitProcess(2)
  }
}
