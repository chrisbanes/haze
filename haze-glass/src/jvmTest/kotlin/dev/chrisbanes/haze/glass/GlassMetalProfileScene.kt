// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.haze.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.io.File
import java.lang.management.ManagementFactory
import java.security.MessageDigest
import kotlin.system.exitProcess
import org.jetbrains.skiko.GraphicsApi

/**
 * Fixed Glass scene for macOS Metal profiling under `xctrace` (#1766). It is a `main`, not a test,
 * so test discovery never opens a window. See `docs/agents/evidence/1766/README.md` for commands.
 *
 * The scene matches the Android `GlassProfilingSample` geometry: `GlassStyle.regular`, `Balanced`,
 * a 280×180 dp grid with 12 dp gaps (four members as 2×2) and a 32 dp merge distance. The source is
 * a drawn gradient with no resources or wall clock; `changing` steps its hue once per frame.
 */
fun main(args: Array<String>) {
  val options = args.toList().chunked(2).associate { (key, value) -> key.removePrefix("--") to value }
  options["dump-shaders"]?.let { dumpShaders(File(it)) }
  val output = options["output"]?.let(::File) ?: return
  val members = options["members"]?.toInt() ?: 1
  require(members in setOf(1, 2, 4)) { "--members must be 1, 2 or 4" }
  val merged = options["merged"]?.toBooleanStrict() ?: false
  val continuous = when (val corners = options["corners"] ?: "circular") {
    "circular" -> false
    "continuous" -> true
    else -> error("Unknown --corners $corners")
  }
  val changing = when (val source = options["source"] ?: "changing") {
    "static" -> false
    "changing" -> true
    else -> error("Unknown --source $source")
  }
  val warmFrames = options["warm-frames"]?.toInt() ?: 240
  val measureNanos = ((options["measure-seconds"]?.toDouble() ?: 5.0) * 1e9).toLong()
  val processStartMs = ManagementFactory.getRuntimeMXBean().startTime

  application {
    val windowState = rememberWindowState(size = DpSize(1280.dp, 720.dp))
    Window(onCloseRequest = ::exitApplication, state = windowState, title = "Glass Metal profile", resizable = false) {
      val hazeState = rememberHazeState()
      val frame = remember { mutableIntStateOf(0) }
      val density = LocalDensity.current.density
      val style = remember(continuous) {
        if (continuous) GlassStyle.regular.then { shape(ContinuousRoundedCornerShape(16.dp)) } else GlassStyle.regular
      }
      LaunchedEffect(Unit) {
        val renderApi = window.renderApi
        if (renderApi != GraphicsApi.METAL) {
          System.err.println("Expected METAL, got $renderApi")
          exitProcess(2)
        }
        // The first callback runs before the first Glass frame draws; the second follows it.
        val firstCallbackMs = System.currentTimeMillis() - processStartMs
        withFrameNanos { frame.intValue++ }
        val secondCallbackMs = System.currentTimeMillis() - processStartMs
        repeat(warmFrames) { withFrameNanos { frame.intValue++ } }
        val startNanos = withFrameNanos { it }
        val startMs = System.currentTimeMillis() - processStartMs
        var frames = 0
        while (withFrameNanos { it } - startNanos < measureNanos) {
          frame.intValue++
          frames++
        }
        val endMs = System.currentTimeMillis() - processStartMs
        output.mkdirs()
        File(output, "run.json").writeText(
          json(
            "members" to members,
            "merged" to merged,
            "corners" to if (continuous) "continuous" else "circular",
            "source" to if (changing) "changing" else "static",
            "renderApi" to renderApi.name,
            "renderInfo" to renderInfo(window),
            "displayScale" to density,
            "os" to "${System.getProperty("os.name")} ${System.getProperty("os.version")} ${System.getProperty("os.arch")}",
            "jvm" to "${System.getProperty("java.vm.name")} ${System.getProperty("java.runtime.version")}",
            "buildSha" to options["build-sha"],
            "processStartEpochMs" to processStartMs,
            "firstFrameCallbackCpuWallMs" to firstCallbackMs,
            "secondFrameCallbackCpuWallMs" to secondCallbackMs,
            "warmFrames" to warmFrames,
            "measureStartMsSinceProcessStart" to startMs,
            "measureEndMsSinceProcessStart" to endMs,
            "measuredFrames" to frames,
          ),
        )
        exitProcess(0)
      }
      Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize().hazeSource(hazeState)) {
          // Read in draw so changing frames redraw the source without recomposition.
          val hue = if (changing) (frame.intValue * 3 % 360).toFloat() else 0f
          drawSourcePattern(hue)
        }
        // Static scenes keep invalidating outside the source, like the Android stable rows.
        Canvas(Modifier.size(1.dp)) {
          drawCircle(Color.Transparent, radius = 1f, center = Offset(frame.intValue % 2f, 0f))
        }
        GlassGrid(
          members = members,
          mergeDistance = if (merged) 32.dp else null,
          input = HazeInput.Sources(hazeState),
          style = style,
          modifier = Modifier.align(Alignment.Center).size(280.dp, 180.dp),
        )
      }
    }
  }
}

