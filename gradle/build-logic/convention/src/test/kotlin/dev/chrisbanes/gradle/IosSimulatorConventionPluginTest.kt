// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.gradle

import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.isEqualTo
import java.nio.file.Path
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class IosSimulatorConventionPluginTest {
  @TempDir
  lateinit var directory: Path

  @Test
  fun preparation_sharesDeviceAcrossProjectsAndSupportsConfigurationCacheReuse() {
    fixture()
    val arguments = arrayOf(":one:prepareIosTestSimulator", ":two:prepareIosTestSimulator")
    runner(*arguments).build()
    assertLifecycle()
    trace().writeText("")
    val second = runner(*arguments).build()
    assertThat(second.output).contains("Reusing configuration cache.")
    assertLifecycle()
  }

  @Test
  fun preparation_failedVerificationCleansUpDevice() {
    fixture()
    val result = runner(":one:prepareIosTestSimulator", fail = "verify").buildAndFail()
    assertThat(result.output).contains("verification failed")
    assertLifecycle()
  }

  @Test
  fun consumer_failedTestCleansUpDevice() {
    fixture()
    directory.resolve("one/build.gradle.kts").toFile().appendText(
      """

      tasks.register("failedTest") {
        dependsOn("prepareIosTestSimulator")
        usesService(gradle.sharedServices.registrations.getByName("hazeIosTestSimulator").service)
        doLast { error("test failure") }
      }
      """.trimIndent(),
    )
    val result = runner(":one:failedTest").buildAndFail()
    assertThat(result.output).contains("test failure")
    assertLifecycle()
  }

  @Test
  fun help_doesNotProvisionDevice() {
    fixture()
    runner("help").build()
    assertThat(trace().exists()).isEqualTo(false)
  }

  @Test
  fun default_doesNotRegisterManagedTasks() {
    fixture()
    val result = runner(":one:tasks", "--all", "-Phaze.iosSimulator.managed=false").build()
    assertThat(result.output.contains("prepareIosTestSimulator")).isEqualTo(false)
    assertThat(trace().exists()).isEqualTo(false)
  }

  private fun assertLifecycle() {
    val lines = trace().readLines()
    assertThat(lines.count { it.startsWith("simctl create ") }).isEqualTo(1)
    assertThat(lines.count { it == "on $UDID" }).isEqualTo(1)
    assertThat(lines.count { it == "verify $UDID" }).isEqualTo(1)
    assertThat(lines.count { it == "simctl delete $UDID" }).isEqualTo(1)
  }

  private fun trace() = directory.resolve("commands.txt").toFile()

  private fun fixture() {
    directory.resolve("settings.gradle.kts").toFile().writeText("rootProject.name = \"fixture\"\ninclude(\"one\", \"two\")\n")
    for (project in listOf("one", "two")) {
      directory.resolve(project).toFile().mkdirs()
      directory.resolve("$project/build.gradle.kts").toFile().writeText(
        "plugins { id(\"dev.chrisbanes.ios.managed-simulator\") }\n",
      )
    }
    val bin = directory.resolve("bin").toFile().apply { mkdirs() }
    bin.resolve("xcrun").apply {
      writeText(
        """
        #!/bin/sh
        printf '%s\n' "${'$'}*" >> "${'$'}SIMSLIM_TEST_TRACE"
        case "${'$'}1 ${'$'}2" in
          'simctl create') echo '$UDID' ;;
          'simctl delete') ;;
          *) exit 1 ;;
        esac
        """.trimIndent() + "\n",
      )
      setExecutable(true)
    }
    bin.resolve("simslim").apply {
      writeText(
        """
        #!/bin/sh
        printf '%s\n' "${'$'}*" >> "${'$'}SIMSLIM_TEST_TRACE"
        if [ "${'$'}1" = "${'$'}SIMSLIM_TEST_FAIL" ]; then
          echo 'verification failed' >&2
          exit 1
        fi
        """.trimIndent() + "\n",
      )
      setExecutable(true)
    }
  }

  private fun runner(vararg tasks: String, fail: String = "") = GradleRunner.create()
    .withProjectDir(directory.toFile())
    .withPluginClasspath()
    .withEnvironment(
      System.getenv() + mapOf(
        "SIMSLIM_TEST_TRACE" to trace().absolutePath,
        "SIMSLIM_TEST_FAIL" to fail,
      ),
    )
    .withArguments(
      "-Phaze.iosSimulator.managed=true",
      "-Phaze.iosSimulator.deviceType=phone",
      "-Phaze.iosSimulator.runtime=runtime",
      "-Phaze.iosSimulator.executable=${directory.resolve("bin/simslim")}",
      "-Phaze.iosSimulator.xcrun=${directory.resolve("bin/xcrun")}",
      "--configuration-cache",
      "--configuration-cache-problems=fail",
      "--no-scan",
      *tasks,
    )

  private companion object {
    const val UDID = "23AE16CF-D297-4A91-A615-5BA5B20D700B"
  }
}
