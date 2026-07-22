// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.benchmark.desktop

import androidx.compose.ui.awt.ComposeWindow
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import org.jetbrains.skiko.SkiaLayer

internal data class BenchmarkWindowEnvironment(
  val composeVersion: String,
  val skikoVersion: String,
  val renderApi: String,
  val framebufferWidth: Int,
  val framebufferHeight: Int,
  val contentScale: Float,
  val refreshRateHz: Int,
)

internal fun collectBenchmarkEnvironment(
  window: BenchmarkWindowEnvironment,
): BenchmarkEnvironment = BenchmarkEnvironment(
  osName = metadata(System.getProperty("os.name")),
  osVersion = metadata(System.getProperty("os.version")),
  architecture = metadata(System.getProperty("os.arch")),
  cpu = metadata(sysctl("machdep.cpu.brand_string")),
  memoryBytes = sysctl("hw.memsize").toLong(),
  javaVendor = metadata(System.getProperty("java.vendor")),
  javaVersion = metadata(System.getProperty("java.version")),
  composeVersion = metadata(window.composeVersion),
  skikoVersion = metadata(window.skikoVersion),
  renderApi = metadata(window.renderApi),
  framebufferWidth = window.framebufferWidth,
  framebufferHeight = window.framebufferHeight,
  contentScale = window.contentScale,
  refreshRateHz = window.refreshRateHz,
  runnerImage = System.getenv("ImageOS")?.let(::metadata),
  runnerImageVersion = System.getenv("ImageVersion")?.let(::metadata),
)

internal fun benchmarkWindowEnvironment(
  window: ComposeWindow,
  layer: SkiaLayer,
): BenchmarkWindowEnvironment = BenchmarkWindowEnvironment(
  composeVersion = ComposeWindow::class.java.`package`.implementationVersion ?: "unknown",
  skikoVersion = SkiaLayer::class.java.`package`.implementationVersion ?: "unknown",
  renderApi = window.renderApi.name,
  framebufferWidth = (layer.width * layer.contentScale).roundToInt(),
  framebufferHeight = (layer.height * layer.contentScale).roundToInt(),
  contentScale = layer.contentScale,
  refreshRateHz = window.graphicsConfiguration.device.displayMode.refreshRate,
)

private fun sysctl(name: String): String {
  val process = ProcessBuilder("/usr/sbin/sysctl", "-n", name)
    .redirectErrorStream(true)
    .start()
  return try {
    check(process.waitFor(2, TimeUnit.SECONDS)) { "sysctl $name timed out" }
    val output = process.inputStream.bufferedReader().use { it.readText().trim() }
    check(process.exitValue() == 0) { "sysctl $name failed: ${metadata(output)}" }
    output
  } finally {
    process.closeStreamsAndTerminate()
  }
}

private fun Process.closeStreamsAndTerminate() {
  runCatching { outputStream.close() }
  runCatching { inputStream.close() }
  runCatching { errorStream.close() }
  if (isAlive) {
    destroy()
    if (isAlive) destroyForcibly()
  }
}

private fun metadata(value: String?): String {
  val source = value ?: "unknown"
  return buildString {
    for (character in source) {
      val candidate = toString() + character
      if (candidate.encodeToByteArray().size > 256) break
      append(character)
    }
  }
}
