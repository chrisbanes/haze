// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.awt.LocalAwtWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import assertk.assertThat
import assertk.assertions.isEqualTo
import assertk.assertions.isFalse
import assertk.assertions.isNotSameInstanceAs
import assertk.assertions.isSameInstanceAs
import assertk.assertions.isTrue
import java.awt.EventQueue
import java.awt.GraphicsEnvironment
import java.awt.Point
import java.awt.Rectangle
import java.awt.Robot
import java.awt.Window
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.LockSupport
import javax.imageio.ImageIO
import kotlin.math.abs
import kotlin.test.Test
import org.jetbrains.skiko.GraphicsApi

@OptIn(ExperimentalComposeUiApi::class)
class HazeDesktopWindowRenderingTest {
  @Test
  fun crossWindowEffect_samplesExpectedScreenRegion() {
    DesktopWindowFixture("crossWindowEffect_samplesExpectedScreenRegion").use { fixture ->
      try {
        fixture.open()
        fixture.awaitDisplayedRegion()
        fixture.saveDiagnostics("initial")
      } catch (failure: Throwable) {
        runCatching { fixture.saveDiagnostics("failure", failure) }.exceptionOrNull()?.let(failure::addSuppressed)
        throw failure
      }
    }
  }

  @Test
  fun effectWindowMove_updatesSampledRegion() {
    withDesktopWindowFixture("effectWindowMove_updatesSampledRegion") { fixture ->
      fixture.moveEffectWindow(Point(260, 196))
      fixture.awaitDisplayedRegion()
      fixture.saveDiagnostics("effect-moved")
    }
  }

  @Test
  fun sourceWindowMove_updatesSampledRegion() {
    withDesktopWindowFixture("sourceWindowMove_updatesSampledRegion") { fixture ->
      fixture.moveSourceWindow(Point(36, 100))
      fixture.awaitDisplayedRegion()
      fixture.saveDiagnostics("source-moved")
    }
  }

  @Test
  fun sourceDrawChange_refreshesEffectWindow() {
    withDesktopWindowFixture("sourceDrawChange_refreshesEffectWindow") { fixture ->
      val previousEffectDraws = fixture.changeSourcePalette()
      fixture.awaitDisplayedRegion(expectedPalette = 1, afterEffectDraws = previousEffectDraws)
      fixture.saveDiagnostics("source-redrawn")
    }
  }

  @Test
  fun windowsReturnToOriginalPosition_restoresSampledRegion() {
    withDesktopWindowFixture("windowsReturnToOriginalPosition_restoresSampledRegion") { fixture ->
      fixture.moveEffectWindow(Point(260, 196))
      fixture.awaitDisplayedRegion()
      fixture.saveDiagnostics("effect-moved")

      fixture.moveSourceWindow(Point(36, 100))
      fixture.awaitDisplayedRegion()
      fixture.saveDiagnostics("both-moved")

      fixture.moveSourceWindow(Point(100, 100))
      fixture.awaitDisplayedRegion()
      fixture.saveDiagnostics("source-restored")

      fixture.moveEffectWindow(Point(196, 196))
      fixture.awaitDisplayedRegion()
      fixture.saveDiagnostics("both-restored")
    }
  }
}

private fun withDesktopWindowFixture(method: String, scenario: (DesktopWindowFixture) -> Unit) {
  DesktopWindowFixture(method).use { fixture ->
    try {
      fixture.open()
      fixture.awaitDisplayedRegion()
      fixture.saveDiagnostics("initial")
      scenario(fixture)
    } catch (failure: Throwable) {
      runCatching { fixture.saveDiagnostics("failure", failure) }.exceptionOrNull()?.let(failure::addSuppressed)
      throw failure
    }
  }
}

@OptIn(ExperimentalComposeUiApi::class)
private class DesktopWindowFixture(method: String) : AutoCloseable {
  private val state = HazeState()
  private val palette = mutableIntStateOf(0)
  private val effectDraws = AtomicInteger()
  private val sourceHost = AtomicReference<Window?>()
  private val effectHost = AtomicReference<Window?>()
  private val sourceDensity = AtomicReference<Float?>()
  private val effectDensity = AtomicReference<Float?>()
  private var sourceWindow: ComposeWindow? = null
  private var effectWindow: ComposeWindow? = null
  private var robot: Robot? = null
  private var lastCapture: BufferedImage? = null
  private var expectedPalette = 0
  private var sourceLocation = Point(100, 100)
  private var effectLocation = Point(196, 196)
  private val outputDirectory = File(
    System.getProperty("haze.desktopWindowTest.outputDir"),
    method,
  )

