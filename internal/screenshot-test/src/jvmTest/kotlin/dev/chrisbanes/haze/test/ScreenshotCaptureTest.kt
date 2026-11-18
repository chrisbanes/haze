// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

@file:OptIn(ExperimentalTestApi::class, ExperimentalRoborazziApi::class, InternalRoborazziApi::class)

package dev.chrisbanes.haze.test

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.v2.runSkikoComposeUiTest
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import assertk.assertions.isNotEmpty
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.InternalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziTaskType
import com.github.takahirom.roborazzi.UiTreeDumpOptions
import com.github.takahirom.roborazzi.provideRoborazziContext
import io.github.takahirom.roborazzi.captureRoboImage
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.Test

class ScreenshotCaptureTest {

  private fun capture(
    options: com.github.takahirom.roborazzi.RoborazziOptions,
    withContent: Boolean = true,
  ): List<File> {
    val dir = createTempDirectory("haze-capture").toFile()
    try {
      runSkikoComposeUiTest {
        if (withContent) {
          setContent { androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(Color.Red)) }
        }
        val context = provideRoborazziContext()
        val previous = context.outputDirectory
        context.setRuleOverrideOutputDirectory(dir.absolutePath)
        try {
          captureRootArtifact("capture.webp", options)
        } finally {
          context.setRuleOverrideOutputDirectory(previous)
        }
      }
      return dir.walkTopDown().filter(File::isFile).toList()
    } finally {
      dir.deleteRecursively()
    }
  }

  private val recording = HazeRoborazziDefaults.roborazziOptions.copy(taskType = RoborazziTaskType.Record)

  @Test
  fun captureRootArtifact_withUiTreeDump_writesDiagnostics() {
    val files = capture(recording.copy(uiTreeDumpOptions = UiTreeDumpOptions()))

    assertThat(files.map(File::getName)).contains("capture.webp")
    assertThat(files.map(File::getName).filter { it != "capture.webp" }).isNotEmpty()
  }

  @Test
  fun captureRootArtifact_withoutUiTreeDump_writesOnlyImage() {
    val files = capture(recording.copy(uiTreeDumpOptions = null))

    assertThat(files.map(File::getName)).isEqualTo(listOf("capture.webp"))
  }

  @Test
  fun captureRootArtifact_whenDisabled_doesNotResolveRoot() {
    // No content is set, so touching the semantics root would throw.
    val files = capture(
      recording.copy(taskType = RoborazziTaskType.None, uiTreeDumpOptions = UiTreeDumpOptions()),
      withContent = false,
    )

    assertThat(files).isEmpty()
  }

  @Test
  fun captureRootArtifact_withoutUiTreeDump_matchesFullRootCapture() {
    val dir = createTempDirectory("haze-capture").toFile()
    try {
      runSkikoComposeUiTest {
        setContent { androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(Color.Red)) }
        val context = provideRoborazziContext()
        val previous = context.outputDirectory
        context.setRuleOverrideOutputDirectory(dir.absolutePath)
        try {
          captureRootArtifact("fast.webp", recording)
          onRoot().captureToImage().captureRoboImage("full.webp", recording)
        } finally {
          context.setRuleOverrideOutputDirectory(previous)
        }
      }
      val fast = dir.walkTopDown().first { it.name == "fast.webp" }.readBytes()
      val full = dir.walkTopDown().first { it.name == "full.webp" }.readBytes()
      assertThat(fast.toList()).isEqualTo(full.toList())
    } finally {
      dir.deleteRecursively()
    }
  }
}
