// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.benchmark.desktop

import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.awt.ComposeWindow
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.jetbrains.skiko.ExperimentalSkikoApi
import org.jetbrains.skiko.GraphicsApi
import org.jetbrains.skiko.SkiaLayer

@OptIn(ExperimentalComposeUiApi::class, ExperimentalSkikoApi::class)
internal class ComposeDesktopBenchmarkHost() {
  suspend fun runScenario(
    scenarioFactory: () -> DesktopBenchmarkScenario,
  ): BenchmarkScenarioResult {
    val scenario = scenarioFactory().also(::validateScenario)
    val recorder = FrameRecorder()
    val session = openWindow(recorder) { scenario.Content() }
    try {
      val replayer = DesktopInputReplayer(session.layer)
      scenario.reset()
      replayer.replay(scenario.events)
      scenario.verifyCompleted()

      scenario.reset()
      delay(500)
      recorder.startMeasurement()
      replayer.replay(scenario.events)
      scenario.verifyCompleted()
      val finalFrame = onSwing {
        recorder.requestNextFrameCompletion { session.layer.needRender() }
      }
      withTimeout(2.seconds) {
        while (!recorder.isFrameCompleted(finalFrame)) delay(1)
      }
      val samples = recorder.stopMeasurement()
      validateMeasuredSamples(samples)

      return BenchmarkScenarioResult(
        id = scenario.id,
        environment = collectBenchmarkEnvironment(session.environment),
        renderDuration = summarizeMetric(samples.map { it.renderDurationNanos }),
        callbackInterval = summarizeMetric(samples.mapNotNull { it.callbackIntervalNanos }),
      )
    } finally {
      disposeWindow(session.window)
    }
  }

  private suspend fun openWindow(
    recorder: FrameRecorder,
    content: @Composable () -> Unit,
  ): BenchmarkWindowSession = onSwing {
    val window = ComposeWindow(skiaLayerAnalytics = recorder)
    try {
      window.isUndecorated = true
      window.isResizable = false
      window.contentPane.preferredSize = Dimension(TARGET_FRAMEBUFFER_WIDTH, TARGET_FRAMEBUFFER_HEIGHT)
      window.setContent { content() }
      window.pack()
      window.setLocationRelativeTo(null)
      window.isVisible = true

      val layer = window.contentPane.singleSkiaLayer()
      layer.checkContentScale()
      val scale = layer.contentScale
      check(scale.isFinite() && scale > 0f) { "Invalid Skia content scale: $scale" }
      val logicalWidth = (TARGET_FRAMEBUFFER_WIDTH / scale).roundToInt()
      val logicalHeight = (TARGET_FRAMEBUFFER_HEIGHT / scale).roundToInt()
      window.contentPane.preferredSize = Dimension(logicalWidth, logicalHeight)
      window.pack()
      window.validate()
      window.renderImmediately()

      check(window.renderApi == GraphicsApi.METAL) {
        "Desktop benchmark requires METAL but Skiko selected ${window.renderApi}"
      }
      val environment = benchmarkWindowEnvironment(window, layer)
      check(abs(environment.framebufferWidth - TARGET_FRAMEBUFFER_WIDTH) <= 1) {
        "Desktop benchmark requires a ${TARGET_FRAMEBUFFER_WIDTH}x$TARGET_FRAMEBUFFER_HEIGHT backing " +
          "surface but created ${environment.framebufferWidth}x${environment.framebufferHeight}"
      }
      check(abs(environment.framebufferHeight - TARGET_FRAMEBUFFER_HEIGHT) <= 1) {
        "Desktop benchmark requires a ${TARGET_FRAMEBUFFER_WIDTH}x$TARGET_FRAMEBUFFER_HEIGHT backing " +
          "surface but created ${environment.framebufferWidth}x${environment.framebufferHeight}"
      }
      BenchmarkWindowSession(window, layer, environment)
    } catch (failure: Throwable) {
      window.dispose()
      throw failure
    }
  }
}

private data class BenchmarkWindowSession(
  val window: ComposeWindow,
  val layer: SkiaLayer,
  val environment: BenchmarkWindowEnvironment,
)

private suspend fun <T> onSwing(block: () -> T): T = withContext(Dispatchers.Swing) { block() }

private suspend fun disposeWindow(window: ComposeWindow) {
  withContext(NonCancellable + Dispatchers.Swing) { window.dispose() }
}

private fun Container.singleSkiaLayer(): SkiaLayer {
  val layers = descendants().filterIsInstance<SkiaLayer>().toList()
  check(layers.size == 1) { "Expected exactly one SkiaLayer but found ${layers.size}" }
  return layers.single()
}

private fun Container.descendants(): Sequence<Component> = sequence {
  components.forEach { component ->
    yield(component)
    if (component is Container) yieldAll(component.descendants())
  }
}

private const val TARGET_FRAMEBUFFER_WIDTH = 1280
private const val TARGET_FRAMEBUFFER_HEIGHT = 720

internal fun validateMeasuredSamples(samples: List<FrameSample>) {
  check(samples.size >= 2) {
    "Desktop benchmark requires at least 2 measured render callbacks for callback-interval " +
      "metrics but recorded ${samples.size}"
  }
}
