// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0

package dev.chrisbanes.gradle

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.registerIfAbsent
import org.gradle.kotlin.dsl.withType
import org.gradle.process.ExecOperations
import org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest

class IosSimulatorConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) = with(target) {
    if (!providers.gradleProperty("haze.iosSimulator.managed").map(String::toBoolean).getOrElse(false)) return

    val simulator = gradle.sharedServices.registerIfAbsent("hazeIosTestSimulator", IosSimulatorService::class) {
      maxParallelUsages.set(1)
      parameters.deviceType.set(providers.gradleProperty("haze.iosSimulator.deviceType"))
      parameters.runtime.set(providers.gradleProperty("haze.iosSimulator.runtime"))
      parameters.executable.set(providers.gradleProperty("haze.iosSimulator.executable").orElse("simslim"))
      parameters.xcrun.set(providers.gradleProperty("haze.iosSimulator.xcrun").orElse("/usr/bin/xcrun"))
      parameters.records.set(layout.buildDirectory.dir("managed-ios-simulators"))
      val directory = rootDir
      parameters.profile.fileProvider(providers.gradleProperty("haze.iosSimulator.profile").map { directory.resolve(it) })
    }
    val prepare = tasks.register<PrepareIosSimulator>("prepareIosTestSimulator") {
      group = "verification"
      description = "Creates and slims an isolated simulator shared by this build's iOS tests."
      service.set(simulator)
      usesService(simulator)
      udidFile.set(layout.buildDirectory.file("managed-ios-simulator.udid"))
      outputs.upToDateWhen { false }
    }
    tasks.withType<KotlinNativeSimulatorTest>().configureEach {
      dependsOn(prepare)
      usesService(simulator)
      standalone.set(false)
      device.set(providers.fileContents(prepare.flatMap { it.udidFile }).asText.map(String::trim))
      doNotTrackState("Managed simulator tests use a fresh device for each build.")
    }
  }
}

abstract class PrepareIosSimulator : DefaultTask() {
  @get:Internal
  abstract val service: Property<IosSimulatorService>

  @get:OutputFile
  abstract val udidFile: RegularFileProperty

  @TaskAction
  fun prepare() {
    val udid = service.get().acquire()
    udidFile.get().asFile.apply {
      parentFile.mkdirs()
      writeText(udid)
    }
  }
}

abstract class IosSimulatorService :
  BuildService<IosSimulatorService.Parameters>,
  AutoCloseable {
  interface Parameters : BuildServiceParameters {
    val deviceType: Property<String>
    val runtime: Property<String>
    val executable: Property<String>
    val xcrun: Property<String>
    val records: DirectoryProperty
    val profile: RegularFileProperty
  }

  @get:Inject
  abstract val exec: ExecOperations

  private var simulator: ManagedIosSimulator? = null

  @Synchronized
  fun acquire(): String {
    val owned = simulator ?: ManagedIosSimulator(
      deviceType = parameters.deviceType.orNull
        ?: error("Set -Phaze.iosSimulator.deviceType to an installed CoreSimulator device type."),
      runtime = parameters.runtime.orNull
        ?: error("Set -Phaze.iosSimulator.runtime to an installed iOS runtime identifier."),
      executable = parameters.executable.get(),
      records = parameters.records.get().asFile,
      profile = parameters.profile.orNull?.asFile,
      run = { command ->
        val output = ByteArrayOutputStream()
        val errors = ByteArrayOutputStream()
        val result = exec.exec {
          commandLine(if (command.first() == "xcrun") listOf(parameters.xcrun.get()) + command.drop(1) else command)
          standardOutput = output
          errorOutput = errors
          isIgnoreExitValue = true
        }
        check(result.exitValue == 0) {
          "${command.joinToString(" ")} failed (${result.exitValue}): ${errors.toString(Charsets.UTF_8).takeLast(4000)}"
        }
        output.toString(Charsets.UTF_8).trim()
      },
    ).also { simulator = it }
    return owned.acquire()
  }

  override fun close() {
    simulator?.close()
  }
}

internal class ManagedIosSimulator(
  private val deviceType: String,
  private val runtime: String,
  private val executable: String,
  private val records: File,
  private val profile: File? = null,
  private val run: (List<String>) -> String,
) : AutoCloseable {
  private var udid: String? = null
  private var ready = false
  private var record: File? = null

  fun acquire(): String {
    if (ready) return checkNotNull(udid)
    check(udid == null) { "Simulator preparation already failed; inspect the recorded UDID before retrying." }
    run(listOf(executable, "version"))
    val name = "HazeGradle-${UUID.randomUUID()}"
    records.mkdirs()
    record = File(records, "$name.txt").also { it.writeText("$name\n$deviceType\n$runtime\n") }
    val created = run(listOf("xcrun", "simctl", "create", name, deviceType, runtime))
    UUID.fromString(created)
    val id = created
    udid = id
    record?.appendText("$id\n")
    val selection = profile?.let { listOf("--profile", it.absolutePath) }.orEmpty()
    run(listOf(executable, "on", id) + selection)
    run(listOf(executable, "verify", id) + selection)
    ready = true
    return id
  }

  override fun close() {
    val id = udid ?: return
    // CoreSimulator deletion also shuts down a booted device; retain the record if deletion fails.
    run(listOf("xcrun", "simctl", "delete", id))
    record?.delete()
    udid = null
    ready = false
  }
}
