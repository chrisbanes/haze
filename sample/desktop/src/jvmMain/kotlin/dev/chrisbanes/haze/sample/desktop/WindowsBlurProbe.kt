// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(dev.chrisbanes.haze.InternalHazeApi::class)

package dev.chrisbanes.haze.sample.desktop

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import dev.chrisbanes.haze.createProgressiveBlurRenderEffect
import java.awt.Dimension
import java.awt.EventQueue
import java.awt.Rectangle
import java.awt.Robot
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JFrame
import kotlin.concurrent.thread
import kotlin.system.exitProcess
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skiko.SkiaLayer
import org.jetbrains.skiko.SkikoRenderDelegate

/** Diagnostic wall times, including Skiko draw/flush/present; these are not GPU timer queries. */
internal fun runWindowsBlurProbe() {
  check(System.getProperty("os.name").startsWith("Windows"))
  System.setProperty("skiko.hardwareInfo.enabled", "true")
  val probeWidth = System.getenv("HAZE_PROBE_WIDTH")?.toInt() ?: 640
  val probeHeight = probeWidth * 3 / 4
  val output = File("build/outputs/windows-blur-probe").apply { mkdirs() }
  val timings = File(output, "timings.csv").apply { writeText("window,phase,milliseconds\n") }
  thread(name = "probe-timeout", isDaemon = true) {
    Thread.sleep(180_000)
    System.err.println("PROBE_TIMEOUT: no completion within 180 seconds")
    exitProcess(2)
  }
  fun measure(window: Int, phase: String, block: () -> Unit) {
    println("PROBE_START window=$window phase=$phase")
    System.out.flush()
    val start = System.nanoTime()
    block()
    val ms = (System.nanoTime() - start) / 1_000_000.0
    timings.appendText("$window,$phase,$ms\n")
    println("PROBE_END window=$window phase=$phase ms=$ms")
  }

  try {
    val robot = Robot()
    repeat(2) { window ->
      lateinit var frame: JFrame
      lateinit var layer: SkiaLayer
      var effect: ImageFilter? = null
      EventQueue.invokeAndWait {
        layer = SkiaLayer()
        layer.renderDelegate = object : SkikoRenderDelegate {
          override fun onRender(canvas: Canvas, width: Int, height: Int, nanoTime: Long) {
            canvas.clear(0xFFFFFFFF.toInt())
            Paint().use { paint ->
              paint.imageFilter = effect
              canvas.saveLayer(Rect.makeWH(probeWidth.toFloat(), probeHeight.toFloat()), paint)
              paint.imageFilter = null
              for (x in 0 until probeWidth step 8) {
                paint.color = if (x % 16 == 0) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                canvas.drawRect(Rect.makeXYWH(x.toFloat(), 0f, 8f, probeHeight.toFloat()), paint)
              }
              canvas.restore()
            }
          }
        }
        frame = JFrame("Haze progressive blur probe $window").apply {
          defaultCloseOperation = JFrame.DISPOSE_ON_CLOSE
          contentPane.add(layer)
          contentPane.preferredSize = Dimension(probeWidth, probeHeight)
          pack()
          setLocation(40, 40)
          isVisible = true
        }
      }
      try {
        EventQueue.invokeAndWait {
          repeat(3) { measure(window, "baseline-$it") { layer.renderImmediately() } }
          val info = "window=$window requested=${System.getenv("SKIKO_RENDER_API")} actual=${layer.renderApi}\n${layer.renderInfo}\n"
          println(info)
          File(output, "renderer.txt").appendText(info)
        }
        robot.waitForIdle()
        lateinit var bounds: Rectangle
        EventQueue.invokeAndWait { bounds = Rectangle(layer.locationOnScreen, layer.size) }
        val baseline = robot.createScreenCapture(bounds)
        ImageIO.write(baseline, "png", File(output, "window-$window-baseline.png"))
        EventQueue.invokeAndWait {
          measure(window, "construct-progressive") {
            val size = Size(probeWidth.toFloat(), probeHeight.toFloat())
            val mask = (Brush.verticalGradient(listOf(Color.Black, Color.Transparent), endY = size.height) as ShaderBrush)
              .createShader(size)
            effect = createProgressiveBlurRenderEffect(24f, size, Offset.Zero, mask)
          }
          repeat(4) { measure(window, "progressive-$it") { layer.renderImmediately() } }
        }
        robot.waitForIdle()
        val blurred = robot.createScreenCapture(bounds)
        ImageIO.write(blurred, "png", File(output, "window-$window-progressive.png"))
        val changed = (0 until baseline.height).sumOf { y ->
          (0 until baseline.width).count { x -> baseline.getRGB(x, y) != blurred.getRGB(x, y) }
        }
        check(changed > baseline.width * baseline.height / 100) { "Progressive draw did not visibly change the screen: $changed pixels" }
        println("PROBE_PIXELS window=$window changed=$changed")
      } finally {
        EventQueue.invokeAndWait {
          layer.dispose()
          frame.dispose()
          effect?.close()
        }
      }
    }
    println("PROBE_COMPLETE")
    exitProcess(0)
  } catch (failure: Throwable) {
    failure.printStackTrace()
    exitProcess(1)
  }
}
