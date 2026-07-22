// Copyright 2026, Christopher Banes and the Haze project contributors
// SPDX-License-Identifier: Apache-2.0


import org.gradle.api.tasks.Exec
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
  id("dev.chrisbanes.kotlin.multiplatform")
  id("dev.chrisbanes.compose")
}

kotlin {
  jvm()
  compilerOptions { optIn.add("dev.chrisbanes.haze.ExperimentalHazeApi") }
  sourceSets {
    jvmMain.dependencies {
      implementation(projects.internal.benchmarkDesktop)
      implementation(projects.hazeGlass)
      implementation(projects.sample.shared)
      implementation(compose.desktop.currentOs)
      implementation(libs.kotlinx.coroutines.swing)
    }
    jvmTest.dependencies {
      implementation(kotlin("test"))
      implementation(libs.assertk)
    }
  }
}

compose.desktop.application {
  mainClass = "dev.chrisbanes.haze.benchmark.desktop.glass.MainKt"
}

val benchmarkJavaLauncher = javaToolchains.launcherFor {
  languageVersion.set(JavaLanguageVersion.of(21))
}

fun registerScenarioSmoke(name: String, scenarioId: String) = tasks.register<Exec>(name) {
  dependsOn("packageUberJarForCurrentOS")
  environment("SKIKO_RENDER_API", "METAL")
  val output = layout.buildDirectory.file("benchmark-smoke/$scenarioId.json")
  outputs.file(output)
  commandLine(
    benchmarkJavaLauncher.get().executablePath.asFile.absolutePath,
    "-Xms512m",
    "-Xmx512m",
    "-jar",
    layout.buildDirectory.file(
      "compose/jars/desktop-macos-arm64-1.0.0.jar",
    ).get().asFile.absolutePath,
    "run",
    "--scenario", scenarioId,
    "--revision", "smoke",
    "--round", "0",
    "--order", "0",
    "--output", output.get().asFile.absolutePath,
    "--smoke",
  )
}

val pointerSmoke = registerScenarioSmoke("desktopPointerBenchmarkSmoke", "pointer_sweep")
val playgroundSmoke = registerScenarioSmoke("desktopPlaygroundBenchmarkSmoke", "playground_drag")

playgroundSmoke.configure { mustRunAfter(pointerSmoke) }

tasks.register("desktopBenchmarkSmoke") {
  dependsOn(pointerSmoke, playgroundSmoke)
}