  fun open() {
    assertThat(GraphicsEnvironment.isHeadless(), "A functioning display is required").isFalse()
    // Own windows before configuring them so partial construction is also cleaned up by use().
    onEdt {
      sourceWindow = ComposeWindow()
      effectWindow = ComposeWindow()
      val source = checkNotNull(sourceWindow)
      val effect = checkNotNull(effectWindow)
      source.isUndecorated = true
      effect.isUndecorated = true
      source.isResizable = false
      effect.isResizable = false
      source.title = "Haze source"
      effect.title = "Haze effect"
      source.setSize(320, 320)
      effect.setSize(64, 64)
      source.setLocation(100, 100)
      effect.setLocation(196, 196)
      val factory = WindowInputDrawingFactory(effectDraws)
      source.setContent {
        val host = LocalAwtWindow.current
        val density = LocalDensity.current.density
        SideEffect {
          sourceDensity.set(density)
          sourceHost.set(host)
        }
        Canvas(Modifier.fillMaxSize().hazeSource(state)) {
          val currentPalette = palette.intValue
          for (row in 0 until 5) {
            for (column in 0 until 5) {
              drawRect(
                color = tileColor(column, row, currentPalette),
                topLeft = Offset(column * 64f, row * 64f),
                size = Size(64f, 64f),
              )
            }
          }
        }
      }
      effect.setContent {
        val host = LocalAwtWindow.current
        val density = LocalDensity.current.density
        SideEffect {
          effectDensity.set(density)
          effectHost.set(host)
        }
        Box(
          Modifier.fillMaxSize().hazeEffect(
            factory = factory,
            input = HazeInput.Sources(state),
            style = Unit,
          ),
        )
      }
      source.isVisible = true
      effect.isVisible = true
      effect.toFront()
    }
    robot = Robot()
    awaitCondition("source registration/capture and effect drawing") {
      onEdt {
        state.areas.size == 1 && state.areas.single().contentLayer != null &&
          state.areas.single().contentVersion > 0 && effectDraws.get() > 0 &&
          sourceHost.get() != null && effectHost.get() != null
      }
    }
    onEdt {
      val source = checkNotNull(sourceWindow)
      val effect = checkNotNull(effectWindow)
      // Verify real composition hosts without depending on Haze's identity implementation.
      assertThat(sourceDensity.get(), "Source Compose density").isEqualTo(1f)
      assertThat(effectDensity.get(), "Effect Compose density").isEqualTo(1f)
      assertThat(sourceHost.get()).isSameInstanceAs(source)
      assertThat(effectHost.get()).isSameInstanceAs(effect)
      assertThat(sourceHost.get()).isNotSameInstanceAs(effectHost.get())
      for (window in listOf(source, effect)) {
        assertThat(window.graphicsConfiguration.defaultTransform.scaleX, "Device scale X").isEqualTo(1.0)
        assertThat(window.graphicsConfiguration.defaultTransform.scaleY, "Device scale Y").isEqualTo(1.0)
        assertThat(window.graphicsConfiguration.defaultTransform.shearX, "Device shear X").isEqualTo(0.0)
        assertThat(window.graphicsConfiguration.defaultTransform.shearY, "Device shear Y").isEqualTo(0.0)
        assertThat(
          window.renderApi in setOf(GraphicsApi.SOFTWARE_FAST, GraphicsApi.SOFTWARE_COMPAT),
          "Active software backend: ${window.renderApi}",
        ).isTrue()
      }
    }
    awaitWindowGeometry()
  }

  fun moveSourceWindow(location: Point) {
    sourceLocation = location
    onEdt { checkNotNull(sourceWindow).setLocation(location) }
    awaitWindowGeometry()
  }

  fun moveEffectWindow(location: Point) {
    effectLocation = location
    onEdt { checkNotNull(effectWindow).setLocation(location) }
    awaitWindowGeometry()
  }

  fun changeSourcePalette(): Int = onEdt {
    val previousEffectDraws = effectDraws.get()
    palette.intValue = 1
    previousEffectDraws
  }

  private fun awaitWindowGeometry() {
    awaitCondition("requested native window geometry: source=$sourceLocation effect=$effectLocation") {
      onEdt {
        sourceWindow?.locationOnScreen == sourceLocation &&
          effectWindow?.locationOnScreen == effectLocation &&
          sourceWindow?.contentPane?.size == java.awt.Dimension(320, 320) &&
          effectWindow?.contentPane?.size == java.awt.Dimension(64, 64)
      }
    }
  }