@androidx.compose.runtime.Composable
private fun GlassGrid(
  members: Int,
  mergeDistance: androidx.compose.ui.unit.Dp?,
  input: HazeInput,
  style: GlassStyle,
  modifier: Modifier,
) {
  val rows = if (members == 4) 2 else 1
  val gap = if (members == 1) 0.dp else 12.dp
  Column(
    modifier = if (mergeDistance != null) modifier.hazeGlassContainer(mergeDistance) else modifier,
    verticalArrangement = Arrangement.spacedBy(gap),
  ) {
    repeat(rows) {
      Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(gap)) {
        repeat(members / rows) {
          Box(
            Modifier.fillMaxHeight().weight(1f)
              .hazeGlass(input = input, style = style, performanceMode = HazePerformanceMode.Balanced),
          )
        }
      }
    }
  }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSourcePattern(hue: Float) {
  val cell = 40.dp.toPx()
  val columns = (size.width / cell).toInt() + 1
  val rows = (size.height / cell).toInt() + 1
  for (row in 0 until rows) {
    for (column in 0 until columns) {
      val cellHue = (hue + column * 360f / columns) % 360f
      val light = if ((row + column) % 2 == 0) 0.62f else 0.38f
      drawRect(
        color = Color.hsl(cellHue, 0.7f, light),
        topLeft = Offset(column * cell, row * cell),
        size = Size(cell, cell),
      )
    }
  }
}

private fun renderInfo(window: java.awt.Container): String? {
  // Skiko's SkiaLayer is not exposed by ComposeWindow; read its renderInfo when present.
  fun find(component: java.awt.Component): java.awt.Component? = when {
    generateSequence<Class<*>>(component.javaClass) { it.superclass }
      .any { it.name == "org.jetbrains.skiko.SkiaLayer" } -> component
    component is java.awt.Container -> component.components.firstNotNullOfOrNull(::find)
    else -> null
  }
  val layer = find(window) ?: return null
  return runCatching { layer.javaClass.getMethod("getRenderInfo").invoke(layer) as String }.getOrNull()
}

/** Writes the split (Skiko) and fused (Android) sources for both corner styles with SHA-256s. */
private fun dumpShaders(directory: File) {
  directory.mkdirs()
  val entries = listOf(false, true).flatMap { continuous ->
    val suffix = if (continuous) "-continuous" else ""
    listOf(
      "fused" to GlassShaders.buildFused(continuous = continuous),
      "fused-no-detail" to GlassShaders.buildFused(sharpDetail = false, continuous = continuous),
      "fused-interaction" to GlassShaders.buildFused(interactionOptics = true, continuous = continuous),
      "fused-merged" to GlassShaders.buildFused(merged = true, continuous = continuous),
      "optical" to GlassShaders.buildOptical(continuous = continuous),
      "optical-interactive" to GlassShaders.buildOptical(interactive = true, continuous = continuous),
      "optical-merged" to GlassShaders.buildOptical(merged = true, continuous = continuous),
      "detail" to GlassShaders.buildRefractionDetail(continuous = continuous),
      "detail-coverage" to GlassShaders.buildRefractionDetail(coverageOnly = true, continuous = continuous),
      "detail-merged" to GlassShaders.buildRefractionDetail(merged = true, continuous = continuous),
      "rim" to GlassShaders.buildRim(continuous = continuous),
      "rim-merged" to GlassShaders.buildRim(merged = true, continuous = continuous),
      "lighting" to GlassShaders.buildInteractionLighting(continuous = continuous),
      "lighting-merged" to GlassShaders.buildInteractionLighting(merged = true, continuous = continuous),
      "merged-background" to GlassShaders.buildMergedBackground(continuous = continuous),
    ).map { (name, source) -> "$name$suffix" to source }
  }
  val manifest = entries.joinToString(",\n", prefix = "[\n", postfix = "\n]\n") { (name, source) ->
    File(directory, "$name.sksl").writeText(source)
    val sha = MessageDigest.getInstance("SHA-256").digest(source.encodeToByteArray())
      .joinToString("") { "%02x".format(it) }
    "  {\"name\": \"$name\", \"path\": \"$name.sksl\", \"sha256\": \"$sha\", \"bytes\": ${source.length}}"
  }
  File(directory, "shaders.json").writeText(manifest)
}

private fun json(vararg values: Pair<String, Any?>): String = values.joinToString(",\n", "{\n", "\n}\n") { (key, value) ->
  val encoded = when (value) {
    null -> "null"
    is Number, is Boolean -> value.toString()
    else -> "\"" + value.toString().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
  }
  "  \"$key\": $encoded"
}
