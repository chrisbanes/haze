// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.gradle

import assertk.assertFailure
import assertk.assertThat
import assertk.assertions.contains
import assertk.assertions.containsExactly
import assertk.assertions.hasMessage
import assertk.assertions.isEmpty
import assertk.assertions.isEqualTo
import java.nio.file.Path
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ManagedIosSimulatorTest {
  @TempDir
  lateinit var directory: Path

  private val udid = "23AE16CF-D297-4A91-A615-5BA5B20D700B"

  @Test
  fun acquire_sharesOneVerifiedDeviceAndCloseDeletesOnlyThatDevice() {
    val commands = mutableListOf<List<String>>()
    val simulator = simulator { command ->
      commands += command
      if (command.take(3) == listOf("xcrun", "simctl", "create")) udid else ""
    }

    assertThat(simulator.acquire()).isEqualTo(udid)
    assertThat(simulator.acquire()).isEqualTo(udid)
    assertThat(directory.toFile().listFiles()!!.single().readText()).contains(udid)
    simulator.close()
    simulator.close()

    assertThat(commands.map { it.take(3) }).containsExactly(
      listOf("simslim", "version"),
      listOf("xcrun", "simctl", "create"),
      listOf("simslim", "on", udid),
      listOf("simslim", "verify", udid),
      listOf("xcrun", "simctl", "delete"),
    )
    assertThat(commands.last()).containsExactly("xcrun", "simctl", "delete", udid)
    assertThat(directory.toFile().listFiles()!!.toList()).isEmpty()
  }

  @Test
  fun acquire_failedSlimmingStillDeletesCreatedDevice() {
    val commands = mutableListOf<List<String>>()
    val simulator = simulator { command ->
      commands += command
      when {
        command.take(3) == listOf("xcrun", "simctl", "create") -> udid
        command.take(2) == listOf("simslim", "on") -> error("slimming failed")
        else -> ""
      }
    }
    assertFailure { simulator.acquire() }.hasMessage("slimming failed")
    simulator.close()
    assertThat(commands.last()).containsExactly("xcrun", "simctl", "delete", udid)
  }

  @Test
  fun acquire_missingSimSlimDoesNotCreateDevice() {
    val commands = mutableListOf<List<String>>()
    val simulator = simulator {
      commands += it
      error("not installed")
    }
    assertFailure { simulator.acquire() }.hasMessage("not installed")
    simulator.close()
    assertThat(commands).containsExactly(listOf("simslim", "version"))
  }

  @Test
  fun acquire_failedCreationRetainsNameForRecovery() {
    val simulator = simulator { command ->
      if (command.first() == "xcrun") error("creation failed") else ""
    }
    assertFailure { simulator.acquire() }.hasMessage("creation failed")
    simulator.close()
    assertThat(directory.toFile().listFiles()!!.single().readText()).contains("HazeGradle-")
  }

  @Test
  fun close_failedDeletionPreservesRecoveryRecord() {
    val simulator = simulator { command ->
      when (command.take(3)) {
        listOf("xcrun", "simctl", "create") -> udid
        listOf("xcrun", "simctl", "delete") -> error("deletion failed")
        else -> ""
      }
    }
    simulator.acquire()
    assertFailure { simulator.close() }.hasMessage("deletion failed")
    assertThat(directory.toFile().listFiles()!!.single().readText()).contains(udid)
  }

  @Test
  fun acquire_appliesSameProfileForSlimmingAndVerification() {
    val commands = mutableListOf<List<String>>()
    val profile = directory.resolve("profile.json").toFile()
    val simulator = ManagedIosSimulator("phone", "runtime", "simslim", directory.toFile(), profile) {
      commands += it
      if (it.take(3) == listOf("xcrun", "simctl", "create")) udid else ""
    }
    simulator.acquire()
    assertThat(commands[2]).containsExactly("simslim", "on", udid, "--profile", profile.absolutePath)
    assertThat(commands[3]).containsExactly("simslim", "verify", udid, "--profile", profile.absolutePath)
    simulator.close()
  }

  private fun simulator(run: (List<String>) -> String) = ManagedIosSimulator("phone", "runtime", "simslim", directory.toFile(), run = run)
}