  fun awaitDisplayedRegion(expectedPalette: Int = 0, afterEffectDraws: Int? = null) {
    this.expectedPalette = expectedPalette
    val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
    var mismatch: String
    do {
      // Robot only reads the native effect rectangle; it never requests rendering.
      val (source, effect) = onEdt { windowRectangles() }
      val image = checkNotNull(robot).createScreenCapture(effect)
      lastCapture = image
      mismatch = sampledRegionMismatch(image, source, effect, expectedPalette)
      if (mismatch.isEmpty() && (afterEffectDraws == null || effectDraws.get() > afterEffectDraws)) {
        assertSampledRegion(image, source, effect, expectedPalette)
        return
      }
      LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(25))
    } while (System.nanoTime() < deadline)
    throw AssertionError(
      "Displayed region pixel mismatch after 10 seconds: $mismatch" +
        "effectDraws=${effectDraws.get()}, required after=$afterEffectDraws",
    )
  }

  fun saveDiagnostics(label: String, failure: Throwable? = null) {
    outputDirectory.mkdirs()
    // Diagnostic read failures must not replace the original assertion or startup failure.
    val geometry = runCatching { onEdt { windowRectangles() } }.getOrNull()
    if (robot != null && geometry != null) {
      runCatching { robot?.createScreenCapture(geometry.second) }.getOrNull()?.let { lastCapture = it }
    }
    lastCapture?.let { ImageIO.write(it, "png", File(outputDirectory, "$label.png")) }
    File(outputDirectory, "$label.txt").writeText(
      buildString {
        appendLine("failure=$failure")
        appendLine("DISPLAY=${System.getenv("DISPLAY")}")
        appendLine("SKIKO_RENDER_API=${System.getenv("SKIKO_RENDER_API")}")
        appendLine("effectDraws=${effectDraws.get()}")
        appendLine("expectedPalette=$expectedPalette")
        appendLine("requested source/effect origins=$sourceLocation/$effectLocation")
        appendLine("source/effect Compose density=${sourceDensity.get()}/${effectDensity.get()}")
        appendLine("sourceHost=${sourceHost.get()}")
        appendLine("effectHost=${effectHost.get()}")
        appendLine("source/effect rectangles=$geometry")
        appendLine(
          runCatching {
            onEdt {
              listOfNotNull(sourceWindow, effectWindow).joinToString("\n") {
                "${it.title}: backend=${it.renderApi}, transform=${it.graphicsConfiguration.defaultTransform}"
              } + "\nsourceAreas=${state.areas}, contentVersion=${state.areas.firstOrNull()?.contentVersion}"
            }
          }.getOrElse { "metadata unavailable: $it" },
        )
        val image = lastCapture
        if (image != null && geometry != null) {
          appendLine(sampledRegionMismatch(image, geometry.first, geometry.second, expectedPalette, includeMatches = true))
        }
      },
    )
  }

  private fun windowRectangles(): Pair<Rectangle, Rectangle> {
    val source = checkNotNull(sourceWindow)
    val effect = checkNotNull(effectWindow)
    return Rectangle(source.locationOnScreen, source.size) to Rectangle(effect.locationOnScreen, effect.size)
  }

  override fun close() {
    onEdt {
      try {
        effectWindow?.dispose()
      } finally {
        sourceWindow?.dispose()
      }
    }
  }
}

private class WindowInputDrawingFactory(
  private val draws: AtomicInteger,
) : HazeEffectFactory<Unit> {
  override fun createRenderer(): HazeEffectRenderer<Unit> = object : HazeEffectRenderer<Unit> {
    override fun HazeEffectDrawScope.draw(style: Unit) {
      drawRect(Color.Magenta)
      drawInput()
      // Distinguish the effect's pixels from the source if native window stacking changes.
      drawRect(Color.Black, alpha = 0.5f)
      draws.incrementAndGet()
    }
  }
}

private fun tileColor(column: Int, row: Int, palette: Int): Color {
  val red = 40 + 32 * column
  val green = 56 + 32 * row
  return if (palette == 0) Color(red, green, 80) else Color(255 - red, 255 - green, 175)
}

private fun assertSampledRegion(image: BufferedImage, source: Rectangle, effect: Rectangle, palette: Int) {
  assertThat(sampledRegionMismatch(image, source, effect, palette), "Displayed region pixel mismatch").isEqualTo("")
}

private fun sampledRegionMismatch(
  image: BufferedImage,
  source: Rectangle,
  effect: Rectangle,
  palette: Int,
  includeMatches: Boolean = false,
): String = buildString {
  for (y in listOf(16, 48)) {
    for (x in listOf(16, 48)) {
      // Expected colours follow the analytic grid at observed screen positions, not Haze transforms.
      val column = (effect.x + x - source.x) / 64
      val row = (effect.y + y - source.y) / 64
      val original = listOf(40 + 32 * column, 56 + 32 * row, 80)
      val sampled = if (palette == 0) original else original.map { 255 - it }
      // A 50% black overlay is applied only by the effect, never by the source window.
      val expected = sampled.map { it / 2 }
      for (dy in -1..1) {
        for (dx in -1..1) {
          val rgb = image.getRGB(x + dx, y + dy)
          val actual = listOf((rgb shr 16) and 255, (rgb shr 8) and 255, rgb and 255)
          if (includeMatches || expected.zip(actual).any { (wanted, observed) -> abs(wanted - observed) > 2 }) {
            appendLine("probe=($x,$y) patch=($dx,$dy) tile=($column,$row) expected=$expected actual=$actual")
          }
        }
      }
    }
  }
}

private fun awaitCondition(description: String, condition: () -> Boolean) {
  val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
  do {
    if (condition()) return
    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(25))
  } while (System.nanoTime() < deadline)
  throw AssertionError("Desktop display prerequisite timed out after 10 seconds: $description")
}

private fun <T> onEdt(block: () -> T): T {
  if (EventQueue.isDispatchThread()) return block()
  val result = CompletableFuture<T>()
  EventQueue.invokeLater {
    try {
      result.complete(block())
    } catch (failure: Throwable) {
      result.completeExceptionally(failure)
    }
  }
  return result.get(10, TimeUnit.SECONDS)
}
