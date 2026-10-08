// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestLogEvent

allprojects {
  tasks.withType<Test>().configureEach {
    if (project.path == ":haze-screenshot-tests" && name == "testAndroidHostTest") {
      // The two slow classes run separately; every other test and matrix verifier stays together.
      val partition = project.providers.gradleProperty("haze.androidScreenshotPartition").orNull
      val slowClasses = listOf(
        "dev.chrisbanes.haze.GlassDepthAndroidScreenshotTest",
        "dev.chrisbanes.haze.GlassContentScreenshotTest",
      )
      when (partition) {
        null -> Unit
        "depth-content" -> filter { slowClasses.forEach(::includeTestsMatching) }
        "remaining" -> filter { slowClasses.forEach(::excludeTestsMatching) }
        else -> error("Unknown Android screenshot partition: $partition")
      }
    }
    testLogging {
      events(TestLogEvent.STARTED, TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
    }
  }
}
